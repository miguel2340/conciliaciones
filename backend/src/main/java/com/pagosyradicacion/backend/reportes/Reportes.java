package com.pagosyradicacion.backend.reportes;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Service
class ReporteService {
  private static final String[] VOUCHER_HEADERS = new String[] {
      "estado_aplicacion",
      "mes_anio_radicacion",
      "tipo_red",
      "nit",
      "nom_prestador",
      "departamento",
      "modalidad_pago",
      "modalidad_factura",
      "voucher",
      "rango_dias",
      "valor_pagado",
      "no_facturas" };

  private static final String VOUCHER_SQL = """
      SELECT 
        estado_aplicacion,
        FORMAT(fecha_radicacion, 'MMMM/yyyy', 'es-ES') AS mes_anio_radicacion,
        tipo_red,
        nit,
        nom_prestador,
        departamento,
        modalidad_pago,
        modalidad_Factura,
        voucher,
        CASE
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 0 AND 30 THEN '0 a 30 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 31 AND 45 THEN '31 a 45 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 46 AND 60 THEN '46 a 60 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 91 AND 120 THEN '91 a 120 d?as'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 121 AND 150 THEN '121 a 150 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 151 AND 180 THEN '151 a 180 días'
          ELSE 'Mayor a 180 días'
        END AS rango_dias,
        ISNULL(SUM(valor_pagado), 0) AS valor_pagado,
        COUNT(prefijo_factura) AS no_facturas
      FROM fomagf.dbo.radicacion_filtrada
      WHERE estado_aplicacion <> 'No Aprobado por Calidad'
      GROUP BY 
        estado_aplicacion,
        FORMAT(fecha_radicacion, 'MMMM/yyyy', 'es-ES'),
        tipo_red,
        nit,
        nom_prestador,
        departamento,
        modalidad_pago,
        modalidad_Factura,
        voucher,
        CASE
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 0 AND 30 THEN '0 a 30 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 31 AND 45 THEN '31 a 45 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 46 AND 60 THEN '46 a 60 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 91 AND 120 THEN '91 a 120 d?as'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 121 AND 150 THEN '121 a 150 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 151 AND 180 THEN '151 a 180 días'
          ELSE 'Mayor a 180 días'
        END
      ORDER BY
        nit ASC,
        nom_prestador ASC,
        FORMAT(fecha_radicacion, 'MMMM/yyyy', 'es-ES') ASC,
        departamento ASC
      """;

  private static final String[] GERENCIAL_HEADERS = new String[] {
      "estado_aplicacion",
      "mes_anio_radicado",
      "tipo_red",
      "nit",
      "nom_prestador",
      "departamento",
      "modalidad_pago",
      "modalidad_factura",
      "mes_anio_factura",
      "rango_dias",
      "Cantidad_facturas",
      "valor_facturado",
      "valor_pagado",
      "valor_iva",
      "valor_glosa_inicial",
      "valor_no_glosado_inicial",
      "valor_aceptado_primera_respuesta",
      "valor_levantado_primera_respuesta",
      "valor_ratificado_primera_respuesta",
      "valor_aceptado_segunda_respuesta",
      "valor_levantado_segunda_respuesta",
      "valor_ratificado_segunda_respuesta",
      "valor_aceptado_conciliacion",
      "valor_levantado_conciliacion",
      "valor_ratificado_conciliacion",
      "valor_actual_aceptado",
      "valor_actual_reconocido",
      "valor_actual_ratificado",
      "iddyg_minimo_mes"
  };

