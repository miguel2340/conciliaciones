package com.pagosyradicacion.backend.radicacion;

import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/radicacion")
public class RadicacionController {

  private final RadicacionService service;
  private final RadicacionFiltradaUpdateService updater;
  private final RadicacionFiltradaJobService jobs;

  public RadicacionController(RadicacionService service, RadicacionFiltradaUpdateService updater, RadicacionFiltradaJobService jobs) {
    this.service = service;
    this.updater = updater;
    this.jobs = jobs;
  }

  @GetMapping
  public RadicacionPageResponse buscarPorNit(
      @RequestParam String nit,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    var result = service.buscarPorNit(nit, page, size);
    return new RadicacionPageResponse(
        result.getContent(),
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber(),
        result.getSize());
  }

  @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> exportarPorNit(@RequestParam String nit) {
    String data = service.exportarPorNitTxt(nit);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(ContentDisposition.attachment().filename("radicacion_" + nit + ".txt").build());
    return ResponseEntity.ok().headers(headers).body(data);
  }

  @PostMapping(value = "/export/multiple", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> exportarPorMultiplesNit(@RequestBody RadicacionMultiExportRequest request) {
    String data = service.exportarPorMultiplesNitTxt(request.nits());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(
        ContentDisposition.attachment().filename("radicacion_multiples_nit.txt").build());
    return ResponseEntity.ok().headers(headers).body(data);
  }

  @PostMapping(value = "/export/fecha", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> exportarPorFecha(@RequestBody(required = false) RadicacionFechaExportRequest request) {
    RadicacionFechaExportRequest filtros = request != null
        ? request
        : new RadicacionFechaExportRequest(null, null, null, null);
    String data = service.exportarPorFechaTxt(filtros);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(ContentDisposition.attachment().filename("radicacion_por_fecha.txt").build());
    return ResponseEntity.ok().headers(headers).body(data);
  }

  @GetMapping("/opciones/estados-aplicacion")
  public List<String> obtenerEstadosAplicacion() {
    return service.obtenerEstadosAplicacion();
  }

  @PostMapping("/actualizar-filtrada")
  public ResponseEntity<Map<String, Object>> actualizarFiltrada() {
    Map<String, Object> res = updater.actualizar();
    return ResponseEntity.ok(res);
  }

  // Async start + status (para evitar timeouts en front)
  @PostMapping("/actualizar-filtrada/start")
  public ResponseEntity<Map<String,Object>> startAsync() {
    String id = jobs.start();
    return ResponseEntity.ok(java.util.Map.of("jobId", id));
  }

  @GetMapping("/actualizar-filtrada/status")
  public ResponseEntity<?> status(@RequestParam String jobId) {
    var st = jobs.get(jobId);
    if (st == null) return ResponseEntity.status(404).body(java.util.Map.of("error","job no encontrado"));
    return ResponseEntity.ok(java.util.Map.of(
      "jobId", st.id,
      "status", st.status.name(),
      "startedAt", st.startedAt.toString(),
      "inserted", st.inserted,
      "message", st.message
    ));
  }
}
