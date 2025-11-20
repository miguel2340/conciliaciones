package com.pagosyradicacion.backend.carga;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CargaRadicacionCapitaService {

  private static final String FINAL_SCHEMA = "dbo";
  private static final String FINAL_TABLE = "radicacion_capita";
  private static final String STAGING_SCHEMA = "dbo";
  private static final String STAGING_TABLE = "radicacion_capita_staging_app";

  private final JdbcTemplate jdbc;

  public CargaRadicacionCapitaService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public String cargarCsvReemplazando(MultipartFile archivo) {
    if (archivo == null || archivo.isEmpty()) {
      throw new BusinessValidationException("Debes adjuntar un archivo CSV");
    }
    String original = archivo.getOriginalFilename();
    if (original != null && original.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls)$")) {
      throw new BusinessValidationException("Archivo Excel (.xlsx/.xls) no soportado. Exporta como CSV (;) o (,) con encabezado");
    }

    // 1) Descubrir columnas de staging y final
    List<Map<String, Object>> stgColsRows = jdbc.queryForList(
        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION",
        STAGING_SCHEMA, STAGING_TABLE);
    if (stgColsRows.isEmpty()) {
      throw new BusinessValidationException("No se encontraron columnas en la tabla staging: " + STAGING_TABLE);
    }
    List<String> stgCols = new ArrayList<>();
    for (var r : stgColsRows) stgCols.add(String.valueOf(r.get("COLUMN_NAME")));
    Map<String, String> stgByNorm = new HashMap<>();
    for (String c : stgCols) stgByNorm.put(norm(c), c);

    List<Map<String, Object>> finColsRows = jdbc.queryForList(
        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION",
        FINAL_SCHEMA, FINAL_TABLE);
    if (finColsRows.isEmpty()) {
      throw new BusinessValidationException("No se encontraron columnas en la tabla final: " + FINAL_TABLE);
    }
    Set<String> finColsSet = new HashSet<>();
    for (var r : finColsRows) finColsSet.add(String.valueOf(r.get("COLUMN_NAME")));

    // 2) Limpiar staging antes de insertar
    jdbc.execute("TRUNCATE TABLE " + STAGING_SCHEMA + "." + STAGING_TABLE);

    // 3) Leer CSV y mapear header → staging columns (normalizando acentos/espacios/puntuación)
    List<String> insertCols = new ArrayList<>();
    List<Integer> csvIndexToStgIndex = new ArrayList<>();
    List<String[]> rows = new ArrayList<>();
    int expectedCols = 0; // columnas según encabezado

    try (InputStream is = archivo.getInputStream();
         BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line; boolean first = true; Character delim = null;
      while ((line = reader.readLine()) != null) {
        if (first) {
          if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
          // Detecta por mejor split entre ; , TAB o |
          String[] header = parseBest(line);
          delim = bestDelimiter(line);
          expectedCols = header.length;
          if (header == null || header.length == 0) throw new BusinessValidationException("El CSV no tiene encabezado válido");

          // build mapping
          for (int i = 0; i < header.length; i++) {
            String h = header[i] == null ? "" : header[i].trim();
            String stgReal = stgByNorm.get(norm(h));
            if (stgReal != null) {
              insertCols.add(stgReal);
              csvIndexToStgIndex.add(i);
            }
          }
          if (insertCols.isEmpty()) {
            throw new BusinessValidationException("El encabezado del CSV no mapea a ninguna columna de staging");
          }
          first = false; continue;
        }
        String[] cols = parseCsv(line, delim != null ? delim : ',');
        if (cols.length <= 1) cols = parseBest(line);
        if (expectedCols > 0 && cols.length != expectedCols) {
          String[] best = parseBest(line);
          if (best.length == expectedCols) {
            cols = best;
          } else {
            int fila = rows.size() + 2; // incluye encabezado
            throw new BusinessValidationException(
              "Separador inconsistente o columnas faltantes en la fila " + fila +
              ": se detectaron " + cols.length + " columnas, pero el encabezado tiene " + expectedCols +
              ". Usa ';' y verifica que el encabezado comience por 'ID;Modalidad Pago;Modalidad Factura;NIT;Nombre Prestador'.");
          }
        }
        rows.add(cols);
      }
    } catch (Exception ex) {
      throw new BusinessValidationException("No fue posible leer el CSV: " + ex.getMessage());
    }

    if (rows.isEmpty()) {
      throw new BusinessValidationException("El archivo no contiene filas de datos");
    }

    // 4) Insert batch en staging (solo columnas mapeadas)
    final int perRow = insertCols.size();
    StringBuilder sb = new StringBuilder();
    sb.append("INSERT INTO ").append(STAGING_SCHEMA).append('.').append(STAGING_TABLE).append(" (");
    for (int i = 0; i < perRow; i++) { if (i>0) sb.append(','); sb.append('[').append(insertCols.get(i)).append(']'); }
    sb.append(") VALUES (");
    for (int i = 0; i < perRow; i++) { if (i>0) sb.append(','); sb.append('?'); }
    sb.append(")");
    String insertSql = sb.toString();

    jdbc.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        String[] r = rows.get(i);
        for (int c = 0; c < perRow; c++) {
          int csvIdx = csvIndexToStgIndex.get(c);
          String v = (csvIdx < r.length) ? r[csvIdx] : null;
          ps.setString(c + 1, v == null ? null : v.trim());
        }
      }
      @Override public int getBatchSize() { return rows.size(); }
    });

    // 5) Reemplazo total: TRUNCATE final y INSERT columnas comunes (intersección)
    try {
      jdbc.execute("TRUNCATE TABLE " + FINAL_SCHEMA + "." + FINAL_TABLE);
    } catch (Exception ex) {
      jdbc.execute("DELETE FROM " + FINAL_SCHEMA + "." + FINAL_TABLE);
    }

    List<String> common = new ArrayList<>();
    for (String sc : stgCols) if (finColsSet.contains(sc)) common.add(sc);
    if (common.isEmpty()) {
      throw new BusinessValidationException("No hay columnas comunes entre staging y final para insertar");
    }

    StringBuilder ins = new StringBuilder();
    ins.append("INSERT INTO ").append(FINAL_SCHEMA).append('.').append(FINAL_TABLE).append(" (");
    for (int i = 0; i < common.size(); i++) { if (i>0) ins.append(','); ins.append('[').append(common.get(i)).append(']'); }
    ins.append(") SELECT ");
    for (int i = 0; i < common.size(); i++) { if (i>0) ins.append(','); ins.append('[').append(common.get(i)).append(']'); }
    ins.append(" FROM ").append(STAGING_SCHEMA).append('.').append(STAGING_TABLE);
    jdbc.execute(ins.toString());

    return "Registros cargados en staging: " + rows.size() + ". Reemplazo total de " + FINAL_TABLE + " completado.";
  }

  /* ===================== Helpers ===================== */
  private static Character bestDelimiter(String firstLine) {
    int best = 0; char bestDelim = ';';
    for (char d : new char[]{';', ',', '\t', '|'}) {
      String[] t = parseCsv(firstLine, d);
      int c = t != null ? t.length : 0;
      if (c > best) { best = c; bestDelim = d; }
    }
    return bestDelim;
  }

  private static int countSeparators(String line, char sep) {
    boolean inQuotes = false; int c = 0;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') { if (inQuotes && i+1<line.length() && line.charAt(i+1)=='"') { i++; } else { inQuotes = !inQuotes; } }
      else if (ch == sep && !inQuotes) { c++; }
    }
    return c;
  }

  private static String[] parseBest(String line) {
    String[][] cand = new String[][] {
      parseCsv(line, ';'),
      parseCsv(line, ','),
      parseCsv(line, '\t'),
      parseCsv(line, '|')
    };
    String[] best = null; int max = -1;
    for (String[] a : cand) { int sz = a != null ? a.length : 0; if (sz > max) { max = sz; best = a; } }
    return best != null ? best : new String[] { line };
  }

  private static String[] parseCsv(String line, char delimiter) {
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
    String[] arr = new String[out.size()];
    for (int i = 0; i < out.size(); i++) arr[i] = out.get(i);
    return arr;
  }

  private static String norm(String s) {
    if (s == null) return "";
    String t = s.trim().toLowerCase(Locale.ROOT);
    t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    t = t.replace('.', ' ').replace('-', ' ');
    t = t.replaceAll("\\s+", " ").trim();
    return t;
  }
}
