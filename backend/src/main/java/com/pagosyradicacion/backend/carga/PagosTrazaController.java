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
public class PagosTrazaController {

  private final PagosTrazaService service;

  public PagosTrazaController(PagosTrazaService service) { this.service = service; }

  @PostMapping(value = "/pagos-traza", consumes = { "multipart/form-data" })
  public ResponseEntity<CargaResponse> cargarPagosTraza(@RequestParam("archivo") MultipartFile archivo) {
    String msg = service.cargarCsv(archivo);
    return ResponseEntity.ok(new CargaResponse(true, msg));
  }

  public record CargaResponse(boolean ok, String message) {}
}
