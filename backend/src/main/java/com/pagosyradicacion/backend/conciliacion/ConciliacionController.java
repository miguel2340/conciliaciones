package com.pagosyradicacion.backend.conciliacion;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conciliacion")
public class ConciliacionController {

  private final ConciliacionService service;
  private final com.pagosyradicacion.backend.listatareas.ListaTareasService listaService;

  public ConciliacionController(ConciliacionService service, com.pagosyradicacion.backend.listatareas.ListaTareasService listaService) {
    this.service = service; this.listaService = listaService; }

  @GetMapping(value = "/export")
  public ResponseEntity<byte[]> export(@RequestParam String nit) {
    byte[] xlsx = service.exportXlsxPorNit(nit);
    String filename = "conciliacion_" + URLEncoder.encode(nit, StandardCharsets.UTF_8) + ".xlsx";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
    headers.set(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    return ResponseEntity.ok().headers(headers).body(xlsx);
  }

  // Prechequeo: indica en cuáles listas de tarea aparece el NIT (si aplica)
  @GetMapping(value = "/precheck")
  public ResponseEntity<java.util.Map<String, Object>> precheck(@RequestParam String nit) {
    java.util.Map<String, Object> data = listaService.checkNitMembership(nit);
    return ResponseEntity.ok(data);
  }
}
