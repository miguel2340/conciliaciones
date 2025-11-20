package com.pagosyradicacion.backend.carga;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PagosTrazaService {
  private final JdbcTemplate jdbc;
  public PagosTrazaService(JdbcTemplate jdbc){ this.jdbc = jdbc; }

  @Transactional
  public String cargarCsv(MultipartFile archivo) {
    if (archivo == null || archivo.isEmpty()) {
      throw new BusinessValidationException("Debes adjuntar un archivo CSV");
    }

    String original = archivo.getOriginalFilename();
    String fuente = original != null ? original.trim() : "archivo";
    if (fuente.length() > 120) fuente = fuente.substring(0, 120);

    ensureStaging();
    // Limpieza por fuente
    jdbc.update("DELETE FROM dbo.pagos_traza_staging WHERE fuente_archivo = ?", fuente);

    CsvModel model = readAndValidate(archivo);
    if (!model.errores.isEmpty()) {
      throw new BusinessValidationException("Errores en CSV: " + String.join(" | ", model.errores));
    }

    insertStaging(model.rows, fuente);
    moveStagingToFinal(fuente);
    // Limpieza staging de esta fuente
    jdbc.update("DELETE FROM dbo.pagos_traza_staging WHERE fuente_archivo = ?", fuente);
    return "Reporte reemplazado: pagos_traza limpiada e insertada desde staging.";
  }

  private static class CsvModel {
    List<String> headers = new ArrayList<>();
    List<String[]> rows = new ArrayList<>();
    List<String> errores = new ArrayList<>();
  }

  private CsvModel readAndValidate(MultipartFile archivo) {
    CsvModel m = new CsvModel();
    try (InputStream is = archivo.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      boolean first = true;
      int fila = 0;
      while ((line = r.readLine()) != null) {
        if (first) {
          if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
          String[] hdr = parseCsvSemicolon(line);
          m.headers = normalizeHeaders(Arrays.asList(hdr));
          List<String> esperados = List.of("identificacion","nombre","voucher","id_pago","fecha_pago","valor_pagado","valor_causado");
          for (int i = 0; i < esperados.size(); i++) {
            String visto = i < m.headers.size() ? m.headers.get(i) : "";
            if (!esperados.get(i).equals(visto)) {
              m.errores.add("Encabezado columna " + (i+1) + " debe ser '" + esperados.get(i) + "' y se recibió '" + visto + "'");
            }
          }
          first = false; continue;
        }
        fila++;
        String[] cols = parseCsvSemicolon(line);
        if (cols.length < 7) {
          m.errores.add("Fila " + (fila+1) + ": número de columnas inválido (se esperan 7)");
          continue;
        }
        // recorta celdas a 7
        String[] row = new String[7];
        for (int i = 0; i < 7; i++) row[i] = trimQuotes(cols[i]);
        m.rows.add(row);
      }
    } catch (Exception ex) {
      throw new BusinessValidationException("No fue posible leer el archivo: " + ex.getMessage());
    }
    return m;
  }

  private void ensureStaging() {
    jdbc.execute("""
      IF OBJECT_ID('dbo.pagos_traza_staging','U') IS NULL
      CREATE TABLE dbo.pagos_traza_staging (
        identificacion    VARCHAR(30) NULL,
        nombre            NVARCHAR(200) NULL,
        voucher           VARCHAR(50) NULL,
        id_pago           VARCHAR(50) NULL,
        fecha_pago_txt    VARCHAR(100) NULL,
        valor_pagado_txt  VARCHAR(100) NULL,
        valor_causado_txt VARCHAR(100) NULL,
        fuente_archivo    NVARCHAR(200) NULL,
        fecha_carga       DATETIME NULL
      )
    """);
  }

  private void insertStaging(List<String[]> rows, String fuente) {
    final LocalDateTime now = LocalDateTime.now();
    String sql = "INSERT INTO dbo.pagos_traza_staging (identificacion,nombre,voucher,id_pago,fecha_pago_txt,valor_pagado_txt,valor_causado_txt,fuente_archivo,fecha_carga) VALUES (?,?,?,?,?,?,?,?,?)";
    jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        String[] c = rows.get(i);
        ps.setString(1, safe(c[0]));
        ps.setString(2, safe(c[1]));
        ps.setString(3, safe(c[2]));
        ps.setString(4, safe(c[3]));
        ps.setString(5, safe(c[4]));
        ps.setString(6, safe(c[5]));
        ps.setString(7, safe(c[6]));
        ps.setString(8, fuente);
        ps.setObject(9, java.sql.Timestamp.valueOf(now));
      }
      @Override public int getBatchSize() { return rows.size(); }
    });
  }

  private void moveStagingToFinal(String fuenteArchivo) {
    // Reemplazo total de pagos_traza
    try {
      jdbc.execute("TRUNCATE TABLE dbo.pagos_traza");
    } catch (Exception ex) {
      jdbc.execute("DELETE FROM dbo.pagos_traza");
    }

    String V_CLEAN = "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(%s,'$',''),',',''),' ',''),CHAR(160),''),CHAR(9),'')";
    String NORM_NUM = "TRY_CONVERT(DECIMAL(18,2), CASE WHEN PATINDEX('%[.,]%', v) > 0 AND CHARINDEX('.', v) > 0 AND CHARINDEX(',', v) > 0 THEN CASE WHEN CHARINDEX('.', v) > CHARINDEX(',', v) THEN REPLACE(v, ',', '') ELSE REPLACE(REPLACE(v, '.', ''), ',', '.') END WHEN CHARINDEX(',', v) > 0 THEN REPLACE(v, ',', '.') ELSE v END)";

    String valorPagadoExpr = NORM_NUM.replace("v", String.format(V_CLEAN, "s.valor_pagado_txt"));
    String valorCausadoExpr = NORM_NUM.replace("v", String.format(V_CLEAN, "s.valor_causado_txt"));

    String sql = """
      INSERT INTO dbo.pagos_traza (identificacion, nombre, voucher, id_pago, fecha_pago, valor_pagado, valor_causado)
      SELECT
        LEFT(NULLIF(LTRIM(RTRIM(s.identificacion)),''), 20) AS identificacion,
        CASE WHEN s.nombre IS NULL OR LTRIM(RTRIM(s.nombre))='' THEN NULL ELSE LEFT(s.nombre, 100) END AS nombre,
        CASE WHEN s.voucher IS NULL OR LTRIM(RTRIM(s.voucher))='' THEN NULL
             WHEN TRY_CONVERT(BIGINT, REPLACE(REPLACE(s.voucher, ',', ''), ' ', '')) IS NOT NULL
             THEN LTRIM(STR(TRY_CONVERT(BIGINT, REPLACE(REPLACE(s.voucher, ',', ''), ' ', ''))))
             ELSE LEFT(LTRIM(RTRIM(s.voucher)), 50)
        END AS voucher,
        CASE WHEN s.id_pago IS NULL OR LTRIM(RTRIM(s.id_pago))='' THEN NULL ELSE LEFT(s.id_pago,50) END AS id_pago,
        CASE 
          WHEN s.fecha_pago_txt IS NULL OR LTRIM(RTRIM(s.fecha_pago_txt))='' THEN NULL
          WHEN TRY_CONVERT(date, s.fecha_pago_txt, 103) IS NOT NULL THEN TRY_CONVERT(date, s.fecha_pago_txt, 103)
          WHEN TRY_CONVERT(date, s.fecha_pago_txt, 120) IS NOT NULL THEN TRY_CONVERT(date, s.fecha_pago_txt, 120)
          WHEN TRY_CONVERT(float, s.fecha_pago_txt) BETWEEN 25000 AND 60000 
            THEN CONVERT(date, DATEADD(day, CAST(ROUND(TRY_CONVERT(float, s.fecha_pago_txt), 0) - 25569 AS int), '1970-01-01'), 120)
          ELSE NULL
        END AS fecha_pago,
        %s AS valor_pagado,
        %s AS valor_causado
      FROM dbo.pagos_traza_staging s
      WHERE s.fuente_archivo = ?
    """.formatted(valorPagadoExpr, valorCausadoExpr);

    jdbc.update(sql, fuenteArchivo);
  }

  /* ===================== Helpers ===================== */

  private static String safe(String v) {
    if (v == null) return null;
    String s = v.replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ').trim();
    return s.isEmpty() ? null : s;
  }

  private static List<String> normalizeHeaders(List<String> hdrs) {
    Map<String, String> aliases = new HashMap<>();
    aliases.put("identificacion", "identificacion");
    aliases.put("nombre", "nombre");
    aliases.put("comprobante", "voucher");
    aliases.put("comprobante contable", "voucher");
    aliases.put("voucher", "voucher");
    aliases.put("id pago", "id_pago");
    aliases.put("idpago", "id_pago");
    aliases.put("id_pago", "id_pago");
    aliases.put("fecha pago", "fecha_pago");
    aliases.put("fechapago", "fecha_pago");
    aliases.put("fecha_pago", "fecha_pago");
    aliases.put("valor pagado", "valor_pagado");
    aliases.put("valorpagado", "valor_pagado");
    aliases.put("valor_pagado", "valor_pagado");
    aliases.put("vr causado", "valor_causado");
    aliases.put("vr. causado", "valor_causado");
    aliases.put("valor causado", "valor_causado");
    aliases.put("valor_causado", "valor_causado");

    List<String> out = new ArrayList<>(hdrs.size());
    for (String h : hdrs) {
      if (h == null) { out.add(""); continue; }
      String n = h.trim().toLowerCase(Locale.ROOT);
      n = n.replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u");
      n = n.replace("Á","a").replace("É","e").replace("Í","i").replace("Ó","o").replace("Ú","u");
      n = n.replace('.', ' ');
      n = n.replaceAll("\\s+", " ").trim();
      out.add(aliases.getOrDefault(n, n));
    }
    return out;
  }

  private static String[] parseCsvSemicolon(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
        else { inQuotes = !inQuotes; }
      } else if (ch == ';' && !inQuotes) {
        out.add(cur.toString()); cur.setLength(0);
      } else { cur.append(ch); }
    }
    out.add(cur.toString());
    return out.toArray(new String[0]);
  }

  private static String trimQuotes(String v) {
    if (v == null) return null;
    String s = v.trim();
    if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
      s = s.substring(1, s.length()-1);
    }
    return s.trim();
  }
}
