package com.pagosyradicacion.backend.carga;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200", "http://172.16.0.232:4200"}, allowCredentials = "true")
@RequestMapping("/api/v1/cargas")
public class CargaPagosController {

  private final CargaPagosService service;

  public CargaPagosController(CargaPagosService service) {
    this.service = service;
  }

  @PostMapping(value = "/pagos", consumes = { "multipart/form-data" })
  public ResponseEntity<CargaResponse> cargarPagos(
      @RequestParam("archivo") MultipartFile archivo,
      @RequestParam(name = "tipo", required = false, defaultValue = "pagos") String tipo) {
    String msg = service.cargarPagosCsv(archivo, tipo);
    return ResponseEntity.ok(new CargaResponse(true, msg));
  }

  public record CargaResponse(boolean ok, String message) {}
}
