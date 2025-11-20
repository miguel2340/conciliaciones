package com.pagosyradicacion.backend.carga;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CargaPagosService {

  private final JdbcTemplate jdbc;

  public CargaPagosService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public String cargarPagosCsv(MultipartFile archivo, String tipoCarga) {
    if (archivo == null || archivo.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes adjuntar un archivo CSV");
    }
    String original = archivo.getOriginalFilename();
    String fo = original != null ? original.substring(0, Math.min(original.length(), 100)) : "archivo";

    String tipo = (tipoCarga == null ? "pagos" : tipoCarga.trim().toLowerCase(Locale.ROOT));
    String tablaDestino = tipo.equals("capita") ? "pagos_capita" : "pagos";

    crearStaging();
    try {
      insertarStagingDesdeCsv(archivo.getInputStream());
      completarFuenteOrigen(fo);
      validarDatosCriticos();
      validarDuplicados(tablaDestino);
      insertarDefinitivo(tablaDestino);
      dropStaging();
      return "Archivo cargado correctamente a " + (tipo.equals("capita") ? "Pagos Cápita" : "Pagos");
    } catch (RuntimeException  ex) {
      dropStagingSeguro();
      throw ex;
    } catch (Exception ex) {
      dropStagingSeguro();
      throw new BusinessValidationException("Error durante la carga: " + ex.getMessage());
    }
  }

  private void crearStaging() {
    jdbc.execute("IF OBJECT_ID('pagos_staging','U') IS NOT NULL DROP TABLE pagos_staging");
    jdbc.execute("""
        CREATE TABLE pagos_staging (
          row_num INT NOT NULL,
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

  private void dropStaging() {
    jdbc.execute("DROP TABLE pagos_staging");
  }

  private void dropStagingSeguro() {
    try { jdbc.execute("IF OBJECT_ID('pagos_staging','U') IS NOT NULL DROP TABLE pagos_staging"); } catch (Exception ignore) {}
  }

  private void insertarStagingDesdeCsv(InputStream is) throws Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    String line;
    boolean first = true;
    List<Row> batch = new ArrayList<>();
    final int BATCH_SIZE = 100;
    int dataRowNum = 0; // 1-based index for data rows (excluding header)
    Character detectedDelim = null; // autodetect ; o ,

    while ((line = reader.readLine()) != null) {
      // strip BOM on first line if present
      if (first && line.length() > 0 && line.charAt(0) == '\uFEFF') {
        line = line.substring(1);
      }
      // Autodetectar delimitador al leer la primera línea no vacía
      if (detectedDelim == null) {
        int sc = countSeparators(line, ';');
        int cc = countSeparators(line, ',');
        detectedDelim = (cc > sc) ? ',' : ';';
      }
      String[] cols = parseCsv(line, detectedDelim.charValue());
      if (cols.length == 1 && (cols[0] == null || cols[0].isBlank())) {
        first = false; // skip blank line
        continue;
      }

      // normalize to 18 columns
      String[] row = new String[18];
      for (int i = 0; i < 18; i++) {
        String v = i < cols.length ? limpiar(cols[i]) : null;
        row[i] = v;
      }

      if (first) {
        String header = String.join(String.valueOf(detectedDelim), row).toLowerCase(Locale.ROOT);
        boolean tieneEncabezado = header.contains("modalidad") || header.contains("nit");
        first = false;
        if (tieneEncabezado) {
          continue; // skip header
        }
      }

      dataRowNum++;
      batch.add(new Row(dataRowNum, row));
      if (batch.size() >= BATCH_SIZE) {
        insertarBatch(batch);
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      insertarBatch(batch);
    }
  }

  private void insertarBatch(List<Row> rows) {
    jdbc.batchUpdate(
        "INSERT INTO pagos_staging (row_num, id, modalidad, nit, nombre_prest, prefijo, no_fact, num_factura, fecha_factura, fecha_radicacion, mes_anio_radicacion, valor_factura, valor_pagado, porcentaje_pago, estado, voucher, feccha_pago, fuente_origen, observacion) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        new BatchPreparedStatementSetter() {
          @Override public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
            Row rr = rows.get(i);
            ps.setInt(1, rr.rowNum());
            String[] r = rr.values();
            for (int c = 0; c < 18; c++) { ps.setString(c + 2, r[c]); }
          }
          @Override public int getBatchSize() { return rows.size(); }
        });
  }

  private record Row(int rowNum, String[] values) {}

  private String limpiar(String v) {
    if (v == null) return null;
    String s = v.replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ').trim();
    return s.isEmpty() ? null : s;
  }

  private void completarFuenteOrigen(String fo) {
    jdbc.update("UPDATE pagos_staging SET fuente_origen = COALESCE(NULLIF(LTRIM(RTRIM(fuente_origen)),''), ?)", fo);
  }

  private void validarDatosCriticos() {
    List<String> issues = new ArrayList<>();

    // Fechas inválidas (muestra ejemplos)
    List<Map<String,Object>> badDates = jdbc.queryForList("""
      SELECT TOP 3 row_num, fecha_factura, fecha_radicacion, feccha_pago
      FROM pagos_staging
      WHERE (fecha_factura IS NULL OR (TRY_CONVERT(DATE, fecha_factura, 103) IS NULL AND TRY_CONVERT(DATE, fecha_factura, 111) IS NULL))
         OR (fecha_radicacion IS NOT NULL AND (TRY_CONVERT(DATE, fecha_radicacion, 103) IS NULL AND TRY_CONVERT(DATE, fecha_radicacion, 111) IS NULL))
         OR (feccha_pago IS NULL OR TRY_CONVERT(DATE, feccha_pago, 103) IS NULL)
    """);
    if (!badDates.isEmpty()) {
      StringBuilder sb = new StringBuilder("Fechas inválidas. Ejemplos: ");
      for (Map<String,Object> r : badDates) {
        sb.append("fila ").append(r.get("row_num")).append(" (feccha_pago='").append(s(r,"feccha_pago")).append("', fecha_factura='").append(s(r,"fecha_factura")).append("') ");
      }
      sb.append(". Formatos válidos: dd/mm/yyyy o yyyy/mm/dd.");
      issues.add(sb.toString());
    }

    // Campos obligatorios
    List<Map<String,Object>> missing = jdbc.queryForList("""
      SELECT TOP 3 row_num, id, nit, voucher
      FROM pagos_staging
      WHERE id IS NULL OR LTRIM(RTRIM(nit))='' OR LTRIM(RTRIM(voucher))=''
    """);
    if (!missing.isEmpty()) {
      StringBuilder sb = new StringBuilder("Faltan campos obligatorios (id/nit/voucher). Ejemplos: ");
      for (Map<String,Object> r : missing) {
        sb.append("fila ").append(r.get("row_num")).append(" (id='").append(s(r,"id")).append("', nit='").append(s(r,"nit")).append("', voucher='").append(s(r,"voucher")).append("') ");
      }
      issues.add(sb.toString());
    }

    // Montos inválidos
    String V_CLEAN = "REPLACE(REPLACE(REPLACE(REPLACE(%s,'$',''),' ',''),CHAR(160),''),CHAR(9),'')";
    String NORM_NUM = "TRY_CONVERT(NUMERIC(18,2), CASE WHEN PATINDEX('%[.,]%', v) > 0 AND CHARINDEX('.', v) > 0 AND CHARINDEX(',', v) > 0 THEN CASE WHEN CHARINDEX('.', v) > CHARINDEX(',', v) THEN REPLACE(v, ',', '') ELSE REPLACE(REPLACE(v, '.', ''), ',', '.') END WHEN CHARINDEX(',', v) > 0 THEN REPLACE(v, ',', '.') ELSE v END)";
    String convBadSql = ("SELECT TOP 3 row_num, valor_factura, valor_pagado FROM pagos_staging WHERE (" +
        NORM_NUM.replace("v", String.format(V_CLEAN, "valor_factura")) + ") IS NULL OR (" +
        NORM_NUM.replace("v", String.format(V_CLEAN, "valor_pagado")) + ") IS NULL");
    List<Map<String,Object>> badAmounts = jdbc.queryForList(convBadSql);
    if (!badAmounts.isEmpty()) {
      StringBuilder sb = new StringBuilder("Montos inválidos. Ejemplos: ");
      for (Map<String,Object> r : badAmounts) {
        sb.append("fila ").append(r.get("row_num")).append(" (valor_factura='").append(s(r,"valor_factura")).append("', valor_pagado='").append(s(r,"valor_pagado")).append("') ");
      }
      sb.append(". Usa formatos como 12345.67 o 12.345,67, con o sin '$'.");
      issues.add(sb.toString());
    }

    if (!issues.isEmpty()) {
      throw new BusinessValidationException(String.join(" | ", issues));
    }
  }

  private static String s(Map<String,Object> r, String k) {
    Object v = r.get(k); return v == null ? "" : String.valueOf(v);
  }

  private void validarDuplicados(String tablaDestino) {
    Integer dups = jdbc.queryForObject(
        "SELECT COUNT(*) FROM pagos_staging s JOIN " + tablaDestino + " p ON p.modalidad = s.modalidad AND p.num_factura = s.num_factura AND p.feccha_pago = TRY_CONVERT(DATE, s.feccha_pago, 103)",
        Integer.class);
    if (dups != null && dups > 0) {
      List<Map<String,Object>> examples = jdbc.queryForList(
          "SELECT TOP 5 s.row_num, s.modalidad, s.num_factura, s.feccha_pago FROM pagos_staging s JOIN " + tablaDestino + " p ON p.modalidad = s.modalidad AND p.num_factura = s.num_factura AND p.feccha_pago = TRY_CONVERT(DATE, s.feccha_pago, 103) ORDER BY s.row_num");
      StringBuilder sb = new StringBuilder("Duplicados detectados en " + tablaDestino + ". Ejemplos: ");
      for (Map<String,Object> r : examples) {
        sb.append("fila ").append(r.get("row_num")).append(" (modalidad='")
          .append(r.get("modalidad")).append("', num_factura='")
          .append(r.get("num_factura")).append("', feccha_pago='")
          .append(r.get("feccha_pago")).append("') ");
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, sb.toString());
    }
  }

  private void insertarDefinitivo(String tablaDestino) {
    String V_CLEAN = "REPLACE(REPLACE(REPLACE(REPLACE(%s,'$',''),' ',''),CHAR(160),''),CHAR(9),'')";
    String NORM_NUM = "TRY_CONVERT(NUMERIC(18,2), CASE WHEN PATINDEX('%[.,]%', v) > 0 AND CHARINDEX('.', v) > 0 AND CHARINDEX(',', v) > 0 THEN CASE WHEN CHARINDEX('.', v) > CHARINDEX(',', v) THEN REPLACE(v, ',', '') ELSE REPLACE(REPLACE(v, '.', ''), ',', '.') END WHEN CHARINDEX(',', v) > 0 THEN REPLACE(v, ',', '.') ELSE v END)";

    String insertSql = """
      INSERT INTO %s (
        id, modalidad, nit, nombre_prest, prefijo, no_fact, num_factura,
        fecha_factura, fecha_radicacion, mes_anio_radicacion,
        valor_factura, valor_pagado, porcentaje_pago,
        estado, voucher, feccha_pago, fuente_origen, observacion)
      SELECT
        id,
        modalidad,
        nit,
        nombre_prest,
        prefijo,
        no_fact,
        num_factura,
        COALESCE(TRY_CONVERT(DATE, fecha_factura, 103), TRY_CONVERT(DATE, fecha_factura, 111)),
        COALESCE(TRY_CONVERT(DATE, fecha_radicacion, 103), TRY_CONVERT(DATE, fecha_radicacion, 111)),
        mes_anio_radicacion,
        %s,
        %s,
        TRY_CONVERT(NUMERIC(5, 2), REPLACE(REPLACE(REPLACE(porcentaje_pago, '%%',''), ' ', ''), CHAR(160), '')),
        estado,
        voucher,
        TRY_CONVERT(DATE, feccha_pago, 103),
        fuente_origen,
        observacion
      FROM pagos_staging
    """.formatted(
        tablaDestino,
        NORM_NUM.replace("v", String.format(V_CLEAN, "valor_factura")),
        NORM_NUM.replace("v", String.format(V_CLEAN, "valor_pagado"))
    );

    jdbc.execute(insertSql);
  }

  // Cuenta separadores fuera de comillas
  private int countSeparators(String line, char sep) {
    boolean inQuotes = false; int c = 0;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') { if (inQuotes && i+1 < line.length() && line.charAt(i+1) == '"') { i++; } else { inQuotes = !inQuotes; } }
      else if (!inQuotes && ch == sep) { c++; }
    }
    return c;
  }

  // CSV simple con delimitador configurable y soporte básico de comillas dobles
  private String[] parseCsv(String line, char delimiter) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          cur.append('"');
          i++; // escape
        } else {
          inQuotes = !inQuotes;
        }
      } else if (ch == delimiter && !inQuotes) {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(ch);
      }
    }
    out.add(cur.toString());
    return out.toArray(new String[0]);
  }
}