  private static final String GERENCIAL_SQL_1 = """
      SELECT 
        estado_aplicacion,
        FORMAT(fecha_radicacion, 'MMMM/yyyy', 'es-ES') AS mes_anio_radicado,
        tipo_red,
        nit,
        nom_prestador,
        departamento,
        modalidad_pago,
        modalidad_Factura AS modalidad_factura,
        FORMAT(fecha_factura, 'MMMM/yyyy', 'es-ES') AS mes_anio_factura,
        CASE
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 0 AND 30 THEN '0 a 30 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 31 AND 45 THEN '31 a 45 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 46 AND 60 THEN '46 a 60 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 91 AND 120 THEN '91 a 120 d?as'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 121 AND 150 THEN '121 a 150 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 151 AND 180 THEN '151 a 180 días'
          ELSE 'Mayor a 180 días'
        END AS rango_dias,
        COUNT(*) AS Cantidad_facturas,
        SUM(valor_factura) AS valor_facturado,
        SUM(valor_pagado) AS valor_pagado,
        SUM(valor_iva) AS valor_iva,
        SUM(valor_glosa_inicial) AS valor_glosa_inicial,
        SUM(valor_no_glosado_inicial) AS valor_no_glosado_inicial,
        SUM(valor_aceptado_primera_respuesta) AS valor_aceptado_primera_respuesta,
        SUM(valor_levantado_primera_respuesta) AS valor_levantado_primera_respuesta,
        SUM(valor_ratificado_primera_respuesta) AS valor_ratificado_primera_respuesta,
        SUM(valor_aceptado_segunda_respuesta) AS valor_aceptado_segunda_respuesta,
        SUM(valor_levantado_segunda_respuesta) AS valor_levantado_segunda_respuesta,
        SUM(valor_ratificado_segunda_respuesta) AS valor_ratificado_segunda_respuesta,
        SUM(valor_aceptado_conciliacion) AS valor_aceptado_conciliacion,
        SUM(valor_levantado_conciliacion) AS valor_levantado_conciliacion,
        SUM(valor_ratificado_conciliacion) AS valor_ratificado_conciliacion,
        SUM(Valor_actual_aceptado) AS valor_actual_aceptado,
        SUM(valor_actual_reconocido) AS valor_actual_reconocido,
        SUM(valor_final_ratificado) AS valor_actual_ratificado,
        MIN(id) AS iddyg_minimo_mes
      FROM fomagf.dbo.radicacion_filtrada
      WHERE  estado_aplicacion NOT IN ('No Aprobado por Calidad', 'En Revisión Calidad')
      GROUP BY
        estado_aplicacion,
        FORMAT(fecha_radicacion, 'MMMM/yyyy', 'es-ES'),
        tipo_red,
        nit,
        nom_prestador,
        departamento,
        modalidad_pago,
        modalidad_Factura,
        FORMAT(fecha_factura, 'MMMM/yyyy', 'es-ES'),
        CASE
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 0 AND 30 THEN '0 a 30 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 31 AND 45 THEN '31 a 45 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 46 AND 60 THEN '46 a 60 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 91 AND 120 THEN '91 a 120 d?as'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 121 AND 150 THEN '121 a 150 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 151 AND 180 THEN '151 a 180 días'
          ELSE 'Mayor a 180 días'
        END
      ORDER BY
        nit ASC,
        nom_prestador ASC,
        departamento ASC
      """;

  private static final String GERENCIAL_SQL_2 = """
      SELECT 
        estado_aplicacion,
        FORMAT(fecha_radicacion, 'MMMM/yyyy', 'es-ES') AS mes_anio_radicado,
        tipo_red,
        nit,
        nom_prestador,
        departamento,
        modalidad_pago,
        modalidad_Factura AS modalidad_factura,
        FORMAT(fecha_factura, 'MMMM/yyyy', 'es-ES') AS mes_anio_factura,
        CASE
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 0 AND 30 THEN '0 a 30 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 31 AND 45 THEN '31 a 45 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 46 AND 60 THEN '46 a 60 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 61 AND 90 THEN '61 a 90 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 91 AND 120 THEN '91 a 120 d?as'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 121 AND 150 THEN '121 a 150 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 151 AND 180 THEN '151 a 180 días'
          ELSE 'Mayor a 180 días'
        END AS rango_dias,
        count(*) AS Cantidad_facturas,
        SUM(valor_factura) AS valor_facturado,
        SUM(valor_pagado) AS valor_pagado,
        SUM(valor_iva) AS valor_iva,
        SUM(valor_glosa_inicial) AS valor_glosa_inicial,
        SUM(valor_no_glosado_inicial) AS valor_no_glosado_inicial,
        SUM(valor_aceptado_primera_respuesta) AS valor_aceptado_primera_respuesta,
        SUM(valor_levantado_primera_respuesta) AS valor_levantado_primera_respuesta,
        SUM(valor_ratificado_primera_respuesta) AS valor_ratificado_primera_respuesta,
        SUM(valor_aceptado_segunda_respuesta) AS valor_aceptado_segunda_respuesta,
        SUM(valor_levantado_segunda_respuesta) AS valor_levantado_segunda_respuesta,
        SUM(valor_ratificado_segunda_respuesta) AS valor_ratificado_segunda_respuesta,
        SUM(valor_aceptado_conciliacion) AS valor_aceptado_conciliacion,
        SUM(valor_levantado_conciliacion) AS valor_levantado_conciliacion,
        SUM(valor_ratificado_conciliacion) AS valor_ratificado_conciliacion,
        SUM(Valor_actual_aceptado) AS valor_actual_aceptado,
        SUM(valor_actual_reconocido) AS valor_actual_reconocido,
        SUM(valor_final_ratificado) AS valor_actual_ratificado,
        MIN(id) AS iddyg_minimo_mes
      FROM fomagf.dbo.radicacion_filtrada_capita
      GROUP BY
        estado_aplicacion,
        FORMAT(fecha_radicacion, 'MMMM/yyyy', 'es-ES'),
        tipo_red,
        nit,
        nom_prestador,
        departamento,
        modalidad_pago,
        modalidad_Factura,
        FORMAT(fecha_factura, 'MMMM/yyyy', 'es-ES'),
        CASE
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 0 AND 30 THEN '0 a 30 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 31 AND 45 THEN '31 a 45 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 46 AND 60 THEN '46 a 60 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 91 AND 120 THEN '91 a 120 d?as'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 121 AND 150 THEN '121 a 150 días'
          WHEN DATEDIFF(DAY, fecha_radicacion, GETDATE()) BETWEEN 151 AND 180 THEN '151 a 180 días'
          ELSE 'Mayor a 180 días'
        END
      ORDER BY
        nit ASC,
        nom_prestador ASC,
        departamento ASC
      """;

