package com.pagosyradicacion.backend.pagosapi;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200", "http://172.16.0.232:4200"}, allowCredentials = "true")
@RequestMapping("/api/v1/pagos-api")
public class PagosApiController {

  private final PagosApiImportService service;

  public PagosApiController(PagosApiImportService service) {
    this.service = service;
  }

  @GetMapping("/dia")
  public Map<String, Object> resumenDia(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
    return service.resumenPorFecha(fecha);
  }

  public record AprobarRequest(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha, String voucher, String observacion) {}

  @PostMapping("/aprobar")
  public ResponseEntity<Map<String, Object>> aprobar(@RequestBody AprobarRequest req) {
    if (req == null || req.fecha() == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "Debe enviar 'fecha'"));
    }
    Map<String, Object> res = service.aprobar(req.fecha(), req.voucher(), req.observacion());
    return ResponseEntity.ok(res);
  }
}
