package com.pagosyradicacion.backend.carga;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CorreccionPagosService {

  private static final DateTimeFormatter DMY_DASH = DateTimeFormatter.ofPattern("d-M-uuuu");
  private static final DateTimeFormatter DMY_SLASH = DateTimeFormatter.ofPattern("d/M/uuuu");

  private final JdbcTemplate jdbc;

  public CorreccionPagosService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String cargarCorreccionCsv(MultipartFile archivo, String usuario, String tipo) {
    if (archivo == null || archivo.isEmpty()) {
      throw new BusinessValidationException("Debes adjuntar un archivo CSV");
    }

    String original = archivo.getOriginalFilename();
    if (original != null && original.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls)$")) {
      throw new BusinessValidationException("Archivo Excel (.xlsx/.xls) no soportado. Exporta como CSV (;) con encabezado y 'id_fomag' como Ãºltima columna.");
    }

    // ValidaciÃ³n rÃ¡pida del separador decimal: exigir coma y rechazar punto
    // (revisa primeras 5 lÃ­neas, columnas 10,11,12)
    try (BufferedReader r = new BufferedReader(new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
      String line; boolean first = true; int checked = 0; boolean error = false; Character detected = null;
      while ((line = r.readLine()) != null && checked < 5) {
        if (first) {
          if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1); // BOM
          int sc = countSeparators(line, ';'); int cc = countSeparators(line, ',');
          detected = (cc > sc) ? ',' : ';';
          first = false; continue; // saltar encabezado
        }
        String[] cols = parseCsv(line, detected != null ? detected : ';');
        for (int idx : new int[]{10,11}) {
          if (idx < cols.length) {
            String v = cols[idx] != null ? cols[idx].trim() : null;
            if (v != null && usesDotAsDecimal(v)) { error = true; break; }
          }
        }
        if (error) break;
        checked++;
      }
      if (error) {
        throw new BusinessValidationException("Para los montos usa coma (,) como separador decimal. La columna de porcentaje puede usar punto (.) o coma (,).");
      }
    } catch (Exception ex) {
      throw new BusinessValidationException("No fue posible leer el archivo: " + ex.getMessage());
    }

    String loteId = java.util.UUID.randomUUID().toString();
    crearTmp(loteId);

    try {
      CsvStats stats = cargarTmpDesdeCsv(archivo.getInputStream(), loteId);
      Integer tmpCount = jdbc.queryForObject("SELECT COUNT(*) FROM CorreccionPagosTmp WHERE lote_id = ?", Integer.class, loteId);
      if (tmpCount == null || tmpCount == 0) {
        throw new BusinessValidationException("El archivo no contiene filas vÃ¡lidas. AsegÃºrate que sea CSV (;) o (,) con encabezado y la Ãºltima columna sea id_fomag.");
      }
      ensureAuxTables();
      String tablaDestino = (tipo != null && tipo.trim().equalsIgnoreCase("capita")) ? "pagos_capita" : "pagos";
      StringBuilder warnings = new StringBuilder();
      try {
        respaldarAntesDeActualizar(usuario, tablaDestino, loteId);
      } catch (org.springframework.dao.DataAccessException ex) {
        if (isInsufficientSpace(ex)) {
          warnings.append(" Advertencia: sin respaldo por espacio insuficiente en BD.");
        } else {
          throw ex;
        }
      }
      try {
        registrarLogCambios(usuario, tablaDestino, loteId);
      } catch (org.springframework.dao.DataAccessException ex) {
        if (isInsufficientSpace(ex)) {
          warnings.append(" Advertencia: sin registro de log por espacio insuficiente en BD.");
        } else {
          throw ex;
        }
      }
      int affected = ejecutarUpdate(tablaDestino, loteId);
      int noEncontrados = jdbc.queryForObject(("SELECT COUNT(*) FROM CorreccionPagosTmp tmp " +
          "LEFT JOIN %s p ON LTRIM(RTRIM(CAST(p.id_fomag AS NVARCHAR(50)))) = LTRIM(RTRIM(CAST(tmp.id_fomag AS NVARCHAR(50)))) " +
          "WHERE p.id_fomag IS NULL AND tmp.lote_id = ?").formatted(tablaDestino), Integer.class, loteId);
      Integer duplicados = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT id_fomag, COUNT(*) c FROM CorreccionPagosTmp WHERE lote_id = ? GROUP BY id_fomag HAVING COUNT(*)>1) d", Integer.class, loteId);
      java.util.List<java.util.Map<String,Object>> ejemplos = jdbc.queryForList(("SELECT TOP 5 tmp.id_fomag AS id " +
          "FROM CorreccionPagosTmp tmp LEFT JOIN %s p ON LTRIM(RTRIM(CAST(p.id_fomag AS NVARCHAR(50)))) = LTRIM(RTRIM(CAST(tmp.id_fomag AS NVARCHAR(50)))) " +
          "WHERE p.id_fomag IS NULL AND tmp.lote_id = ? ORDER BY tmp.id_fomag").formatted(tablaDestino), loteId);
      String ej = ejemplos.isEmpty() ? "" : " Ejemplos no encontrados: " + ejemplos.stream().map(m -> "['"+String.valueOf(m.get("id"))+"']").limit(5).reduce((a,b)->a+" "+b).orElse("");
      dropTmp(loteId);
      return ("Archivo procesado. Total lineas leidas: " + stats.total +
             ". Cargadas: " + (tmpCount != null ? tmpCount : 0) +
             ". Sin id_fomag: " + stats.sinIdFomag +
             ". Duplicados en archivo: " + (duplicados != null ? duplicados : 0) +
             ". Actualizadas: " + affected +
             ". No encontradas en " + tablaDestino + ": " + noEncontrados + "." + ej + (warnings.length()>0 ? warnings.toString() : ""));
    } catch (RuntimeException ex) {
      dropTmpSeguro(loteId);
      if (isInsufficientSpace(ex)) {
        try {
          return actualizarDirectoDesdeCsv(archivo.getInputStream(), usuario, tipo);
        } catch (Exception sx) {
          throw new BusinessValidationException("Error al procesar en modo streaming: " + sx.getMessage());
        }
      }
      throw ex;
    } catch (Exception ex) {
      dropTmpSeguro(loteId);
      // Fallback streaming si es por espacio insuficiente
      if (isInsufficientSpace(ex)) {
        try {
          return actualizarDirectoDesdeCsv(archivo.getInputStream(), usuario, tipo);
        } catch (Exception sx) {
          throw new BusinessValidationException("Error al procesar en modo streaming: " + sx.getMessage());
        }
      }
      throw new BusinessValidationException("Error al procesar el archivo: " + ex.getMessage());
    }
  }

  private String actualizarDirectoDesdeCsv(InputStream is, String usuario, String tipo) throws Exception {
    String tablaDestino = (tipo != null && tipo.trim().equalsIgnoreCase("capita")) ? "pagos_capita" : "pagos";
    String sql = ("UPDATE p SET " +
        "p.modalidad = ?, p.nit = ?, p.nombre_prest = ?, p.prefijo = ?, p.no_fact = ?, p.num_factura = ?, " +
        "p.fecha_factura = COALESCE(?, p.fecha_factura), p.fecha_radicacion = COALESCE(?, p.fecha_radicacion), p.mes_anio_radicacion = ?, " +
        "p.valor_factura = COALESCE(?, p.valor_factura), p.valor_pagado = COALESCE(?, p.valor_pagado), p.porcentaje_pago = COALESCE(?, p.porcentaje_pago), " +
        "p.estado = ?, p.voucher = ?, p.feccha_pago = COALESCE(?, p.feccha_pago), p.fuente_origen = ?, p.observacion = ? " +
        "FROM %s p WHERE LTRIM(RTRIM(CAST(p.id_fomag AS NVARCHAR(50)))) = LTRIM(RTRIM(CAST(? AS NVARCHAR(50))))").formatted(tablaDestino);

    int total = 0, sinId = 0, actualizadas = 0, noEncontradas = 0;
    java.util.ArrayList<String> ejemplosNo = new java.util.ArrayList<>();

    try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line; boolean first = true; Character detected = null;
      while ((line = r.readLine()) != null) {
        if (first) {
          if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
          int sc = countSeparators(line, ';'); int cc = countSeparators(line, ',');
          detected = (cc > sc) ? ',' : ';';
          first = false; continue;
        }
        total++;
        String[] c = parseCsv(line, detected != null ? detected : ';');
        normalizeRow(c);
        if (c.length < 19 || c[18] == null || c[18].isBlank()) { sinId++; continue; }

        Object fechaFactura = parseDate(c, 7);
        Object fechaRadic = parseDate(c, 8);
        Object fechaPago = parseDate(c, 15);
        java.math.BigDecimal valFactura = parseDecimal(c, 10);
        java.math.BigDecimal valPagado = parseDecimal(c, 11);
        java.math.BigDecimal porcentaje = parsePercent(c, 12);

        int upd = jdbc.update(sql,
            truncate(c.length>1?c[1]:null, 50),
            truncate(c.length>2?c[2]:null, 50),
            truncate(c.length>3?c[3]:null, 255),
            truncate(c.length>4?c[4]:null, 50),
            truncate(c.length>5?c[5]:null, 50),
            truncate(c.length>6?c[6]:null, 50),
            fechaFactura,
            fechaRadic,
            truncate(c.length>9?c[9]:null, 20),
            valFactura,
            valPagado,
            porcentaje,
            truncate(c.length>13?c[13]:null, 50),
            truncate(c.length>14?c[14]:null, 50),
            fechaPago,
            truncate(c.length>16?c[16]:null, 100),
            truncate(c.length>17?c[17]:null, 255),
            c[18]
        );
        if (upd > 0) { actualizadas += upd; }
        else { noEncontradas++; if (ejemplosNo.size()<5) ejemplosNo.add(c[18]); }
      }
    }

    String ej = ejemplosNo.isEmpty()?"":" Ejemplos no encontrados: " + String.join(" ", ejemplosNo);
    return "Archivo procesado (streaming). Total lineas leidas: " + total +
           ". Sin id_fomag: " + sinId +
           ". Actualizadas: " + actualizadas +
           ". No encontradas en " + ((tipo!=null&&tipo.equalsIgnoreCase("capita"))?"pagos_capita":"pagos") + ": " + noEncontradas + "." + ej;
  }

  private static String truncate(String s, int max) { if (s == null) return null; return s.length() <= max ? s : s.substring(0, max); }
  private java.math.BigDecimal parsePercent(String[] c, int idx) {
    if (idx >= c.length) return null; String v = c[idx]; if (v == null || v.isBlank()) return null; v = v.replace("%", "");
    try { return new java.math.BigDecimal(v.replace(",",".").trim()); } catch (Exception e) { return null; }
  }

  private void ensureAuxTables() {
    // Crea tablas auxiliares si no existen (respaldo y log)
    jdbc.execute("""
      IF OBJECT_ID('respaldo_pagos','U') IS NULL
      BEGIN
        CREATE TABLE respaldo_pagos (
          id INT NULL,
          usuario NVARCHAR(100) NOT NULL,
          fecha_respaldo DATETIME NOT NULL,
          modalidad VARCHAR(50) NULL,
          nit VARCHAR(50) NULL,
          nombre_prest NVARCHAR(255) NULL,
          num_factura VARCHAR(50) NULL,
          concepto NVARCHAR(255) NULL,
          valor_factura NUMERIC(18,2) NULL,
          valor_pagado NUMERIC(18,2) NULL,
          porcentaje_pago NUMERIC(5,2) NULL,
          estado VARCHAR(50) NULL,
          voucher VARCHAR(50) NULL,
          feccha_pago DATE NULL,
          observacion NVARCHAR(255) NULL,
          id_fomag VARCHAR(50) NULL,
          estado_registro VARCHAR(50) NULL,
          estado_pago VARCHAR(50) NULL,
          prefijo VARCHAR(20) NULL,
          no_fact VARCHAR(50) NULL,
          fecha_factura DATE NULL,
          fecha_radicacion DATE NULL,
          mes_anio_radicacion VARCHAR(20) NULL,
          fuente_origen VARCHAR(100) NULL
        );
      END
    """);

    jdbc.execute("""
      IF OBJECT_ID('log_actualizaciones_pagos','U') IS NULL
      BEGIN
        CREATE TABLE log_actualizaciones_pagos (
          id INT IDENTITY(1,1) PRIMARY KEY,
          id_fomag VARCHAR(50) NOT NULL,
          usuario NVARCHAR(100) NOT NULL,
          fecha_actualizacion DATETIME NOT NULL,
          campos_actualizados NVARCHAR(MAX) NULL
        );
      END
    """);
  }

  private void crearTmp(String loteId) {
    jdbc.execute("""
      IF OBJECT_ID('dbo.CorreccionPagosTmp','U') IS NULL
      BEGIN
        CREATE TABLE dbo.CorreccionPagosTmp (
          lote_id VARCHAR(40) NOT NULL,
          id INT NULL,
          modalidad VARCHAR(50) NULL,
          nit VARCHAR(50) NULL,
          nombre NVARCHAR(500) NULL,
          prefijo VARCHAR(50) NULL,
          no_fact VARCHAR(100) NULL,
          num_factura VARCHAR(100) NULL,
          fecha_factura DATE NULL,
          fecha_radicacion DATE NULL,
          mes_anio_radicacion VARCHAR(20) NULL,
          valor_factura VARCHAR(100) NULL,
          valor_pagado VARCHAR(100) NULL,
          porcentaje_pago VARCHAR(50) NULL,
          estado VARCHAR(50) NULL,
          voucher VARCHAR(100) NULL,
          feccha_pago DATE NULL,
          fuente_origen VARCHAR(200) NULL,
          observacion NVARCHAR(1000) NULL,
          id_fomag VARCHAR(50) NOT NULL
        );
        CREATE INDEX IX_CorreccionPagosTmp_Lote ON dbo.CorreccionPagosTmp(lote_id);
      END
    """);
    jdbc.update("DELETE FROM dbo.CorreccionPagosTmp WHERE lote_id = ?", loteId);
  }

  private void dropTmp(String loteId) { try { jdbc.update("DELETE FROM dbo.CorreccionPagosTmp WHERE lote_id = ?", loteId); } catch (Exception ignore) {} }
  private void dropTmpSeguro(String loteId) { try { jdbc.update("DELETE FROM dbo.CorreccionPagosTmp WHERE lote_id = ?", loteId); } catch (Exception ignore) {} }

  private CsvStats cargarTmpDesdeCsv(InputStream is, String loteId) throws Exception {
    int total = 0; int cargadas = 0; int sinId = 0;
    try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line; boolean first = true; List<String[]> buffer = new ArrayList<>(); final int BATCH = 200;
      Character detected = null;
      while ((line = r.readLine()) != null) {
        if (first) {
          if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
          // detectar delimitador en encabezado
          int sc = countSeparators(line, ';');
          int cc = countSeparators(line, ',');
          detected = (cc > sc) ? ',' : ';';
          String[] header = parseCsv(line, detected);
          if (header.length < 19) {
            // seguimos pero probablemente no insertarÃ¡ filas vÃ¡lidas
          }
          first = false; // ya consumimos encabezado
          continue;
        }
        total++;
        String[] cols = parseCsv(line, detected != null ? detected : ';');
        normalizeRow(cols);
        if (cols.length < 19 || cols[18] == null || cols[18].isBlank()) {
          sinId++;
          continue; // requiere id_fomag
        }
        buffer.add(cols);
        cargadas++;
        if (buffer.size() >= BATCH) { insertarBatchTmp(buffer, loteId); buffer.clear(); }
      }
      if (!buffer.isEmpty()) insertarBatchTmp(buffer, loteId);
    }
    return new CsvStats(total, cargadas, sinId);
  }

  private boolean isInsufficientSpace(Throwable t) {
    while (t != null) {
      String msg = t.getMessage();
      if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains("insufficient disk space")) {
        return true;
      }
      // SQL Server error 1101
      try {
        if (t instanceof com.microsoft.sqlserver.jdbc.SQLServerException ex) {
          // SQLServerException has getErrorCode
          if (ex.getErrorCode() == 1101) return true;
        }
      } catch (Throwable ignore) {}
      t = t.getCause();
    }
    return false;
  }

  private static class CsvStats {
    final int total; // lineas leidas (sin encabezado)
    final int cargadas; // filas insertadas a tmp
    final int sinIdFomag; // filas omitidas por no tener id_fomag
    CsvStats(int total, int cargadas, int sinIdFomag) {
      this.total = total; this.cargadas = cargadas; this.sinIdFomag = sinIdFomag;
    }
  }

  private int countSeparators(String line, char sep) {
    boolean inQuotes = false; int c = 0;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') { if (inQuotes && i+1<line.length() && line.charAt(i+1)=='"') { i++; } else { inQuotes = !inQuotes; } }
      else if (ch == sep && !inQuotes) { c++; }
    }
    return c;
  }

  private String[] parseCsv(String line, char delimiter) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
        else { inQuotes = !inQuotes; }
      } else if (ch == delimiter && !inQuotes) {
        out.add(cur.toString()); cur.setLength(0);
      } else { cur.append(ch); }
    }
    out.add(cur.toString());
    return out.toArray(new String[0]);
  }

  private void insertarBatchTmp(List<String[]> rows, String loteId) {
    String sql = "INSERT INTO CorreccionPagosTmp (lote_id, id, modalidad, nit, nombre, prefijo, no_fact, num_factura, fecha_factura, fecha_radicacion, mes_anio_radicacion, valor_factura, valor_pagado, porcentaje_pago, estado, voucher, feccha_pago, fuente_origen, observacion, id_fomag) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        String[] c = rows.get(i);
        ps.setString(1, loteId);
        ps.setObject(2, parseInt(c,0));
        ps.setString(3, c.length>1?c[1]:null);
        ps.setString(4, c.length>2?c[2]:null);
        ps.setString(5, c.length>3?c[3]:null);
        ps.setString(6, c.length>4?c[4]:null);
        ps.setString(7, c.length>5?c[5]:null);
        ps.setString(8, c.length>6?c[6]:null);
        ps.setObject(9, parseDate(c,7));
        ps.setObject(10, parseDate(c,8));
        ps.setString(11, c.length>9?c[9]:null);
        ps.setString(12, pickMoney(c, 10));
        ps.setString(13, pickMoney(c, 11));
        ps.setString(14, pickPercent(c, 12));
        ps.setString(15, c.length>13?c[13]:null);
        ps.setString(16, c.length>14?c[14]:null);
        ps.setObject(17, parseDate(c,15));
        ps.setString(18, c.length>16?c[16]:null);
        ps.setString(19, c.length>17?c[17]:null);
        ps.setString(20, c.length>18?c[18]:null);
      }
      @Override public int getBatchSize() { return rows.size(); }
    });
  }

  private void respaldarAntesDeActualizar(String usuario, String tablaDestino, String loteId) {
    String sql = """
      INSERT INTO respaldo_pagos (
        usuario, fecha_respaldo, id, modalidad, nit, nombre_prest, num_factura, concepto,
        valor_factura, valor_pagado, porcentaje_pago, estado, voucher, feccha_pago,
        observacion, id_fomag, estado_registro, estado_pago, prefijo, no_fact,
        fecha_factura, fecha_radicacion, mes_anio_radicacion, fuente_origen
      )
      SELECT ?, GETDATE(),
        p.id, p.modalidad, p.nit, p.nombre_prest, p.num_factura, p.concepto,
        p.valor_factura, p.valor_pagado, p.porcentaje_pago, p.estado, p.voucher, p.feccha_pago,
        p.observacion, p.id_fomag, p.estado_registro, p.estado_pago, p.prefijo, p.no_fact,
        p.fecha_factura, p.fecha_radicacion, p.mes_anio_radicacion, p.fuente_origen
      FROM %s p INNER JOIN CorreccionPagosTmp tmp ON LTRIM(RTRIM(CAST(p.id_fomag AS NVARCHAR(50)))) = LTRIM(RTRIM(CAST(tmp.id_fomag AS NVARCHAR(50))))
      WHERE tmp.lote_id = ?
    """;
    jdbc.update(sql.formatted(tablaDestino), usuario != null ? usuario : "desconocido", loteId);
  }

  private void registrarLogCambios(String usuario, String tablaDestino, String loteId) {
    String sql = """
      INSERT INTO log_actualizaciones_pagos (id_fomag, usuario, fecha_actualizacion, campos_actualizados)
      SELECT cambios.id_fomag, ?, GETDATE(), STRING_AGG(campo, ', ')
      FROM (
        SELECT p.id_fomag, 'modalidad' AS campo
        FROM %s p INNER JOIN CorreccionPagosTmp tmp ON LTRIM(RTRIM(CAST(p.id_fomag AS NVARCHAR(50)))) = LTRIM(RTRIM(CAST(tmp.id_fomag AS NVARCHAR(50))))
        WHERE tmp.lote_id = ? AND ISNULL(p.modalidad,'') COLLATE DATABASE_DEFAULT <> ISNULL(tmp.modalidad,'') COLLATE DATABASE_DEFAULT
        UNION ALL
        SELECT p.id_fomag, 'nit'
        FROM %s p INNER JOIN CorreccionPagosTmp tmp ON LTRIM(RTRIM(CAST(p.id_fomag AS NVARCHAR(50)))) = LTRIM(RTRIM(CAST(tmp.id_fomag AS NVARCHAR(50))))
        WHERE tmp.lote_id = ? AND ISNULL(LTRIM(RTRIM(p.nit)),'') COLLATE DATABASE_DEFAULT <> ISNULL(LTRIM(RTRIM(tmp.nit)),'') COLLATE DATABASE_DEFAULT
        UNION ALL
        SELECT p.id_fomag, 'nombre_prest'
        FROM %s p INNER JOIN CorreccionPagosTmp tmp ON LTRIM(RTRIM(CAST(p.id_fomag AS NVARCHAR(50)))) = LTRIM(RTRIM(CAST(tmp.id_fomag AS NVARCHAR(50))))
        WHERE tmp.lote_id = ? AND ISNULL(p.nombre_prest,'') COLLATE DATABASE_DEFAULT <> ISNULL(tmp.nombre,'') COLLATE DATABASE_DEFAULT
      ) cambios
      GROUP BY cambios.id_fomag
    """;
    jdbc.update(sql.formatted(tablaDestino, tablaDestino, tablaDestino), usuario != null ? usuario : "desconocido", loteId, loteId, loteId);
  }

  private int ejecutarUpdate(String tablaDestino, String loteId) {
    String V_CLEAN = "REPLACE(REPLACE(REPLACE(REPLACE(%s,'$',''),' ',''),CHAR(160),''),CHAR(9),'')";
    String NORM_NUM = "TRY_CONVERT(NUMERIC(18,2), CASE WHEN PATINDEX('%[.,]%', v) > 0 AND CHARINDEX('.', v) > 0 AND CHARINDEX(',', v) > 0 THEN CASE WHEN CHARINDEX('.', v) > CHARINDEX(',', v) THEN REPLACE(v, ',', '') ELSE REPLACE(REPLACE(v, '.', ''), ',', '.') END WHEN CHARINDEX(',', v) > 0 THEN REPLACE(v, ',', '.') ELSE v END)";

    String valorFacturaExpr = NORM_NUM.replace("v", String.format(V_CLEAN, "tmp.valor_factura"));
    String valorPagadoExpr = NORM_NUM.replace("v", String.format(V_CLEAN, "tmp.valor_pagado"));
    String porcentajeExpr = "TRY_CONVERT(NUMERIC(5,2), REPLACE(REPLACE(REPLACE(tmp.porcentaje_pago, '%',''), ' ', ''), CHAR(160), ''))";

    String sql = """
      UPDATE p SET
        p.modalidad = LEFT(tmp.modalidad, 50),
        p.nit = LEFT(tmp.nit, 50),
        p.nombre_prest = LEFT(tmp.nombre, 255),
        p.prefijo = LEFT(tmp.prefijo, 50),
        p.no_fact = LEFT(tmp.no_fact, 50),
        p.num_factura = LEFT(tmp.num_factura, 50),
        p.fecha_factura = COALESCE(tmp.fecha_factura, p.fecha_factura),
        p.fecha_radicacion = COALESCE(tmp.fecha_radicacion, p.fecha_radicacion),
        p.mes_anio_radicacion = LEFT(tmp.mes_anio_radicacion, 20),
        p.valor_factura = %s,
        p.valor_pagado = %s,
        p.porcentaje_pago = %s,
        p.estado = LEFT(tmp.estado, 50),
        p.voucher = LEFT(tmp.voucher, 50),
        p.feccha_pago = COALESCE(tmp.feccha_pago, p.feccha_pago),
        p.fuente_origen = LEFT(tmp.fuente_origen, 100),
        p.observacion = LEFT(tmp.observacion, 255)
      FROM %s p INNER JOIN CorreccionPagosTmp tmp ON LTRIM(RTRIM(CAST(p.id_fomag AS NVARCHAR(50)))) = LTRIM(RTRIM(CAST(tmp.id_fomag AS NVARCHAR(50))))
      WHERE tmp.lote_id = ?
    """.formatted(valorFacturaExpr, valorPagadoExpr, porcentajeExpr, tablaDestino);

    int affected = jdbc.update(sql, loteId);
    Integer tmpCount = jdbc.queryForObject("SELECT COUNT(*) FROM CorreccionPagosTmp WHERE lote_id = ?", Integer.class, loteId);
    if (tmpCount != null && tmpCount > 0 && affected == 0) {
      String missingSql = """
        SELECT TOP 3 tmp.id_fomag AS id
        FROM CorreccionPagosTmp tmp
        LEFT JOIN %s p ON LTRIM(RTRIM(CAST(p.id_fomag AS NVARCHAR(50)))) = LTRIM(RTRIM(CAST(tmp.id_fomag AS NVARCHAR(50))))
        WHERE p.id_fomag IS NULL AND tmp.lote_id = ?
      """.formatted(tablaDestino);
      List<java.util.Map<String,Object>> miss = jdbc.queryForList(missingSql, loteId);
      StringBuilder sb = new StringBuilder("No se encontraron coincidencias por id_fomag en ").append(tablaDestino).append(". Ejemplos: ");
      for (var r : miss) sb.append("['").append(String.valueOf(r.get("id"))).append("'] ");
      sb.append(". Verifica que el CSV sea (;) o (,) y que la Ãºltima columna sea id_fomag.");
      throw new BusinessValidationException(sb.toString());
    }
    return affected;
  }

  private String[] splitSemicolon(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i=0;i<line.length();i++) {
      char ch = line.charAt(i);
      if (ch=='"') {
        if (inQuotes && i+1<line.length() && line.charAt(i+1)=='"') { cur.append('"'); i++; }
        else { inQuotes = !inQuotes; }
      } else if (ch==';' && !inQuotes) {
        out.add(cur.toString()); cur.setLength(0);
      } else { cur.append(ch); }
    }
    out.add(cur.toString());
    return out.toArray(new String[0]);
  }

  private void normalizeRow(String[] cols) {
    for (int i=0;i<cols.length;i++) {
      if (cols[i]==null) continue;
      cols[i] = cols[i].trim();
      if (cols[i].isEmpty()) cols[i] = null;
    }
  }

  private Integer parseInt(String[] c, int idx) {
    try { return (idx < c.length && c[idx]!=null) ? Integer.valueOf(c[idx]) : null; } catch (Exception e) { return null; }
  }

  private Date parseDate(String[] c, int idx) {
    if (idx >= c.length) return null;
    String v = c[idx];
    if (v == null || v.isBlank()) return null;
    v = v.trim();
    if (v.equals("0") || v.equals("0000-00-00") || v.equals("00/00/0000")) return null;
    v = v.replace('/', '-');
    try {
      LocalDate d = LocalDate.parse(v, DMY_DASH);
      return Date.valueOf(d);
    } catch (DateTimeParseException ex) {
      try {
        LocalDate d = LocalDate.parse(v.replace('-', '/'), DMY_SLASH);
        return Date.valueOf(d);
      } catch (DateTimeParseException ex2) {
        return null;
      }
    }
  }

  private boolean usesDotAsDecimal(String v) {
    if (v == null) return false;
    String t = v.replace("$", "").replace(" ", "").replace("\u00A0", "").replace("\t", "");
    int lastDot = t.lastIndexOf('.');
    int lastComma = t.lastIndexOf(',');
    if (lastDot >= 0 && lastComma >= 0) {
      // Si el punto estÃ¡ a la derecha de la Ãºltima coma, asumimos punto decimal
      return lastDot > lastComma;
    }
    if (lastDot >= 0) {
      // Solo hay punto: tratar como decimal si tiene 1-2 dÃ­gitos a la derecha (caso comÃºn en montos/porcentajes)
      int digitsAfter = t.length() - lastDot - 1;
      return digitsAfter > 0 && digitsAfter <= 2;
    }
    return false;
  }

  private java.math.BigDecimal parseDecimal(String[] c, int idx) {
    if (idx >= c.length) return null;
    String v = c[idx];
    if (v == null || v.isBlank()) return null;
    v = v.replace("$", "").replace(" ", "").replace("\u00A0", "").replace("\t", "");
    // si contiene coma y no punto, cambiar a punto
    if (v.indexOf(',') >= 0 && v.indexOf('.') < 0) {
      v = v.replace(',', '.');
    } else if (v.indexOf('.') >= 0 && v.indexOf(',') >= 0) {
      // escoger el separador decimal mÃ¡s a la derecha
      int lastDot = v.lastIndexOf('.');
      int lastComma = v.lastIndexOf(',');
      if (lastDot > lastComma) {
        v = v.replace(",", "");
      } else {
        v = v.replace(".", "");
        v = v.replace(',', '.');
      }
    }
    try { return new java.math.BigDecimal(v); } catch (Exception e) { return null; }
  }

  private static String pickMoney(String[] c, int idx) {
    // Retorna primera celda desde idx que contenga dÃ­gitos (hasta +2 posiciones); ignora celdas con solo sÃ­mbolos
    for (int k = idx; k <= idx + 2 && k < c.length; k++) {
      String v = c[k];
      if (v == null) continue;
      String t = v.replace("$", "").replace(" ", "").replace("\u00A0", "").replace("\t", "");
      if (t.matches(".*\\d.*")) return v; // contiene al menos un dÃ­gito
    }
    return c.length>idx ? c[idx] : null;
  }

  private static String pickPercent(String[] c, int idx) {
    // Busca algo con dÃ­gitos o '%' en posiciÃ³n idx..idx+2
    for (int k = idx; k <= idx + 2 && k < c.length; k++) {
      String v = c[k];
      if (v == null) continue;
      if (v.matches(".*[0-9%].*")) return v;
    }
    return c.length>idx ? c[idx] : null;
  }
}