  private final JdbcTemplate jdbcTemplate;

  ReporteService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  String exportarVoucherTxt() {
    List<String> rows = new ArrayList<>();
    rows.add(String.join("|", VOUCHER_HEADERS));
    rows.addAll(jdbcTemplate.query(VOUCHER_SQL, (rs, rowNum) -> mapVoucherRow(rs)));
    return String.join(System.lineSeparator(), rows) + System.lineSeparator();
  }

  String exportarGerencialTxt() {
    List<String> out = new ArrayList<>();
    out.add("# GERENCIAL radicacion_filtrada");
    out.add(String.join("|", GERENCIAL_HEADERS));
    out.addAll(jdbcTemplate.query(GERENCIAL_SQL_1, (rs, rowNum) -> mapGerencialRow(rs)));
    out.add("");
    out.add("# GERENCIAL radicacion_filtrada_capita");
    out.add(String.join("|", GERENCIAL_HEADERS));
    out.addAll(jdbcTemplate.query(GERENCIAL_SQL_2, (rs, rowNum) -> mapGerencialRow(rs)));
    return String.join(System.lineSeparator(), out) + System.lineSeparator();
  }

  private String mapVoucherRow(ResultSet rs) throws SQLException {
    String estado = safe(rs.getString("estado_aplicacion"));
    String mes = safe(rs.getString("mes_anio_radicacion"));
    String tipoRed = safe(rs.getString("tipo_red"));
    String nit = safe(rs.getString("nit"));
    String prestador = safe(rs.getString("nom_prestador"));
    String dpto = safe(rs.getString("departamento"));
    String modPago = safe(rs.getString("modalidad_pago"));
    String modFactura = safe(rs.getString("modalidad_Factura"));
    String voucher = safe(rs.getString("voucher"));
    String rango = safe(rs.getString("rango_dias"));
    BigDecimal valorPagado = rs.getBigDecimal("valor_pagado");
    int noFacturas = rs.getInt("no_facturas");

    return String.join("|", new String[] {
        estado, mes, tipoRed, nit, prestador, dpto, modPago, modFactura, voucher, rango,
        valorPagado == null ? "0" : valorPagado.toPlainString(),
        Integer.toString(noFacturas)
    });
  }

  private String mapGerencialRow(ResultSet rs) throws SQLException {
    String[] v = new String[] {
        safe(rs.getString("estado_aplicacion")),
        safe(rs.getString("mes_anio_radicado")),
        safe(rs.getString("tipo_red")),
        safe(rs.getString("nit")),
        safe(rs.getString("nom_prestador")),
        safe(rs.getString("departamento")),
        safe(rs.getString("modalidad_pago")),
        safe(rs.getString("modalidad_factura")),
        safe(rs.getString("mes_anio_factura")),
        safe(rs.getString("rango_dias")),
        safe(rs.getString("Cantidad_facturas")),
        safe(rs.getString("valor_facturado")),
        safe(rs.getString("valor_pagado")),
        safe(rs.getString("valor_iva")),
        safe(rs.getString("valor_glosa_inicial")),
        safe(rs.getString("valor_no_glosado_inicial")),
        safe(rs.getString("valor_aceptado_primera_respuesta")),
        safe(rs.getString("valor_levantado_primera_respuesta")),
        safe(rs.getString("valor_ratificado_primera_respuesta")),
        safe(rs.getString("valor_aceptado_segunda_respuesta")),
        safe(rs.getString("valor_levantado_segunda_respuesta")),
        safe(rs.getString("valor_ratificado_segunda_respuesta")),
        safe(rs.getString("valor_aceptado_conciliacion")),
        safe(rs.getString("valor_levantado_conciliacion")),
        safe(rs.getString("valor_ratificado_conciliacion")),
        safe(rs.getString("valor_actual_aceptado")),
        safe(rs.getString("valor_actual_reconocido")),
        safe(rs.getString("valor_actual_ratificado")),
        safe(rs.getString("iddyg_minimo_mes"))
    };
    return String.join("|", v);
  }

  private String safe(String v) {
    if (v == null) return "";
    return v.replace('|', '/');
  }
}

@RestController
@RequestMapping("/api/v1/reportes")
class ReporteController {

  private final ReporteService service;

  ReporteController(ReporteService service) {
    this.service = service;
  }

  @GetMapping(value = "/voucher", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> exportarVoucher() {
    String data = service.exportarVoucherTxt();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(ContentDisposition.attachment().filename("reporte_voucher.txt").build());
    return ResponseEntity.ok().headers(headers).body(data);
  }

  @GetMapping(value = "/gerencial", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> exportarGerencial() {
    String data = service.exportarGerencialTxt();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(ContentDisposition.attachment().filename("reporte_gerencial.txt").build());
    return ResponseEntity.ok().headers(headers).body(data);
  }
}
