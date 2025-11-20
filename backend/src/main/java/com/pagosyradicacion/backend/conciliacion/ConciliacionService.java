package com.pagosyradicacion.backend.conciliacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

@Service
public class ConciliacionService {

  private final JdbcTemplate jdbc;
  public ConciliacionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public String exportCsvPorNit(String nit) {
    final List<Map<String, Object>> rows = new ArrayList<>();

    String sql = """
      SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
      SELECT 
        id, modalidad_pago, nit, nom_prestador, tipo_red, departamento, municipio,
        prefijo, factura, prefijo_factura, mes_radicacion, estado_aplicacion, fecha_radicacion, rango_dias, fecha_factura,
        valor_factura, valor_iva, nota_credito, valor_glosa_inicial, valor_no_glosado_inicial,
        valor_aceptado_primera_respuesta, valor_levantado_primera_respuesta, valor_ratificado_primera_respuesta,
        valor_aceptado_segunda_respuesta, valor_levantado_segunda_respuesta, valor_ratificado_segunda_respuesta,
        valor_aceptado_conciliacion, valor_levantado_conciliacion, valor_ratificado_conciliacion,
        Valor_actual_aceptado, valor_actual_reconocido,
        valor_final_ratificado, valor_pagado, porcentaje_pago, estado, voucher, feccha_pago
      FROM dbo.radicacion_filtrada
      WHERE nit = ?
      ORDER BY factura, feccha_pago, id
    """;

    jdbc.query(sql, new org.springframework.jdbc.core.RowCallbackHandler() {
      @Override public void processRow(ResultSet rs) throws SQLException {
        collectRow(rs, rows);
      }
    }, nit);

    // Precalcular agrupaciones por factura para %Pagos Aplicado
    Map<String, BigDecimal> sumVF = rows.stream().collect(Collectors.groupingBy(
        r -> asStr(r.get("factura")), Collectors.mapping(r -> asBD(r.get("valor_factura")), Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    Map<String, BigDecimal> sumVFR = rows.stream().collect(Collectors.groupingBy(
        r -> asStr(r.get("factura")), Collectors.mapping(r -> asBD(r.get("valor_pagado")), Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

    // Construir CSV (;) con encabezados + columnas calculadas
    String[] headers = new String[] {
      "id","modalidad_pago","nit","nom_prestador","tipo_red","departamento","municipio",
      "prefijo","factura","prefijo_factura","mes_radicacion","estado_aplicacion","fecha_radicacion","rango_dias","fecha_factura",
      // P desde aquÃƒÂ­
      "valor_factura","valor_iva","nota_credito","valor_glosa_inicial","valor_no_glosado_inicial",
      "valor_aceptado_primera_respuesta","valor_levantado_primera_respuesta","valor_ratificado_primera_respuesta",
      "valor_aceptado_segunda_respuesta","valor_levantado_segunda_respuesta","valor_ratificado_segunda_respuesta",
      "valor_aceptado_conciliacion","valor_levantado_conciliacion","valor_ratificado_conciliacion",
      "Valor_actual_aceptado","valor_actual_reconocido",
      // AF primero, luego AG calculada
      "valor_final_ratificado","En_tramite",
      // AH en adelante base
      "valor_pagado","porcentaje_pago","estado","voucher","feccha_pago",
      // Calculadas extra
      "Estado_Saldo","Saldo_Sin_Glosas","Estado_de_Auditoria","Estado_Aplicacion_Pagos","Porc_Pagos_Aplicado"
    };

    StringBuilder out = new StringBuilder();
    writeRow(out, headers);

    for (Map<String, Object> r : rows) {
      BigDecimal valorFactura = asBD(r.get("valor_factura"));
      BigDecimal valorRatConc = asBD(r.get("valor_ratificado_conciliacion"));
      BigDecimal valorActAcept = asBD(r.get("Valor_actual_aceptado"));
      BigDecimal valorActRecon = asBD(r.get("valor_actual_reconocido"));
      BigDecimal valorFinalRat = asBD(r.get("valor_final_ratificado"));
      BigDecimal valorPagado   = asBD(r.get("valor_pagado"));
      BigDecimal pctPago       = asBD(r.get("porcentaje_pago"));
      String estadoAplicacion  = asStr(r.get("estado_aplicacion"));
      String factura           = asStr(r.get("factura"));

      // Calculadas
      BigDecimal enTramite = valorFactura.subtract(valorRatConc).subtract(valorActAcept).subtract(valorActRecon);
      BigDecimal estadoSaldo = valorFactura.subtract(valorFinalRat).subtract(valorRatConc).subtract(valorActRecon);
      BigDecimal saldoSinGlosas = valorActAcept.subtract(valorFinalRat);

      String estadoAuditoria = estadoAuditoria(estadoAplicacion);
      String estadoAplicPagos = estadoAplicacionPagosExcelLogic(estadoAuditoria, valorPagado, pctPago);

      BigDecimal totalVF = sumVF.getOrDefault(factura, BigDecimal.ZERO);
      BigDecimal totalVFR = sumVFR.getOrDefault(factura, BigDecimal.ZERO);
      BigDecimal porcAplicado = BigDecimal.ZERO;
      if (totalVF.compareTo(BigDecimal.ZERO) != 0) {
        porcAplicado = totalVFR.divide(totalVF, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
      }

      List<String> values = new ArrayList<>();
      // Hasta O
      for (String key : new String[]{
          "id","modalidad_pago","nit","nom_prestador","tipo_red","departamento","municipio",
          "prefijo","factura","prefijo_factura","mes_radicacion","estado_aplicacion","fecha_radicacion","rango_dias","fecha_factura"}) {
        values.add(csvSafe(normalizeText(asStr(r.get(key)))));
      }
      // P.. AF
      for (String key : new String[]{
          "valor_factura","valor_iva","nota_credito","valor_glosa_inicial","valor_no_glosado_inicial",
          "valor_aceptado_primera_respuesta","valor_levantado_primera_respuesta","valor_ratificado_primera_respuesta",
          "valor_aceptado_segunda_respuesta","valor_levantado_segunda_respuesta","valor_ratificado_segunda_respuesta",
          "valor_aceptado_conciliacion","valor_levantado_conciliacion","valor_ratificado_conciliacion",
          "Valor_actual_aceptado","valor_actual_reconocido"}) {
        values.add(asString(r.get(key)));
      }
      // AF: valor_final_ratificado
      values.add(asString(valorFinalRat));
      // AG: En_tramite
      values.add(asString(enTramite));
      // AH.. AL: valor_pagado, porcentaje_pago, estado, voucher, feccha_pago
      for (String key : new String[]{
          "valor_pagado","porcentaje_pago","estado","voucher","feccha_pago"}) {
        values.add(csvSafe(normalizeText(asStr(r.get(key)))));
      }
      // Calculadas extra
      values.add(asString(estadoSaldo));
      values.add(asString(saldoSinGlosas));
      values.add(csvSafe(estadoAuditoria));
      values.add(csvSafe(estadoAplicPagos));
      values.add(asString(porcAplicado.setScale(2, RoundingMode.HALF_UP)));

      writeRow(out, values.toArray(new String[0]));
    }

    return out.toString();
  }

  private static void collectRow(ResultSet rs, List<Map<String, Object>> rows) throws SQLException {
    Map<String, Object> r = new HashMap<>();
    r.put("id", rs.getObject("id"));
    r.put("modalidad_pago", normalizeText(rs.getString("modalidad_pago")));
    r.put("nit", rs.getObject("nit"));
    r.put("nom_prestador", normalizeText(rs.getString("nom_prestador")));
    r.put("tipo_red", normalizeText(rs.getString("tipo_red")));
    r.put("departamento", normalizeText(rs.getString("departamento")));
    r.put("municipio", normalizeText(rs.getString("municipio")));
    r.put("prefijo", normalizeText(rs.getString("prefijo")));
    r.put("factura", normalizeText(rs.getString("factura")));
    r.put("prefijo_factura", normalizeText(rs.getString("prefijo_factura")));
    r.put("mes_radicacion", normalizeText(rs.getString("mes_radicacion")));
    r.put("estado_aplicacion", normalizeText(rs.getString("estado_aplicacion")));
    r.put("fecha_radicacion", normalizeText(rs.getString("fecha_radicacion")));
    r.put("fecha_factura", normalizeText(rs.getString("fecha_factura")));
    r.put("rango_dias", normalizeText(rs.getString("rango_dias")));
    r.put("valor_factura", rs.getObject("valor_factura"));
    r.put("valor_iva", rs.getObject("valor_iva"));
    r.put("nota_credito", rs.getObject("nota_credito"));
    r.put("valor_glosa_inicial", rs.getObject("valor_glosa_inicial"));
    r.put("valor_no_glosado_inicial", rs.getObject("valor_no_glosado_inicial"));
    r.put("valor_aceptado_primera_respuesta", rs.getObject("valor_aceptado_primera_respuesta"));
    r.put("valor_levantado_primera_respuesta", rs.getObject("valor_levantado_primera_respuesta"));
    r.put("valor_ratificado_primera_respuesta", rs.getObject("valor_ratificado_primera_respuesta"));
    r.put("valor_aceptado_segunda_respuesta", rs.getObject("valor_aceptado_segunda_respuesta"));
    r.put("valor_levantado_segunda_respuesta", rs.getObject("valor_levantado_segunda_respuesta"));
    r.put("valor_ratificado_segunda_respuesta", rs.getObject("valor_ratificado_segunda_respuesta"));
    r.put("valor_aceptado_conciliacion", rs.getObject("valor_aceptado_conciliacion"));
    r.put("valor_levantado_conciliacion", rs.getObject("valor_levantado_conciliacion"));
    r.put("valor_ratificado_conciliacion", rs.getObject("valor_ratificado_conciliacion"));
    r.put("Valor_actual_aceptado", rs.getObject("Valor_actual_aceptado"));
    r.put("valor_actual_reconocido", rs.getObject("valor_actual_reconocido"));
    r.put("valor_final_ratificado", rs.getObject("valor_final_ratificado"));
    r.put("valor_pagado", rs.getObject("valor_pagado"));
    r.put("porcentaje_pago", rs.getObject("porcentaje_pago"));
    r.put("estado", normalizeText(rs.getString("estado")));
    r.put("voucher", normalizeText(rs.getString("voucher")));
    r.put("feccha_pago", normalizeText(rs.getString("feccha_pago")));
    rows.add(r);
  }

  private static String estadoAuditoria(String estadoAplicacion) {
    if (estadoAplicacion == null) return "En tr\u00E1mite de Auditor\u00EDa";
    String s = estadoAplicacion.trim().toLowerCase(Locale.ROOT);
    if (s.contains("auditado")) return "Auditor\u00EDa Terminada"; // incluye "Auditado" y "Auditado con Glosas"
    return "En tr\u00E1mite de Auditor\u00EDa";
  }

  // Implementa exactamente la fÃƒÂ³rmula que compartiste para AP (Estado AplicaciÃƒÂ³n Pagos)
  private static String estadoAplicacionPagosExcelLogic(String estadoAuditoria, BigDecimal valorPagado, BigDecimal pctPago) {
    boolean enTramite = "En tr\u00E1mite de Auditor\u00EDa".equals(estadoAuditoria);
    boolean pagadoPos = valorPagado != null && valorPagado.compareTo(BigDecimal.ZERO) > 0;
    BigDecimal hundred = new BigDecimal("100");
    if (enTramite) {
      return pagadoPos ? "Postulacion en Giro Previo" : "No Pagado";
    } else {
      if (!pagadoPos) return "No Pagado";
      if ("Auditor\u00EDa Terminada".equals(estadoAuditoria)) {
        if (pctPago != null && pctPago.compareTo(hundred) == 0) return "Legalizado 100%";
        if (pagadoPos) return "Legalizado Parcial";
        return "No Pagado";
      }
      return "No Pagado";
    }
  }

  private static String csvSafe(String v) {
    if (v == null) return "";
    String s = normalizeText(v);
    if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
      s = '"' + s.replace("\"", "\"\"") + '"';
    }
    return s;
  }

  // Normaliza textos con mojibake (UTF-8 mal interpretado como ISO-8859-1)
  private static String normalizeText(String s) {
    if (s == null) return null;
    String t = s;
    if (t.indexOf('Ã') >= 0 || t.indexOf('Â') >= 0 || t.indexOf('æ') >= 0 || t.indexOf('¢') >= 0 || t.indexOf('œ') >= 0) {
      try {
        t = new String(t.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), java.nio.charset.StandardCharsets.UTF_8);
      } catch (Exception ignore) {}
    }
    return t;
  }

  private static String asString(Object v) { return v == null ? "" : String.valueOf(v); }
  private static String asStr(Object v) { return v == null ? "" : String.valueOf(v); }
  private static BigDecimal asBD(Object v) {
    if (v == null) return BigDecimal.ZERO;
    if (v instanceof BigDecimal bd) return bd;
    try { return new BigDecimal(String.valueOf(v)); } catch (Exception e) { return BigDecimal.ZERO; }
  }

  // Intenta parsear varias formas comunes de fecha a LocalDate
  private static java.time.LocalDate parseDateFlexible(String s) {
    if (s == null || s.isBlank()) return null;
    String t = s.trim();
    String[] patterns = new String[] { "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy/MM/dd", "dd-MM-yyyy" };
    for (String p : patterns) {
      try { return java.time.LocalDate.parse(t, java.time.format.DateTimeFormatter.ofPattern(p)); } catch (Exception ignore) {}
    }
    // último recurso: intentar parse ISO_DATE
    try { return java.time.LocalDate.parse(t); } catch (Exception ignore) {}
    return null;
  }

  private static void writeRow(StringBuilder sb, String[] cols) {
    for (int i = 0; i < cols.length; i++) {
      if (i > 0) sb.append(';');
      String v = cols[i] == null ? "" : cols[i];
      if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
        sb.append('"').append(v.replace("\"", "\"\"")).append('"');
      } else {
        sb.append(v);
      }
    }
    sb.append('\n');
  }

  // =========== XLSX Export (con fÃƒÂ³rmulas y hoja 2 en blanco) ===========
  public byte[] exportXlsxPorNit(String nit) {
    // Preparamos datos crudos reutilizando el mÃƒÂ©todo CSV
    final List<Map<String, Object>> rows = new ArrayList<>();
    String sql = """
      SET NOCOUNT ON; SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
      SELECT 
        id, modalidad_pago, nit, nom_prestador, tipo_red, departamento, municipio,
        prefijo, factura, prefijo_factura, mes_radicacion, estado_aplicacion, fecha_radicacion, rango_dias, fecha_factura,
        valor_factura, valor_iva, nota_credito, valor_glosa_inicial, valor_no_glosado_inicial,
        valor_aceptado_primera_respuesta, valor_levantado_primera_respuesta, valor_ratificado_primera_respuesta,
        valor_aceptado_segunda_respuesta, valor_levantado_segunda_respuesta, valor_ratificado_segunda_respuesta,
        valor_aceptado_conciliacion, valor_levantado_conciliacion, valor_ratificado_conciliacion,
        Valor_actual_aceptado, valor_actual_reconocido,
        valor_final_ratificado, valor_pagado, porcentaje_pago, estado, voucher, feccha_pago
      FROM dbo.radicacion_filtrada WHERE nit = ? ORDER BY factura, feccha_pago, id
    """;
    jdbc.query(sql, new org.springframework.jdbc.core.RowCallbackHandler() {
      @Override public void processRow(ResultSet rs) throws SQLException {
        collectRow(rs, rows);
      }
    }, nit);
    try (java.io.InputStream tplIs = openTemplateInputStream()) {
      // Cargar plantilla (XSSFWorkbook), limpiar la hoja objetivo ANTES de pasar a SXSSF
      org.apache.poi.xssf.usermodel.XSSFWorkbook base = (tplIs != null ? new org.apache.poi.xssf.usermodel.XSSFWorkbook(tplIs) : new org.apache.poi.xssf.usermodel.XSSFWorkbook());
      final boolean usingTemplate = base.getNumberOfSheets() > 0;
      int keepIdx = -1;
      for (int i = 0; i < base.getNumberOfSheets(); i++) {
        String nm = base.getSheetName(i);
        String norm = nm.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (norm.equals("detalleradicacion")) {
          if (keepIdx == -1) keepIdx = i; else { base.removeSheetAt(i); i--; }
        }
      }
      boolean sheetFromTemplate;
      if (keepIdx == -1) {
        base.createSheet("Detalle_Radicacion");
        keepIdx = base.getSheetIndex("Detalle_Radicacion");
        sheetFromTemplate = false;
      } else {
        sheetFromTemplate = true;
        org.apache.poi.xssf.usermodel.XSSFSheet xssSheet = base.getSheetAt(keepIdx);
        for (int i = xssSheet.getNumMergedRegions() - 1; i >= 0; i--) xssSheet.removeMergedRegion(i);
        // Conservar encabezados de la fila 0, borrar contenido desde fila 1
        for (int i = xssSheet.getLastRowNum(); i >= 1; i--) {
          org.apache.poi.ss.usermodel.Row rr = xssSheet.getRow(i);
          if (rr != null) xssSheet.removeRow(rr);
        }
      }

      try (org.apache.poi.xssf.streaming.SXSSFWorkbook wb = new org.apache.poi.xssf.streaming.SXSSFWorkbook(base, 500, true, false);
           java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
        org.apache.poi.ss.usermodel.Sheet sh = wb.getSheetAt(keepIdx);

      // Headers
      String[] headers = new String[] {
        "id","modalidad_pago","nit","nom_prestador","tipo_red","departamento","municipio",
        "prefijo","factura","prefijo_factura","mes_radicacion","estado_aplicacion","fecha_radicacion","rango_dias",
        "fecha_factura","valor_factura","valor_iva","nota_credito","valor_glosa_inicial","valor_no_glosado_inicial",
        "valor_aceptado_primera_respuesta","valor_levantado_primera_respuesta","valor_ratificado_primera_respuesta",
        "valor_aceptado_segunda_respuesta","valor_levantado_segunda_respuesta","valor_ratificado_segunda_respuesta",
        "valor_aceptado_conciliacion","valor_levantado_conciliacion","valor_ratificado_conciliacion",
        "Valor_actual_aceptado","valor_actual_reconocido",
        "valor_final_ratificado","En_tramite",
        "valor_pagado","porcentaje_pago","estado","voucher","feccha_pago",
        "Estado_Saldo","Saldo_Sin_Glosas","Estado_de_Auditoria","Estado_Aplicacion_Pagos","Porc_Pagos_Aplicado"
      };

        if (!sheetFromTemplate) {
          org.apache.poi.ss.usermodel.Row hr = sh.createRow(0);
          for (int c = 0; c < headers.length; c++) hr.createCell(c).setCellValue(headers[c]);
        }
      // Ocultar columna C (modalidad_Factura) para que no "aparezca" en el reporte,
      // pero se conserve el posicionamiento de letras requerido por las fÃƒÂ³rmulas.
      // ÃƒÂndice 0-based => 2 = columna C
      

      // Estilos simples
        org.apache.poi.ss.usermodel.CellStyle pctStyle = wb.createCellStyle();
      pctStyle.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
      org.apache.poi.ss.usermodel.CellStyle numStyle = wb.createCellStyle();
      numStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
      org.apache.poi.ss.usermodel.CellStyle dateStyle = wb.createCellStyle();
      dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));

        int rIdx = 1; // fila 1 es header, datos comienzan en 2 (Excel indexing)
        for (Map<String, Object> r : rows) {
          org.apache.poi.ss.usermodel.Row row = sh.createRow(rIdx);
        int c = 0;
        // A..O (agrega fecha_factura)
        for (String key : new String[]{
            "id","modalidad_pago","nit","nom_prestador","tipo_red","departamento","municipio",
            "prefijo","factura","prefijo_factura","mes_radicacion","estado_aplicacion","fecha_radicacion","rango_dias","fecha_factura"}) {
          if (key.equals("fecha_radicacion") || key.equals("fecha_factura")) {
            java.time.LocalDate ld = parseDateFlexible(asStr(r.get(key)));
            if (ld != null) { var d = row.createCell(c++); d.setCellValue(java.sql.Date.valueOf(ld)); d.setCellStyle(dateStyle); continue; }
          }
          row.createCell(c++).setCellValue(normalizeText(asStr(r.get(key))));
        }
        // P..AE (valores numéricos donde aplique)
        for (String key : new String[]{
            "valor_factura","valor_iva","nota_credito","valor_glosa_inicial","valor_no_glosado_inicial",
            "valor_aceptado_primera_respuesta","valor_levantado_primera_respuesta","valor_ratificado_primera_respuesta",
            "valor_aceptado_segunda_respuesta","valor_levantado_segunda_respuesta","valor_ratificado_segunda_respuesta",
            "valor_aceptado_conciliacion","valor_levantado_conciliacion","valor_ratificado_conciliacion",
            "Valor_actual_aceptado","valor_actual_reconocido"}) {
          var cell = row.createCell(c++); cell.setCellValue(asBD(r.get(key)).doubleValue()); cell.setCellStyle(numStyle);
        }
        // AG En_tramite formula = P - AD - AE - AF (ahora AF es valor_final_ratificado)
        String ridx = String.valueOf(rIdx + 1);
        // AF ya lo pusimos abajo; aquÃƒÂ­ lo agregamos primero y luego En_tramite
        // AF valor_final_ratificado (num)
        var afCell = row.createCell(c++); afCell.setCellValue(asBD(r.get("valor_final_ratificado")).doubleValue()); afCell.setCellStyle(numStyle);
        // AG En_tramite
        {
          var f = row.createCell(c++);
          f.setCellFormula("P"+ridx+"-AD"+ridx+"-AE"+ridx+"-AF"+ridx);
          f.setCellStyle(numStyle);
        }
        // AH..AL base: valor_pagado, porcentaje_pago, estado, voucher, feccha_pago
        for (String key : new String[]{"valor_pagado","porcentaje_pago","estado","voucher","feccha_pago"}) {
          if (key.equals("porcentaje_pago")) {
            var cell = row.createCell(c++); cell.setCellValue(asBD(r.get(key)).divide(new java.math.BigDecimal("100")).doubleValue()); cell.setCellStyle(pctStyle);
          } else if (key.equals("valor_pagado")) {
            var cell = row.createCell(c++); cell.setCellValue(asBD(r.get(key)).doubleValue()); cell.setCellStyle(numStyle);
          } else {
            row.createCell(c++).setCellValue(normalizeText(asStr(r.get(key))));
          }
        }
        // AM Estado_Saldo = P - AH - AD - AF
        {
          var f = row.createCell(c++);
          f.setCellFormula("P"+ridx+"-AH"+ridx+"-AD"+ridx+"-AF"+ridx);
          f.setCellStyle(numStyle);
        }
        // AN Saldo_Sin_Glosas = AE - AH
        {
          var f = row.createCell(c++);
          f.setCellFormula("AE"+ridx+"-AH"+ridx);
          f.setCellStyle(numStyle);
        }
        // AO Estado de Auditoría: escrito como texto (sin fórmula) para evitar problemas de codificación
        row.createCell(c++).setCellValue(estadoAuditoria(asStr(r.get("estado_aplicacion"))));
        // AP Estado AplicaciÃƒÂ³n Pagos: segÃƒÂºn fÃƒÂ³rmula proporcionada
        // AP Estado AplicaciÃ³n Pagos (calculado)
        row.createCell(c++).setCellValue(estadoAplicacionPagosExcelLogic(estadoAuditoria(asStr(r.get("estado_aplicacion"))), asBD(r.get("valor_pagado")), asBD(r.get("porcentaje_pago"))));
        // AQ % Pagos Aplicado = SUMIF(J:J,J2,AH:AH)/SUMIF(J:J,J2,P:P)
        var aq = row.createCell(c++); aq.setCellFormula("SUMIF(I:I,I"+ridx+",AH:AH)/SUMIF(I:I,I"+ridx+",P:P)"); aq.setCellStyle(pctStyle);

          rIdx++;
        }

        // Forzar recálculo de fórmulas al abrir y asignar anchos (sin autoSize para streaming)
        wb.setForceFormulaRecalculation(true);
        sh.setForceFormulaRecalculation(true);
        if (!sheetFromTemplate) {
          for (int i = 0; i < headers.length; i++) sh.setColumnWidth(i, 6000);
        }

      // ====================== Hoja 2: Estado de Cuenta ======================
      org.apache.poi.ss.usermodel.Sheet sh2 = wb.getSheet("Estado_Cuenta");
      if (sh2 == null) {
        if (!usingTemplate) {
        sh2 = wb.createSheet("Estado_Cuenta");
      int r = 0;
      // Encabezados entidad
      sh2.createRow(r++).createCell(0).setCellValue("FIDEICOMISOS PATRIMONIOS AUTÃƒâ€œNOMOS FIDUCIARIA LA PREVISORA S.A.");
      sh2.createRow(r++).createCell(0).setCellValue("FONDO DE PRESTACIONES SOCIALES DEL MAGISTERIO");
      r++;
      sh2.createRow(r++).createCell(0).setCellValue("PROCESO: AdministraciÃƒÂ³n de Servicios de Salud");
      sh2.createRow(r++).createCell(0).setCellValue("NIT: "+ (rows.isEmpty()? nit : asStr(rows.get(0).get("nit"))));
      sh2.createRow(r++).createCell(0).setCellValue("CÃƒâ€œDIGO:");
      sh2.createRow(r++).createCell(0).setCellValue("ESTADO DE CUENTA POR PRESTADOR");
      sh2.createRow(r++).createCell(0).setCellValue("VERSIÃƒâ€œN: 06");
      r++;
      // Estado de cuenta al hoy
      String hoy = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d-MMMM-yyyy", new java.util.Locale("es","CO")));
      sh2.createRow(r++).createCell(0).setCellValue("Estado de cuenta por Prestador al "+hoy);
      sh2.createRow(r++).createCell(0).setCellValue("No. Formato -2384");
      r++;
      // Datos prestador
      String razon = rows.stream().map(m -> asStr(m.get("nom_prestador"))).filter(s -> !s.isBlank()).findFirst().orElse("");
      sh2.createRow(r++).createCell(0).setCellValue("NIT del Prestador o Proveedor");
      sh2.createRow(r++).createCell(0).setCellValue("RazÃƒÂ³n social: "+ razon);
      // Corte de radicaciÃƒÂ³n fijo al 30/09 del aÃƒÂ±o en curso
      int year = java.time.LocalDate.now().getYear();
      sh2.createRow(r++).createCell(0).setCellValue("Corte de RadicaciÃƒÂ³n "+String.format("%02d/09/%d",30,year));
      r++;
      sh2.createRow(r++).createCell(0).setCellValue("Fecha de ElaboraciÃƒÂ³n: "+ hoy);
      sh2.createRow(r++).createCell(0).setCellValue("Modalidad: Evento");
      // Cantidad de facturas (cuenta filas detalle menos encabezado)
      org.apache.poi.ss.usermodel.Row rf = sh2.createRow(r++);
      rf.createCell(0).setCellValue("Cantidad de facturas radicadas al corte");
      rf.createCell(1).setCellFormula("COUNTA('Detalle_Radicacion'!I:I)-1");
      r += 1;

      // Tabla de resumen: encabezados
      org.apache.poi.ss.usermodel.Row th = sh2.createRow(r++);
      th.createCell(0).setCellValue("DescripciÃƒÂ³n");
      th.createCell(1).setCellValue("Vigencia "+(year-1));
      th.createCell(2).setCellValue("Vigencia "+year);
      th.createCell(3).setCellValue("Total");

      String sRef = "'Detalle_Radicacion'!";
      // Fechas como serial de Excel para evitar concatenaciones con DATE() que POI no parsea en setCellFormula
      int dateStartPrev = (int)Math.floor(org.apache.poi.ss.usermodel.DateUtil.getExcelDate(java.sql.Date.valueOf(java.time.LocalDate.of(year-1,1,1))));
      int dateEndPrev   = (int)Math.floor(org.apache.poi.ss.usermodel.DateUtil.getExcelDate(java.sql.Date.valueOf(java.time.LocalDate.of(year-1,12,31))));
      int dateStartCur  = (int)Math.floor(org.apache.poi.ss.usermodel.DateUtil.getExcelDate(java.sql.Date.valueOf(java.time.LocalDate.of(year,1,1))));
      int dateEndCur    = (int)Math.floor(org.apache.poi.ss.usermodel.DateUtil.getExcelDate(java.sql.Date.valueOf(java.time.LocalDate.of(year,12,31))));

      // Fila A: Total Radicado (Sin Devoluciones)
      org.apache.poi.ss.usermodel.Row ra = sh2.createRow(r++);
      ra.createCell(0).setCellValue("A. (+) Total Radicado (Sin Devoluciones)");
      ra.createCell(1).setCellFormula("SUMIFS("+sRef+"O:O,"+sRef+"M:M,\">="+""+dateStartPrev+"\","+sRef+"M:M,\"<="+""+dateEndPrev+"\")");
      ra.createCell(2).setCellFormula("SUMIFS("+sRef+"O:O,"+sRef+"M:M,\">="+""+dateStartCur+"\","+sRef+"M:M,\"<="+""+dateEndCur+"\")");
      ra.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // Fila A1: RadicaciÃƒÂ³n corriente (0 al aÃƒÂ±o anterior; aÃƒÂ±o actual sum por rangos)
      org.apache.poi.ss.usermodel.Row ra1 = sh2.createRow(r++);
      ra1.createCell(0).setCellValue("A1. RadicaciÃƒÂ³n corriente");
      ra1.createCell(1).setCellValue(0);
      ra1.createCell(2).setCellFormula(
        "SUMIF("+sRef+"N:N,\"0 a 30 dÃƒÂ­as\","+sRef+"P:P)+SUMIF("+sRef+"N:N,\"31 a 45 dÃƒÂ­as\","+sRef+"P:P)+SUMIF("+sRef+"N:N,\"46 a 60 dÃƒÂ­as\","+sRef+"P:P)"
      );
      ra1.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // Fila A2: Radicado no corriente (A - A1)
      org.apache.poi.ss.usermodel.Row ra2 = sh2.createRow(r++);
      ra2.createCell(0).setCellValue("A2. Radicado no corriente");
      ra2.createCell(1).setCellFormula("B"+(r-2)); // =B de fila A
      ra2.createCell(2).setCellFormula("C"+(r-3)+"-C"+(r-2)); // = C(A) - C(A1)
      ra2.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // Fila B: Sin Respuesta a glosa o aceptado (col AD)
      org.apache.poi.ss.usermodel.Row rb = sh2.createRow(r++);
      rb.createCell(0).setCellValue("B. (-) Sin Respuesta a glosa o aceptado");
      rb.createCell(1).setCellFormula("SUMIFS("+sRef+"AC:AC,"+sRef+"M:M,\">="+""+dateStartPrev+"\","+sRef+"M:M,\"<="+""+dateEndPrev+"\")");
      rb.createCell(2).setCellFormula("SUMIFS("+sRef+"AC:AC,"+sRef+"M:M,\">="+""+dateStartCur+"\","+sRef+"M:M,\"<="+""+dateEndCur+"\")");
      rb.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // Fila C: Valor Pagado (AH)
      org.apache.poi.ss.usermodel.Row rc = sh2.createRow(r++);
      rc.createCell(0).setCellValue("C. (-) Valor Pagado");
      rc.createCell(1).setCellFormula("SUMIFS("+sRef+"AG:AG,"+sRef+"M:M,\">="+""+dateStartPrev+"\","+sRef+"M:M,\"<="+""+dateEndPrev+"\")");
      rc.createCell(2).setCellFormula("SUMIFS("+sRef+"AG:AG,"+sRef+"M:M,\">="+""+dateStartCur+"\","+sRef+"M:M,\"<="+""+dateEndCur+"\")");
      rc.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // Fila C1: Vr por Aplicar Pago a factura (AO<0 y por fecha)<-- usaremos AO<0 y fecha < 1/1/prevYear o <1/1/currentYear respectivamente
      org.apache.poi.ss.usermodel.Row rc1 = sh2.createRow(r++);
      rc1.createCell(0).setCellValue("C1. Vr por Aplicar Pago a factura");
      rc1.createCell(1).setCellFormula("SUMIFS("+sRef+"AL:AL,"+sRef+"M:M,\"<"+""+dateStartPrev+"\","+sRef+"AL:AL,\"<0\")");
      rc1.createCell(2).setCellFormula("SUMIFS("+sRef+"AL:AL,"+sRef+"M:M,\"<"+""+dateStartCur+"\","+sRef+"AL:AL,\"<0\")");
      rc1.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // Fila C2: Vr Pago bajo factura = C - C1
      org.apache.poi.ss.usermodel.Row rc2 = sh2.createRow(r++);
      rc2.createCell(0).setCellValue("C2. Vr Pago bajo factura");
      rc2.createCell(1).setCellFormula("B"+(r-2)+"-B"+(r-1));
      rc2.createCell(2).setCellFormula("C"+(r-3)+"-C"+(r-2));
      rc2.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // D: Anticipo Medico (0)
      org.apache.poi.ss.usermodel.Row rd = sh2.createRow(r++);
      rd.createCell(0).setCellValue("D. Anticipo Medico (Pendiente de legalizar)");
      rd.createCell(1).setCellValue(0); rd.createCell(2).setCellValue(0); rd.createCell(3).setCellValue(0);

      // E: Estado Saldo = A - A1 - B - C
      org.apache.poi.ss.usermodel.Row re = sh2.createRow(r++);
      re.createCell(0).setCellValue("E. (=) Estado Saldo (A-A1-B-C)");
      re.createCell(1).setCellFormula("B"+(th.getRowNum()+2)+"-B"+(th.getRowNum()+3)+"-B"+(th.getRowNum()+5)+"-B"+(th.getRowNum()+6));
      re.createCell(2).setCellFormula("C"+(th.getRowNum()+2)+"-C"+(th.getRowNum()+3)+"-C"+(th.getRowNum()+5)+"-C"+(th.getRowNum()+6));
      re.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // F: Valor Ratificado Glosa (AF) por rangos de dÃƒÂ­as (>=121) (aprox)
      org.apache.poi.ss.usermodel.Row rfF = sh2.createRow(r++);
      rfF.createCell(0).setCellValue("F. (-) Valor Ratificado Glosa");
      rfF.createCell(1).setCellFormula("SUMIFS("+sRef+"AE:AE,"+sRef+"M:M,\">="+""+dateStartPrev+"\","+sRef+"M:M,\"<="+""+dateEndPrev+"\")");
      rfF.createCell(2).setCellFormula("SUMIFS("+sRef+"AE:AE,"+sRef+"M:M,\">="+""+dateStartCur+"\","+sRef+"M:M,\"<="+""+dateEndCur+"\")");
      rfF.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // G: Saldo Sin Glosas = E - F
      org.apache.poi.ss.usermodel.Row rg = sh2.createRow(r++);
      rg.createCell(0).setCellValue("G. (=) Saldo Sin Glosas (E-F)");
      rg.createCell(1).setCellFormula("B"+(re.getRowNum()+1)+"-B"+(rfF.getRowNum()+1));
      rg.createCell(2).setCellFormula("C"+(re.getRowNum()+1)+"-C"+(rfF.getRowNum()+1));
      rg.createCell(3).setCellFormula("SUM(B"+r+":C"+r+")");

      // Notas y firma
      r += 2;
      sh2.createRow(r++).createCell(0).setCellValue("Nota1: en el literal G si el saldo refleja valor negativo indica que no es procedente liberar saldo por concepto de glosas vigentes");
      sh2.createRow(r++).createCell(0).setCellValue("Nota 2: El Presente formato no constituye pago obligaciÃƒÂ³n y por tanto no puede ser tomado como exigibilidad y valor definitivo; ya que las facturas estÃƒÂ¡n sujetas a un proceso de auditorÃƒÂ­a de cuentas mÃƒÂ©dicas.");
      sh2.createRow(r++).createCell(0).setCellValue("Nota 3: En los casos que registran valor ratificado de glosas; estan sujetos a variaciÃƒÂ³n conforme a las respuesta de glosas efectuadas en la plataforma.");
      sh2.createRow(r++).createCell(0).setCellValue("Nota 4: La informaciÃƒÂ³n contenida en el presente formato esta elaborado a la fecha de corte de radicaciÃƒÂ³n y no contempla la facturaciÃƒÂ³n en estado devuelta.");
      sh2.createRow(r++).createCell(0).setCellValue("Nota 5: Para el item C1 corresponde a facturacion a la que se realizÃƒÂ³ giro previo y por devoluciones sustentadas no se cuenta con valores para aplicar.");
      r += 2;
      sh2.createRow(r++).createCell(0).setCellValue("Elaborado por: ");
      sh2.createRow(r++).createCell(0).setCellValue("Cargo: Profesional");
      sh2.createRow(r++).createCell(0).setCellValue("CoordinaciÃƒÂ³n de conciliaciÃƒÂ³n y cartera");
      sh2.createRow(r++).createCell(0).setCellValue("DirecciÃƒÂ³n administrativa y Financiera de la Gerencia de Servicios de Salud FOMAG");

      }}

        wb.write(bos);
        return bos.toByteArray();
      }
    } catch (Exception e) {
      throw new RuntimeException("Error generando XLSX: " + e.getMessage(), e);
    }
  }

  // Plantilla opcional para la hoja 2 (Estado_Cuenta)
  // - Usa CONCILIACION_XLSX_TEMPLATE (ruta absoluta) si estÃƒÂ¡ definida.
  // - Si no, intenta cargar desde el classpath:
  //   conciliacion/estado_cuenta_template.xlsx o estado_cuenta_template.xlsx
  private java.io.InputStream openTemplateInputStream() {
    try {
      String path = System.getenv("CONCILIACION_XLSX_TEMPLATE");
      if (path != null && !path.isBlank()) {
        java.io.File f = new java.io.File(path);
        if (f.exists() && f.isFile()) return new java.io.FileInputStream(f);
      }
    } catch (Exception ignore) {}
    try {
      ClassLoader cl = this.getClass().getClassLoader();
      java.io.InputStream in = cl.getResourceAsStream("conciliacion/estado_cuenta_template.xlsx");
      if (in != null) return in;
      return cl.getResourceAsStream("estado_cuenta_template.xlsx");
    } catch (Exception ignore) {}
    return null;
  }
}




