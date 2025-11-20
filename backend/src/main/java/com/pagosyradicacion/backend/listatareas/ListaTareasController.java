package com.pagosyradicacion.backend.listatareas;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lista-tareas")
public class ListaTareasController {
  private static final Logger log = LoggerFactory.getLogger(ListaTareasController.class);

  private static final Map<String, String> REPORTS = new LinkedHashMap<>();
  static {
    REPORTS.put("general", "General: pagos traza vs radicación/pagos (coinciden en radicación)");
    REPORTS.put("faltantes", "Voucher faltantes (en traza sin coincidencia en radicación)");
    REPORTS.put("pagado_mayor", "Valor pagado > valor causado");
    REPORTS.put("no_en_traza", "NIT/Voucher en radicación que NO están en pagos_traza");
    REPORTS.put("nit_no_en_traza", "NIT en radicación que NO están en pagos_traza (ningún voucher)");
    REPORTS.put("pagos_no_cruzan", "Pagos sin cruce en radicación (MOD+ID+NIT)");
    REPORTS.put("pagado_mayor_fact", "Pagado > Factura (servicios + cápita)");
  }

  private final ListaTareasService service;

  public ListaTareasController(ListaTareasService service) {
    this.service = service;
  }

  private static boolean isValidType(String type) {
    return REPORTS.containsKey(type);
  }

  @GetMapping("/metrics/{type}")
  public ResponseEntity<?> metrics(@PathVariable("type") String type, @RequestParam(name = "fresh", required = false) Boolean fresh) {
    if (!isValidType(type)) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Tipo no válido"));
    }

    String trace = "metrics_" + java.util.UUID.randomUUID();
    try {
      Map<String, Object> metrics = service.metricsCached(type, Boolean.TRUE.equals(fresh));
      return ResponseEntity.ok()
          .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
          .body(Map.of(
              "ok", true,
              "type", type,
              "label", REPORTS.get(type),
              "metrics", metrics,
              "trace_id", trace));
    } catch (Exception e) {
      log.error("metrics failed: type={}, trace={}, msg={}", type, trace, e.getMessage(), e);
      return ResponseEntity.internalServerError().body(Map.of(
          "ok", false,
          "error", "Error al consultar métricas",
          "trace_id", trace));
    }
  }

  @GetMapping("/metrics/refresh")
  public ResponseEntity<?> refresh(@RequestParam(name = "types", required = false) String types) {
    try {
      List<String> list;
      if (types == null || types.isBlank()) {
        list = new java.util.ArrayList<>(REPORTS.keySet());
      } else {
        list = java.util.Arrays.stream(types.split(","))
            .map(String::trim)
            .filter(REPORTS::containsKey)
            .toList();
      }
      Map<String, Map<String, Object>> data = service.refreshAll(list);
      return ResponseEntity.ok(Map.of(
          "ok", true,
          "refreshed", data,
          "ts", java.time.Instant.now().toString()
      ));
    } catch (Exception e) {
      log.error("metrics refresh failed: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError().body(Map.of("ok", false));
    }
  }

  @GetMapping("/data/{type}")
  public ResponseEntity<?> data(@PathVariable("type") String type, @RequestParam(name = "limit", defaultValue = "200") int limit) {
    if (!isValidType(type)) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Tipo no válido"));
    }
    limit = Math.max(10, Math.min(limit, 1000));
    String trace = "table_" + java.util.UUID.randomUUID();
    try {
      List<Map<String, Object>> rows = service.rowsFor(type, limit);
      return ResponseEntity.ok()
          .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
          .body(Map.of(
              "ok", true,
              "type", type,
              "label", REPORTS.get(type),
              "rows", rows,
              "trace_id", trace));
    } catch (Exception e) {
      log.error("table failed: type={}, trace={}, msg={}", type, trace, e.getMessage(), e);
      return ResponseEntity.internalServerError().body(Map.of(
          "ok", false,
          "error", "No se pudo cargar la tabla",
          "trace_id", trace));
    }
  }

  @GetMapping(value = "/download", produces = "text/csv")
  public ResponseEntity<byte[]> download(@RequestParam(name = "type", defaultValue = "general") String type) {
    if (!isValidType(type)) {
      return ResponseEntity.badRequest().build();
    }

    String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT).format(LocalDateTime.now());
    String filename = String.format("reporte_%s_%s.csv", type, ts);

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      // Write UTF-8 BOM
      baos.write(0xEF);
      baos.write(0xBB);
      baos.write(0xBF);

      try (OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
        service.writeCsv(type, writer);
        writer.flush();
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
      headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
      return ResponseEntity.ok().headers(headers).body(baos.toByteArray());

    } catch (Exception e) {
      log.error("download failed: type={}, filename={}, msg={}", type, filename, e.getMessage(), e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
