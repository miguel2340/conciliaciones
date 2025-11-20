package com.pagosyradicacion.backend.radicacion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RadicacionService {

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter DMY_DATE = DateTimeFormatter.ofPattern("d/M/uuuu");
  private static final DateTimeFormatter DMY_DATE_TIME = DateTimeFormatter.ofPattern("d/M/uuuu HH:mm[:ss]");
  private static final int DEFAULT_PAGE_SIZE = 100;
  private static final int MAX_PAGE_SIZE = 500;
  private static final Set<String> DATE_COLUMNS = Set.of("fecha_radicacion", "fecha_factura", "feccha_pago", "fecha_pago");
  private static final String[] EXPORT_COLUMNS = {
      "id",
      "modalidad_pago",
      "nit",
      "nom_prestador",
      "tipo_red",
      "departamento",
      "municipio",
      "prefijo",
      "factura",
      "prefijo_factura",
      "mes_radicacion",
      "estado_aplicacion",
      "fecha_radicacion",
      "rango_dias",
      "fecha_factura",
      "valor_factura",
      "valor_iva",
      "nota_credito",
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
      "valor_final_ratificado",
      "valor_pagado",
      "porcentaje_pago",
      "estado",
      "voucher",
      "feccha_pago",
      "id_fomag",
      "nit_norm",
      "voucher_norm"
  };
  private static final String EXPORT_QUERY = """
      SELECT
        id,
        modalidad_pago,
        nit,
        nom_prestador,
        tipo_red,
        departamento,
        municipio,
        prefijo,
        factura,
        prefijo_factura,
        mes_radicacion,
        estado_aplicacion,
        fecha_radicacion,
        rango_dias,
        fecha_factura,
        valor_factura,
        valor_iva,
        nota_credito,
        valor_glosa_inicial,
        valor_no_glosado_inicial,
        valor_aceptado_primera_respuesta,
        valor_levantado_primera_respuesta,
        valor_ratificado_primera_respuesta,
        valor_aceptado_segunda_respuesta,
        valor_levantado_segunda_respuesta,
        valor_ratificado_segunda_respuesta,
        valor_aceptado_conciliacion,
        valor_levantado_conciliacion,
        valor_ratificado_conciliacion,
        Valor_actual_aceptado AS valor_actual_aceptado,
        valor_actual_reconocido,
        valor_final_ratificado,
        valor_pagado,
        porcentaje_pago,
        estado,
        voucher,
        feccha_pago,
        id_fomag,
        CAST(NULL AS varchar(255)) AS nit_norm,
        CAST(NULL AS varchar(255)) AS voucher_norm
      FROM radicacion_filtrada
      WHERE UPPER(nit) = UPPER(?)
      ORDER BY id
      """;

  private static final String[] RADICACION3_EXPORT_COLUMNS = {
      "id",
      "modalidad_pago",
      "modalidad_factura",
      "nit",
      "nom_prestador",
      "tipo_red",
      "departamento",
      "municipio",
      "prefijo",
      "factura",
      "prefijo_factura",
      "mes_radicacion",
      "valor_factura",
      "fac_electronica",
      "cert_bancario",
      "rut",
      "parafiscales",
      "porcentaje",
      "estado_aplicacion",
      "fecha_radicacion",
      "fecha_factura",
      "region",
      "valor_pagado",
      "fecha_pago",
      "porcentaje_pago",
      "valor_iva",
      "nota_credito",
      "valor_nota_credito",
      "ano_radicacion",
      "valor_glosa_inicial",
      "valor_no_glosado_inicial",
      "valor_aceptado_pri_respuesta",
      "valor_levantado_pri_respuesta",
      "valor_ratificado_pri_respeuesta",
      "valor_aceptado_seg_respuesta",
      "valor_levantado_seg_respuesta",
      "valor_ratificado_seg_respeuesta",
      "valor_aceptado_conciliacion",
      "valor_levantado_conciliacion",
      "valor_ratificado_conciliacion",
      "valor_final_aceptado",
      "valor_final_reconocido",
      "valor_final_ratificado",
      "modalidad_norm",
      "id_norm",
      "nit_norm"
  };

  private static final String RADICACION3_BASE_QUERY = """
      SELECT
        id,
        modalidad_pago,
        modalidad_Factura AS modalidad_factura,
        nit,
        nom_prestador,
        tipo_red,
        departamento,
        municipio,
        prefijo,
        factura,
        prefijo_factura,
        mes_radicacion,
        valor_factura,
        fac_electronica,
        cert_bancario,
        rut,
        parafiscales,
        porcentaje,
        estado_aplicacion,
        fecha_radicacion,
        fecha_factura,
        region,
        valor_pagado,
        fecha_pago,
        porcentaje_pago,
        valor_iva,
        nota_credito,
        valor_nota_credito,
        ano_radicacion,
        valor_glosa_inicial,
        valor_no_glosado_inicial,
        valor_aceptado_pri_respuesta,
        valor_levantado_pri_respuesta,
        valor_ratificado_pri_respeuesta,
        valor_aceptado_seg_respuesta,
        valor_levantado_seg_respuesta,
        valor_ratificado_seg_respeuesta,
        valor_aceptado_conciliacion,
        valor_levantado_conciliacion,
        valor_ratificado_conciliacion,
        valor_final_aceptado,
        valor_final_reconocido,
        valor_final_ratificado,
        modalidad_norm,
        id_norm,
        CAST(NULL AS varchar(255)) AS nit_norm
      FROM radicacion3
      """;

  private final RadicacionRegistroRepository repository;
  private final JdbcTemplate jdbcTemplate;

  public RadicacionService(RadicacionRegistroRepository repository, JdbcTemplate jdbcTemplate) {
    this.repository = repository;
    this.jdbcTemplate = jdbcTemplate;
  }

  public Page<RadicacionRegistro> buscarPorNit(String nit, Integer page, Integer size) {
    if (nit == null || nit.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El NIT es obligatorio");
    }

    int pageNumber = page != null && page >= 0 ? page : 0;
    int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    Pageable pageable = PageRequest.of(pageNumber, pageSize);

    return repository.findByNitIgnoreCase(nit.trim(), pageable);
  }

  public String exportarPorNitTxt(String nit) {
    if (nit == null || nit.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El NIT es obligatorio");
    }

    List<String> registros = obtenerRegistrosParaNit(nit.trim());

    if (registros.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontraron registros para el NIT indicado");
    }

    List<String> lineas = new ArrayList<>();
    lineas.add(String.join("|", EXPORT_COLUMNS));
    lineas.addAll(registros);

    return String.join(System.lineSeparator(), lineas) + System.lineSeparator();
  }

  public String exportarPorMultiplesNitTxt(List<String> nits) {
    if (nits == null || nits.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes proporcionar al menos un NIT");
    }

    Set<String> unicos = nits.stream()
        .filter(nit -> nit != null && !nit.isBlank())
        .map(String::trim)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    if (unicos.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los NIT suministrados son invalidos");
    }

    List<String> resultado = new ArrayList<>();
    resultado.add(String.join("|", EXPORT_COLUMNS));

    int conRegistros = 0;
    for (String nit : unicos) {
      List<String> registros = obtenerRegistrosParaNit(nit);
      if (registros.isEmpty()) {
        // Saltar NIT sin registros (no cancelar toda la descarga)
        continue;
      }

      resultado.add("# NIT " + nit);
      resultado.addAll(registros);
      conRegistros++;
    }

    // Si ninguno de los NIT tiene registros, mantener comportamiento de error
    if (conRegistros == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontraron registros para los NIT suministrados");
    }

    return String.join(System.lineSeparator(), resultado) + System.lineSeparator();
  }

  public String exportarPorFechaTxt(RadicacionFechaExportRequest request) {
    RadicacionFechaExportRequest filtros = request != null
        ? request
        : new RadicacionFechaExportRequest(null, null, null, null);

    List<String> registros = obtenerRegistrosPorFecha(filtros);

    if (registros.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontraron registros con los filtros suministrados");
    }

    List<String> resultado = new ArrayList<>();
    resultado.add(String.join("|", RADICACION3_EXPORT_COLUMNS));
    resultado.addAll(registros);

    return String.join(System.lineSeparator(), resultado) + System.lineSeparator();
  }

  private List<String> obtenerRegistrosParaNit(String nit) {
    return jdbcTemplate.query(EXPORT_QUERY,
        ps -> ps.setString(1, nit),
        (rs, rowNum) -> mapearRegistroATxt(rs));
  }

  private List<String> obtenerRegistrosPorFecha(RadicacionFechaExportRequest filtros) {
    LocalDate fechaInicio = parseDate(filtros.fechaInicio(), "fechaInicio");
    LocalDate fechaFin = parseDate(filtros.fechaFin(), "fechaFin");

    if (fechaInicio != null && fechaFin != null && fechaFin.isBefore(fechaInicio)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha fin no puede ser anterior a la fecha inicio");
    }

    List<Object> params = new ArrayList<>();
    StringBuilder sql = new StringBuilder(RADICACION3_BASE_QUERY);
    sql.append(" WHERE 1 = 1");

    if (fechaInicio != null) {
      sql.append(" AND fecha_radicacion >= ?");
      params.add(Timestamp.valueOf(fechaInicio.atStartOfDay()));
    }

    if (fechaFin != null) {
      sql.append(" AND fecha_radicacion < ?");
      params.add(Timestamp.valueOf(fechaFin.plusDays(1).atStartOfDay()));
    }

    List<String> estados = sanitizeValues(filtros.estadosAplicacion());
    if (!estados.isEmpty()) {
      sql.append(" AND UPPER(estado_aplicacion) IN (");
      for (int i = 0; i < estados.size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        sql.append("?");
        params.add(estados.get(i).toUpperCase());
      }
      sql.append(")");
    }

    List<String> nits = sanitizeValues(filtros.nits());
    if (!nits.isEmpty()) {
      sql.append(" AND UPPER(nit) IN (");
      for (int i = 0; i < nits.size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        sql.append("?");
        params.add(nits.get(i).toUpperCase());
      }
      sql.append(")");
    }

    sql.append(" ORDER BY fecha_radicacion, id");

    return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> mapearRegistroRadicacion3(rs));
  }

  private List<String> sanitizeValues(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    LinkedHashSet<String> sanitized = values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));

    return new ArrayList<>(sanitized);
  }

  private LocalDate parseDate(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Formato invalido para " + fieldName + ". Usa AAAA-MM-DD");
    }
  }

  private String mapearRegistroATxt(ResultSet rs) throws SQLException {
    String[] values = new String[EXPORT_COLUMNS.length];
    for (int i = 0; i < EXPORT_COLUMNS.length; i++) {
      values[i] = extractColumn(rs, EXPORT_COLUMNS[i]);
    }
    return String.join("|", values);
  }

  private String mapearRegistroRadicacion3(ResultSet rs) throws SQLException {
    String[] values = new String[RADICACION3_EXPORT_COLUMNS.length];
    for (int i = 0; i < RADICACION3_EXPORT_COLUMNS.length; i++) {
      values[i] = extractColumn(rs, RADICACION3_EXPORT_COLUMNS[i]);
    }
    return String.join("|", values);
  }

  private String extractColumn(ResultSet rs, String column) throws SQLException {
    if (DATE_COLUMNS.contains(column)) {
      Object o = rs.getObject(column);
      return formatAnyDate(o);
    }

    String value = rs.getString(column);
    if (value == null) {
      return "";
    }
    return value.replace('|', '/');
  }

  private String formatAnyDate(Object o) {
    try {
      if (o == null) return "";
      if (o instanceof Timestamp ts) return ts.toLocalDateTime().format(DATE_TIME_FORMATTER);
      if (o instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay().format(DATE_TIME_FORMATTER);
      if (o instanceof java.time.LocalDateTime ldt) return ldt.format(DATE_TIME_FORMATTER);
      if (o instanceof java.time.LocalDate ld) return ld.atStartOfDay().format(DATE_TIME_FORMATTER);
      String s = String.valueOf(o).trim();
      if (s.isEmpty()) return "";
      // Try common formats: ISO date-time, ISO date, dd/MM/yyyy[ HH:mm[:ss]], yyyy/MM/dd
      try { return LocalDateTime.parse(s, DATE_TIME_FORMATTER).format(DATE_TIME_FORMATTER); } catch (Exception ignore) {}
      try { return LocalDate.parse(s).atStartOfDay().format(DATE_TIME_FORMATTER); } catch (Exception ignore) {}
      try { return LocalDateTime.parse(s, DMY_DATE_TIME).format(DATE_TIME_FORMATTER); } catch (Exception ignore) {}
      try { return LocalDate.parse(s, DMY_DATE).atStartOfDay().format(DATE_TIME_FORMATTER); } catch (Exception ignore) {}
      try { return LocalDate.parse(s.replace('-', '/'), DMY_DATE).atStartOfDay().format(DATE_TIME_FORMATTER); } catch (Exception ignore) {}
      // If not parseable, return as-is to avoid exception, preserving data
      return s;
    } catch (Exception e) {
      return ""; // be safe
    }
  }

  public java.util.List<String> obtenerEstadosAplicacion() {
    String sql = "SELECT DISTINCT estado_aplicacion FROM radicacion3 WHERE estado_aplicacion IS NOT NULL AND LTRIM(RTRIM(estado_aplicacion)) <> '' ORDER BY estado_aplicacion";
    return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
  }}
