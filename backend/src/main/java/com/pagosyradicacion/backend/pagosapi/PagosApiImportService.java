package com.pagosyradicacion.backend.pagosapi;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PagosApiImportService {

  private final RemotePagosClient remote;
  private final JdbcTemplate jdbc;

  public PagosApiImportService(RemotePagosClient remote, JdbcTemplate jdbc) {
    this.remote = remote;
    this.jdbc = jdbc;
  }

  public Map<String, Object> resumenPorFecha(LocalDate fecha) {
    List<Map<String, Object>> rows = remote.fetchPagosAll(fecha);

    // Aggregate
    double totalFactura = 0.0;
    double totalPagado = 0.0;
    int cantidad = 0;
    Map<String, VoucherResumen> vouchers = new LinkedHashMap<>();

    for (Map<String, Object> r : rows) {
      cantidad++;
      double vf = toDouble(r.get("valor_factura"));
      double vp = toDouble(r.get("valor_pagado"));
      totalFactura += vf;
      totalPagado += vp;
      String v = safeStr(r.get("voucher"));
      VoucherResumen agg = vouchers.computeIfAbsent(v, k -> new VoucherResumen(k));
      agg.cantidad++;
      agg.totalFactura += vf;
      agg.totalPagado += vp;
    }

    List<Map<String, Object>> voucherList = new ArrayList<>();
    for (VoucherResumen v : vouchers.values()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("voucher", v.voucher);
      m.put("cantidad", v.cantidad);
      m.put("total_valor_pagado", round2(v.totalPagado));
      m.put("total_valor_factura", round2(v.totalFactura));
      voucherList.add(m);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("fecha", fecha.toString());
    Map<String, Object> metricas = new LinkedHashMap<>();
    metricas.put("cantidad", cantidad);
    metricas.put("total_valor_pagado", round2(totalPagado));
    metricas.put("total_valor_factura", round2(totalFactura));
    out.put("metricas", metricas);
    out.put("vouchers", voucherList);
    return out;
  }

  @Transactional
  public Map<String, Object> aprobar(LocalDate fecha, String voucher, String observacion) {
    List<Map<String, Object>> rows = remote.fetchPagosAll(fecha);
    if (voucher != null && !voucher.isBlank()) {
      String vv = voucher.trim();
      rows.removeIf(r -> !vv.equals(safeStr(r.get("voucher"))));
    }
    if (rows.isEmpty()) {
      return Map.of("ok", Boolean.TRUE, "insertados", 0, "saltados", 0);
    }

    crearStaging();
    try {
      int totalInserted = 0;
      final int CHUNK = 5000; // reduce peak usage en BD
      for (int i = 0; i < rows.size(); i += CHUNK) {
        int end = Math.min(i + CHUNK, rows.size());
        List<Map<String, Object>> sub = rows.subList(i, end);
        insertarStaging(sub, observacion);
        prevalidarMontosContraEsquema("pagos");
        int ins = insertarDefinitivoDesdeStaging("pagos");
        totalInserted += ins;
        // Liberar espacio del staging inmediatamente
        try { jdbc.update("DELETE FROM pagos_api_staging"); } catch (Exception ignore) {}
      }
      dropStaging();
      int saltados = Math.max(0, rows.size() - totalInserted);
      return Map.of("ok", Boolean.TRUE, "insertados", totalInserted, "saltados", saltados);
    } catch (RuntimeException ex) {
      dropStagingSeguro();
      throw ex;
    } catch (Exception ex) {
      dropStagingSeguro();
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error aprobando pagos: " + ex.getMessage(), ex);
    }
  }

  private static String safeStr(Object o) {
    return o == null ? null : String.valueOf(o);
  }

  private static double toDouble(Object v) {
    if (v == null) return 0.0;
    if (v instanceof Number n) return n.doubleValue();
    try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0.0; }
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  // API remoto puede estar abierto; no exigimos token.
  private void ensureConfigured() { /* noop */ }

  private void crearStaging() {
    jdbc.execute("IF OBJECT_ID('pagos_api_staging','U') IS NOT NULL DROP TABLE pagos_api_staging");
    jdbc.execute("""
        CREATE TABLE pagos_api_staging (
          id VARCHAR(100) NULL,
          modalidad VARCHAR(50) NULL,
          nit VARCHAR(50) NULL,
          nombre_prest VARCHAR(255) NULL,
          prefijo VARCHAR(50) NULL,
          no_fact VARCHAR(50) NULL,
          num_factura VARCHAR(50) NULL,
          fecha_factura VARCHAR(50) NULL,
          fecha_radicacion VARCHAR(50) NULL,
          mes_anio_radicacion VARCHAR(50) NULL,
          valor_factura VARCHAR(100) NULL,
          valor_pagado VARCHAR(100) NULL,
          porcentaje_pago VARCHAR(50) NULL,
          estado VARCHAR(50) NULL,
          voucher VARCHAR(50) NULL,
          feccha_pago VARCHAR(50) NULL,
          fuente_origen VARCHAR(100) NULL,
          observacion VARCHAR(255) NULL
        )
        """);
  }

  private void dropStaging() { jdbc.execute("DROP TABLE pagos_api_staging"); }
  private void dropStagingSeguro() { try { jdbc.execute("IF OBJECT_ID('pagos_api_staging','U') IS NOT NULL DROP TABLE pagos_api_staging"); } catch (Exception ignore) {} }

  private void insertarStaging(List<Map<String, Object>> rows, String observacion) {
    List<String[]> mapped = new ArrayList<>(rows.size());
    for (Map<String, Object> r : rows) {
      String[] v = new String[18];
      v[0]  = s(r, "id");
      v[1]  = s(r, "modalidad");
      v[2]  = s(r, "nit");
      v[3]  = s(r, "nombre_prest");
      v[4]  = s(r, "prefijo");
      v[5]  = s(r, "no_fact");
      v[6]  = s(r, "num_factura");
      v[7]  = dateStr(r.get("fecha_factura"));
      v[8]  = dateStr(r.get("fecha_radicacion"));
      v[9]  = s(r, "mes_anio_radicacion");
      v[10] = s(r, "valor_factura");
      v[11] = s(r, "valor_pagado");
      v[12] = s(r, "porcentaje_pago");
      v[13] = s(r, "estado");
      v[14] = s(r, "voucher");
      v[15] = dateStr(r.get("feccha_pago"));
      v[16] = s(r, "fuente_origen");
      v[17] = observacion != null ? observacion : s(r, "observacion");
      mapped.add(v);
    }

    jdbc.batchUpdate(
        "INSERT INTO pagos_api_staging (id, modalidad, nit, nombre_prest, prefijo, no_fact, num_factura, fecha_factura, fecha_radicacion, mes_anio_radicacion, valor_factura, valor_pagado, porcentaje_pago, estado, voucher, feccha_pago, fuente_origen, observacion) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        new BatchPreparedStatementSetter() {
          @Override public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
            String[] r = mapped.get(i);
            for (int c = 0; c < 18; c++) { ps.setString(c + 1, r[c]); }
          }
          @Override public int getBatchSize() { return mapped.size(); }
        });
  }

  private static String s(Map<String, Object> r, String k) {
    Object v = r.get(k);
    return v == null ? null : String.valueOf(v);
  }

  private static String dateStr(Object v) {
    if (v == null) return null;
    String s = String.valueOf(v).trim();
    if (s.isEmpty()) return null;
    // Expect formats like 'yyyy-MM-dd' or 'yyyy-MM-dd HH:mm:ss'
    try {
      if (s.length() == 10) { LocalDate.parse(s); return s; }
      DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      LocalDateTime.parse(s, f); return s.substring(0, 10); // keep date only for conversion
    } catch (Exception ignore) {
      return s; // let SQL TRY_CONVERT handle
    }
  }

  private int insertarDefinitivoDesdeStaging(String tablaDestino) {
    String V_CLEAN = "REPLACE(REPLACE(REPLACE(REPLACE(%s,'$',''),' ',''),CHAR(160),''),CHAR(9),'')";
    String NORM_NUM = "TRY_CONVERT(NUMERIC(18,2), CASE WHEN PATINDEX('%[.,]%', v) > 0 AND CHARINDEX('.', v) > 0 AND CHARINDEX(',', v) > 0 THEN CASE WHEN CHARINDEX('.', v) > CHARINDEX(',', v) THEN REPLACE(v, ',', '') ELSE REPLACE(REPLACE(v, '.', ''), ',', '.') END WHEN CHARINDEX(',', v) > 0 THEN REPLACE(v, ',', '.') ELSE v END)";

    String insertSql = """
      ;WITH x AS (
        SELECT
          id,
          modalidad,
          nit,
          nombre_prest,
          prefijo,
          no_fact,
          num_factura,
          fecha_factura = COALESCE(TRY_CONVERT(DATE, fecha_factura, 103), TRY_CONVERT(DATE, fecha_factura, 111), TRY_CONVERT(DATE, fecha_factura, 120)),
          fecha_radicacion = COALESCE(TRY_CONVERT(DATE, fecha_radicacion, 103), TRY_CONVERT(DATE, fecha_radicacion, 111), TRY_CONVERT(DATE, fecha_radicacion, 120)),
          mes_anio_radicacion,
          valor_factura = %s,
          valor_pagado  = %s,
          porcentaje_pago = TRY_CONVERT(NUMERIC(5, 2), REPLACE(REPLACE(REPLACE(porcentaje_pago, '%%',''), ' ', ''), CHAR(160), '')),
          estado,
          voucher,
          feccha_pago   = COALESCE(TRY_CONVERT(DATE, feccha_pago, 103), TRY_CONVERT(DATE, feccha_pago, 111), TRY_CONVERT(DATE, feccha_pago, 120)),
          fuente_origen,
          observacion
        FROM pagos_api_staging
      )
      INSERT INTO %s (
        id, modalidad, nit, nombre_prest, prefijo, no_fact, num_factura,
        fecha_factura, fecha_radicacion, mes_anio_radicacion,
        valor_factura, valor_pagado, porcentaje_pago,
        estado, voucher, feccha_pago, fuente_origen, observacion)
      SELECT
        x.id, x.modalidad, x.nit, x.nombre_prest, x.prefijo, x.no_fact, x.num_factura,
        x.fecha_factura, x.fecha_radicacion, x.mes_anio_radicacion,
        x.valor_factura, x.valor_pagado, x.porcentaje_pago,
        x.estado, x.voucher, x.feccha_pago, x.fuente_origen, x.observacion
      FROM x
      WHERE NOT EXISTS (
        SELECT 1 FROM %s p
        WHERE p.modalidad = x.modalidad AND p.num_factura = x.num_factura AND p.feccha_pago = x.feccha_pago
      );
      SELECT @@ROWCOUNT as inserted;
      """.formatted(
        NORM_NUM.replace("v", String.format(V_CLEAN, "valor_factura")),
        NORM_NUM.replace("v", String.format(V_CLEAN, "valor_pagado")),
        tablaDestino, tablaDestino);

    Integer inserted = jdbc.queryForObject(insertSql, Integer.class);
    return inserted == null ? 0 : inserted.intValue();
  }

  // --- Helpers de validación para evitar overflow numérico en la tabla destino ---
  private void prevalidarMontosContraEsquema(String tablaDestino) {
    try {
      Map<String, PScale> caps = obtenerCapacidadesNumericas(tablaDestino);
      PScale capVF = caps.get("valor_factura");
      PScale capVP = caps.get("valor_pagado");
      PScale capPct = caps.get("porcentaje_pago");

      // Si no pudimos leer los metadatos, no validamos (evitamos falsos positivos)
      if (capVF == null && capVP == null && capPct == null) return;

      String V_CLEAN = "REPLACE(REPLACE(REPLACE(REPLACE(%s,'$',''),' ',''),CHAR(160),''),CHAR(9),'')";
      String NORM_NUM = "TRY_CONVERT(NUMERIC(18,2), CASE WHEN PATINDEX('%[.,]%', v) > 0 AND CHARINDEX('.', v) > 0 AND CHARINDEX(',', v) > 0 THEN CASE WHEN CHARINDEX('.', v) > CHARINDEX(',', v) THEN REPLACE(v, ',', '') ELSE REPLACE(REPLACE(v, '.', ''), ',', '.') END WHEN CHARINDEX(',', v) > 0 THEN REPLACE(v, ',', '.') ELSE v END)";

      String exprVF = NORM_NUM.replace("v", String.format(V_CLEAN, "valor_factura"));
      String exprVP = NORM_NUM.replace("v", String.format(V_CLEAN, "valor_pagado"));
      String exprPct = "TRY_CONVERT(NUMERIC(18,2), REPLACE(REPLACE(REPLACE(porcentaje_pago, '%%',''), ' ', ''), CHAR(160), ''))";

      // Buscamos ejemplos de filas inválidas (que exceden la capacidad del destino)
      StringBuilder badSql = new StringBuilder();
      badSql.append("SELECT TOP 5 id, num_factura, voucher, ")
            .append("vf = ").append(exprVF).append(", vp = ").append(exprVP).append(", pct = ").append(exprPct)
            .append(" FROM pagos_api_staging WHERE 1=0");
      List<Object> badParams = new java.util.ArrayList<>();
      if (capVF != null) { badSql.append(" OR (").append(exprVF).append(" > ?)"); badParams.add(capVF.max()); }
      if (capVP != null) { badSql.append(" OR (").append(exprVP).append(" > ?)"); badParams.add(capVP.max()); }
      if (capPct != null) { badSql.append(" OR (").append(exprPct).append(" > ?)"); badParams.add(capPct.max()); }

      if (!badParams.isEmpty()) {
        List<Map<String, Object>> bad = jdbc.queryForList(badSql.toString(), badParams.toArray());
        if (!bad.isEmpty()) {
          StringBuilder msg = new StringBuilder("Montos fuera de rango para tabla '")
              .append(tablaDestino).append("'. Verifica precisión de columnas y los valores del archivo/API. Ejemplos problemáticos: ");
          for (Map<String, Object> r : bad) {
            msg.append("[id=").append(String.valueOf(r.get("id")))
               .append(", num_factura=").append(String.valueOf(r.get("num_factura")))
               .append(", voucher=").append(String.valueOf(r.get("voucher")))
               .append(", valor_factura=").append(String.valueOf(r.get("vf")))
               .append(", valor_pagado=").append(String.valueOf(r.get("vp")))
               .append(", porcentaje=").append(String.valueOf(r.get("pct")))
               .append("] ");
          }
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg.toString());
        }
      }
    } catch (ResponseStatusException ex) {
      throw ex; // propagar mensaje claro al controlador
    } catch (Exception ex) {
      // Si algo falla en la validación, no bloqueamos: seguimos y dejamos que BD valide
    }
  }

  private record PScale(int precision, int scale) {
    BigDecimal max() {
      int intDigits = Math.max(1, precision - scale);
      String intPart = "9".repeat(intDigits);
      String decPart = scale > 0 ? ("." + "9".repeat(scale)) : "";
      return new BigDecimal(intPart + decPart);
    }
  }

  private Map<String, PScale> obtenerCapacidadesNumericas(String tabla) {
    String metaSql = """
      SELECT COLUMN_NAME, NUMERIC_PRECISION, NUMERIC_SCALE
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_NAME = ? AND COLUMN_NAME IN ('valor_factura','valor_pagado','porcentaje_pago')
        AND DATA_TYPE IN ('numeric','decimal')
    """;
    List<Map<String, Object>> rows = jdbc.queryForList(metaSql, tabla);
    Map<String, PScale> out = new HashMap<>();
    for (Map<String, Object> r : rows) {
      Object pObj = r.get("NUMERIC_PRECISION");
      Object sObj = r.get("NUMERIC_SCALE");
      if (pObj instanceof Number pNum && sObj instanceof Number sNum) {
        out.put(String.valueOf(r.get("COLUMN_NAME")).toLowerCase(Locale.ROOT), new PScale(pNum.intValue(), sNum.intValue()));
      }
    }
    return out;
  }

  private static class VoucherResumen {
    String voucher;
    int cantidad = 0;
    double totalFactura = 0.0;
    double totalPagado = 0.0;
    VoucherResumen(String v) { this.voucher = v; }
  }
}
