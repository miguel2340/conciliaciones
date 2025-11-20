package com.pagosyradicacion.backend.listatareas;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class ListaTareasService {

  private final JdbcTemplate jdbc;
  private static final long METRICS_TTL_MS = TimeUnit.MINUTES.toMillis(10);
  private final ConcurrentHashMap<String, CacheEntry> metricsCache = new ConcurrentHashMap<>();

  private static final class CacheEntry {
    final long ts;
    final Map<String, Object> data;
    CacheEntry(long ts, Map<String, Object> data) { this.ts = ts; this.data = data; }
  }

  public ListaTareasService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /* ===================== SQL Builders ===================== */

  private String baseCTE() {
    return """
        ;WITH pagos_traza_agrupado AS (
            SELECT 
                nit     = LTRIM(RTRIM(REPLACE(REPLACE(CAST(pt.identificacion AS VARCHAR(30)),'.',''),'-',''))),
                voucher = LTRIM(RTRIM(CAST(pt.voucher AS VARCHAR(50)))),
                valor_causado = SUM(TRY_CONVERT(DECIMAL(18,2), pt.valor_causado))
            FROM fomagf.dbo.pagos_traza pt WITH (NOLOCK)
            WHERE pt.identificacion IS NOT NULL
              AND pt.voucher IS NOT NULL AND pt.voucher <> ''
            GROUP BY
                LTRIM(RTRIM(REPLACE(REPLACE(CAST(pt.identificacion AS VARCHAR(30)),'.',''),'-',''))),
                LTRIM(RTRIM(CAST(pt.voucher AS VARCHAR(50))))
        ),
        rad_union_base AS (
            SELECT 
                nit     = LTRIM(RTRIM(REPLACE(REPLACE(CAST(rf.nit AS VARCHAR(30)),'.',''),'-',''))),
                voucher = LTRIM(RTRIM(CAST(rf.voucher AS VARCHAR(50)))),
                valor_factura = TRY_CONVERT(DECIMAL(18,2), rf.valor_factura),
                valor_pagado  = TRY_CONVERT(DECIMAL(18,2), rf.valor_pagado)
            FROM fomagf.dbo.radicacion_filtrada rf WITH (NOLOCK)
            WHERE rf.voucher IS NOT NULL AND rf.voucher <> ''

            UNION ALL

            SELECT 
                nit     = LTRIM(RTRIM(REPLACE(REPLACE(CAST(rfc.nit AS VARCHAR(30)),'.',''),'-',''))),
                voucher = LTRIM(RTRIM(CAST(rfc.voucher AS VARCHAR(50)))),
                valor_factura = TRY_CONVERT(DECIMAL(18,2), rfc.valor_factura),
                valor_pagado  = TRY_CONVERT(DECIMAL(18,2), rfc.valor_pagado)
            FROM fomagf.dbo.radicacion_filtrada_capita rfc WITH (NOLOCK)
            WHERE rfc.voucher IS NOT NULL AND rfc.voucher <> ''
        ),
        rad_union_agrupado AS (
            SELECT
                nit,
                voucher,
                valor_factura_radicado = SUM(valor_factura),
                valor_pagado_pagos     = SUM(valor_pagado)
            FROM rad_union_base
            GROUP BY nit, voucher
        )
        """;
  }

  private String fromJoin() {
    return """
        FROM pagos_traza_agrupado pt
        LEFT JOIN rad_union_agrupado r 
          ON r.nit = pt.nit 
         AND r.voucher = pt.voucher
        """;
  }

  private String whereFor(String type) {
    return switch (type) {
      case "general" -> "WHERE r.valor_factura_radicado IS NOT NULL";
      case "faltantes" -> "WHERE r.valor_factura_radicado IS NULL";
      case "pagado_mayor" -> "WHERE r.valor_pagado_pagos > pt.valor_causado";
      default -> "";
    };
  }

  private String selectFor(String type) {
    if ("pagos_no_cruzan".equals(type)) {
      return """
          ;WITH pagos_clean AS (
              SELECT 
                  modalidad = UPPER(LTRIM(RTRIM(p.modalidad))),
                  id        = LTRIM(RTRIM(CAST(p.id AS VARCHAR(50)))),
                  nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(p.nit AS VARCHAR(30)),'.',''),'-',''))),
                  voucher   = LTRIM(RTRIM(CAST(p.voucher AS VARCHAR(50)))),
                  valor_pagado = TRY_CONVERT(DECIMAL(18,2), p.valor_pagado)
              FROM dbo.pagos p WITH (NOLOCK)

              UNION ALL

              SELECT 
                  modalidad = UPPER(LTRIM(RTRIM(pc.modalidad))),
                  id        = LTRIM(RTRIM(CAST(pc.id AS VARCHAR(50)))),
                  nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(pc.nit AS VARCHAR(30)),'.',''),'-',''))),
                  voucher   = LTRIM(RTRIM(CAST(pc.voucher AS VARCHAR(50)))),
                  valor_pagado = TRY_CONVERT(DECIMAL(18,2), pc.valor_pagado)
              FROM dbo.pagos_capita pc WITH (NOLOCK)
          ),
          rad_clean AS (
              SELECT
                  modalidad = UPPER(LTRIM(RTRIM(r.modalidad_pago))),
                  id        = LTRIM(RTRIM(CAST(r.id AS VARCHAR(50)))),
                  nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(r.nit AS VARCHAR(30)),'.',''),'-','')))
              FROM dbo.radicacion3 r WITH (NOLOCK)

              UNION ALL

              SELECT
                  modalidad = UPPER(LTRIM(RTRIM(rc.[Modalidad Pago]))),
                  id        = LTRIM(RTRIM(CAST(rc.[ID] AS VARCHAR(50)))),
                  nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(rc.[NIT] AS VARCHAR(30)),'.',''),'-','')))
              FROM dbo.radicacion_capita rc WITH (NOLOCK)
          )
          SELECT
              modalidad = p.modalidad,
              id        = p.id,
              nit       = p.nit,
              voucher   = p.voucher,
              valor_pagado = p.valor_pagado
          FROM pagos_clean p
          LEFT JOIN rad_clean r
                 ON r.modalidad = p.modalidad
                AND r.id        = p.id
                AND r.nit       = p.nit
          WHERE r.id IS NULL
            AND p.modalidad IS NOT NULL
            AND p.modalidad <> '0'
            AND p.id IS NOT NULL
            AND p.nit IS NOT NULL
          ORDER BY p.modalidad, p.nit, p.id
          """;
    }

    String cte = baseCTE();

    if ("no_en_traza".equals(type)) {
      return cte + """
          SELECT 
              r.nit,
              voucher = r.voucher,
              valor_causado = NULL,
              valor_factura_radicado = r.valor_factura_radicado,
              valor_pagado_pagos     = r.valor_pagado_pagos
          FROM rad_union_agrupado r
          WHERE r.voucher IS NOT NULL AND r.voucher <> ''
            AND NOT EXISTS (
                  SELECT 1
                  FROM pagos_traza_agrupado pt
                  WHERE pt.nit = r.nit
                    AND pt.voucher = r.voucher
            )
          ORDER BY r.nit, r.voucher
          """;
    }

    if ("nit_no_en_traza".equals(type)) {
      return cte + """
          SELECT 
              r.nit,
              voucher = NULL,
              valor_causado = NULL,
              valor_factura_radicado = NULL,
              valor_pagado_pagos = NULL
          FROM rad_union_agrupado r
          WHERE NOT EXISTS (
              SELECT 1
              FROM pagos_traza_agrupado pt
              WHERE pt.nit = r.nit
          )
          GROUP BY r.nit
          ORDER BY r.nit
          """;
    }

    if ("pagado_mayor_fact".equals(type)) {
      return """
          SELECT
              nit = LTRIM(RTRIM(REPLACE(REPLACE(CAST(nit AS VARCHAR(30)),'.',''),'-',''))),
              TRY_CONVERT(DECIMAL(18,2), valor_factura) AS valor_factura,
              TRY_CONVERT(DECIMAL(18,2), valor_pagado)  AS valor_pagado,
              voucher = LTRIM(RTRIM(CAST(voucher AS VARCHAR(50))))
          FROM fomagf.dbo.radicacion_filtrada WITH (NOLOCK)
          WHERE voucher IS NOT NULL AND voucher <> ''
            AND TRY_CONVERT(DECIMAL(18,2), valor_factura) IS NOT NULL
            AND TRY_CONVERT(DECIMAL(18,2), valor_pagado)  IS NOT NULL
            AND TRY_CONVERT(DECIMAL(18,2), valor_factura) <> 0
            AND TRY_CONVERT(DECIMAL(18,2), valor_pagado)  > TRY_CONVERT(DECIMAL(18,2), valor_factura)

          UNION ALL

          SELECT
              nit = LTRIM(RTRIM(REPLACE(REPLACE(CAST(nit AS VARCHAR(30)),'.',''),'-',''))),
              TRY_CONVERT(DECIMAL(18,2), valor_factura) AS valor_factura,
              TRY_CONVERT(DECIMAL(18,2), valor_pagado)  AS valor_pagado,
              voucher = LTRIM(RTRIM(CAST(voucher AS VARCHAR(50))))
          FROM fomagf.dbo.radicacion_filtrada_capita WITH (NOLOCK)
          WHERE voucher IS NOT NULL AND voucher <> ''
            AND TRY_CONVERT(DECIMAL(18,2), valor_factura) IS NOT NULL
            AND TRY_CONVERT(DECIMAL(18,2), valor_pagado)  IS NOT NULL
            AND TRY_CONVERT(DECIMAL(18,2), valor_factura) <> 0
            AND TRY_CONVERT(DECIMAL(18,2), valor_pagado)  > TRY_CONVERT(DECIMAL(18,2), valor_factura)
          """;
    }

    // general, faltantes, pagado_mayor
    String from = fromJoin();
    String where = whereFor(type);
    return cte + """
        SELECT 
            pt.nit,
            voucher = pt.voucher,
            pt.valor_causado,
            r.valor_factura_radicado,
            r.valor_pagado_pagos
        """ + from + "\n" + where + "\nORDER BY pt.nit, pt.voucher";
  }

  /* ===================== Public API ===================== */

  public Map<String, Object> metricsCached(String type, boolean fresh) {
    if (!fresh) {
      CacheEntry ce = metricsCache.get(type);
      if (ce != null && (System.currentTimeMillis() - ce.ts) < METRICS_TTL_MS) {
        return ce.data;
      }
    }
    Map<String, Object> data = metricsFor(type);
    metricsCache.put(type, new CacheEntry(System.currentTimeMillis(), data));
    return data;
  }

  public Map<String, Map<String, Object>> refreshAll(List<String> types) {
    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    for (String t : types) {
      Map<String, Object> m = metricsFor(t);
      metricsCache.put(t, new CacheEntry(System.currentTimeMillis(), m));
      out.put(t, m);
    }
    return out;
  }

  /**
   * Verifica en qué consultas de "Lista de Tareas" aparece un NIT.
   * No bloquea descarga; solo informa los tipos coincidentes.
   */
  public Map<String, Object> checkNitMembership(String nitRaw) {
    String nit = normalizeNit(nitRaw);
    List<String> tipos = List.of(
      "faltantes", "pagado_mayor", "no_en_traza", "nit_no_en_traza", "pagado_mayor_fact", "pagos_no_cruzan"
    );
    List<String> matches = new ArrayList<>();
    for (String t : tipos) {
      if (existsIn(t, nit)) matches.add(t);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("nit", nitRaw);
    out.put("nitNorm", nit);
    out.put("matches", matches);
    out.put("found", !matches.isEmpty());
    return out;
  }

  private boolean existsIn(String type, String nitNorm) {
    String sql = existsSqlFor(type);
    List<Integer> rows = jdbc.query(sql, ps -> { ps.setString(1, nitNorm); }, (rs, i) -> 1);
    return !rows.isEmpty();
  }

  // Construye una consulta TOP 1 sin ORDER BY y con filtro por NIT, evitando anidar CTEs
  private String existsSqlFor(String type) {
    switch (type) {
      case "faltantes":
      case "pagado_mayor": {
        String cte = baseCTE();
        String where = whereFor(type);
        String from = fromJoin();
        return "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 10000; " +
               cte + "\nSELECT TOP 1 1\n" + from + "\n" + where + " AND pt.nit = ?";
      }
      case "no_en_traza": {
        String cte = baseCTE();
        return "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 10000; " +
               cte + "\nSELECT TOP 1 1\nFROM rad_union_agrupado r\nWHERE r.voucher IS NOT NULL AND r.voucher <> ''\n  AND NOT EXISTS (\n        SELECT 1 FROM pagos_traza_agrupado pt WHERE pt.nit = r.nit AND pt.voucher = r.voucher\n  )\n  AND r.nit = ?";
      }
      case "nit_no_en_traza": {
        String cte = baseCTE();
        return "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 10000; " +
               cte + "\nSELECT TOP 1 1\nFROM rad_union_agrupado r\nWHERE NOT EXISTS ( SELECT 1 FROM pagos_traza_agrupado pt WHERE pt.nit = r.nit )\n  AND r.nit = ?";
      }
      case "pagado_mayor_fact": {
        String union = """
            SELECT
                nit = LTRIM(RTRIM(REPLACE(REPLACE(CAST(nit AS VARCHAR(30)),'.',''),'-',''))),
                TRY_CONVERT(DECIMAL(18,2), valor_factura) AS valor_factura,
                TRY_CONVERT(DECIMAL(18,2), valor_pagado)  AS valor_pagado,
                voucher = LTRIM(RTRIM(CAST(voucher AS VARCHAR(50))))
            FROM fomagf.dbo.radicacion_filtrada WITH (NOLOCK)
            WHERE voucher IS NOT NULL AND voucher <> ''
              AND TRY_CONVERT(DECIMAL(18,2), valor_factura) IS NOT NULL
              AND TRY_CONVERT(DECIMAL(18,2), valor_pagado)  IS NOT NULL
              AND TRY_CONVERT(DECIMAL(18,2), valor_factura) <> 0
              AND TRY_CONVERT(DECIMAL(18,2), valor_pagado)  > TRY_CONVERT(DECIMAL(18,2), valor_factura)

            UNION ALL

            SELECT
                nit = LTRIM(RTRIM(REPLACE(REPLACE(CAST(nit AS VARCHAR(30)),'.',''),'-',''))),
                TRY_CONVERT(DECIMAL(18,2), valor_factura) AS valor_factura,
                TRY_CONVERT(DECIMAL(18,2), valor_pagado)  AS valor_pagado,
                voucher = LTRIM(RTRIM(CAST(voucher AS VARCHAR(50))))
            FROM fomagf.dbo.radicacion_filtrada_capita WITH (NOLOCK)
            WHERE voucher IS NOT NULL AND voucher <> ''
              AND TRY_CONVERT(DECIMAL(18,2), valor_factura) IS NOT NULL
              AND TRY_CONVERT(DECIMAL(18,2), valor_pagado)  IS NOT NULL
              AND TRY_CONVERT(DECIMAL(18,2), valor_factura) <> 0
              AND TRY_CONVERT(DECIMAL(18,2), valor_pagado)  > TRY_CONVERT(DECIMAL(18,2), valor_factura)
            """;
        return "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 10000; SELECT TOP 1 1 FROM (" + union + ") q WHERE q.nit = ?";
      }
      case "pagos_no_cruzan": {
        String sql = """
            ;WITH pagos_clean AS (
                SELECT 
                    modalidad = UPPER(LTRIM(RTRIM(p.modalidad))),
                    id        = LTRIM(RTRIM(CAST(p.id AS VARCHAR(50)))),
                    nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(p.nit AS VARCHAR(30)),'.',''),'-',''))),
                    voucher   = LTRIM(RTRIM(CAST(p.voucher AS VARCHAR(50)))),
                    valor_pagado = TRY_CONVERT(DECIMAL(18,2), p.valor_pagado)
                FROM dbo.pagos p WITH (NOLOCK)

                UNION ALL

                SELECT 
                    modalidad = UPPER(LTRIM(RTRIM(pc.modalidad))),
                    id        = LTRIM(RTRIM(CAST(pc.id AS VARCHAR(50)))),
                    nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(pc.nit AS VARCHAR(30)),'.',''),'-',''))),
                    voucher   = LTRIM(RTRIM(CAST(pc.voucher AS VARCHAR(50)))),
                    valor_pagado = TRY_CONVERT(DECIMAL(18,2), pc.valor_pagado)
                FROM dbo.pagos_capita pc WITH (NOLOCK)
            ),
            rad_clean AS (
                SELECT
                    modalidad = UPPER(LTRIM(RTRIM(r.modalidad_pago))),
                    id        = LTRIM(RTRIM(CAST(r.id AS VARCHAR(50)))),
                    nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(r.nit AS VARCHAR(30)),'.',''),'-','')))
                FROM dbo.radicacion3 r WITH (NOLOCK)
                UNION ALL
                SELECT
                    modalidad = UPPER(LTRIM(RTRIM(rc.[Modalidad Pago]))),
                    id        = LTRIM(RTRIM(CAST(rc.[ID] AS VARCHAR(50)))),
                    nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(rc.[NIT] AS VARCHAR(30)),'.',''),'-','')))
                FROM dbo.radicacion_capita rc WITH (NOLOCK)
            )
            SELECT TOP 1 1
            FROM pagos_clean p
            LEFT JOIN rad_clean r
                   ON r.modalidad = p.modalidad
                  AND r.id        = p.id
                  AND r.nit       = p.nit
            WHERE r.id IS NULL
              AND p.modalidad IS NOT NULL
              AND p.modalidad <> '0'
              AND p.id IS NOT NULL
              AND p.nit IS NOT NULL
              AND p.nit = ?
            """;
        return "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 10000; " + sql;
      }
      default:
        // por defecto, usar la variante 'faltantes'
        String cte = baseCTE();
        String where = whereFor("faltantes");
        String from = fromJoin();
        return "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 10000; " +
               cte + "\nSELECT TOP 1 1\n" + from + "\n" + where + " AND pt.nit = ?";
    }
  }

  private static String normalizeNit(String nit) {
    if (nit == null) return "";
    String s = nit.trim();
    s = s.replace(".", "").replace("-", "");
    return s;
  }

  public Map<String, Object> metricsFor(String type) {
    if ("general".equals(type)) {
      String sqlPT = """
          SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 20000;
          ;WITH pt AS (
              SELECT
                  voucher = LTRIM(RTRIM(CAST(voucher AS VARCHAR(50)))),
                  valor_causado = TRY_CONVERT(DECIMAL(18,2), valor_causado),
                  valor_pagado  = TRY_CONVERT(DECIMAL(18,2), valor_pagado)
              FROM fomagf.dbo.pagos_traza WITH (NOLOCK)
              WHERE voucher IS NOT NULL AND voucher <> ''
          ),
          pt_causado_por_voucher AS (
              SELECT voucher, valor_causado_v = MAX(COALESCE(valor_causado,0))
              FROM pt GROUP BY voucher
          ),
          agg_causado AS ( SELECT SUM(valor_causado_v) AS suma_causado FROM pt_causado_por_voucher ),
          agg_pagado  AS ( SELECT SUM(COALESCE(valor_pagado,0)) AS suma_pagado  FROM pt )
          SELECT c.suma_causado, p.suma_pagado
          FROM agg_causado c CROSS JOIN agg_pagado p;
          """;
      Map<String, Object> pt = jdbc.queryForMap(sqlPT);
      double sumCausadoPT = toDouble(pt.get("suma_causado"));
      double sumPagadoPT = toDouble(pt.get("suma_pagado"));

      String cte = baseCTE();
      String from = fromJoin();
      String sqlRF = cte + """
          SELECT
              rows         = COUNT(1),
              suma_factura = SUM(COALESCE(r.valor_factura_radicado,0))
          """ + from + "\nWHERE r.valor_factura_radicado IS NOT NULL;";

      Map<String, Object> rf = jdbc.queryForMap(
          "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 20000; " + sqlRF);
      // Suma adicional de cápita desde dbo.radicacion_capita
      String sqlCapita = """
          SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 20000;
          SELECT suma_capita = SUM(TRY_CONVERT(DECIMAL(18,2), [Valor Factura]))
          FROM dbo.radicacion_capita WITH (NOLOCK);
          """;
      Map<String, Object> cap = jdbc.queryForMap(sqlCapita);
      double sumaCapita = toDouble(cap.get("suma_capita"));
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("rows", toInt(rf.get("rows")));
      out.put("causado", sumCausadoPT);
      out.put("pagado", sumPagadoPT);
      out.put("factura", toDouble(rf.get("suma_factura")) + sumaCapita);
      return out;
    }

    if ("pagos_no_cruzan".equals(type)) {
      String sql = """
          SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 20000;
          ;WITH pagos_clean AS (
              SELECT 
                  modalidad = UPPER(LTRIM(RTRIM(p.modalidad))),
                  id        = LTRIM(RTRIM(CAST(p.id AS VARCHAR(50)))),
                  nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(p.nit AS VARCHAR(30)),'.',''),'-',''))),
                  valor_pagado = TRY_CONVERT(DECIMAL(18,2), p.valor_pagado)
              FROM dbo.pagos p WITH (NOLOCK)
              UNION ALL
              SELECT 
                  modalidad = UPPER(LTRIM(RTRIM(pc.modalidad))),
                  id        = LTRIM(RTRIM(CAST(pc.id AS VARCHAR(50)))),
                  nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(pc.nit AS VARCHAR(30)),'.',''),'-',''))),
                  valor_pagado = TRY_CONVERT(DECIMAL(18,2), pc.valor_pagado)
              FROM dbo.pagos_capita pc WITH (NOLOCK)
          ),
          rad_clean AS (
              SELECT
                  modalidad = UPPER(LTRIM(RTRIM(r.modalidad_pago))),
                  id        = LTRIM(RTRIM(CAST(r.id AS VARCHAR(50)))),
                  nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(r.nit AS VARCHAR(30)),'.',''),'-','')))
              FROM dbo.radicacion3 r WITH (NOLOCK)
              UNION ALL
              SELECT
                  modalidad = UPPER(LTRIM(RTRIM(rc.[Modalidad Pago]))),
                  id        = LTRIM(RTRIM(CAST(rc.[ID] AS VARCHAR(50)))),
                  nit       = LTRIM(RTRIM(REPLACE(REPLACE(CAST(rc.[NIT] AS VARCHAR(30)),'.',''),'-','')))
              FROM dbo.radicacion_capita rc WITH (NOLOCK)
          )
          SELECT
              filas       = COUNT_BIG(1),
              suma_pagado = SUM(COALESCE(p.valor_pagado,0))
          FROM pagos_clean p
          LEFT JOIN rad_clean r
                 ON r.modalidad = p.modalidad
                AND r.id        = p.id
                AND r.nit       = p.nit
          WHERE r.id IS NULL
            AND p.modalidad IS NOT NULL
            AND p.modalidad <> '0'
            AND p.id IS NOT NULL
            AND p.nit IS NOT NULL;
          """;
      Map<String, Object> row = jdbc.queryForMap(sql);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("rows", toInt(row.get("filas")));
      out.put("causado", 0.0);
      out.put("pagado", toDouble(row.get("suma_pagado")));
      out.put("factura", 0.0);
      return out;
    }

    String cte = baseCTE();
    String from = fromJoin();

    if ("no_en_traza".equals(type)) {
      String sql = cte + """
          SELECT
              rows         = COUNT(1),
              suma_causado = 0.0,
              suma_pagado  = SUM(COALESCE(r.valor_pagado_pagos,0)),
              suma_factura = SUM(COALESCE(r.valor_factura_radicado,0))
          FROM rad_union_agrupado r
          WHERE r.voucher IS NOT NULL AND r.voucher <> ''
            AND NOT EXISTS (
                  SELECT 1 FROM pagos_traza_agrupado pt
                  WHERE pt.nit = r.nit AND pt.voucher = r.voucher
            )
          """;
      Map<String, Object> row = jdbc.queryForMap(
          "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 20000; " + sql);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("rows", toInt(row.get("rows")));
      out.put("causado", 0.0);
      out.put("pagado", toDouble(row.get("suma_pagado")));
      out.put("factura", toDouble(row.get("suma_factura")));
      return out;
    }

    if ("nit_no_en_traza".equals(type)) {
      String sql = cte + """
          SELECT rows = COUNT(DISTINCT r.nit)
          FROM rad_union_agrupado r
          WHERE NOT EXISTS (SELECT 1 FROM pagos_traza_agrupado pt WHERE pt.nit = r.nit)
          """;
      Map<String, Object> row = jdbc.queryForMap(
          "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 20000; " + sql);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("rows", toInt(row.get("rows")));
      out.put("causado", 0.0);
      out.put("pagado", 0.0);
      out.put("factura", 0.0);
      return out;
    }

    if ("faltantes".equals(type) || "pagado_mayor".equals(type)) {
      String where = whereFor(type);
      String sql = cte + """
          SELECT
              rows           = COUNT(1),
              suma_causado   = SUM(COALESCE(pt.valor_causado,0)),
              suma_pagado    = SUM(COALESCE(r.valor_pagado_pagos,0)),
              suma_factura   = SUM(COALESCE(r.valor_factura_radicado,0))
          """ + from + "\n" + where;
      Map<String, Object> row = jdbc.queryForMap(
          "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 20000; " + sql);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("rows", toInt(row.get("rows")));
      out.put("causado", toDouble(row.get("suma_causado")));
      out.put("pagado", toDouble(row.get("suma_pagado")));
      out.put("factura", toDouble(row.get("suma_factura")));
      return out;
    }

    if ("pagado_mayor_fact".equals(type)) {
      Map<String, Object> row = jdbc.queryForMap(summaryForPagadoMayorFact_Originales());
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("rows", toInt(row.get("filas")));
      out.put("causado", 0.0);
      out.put("pagado", toDouble(row.get("suma_pagado")));
      out.put("factura", toDouble(row.get("suma_factura")));
      Map<String, Object> extra = new LinkedHashMap<>();
      extra.put("vouchers", toInt(row.get("vouchers")));
      extra.put("prestadores", toInt(row.get("prestadores")));
      out.put("extra", extra);
      return out;
    }

    return Map.of("rows", 0, "causado", 0.0, "pagado", 0.0, "factura", 0.0);
  }

  public List<Map<String, Object>> rowsFor(String type, int limit) {
    String sql = selectFor(type).trim();
    String trimmed = sql.stripLeading();
    boolean startsWithCTE = trimmed.startsWith(";WITH") || trimmed.startsWith("WITH");
    String finalSql;
    if (startsWithCTE) {
      finalSql = String.format(Locale.ROOT, "SET ROWCOUNT %d; %s; SET ROWCOUNT 0;", limit, sql);
    } else {
      finalSql = String.format(Locale.ROOT, "SELECT TOP %d * FROM ( %s ) AS data", limit, sql);
    }
    finalSql = "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 12000; " + finalSql;
    return jdbc.queryForList(finalSql);
  }

  public void writeCsv(String type, Writer writer) throws IOException {
    // Headers depend on report type
    List<String> headers = new ArrayList<>();
    if ("pagos_no_cruzan".equals(type)) {
      headers = List.of("modalidad", "id", "nit", "voucher", "valor_pagado");
    } else if ("pagado_mayor_fact".equals(type)) {
      headers = List.of("nit", "valor_factura", "valor_pagado", "voucher");
    } else {
      headers = List.of("nit", "voucher", "valor_causado", "valor_factura_radicado", "valor_pagado_pagos");
    }
    writeCsvRow(writer, headers);

    String sql = selectFor(type);
    String finalSql = "SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 20000; " + sql;

    jdbc.query(finalSql, new RowCallbackHandler() {
      @Override
      public void processRow(ResultSet rs) throws SQLException {
        try {
          List<String> cols = new ArrayList<>();
          if ("pagos_no_cruzan".equals(type)) {
            cols.add(safe(rs.getString("modalidad")));
            cols.add(safe(rs.getString("id")));
            cols.add(safe(rs.getString("nit")));
            cols.add(safe(rs.getString("voucher")));
            cols.add(asString(rs.getObject("valor_pagado")));
          } else if ("pagado_mayor_fact".equals(type)) {
            cols.add(safe(rs.getString("nit")));
            cols.add(asString(rs.getObject("valor_factura")));
            cols.add(asString(rs.getObject("valor_pagado")));
            cols.add(safe(rs.getString("voucher")));
          } else {
            cols.add(safe(rs.getString("nit")));
            cols.add(safe(rs.getString("voucher")));
            cols.add(asString(rs.getObject("valor_causado")));
            cols.add(asString(rs.getObject("valor_factura_radicado")));
            cols.add(asString(rs.getObject("valor_pagado_pagos")));
          }
          writeCsvRow(writer, cols);
        } catch (IOException ioe) {
          throw new SQLException("CSV write error", ioe);
        }
      }
    });
  }

  /* ===================== Helpers ===================== */

  private static void writeCsvRow(Writer w, List<String> values) throws IOException {
    // Semicolon-separated, values quoted with double quotes; double quotes escaped by doubling
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) sb.append(';');
      String v = values.get(i);
      if (v == null) v = "";
      sb.append('"').append(v.replace("\"", "\"\"")) .append('"');
    }
    sb.append('\n');
    w.write(sb.toString());
  }

  private static String safe(String v) {
    return v == null ? "" : v;
  }

  private static String asString(Object o) {
    if (o == null) return "";
    if (o instanceof BigDecimal bd) return bd.toPlainString();
    return String.valueOf(o);
  }

  private static int toInt(Object v) {
    if (v == null) return 0;
    if (v instanceof Number n) return n.intValue();
    try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
  }

  private static double toDouble(Object v) {
    if (v == null) return 0.0;
    if (v instanceof Number n) return n.doubleValue();
    try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0.0; }
  }

  private String summaryForPagadoMayorFact_Originales() {
    return """
        SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED; SET LOCK_TIMEOUT 8000;

        ;WITH src AS (
            SELECT 
                nit           = LTRIM(RTRIM(REPLACE(REPLACE(CAST(rf.nit AS VARCHAR(30)),'.',''),'-',''))),
                voucher       = LTRIM(RTRIM(CAST(rf.voucher AS VARCHAR(50)))),
                valor_factura = TRY_CONVERT(DECIMAL(18,2), rf.valor_factura),
                valor_pagado  = TRY_CONVERT(DECIMAL(18,2), rf.valor_pagado)
            FROM fomagf.dbo.radicacion_filtrada rf WITH (NOLOCK)
            WHERE TRY_CONVERT(DECIMAL(18,2), rf.valor_factura) IS NOT NULL
              AND TRY_CONVERT(DECIMAL(18,2), rf.valor_pagado)  IS NOT NULL

            UNION ALL

            SELECT 
                nit           = LTRIM(RTRIM(REPLACE(REPLACE(CAST(rfc.nit AS VARCHAR(30)),'.',''),'-',''))),
                voucher       = LTRIM(RTRIM(CAST(rfc.voucher AS VARCHAR(50)))),
                valor_factura = TRY_CONVERT(DECIMAL(18,2), rfc.valor_factura),
                valor_pagado  = TRY_CONVERT(DECIMAL(18,2), rfc.valor_pagado)
            FROM fomagf.dbo.radicacion_filtrada_capita rfc WITH (NOLOCK)
            WHERE TRY_CONVERT(DECIMAL(18,2), rfc.valor_factura) IS NOT NULL
              AND TRY_CONVERT(DECIMAL(18,2), rfc.valor_pagado)  IS NOT NULL
        ),
        filtrado AS (
            SELECT *
            FROM src
            WHERE valor_factura <> 0
              AND valor_pagado  > valor_factura
        )
        SELECT
            vouchers     = COUNT(DISTINCT voucher),
            prestadores  = COUNT(DISTINCT nit),
            filas        = COUNT(1),
            suma_factura = SUM(valor_factura),
            suma_pagado  = SUM(valor_pagado)
        FROM filtrado;
        """;
  }
}
