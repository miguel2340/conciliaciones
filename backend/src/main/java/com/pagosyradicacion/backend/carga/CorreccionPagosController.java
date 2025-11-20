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
public class CorreccionPagosController {

  private final CorreccionPagosService service;

  public CorreccionPagosController(CorreccionPagosService service) {
    this.service = service;
  }

  @PostMapping(value = "/correcciones-pagos", consumes = { "multipart/form-data" })
  public ResponseEntity<CargaResponse> cargarCorrecciones(
      @RequestParam("archivo") MultipartFile archivo,
      @RequestParam(name = "usuario", required = false) String usuario,
      @RequestParam(name = "tipo", required = false) String tipo) {
    String msg = service.cargarCorreccionCsv(archivo, usuario != null ? usuario : "desconocido", tipo);
    return ResponseEntity.ok(new CargaResponse(true, msg));
  }

  public record CargaResponse(boolean ok, String message) {}
}
