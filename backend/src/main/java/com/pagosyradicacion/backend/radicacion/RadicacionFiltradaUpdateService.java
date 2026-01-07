package com.pagosyradicacion.backend.radicacion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class RadicacionFiltradaUpdateService {

  private final JdbcTemplate jdbc;

  public RadicacionFiltradaUpdateService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public Map<String, Object> actualizar() {
    jdbc.update("TRUNCATE TABLE dbo.radicacion_filtrada");

    String sql = """
      INSERT INTO dbo.radicacion_filtrada (
        id, modalidad_pago, modalidad_Factura, nit, nom_prestador, tipo_red, departamento, municipio, prefijo, factura, prefijo_factura,
        mes_radicacion, estado_aplicacion, fecha_radicacion, rango_dias, fecha_factura, valor_factura, valor_iva,
        nota_credito, valor_glosa_inicial, valor_no_glosado_inicial, valor_aceptado_primera_respuesta,
        valor_levantado_primera_respuesta, valor_ratificado_primera_respuesta, valor_aceptado_segunda_respuesta,
        valor_levantado_segunda_respuesta, valor_ratificado_segunda_respuesta, valor_aceptado_conciliacion,
        valor_levantado_conciliacion, valor_ratificado_conciliacion, Valor_actual_aceptado, valor_actual_reconocido,
        valor_final_ratificado, valor_pagado, porcentaje_pago, estado, voucher, feccha_pago, id_fomag,procedencia
      )
      SELECT
        r.id,
        r.modalidad_pago,
        r.modalidad_Factura,
        r.nit,
        r.nom_prestador,
        r.tipo_red,
        r.departamento,
        r.municipio,
        r.prefijo,
        r.factura,
        r.prefijo_factura,
        r.mes_radicacion,
        r.estado_aplicacion,
        r.fecha_radicacion,
        CASE
          WHEN DATEDIFF(DAY, r.fecha_radicacion, GETDATE()) BETWEEN 0 AND 30 THEN '0 a 30 días'
          WHEN DATEDIFF(DAY, r.fecha_radicacion, GETDATE()) BETWEEN 31 AND 45 THEN '31 a 45 días'
          WHEN DATEDIFF(DAY, r.fecha_radicacion, GETDATE()) BETWEEN 46 AND 60 THEN '46 a 60 días'
          WHEN DATEDIFF(DAY, r.fecha_radicacion, GETDATE()) BETWEEN 61 AND 90 THEN '61 a 90 días'
          WHEN DATEDIFF(DAY, r.fecha_radicacion, GETDATE()) BETWEEN 91 AND 120 THEN '91 a 120 días'
          WHEN DATEDIFF(DAY, r.fecha_radicacion, GETDATE()) BETWEEN 121 AND 150 THEN '121 a 150 días'
          WHEN DATEDIFF(DAY, r.fecha_radicacion, GETDATE()) BETWEEN 151 AND 180 THEN '151 a 180 días'
          ELSE 'Mayor a 180 días'
        END AS rango_dias,
        r.fecha_factura,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_factura ELSE 0 END AS valor_factura,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_iva ELSE 0 END AS valor_iva,
        r.nota_credito,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_glosa_inicial ELSE 0 END AS valor_glosa_inicial,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_no_glosado_inicial ELSE 0 END AS valor_no_glosado_inicial,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_aceptado_pri_respuesta ELSE 0 END AS valor_aceptado_primera_respuesta,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_levantado_pri_respuesta ELSE 0 END AS valor_levantado_primera_respuesta,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_ratificado_pri_respeuesta ELSE 0 END AS valor_ratificado_primera_respuesta,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_aceptado_seg_respuesta ELSE 0 END AS valor_aceptado_segunda_respuesta,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_levantado_seg_respuesta ELSE 0 END AS valor_levantado_segunda_respuesta,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_ratificado_seg_respeuesta ELSE 0 END AS valor_ratificado_segunda_respuesta,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_aceptado_conciliacion ELSE 0 END AS valor_aceptado_conciliacion,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_levantado_conciliacion ELSE 0 END AS valor_levantado_conciliacion,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_ratificado_conciliacion ELSE 0 END AS valor_ratificado_conciliacion,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_final_aceptado ELSE 0 END AS valor_actual_aceptado,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_final_reconocido ELSE 0 END AS valor_actual_reconocido,
        CASE WHEN ROW_NUMBER() OVER (PARTITION BY r.modalidad_pago, r.id, r.nit ORDER BY p.feccha_pago) = 1 THEN r.valor_final_ratificado ELSE 0 END AS valor_actual_ratificado,
        p.valor_pagado,
        p.porcentaje_pago,
        p.estado,
        p.voucher,
        p.feccha_pago,
        p.id_fomag,
		r.procedencia
      FROM dbo.radicacion3 r
      LEFT JOIN dbo.pagos p
        ON (r.modalidad_pago + CAST(r.id AS VARCHAR(50)) + CAST(r.nit AS VARCHAR(50))) =
           (p.modalidad + CAST(p.id AS VARCHAR(50)) + CAST(p.nit AS VARCHAR(50)))
      WHERE r.estado_aplicacion <> 'No Aprobado por Calidad'
        AND r.estado_aplicacion <> 'En Revisi+¦n Calidad'
    """;

    int inserted = jdbc.update(sql);
    Map<String, Object> out = new HashMap<>();
    out.put("ok", true);
    out.put("inserted", inserted);
    return out;
  }
}

