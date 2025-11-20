package com.pagosyradicacion.backend.radicacion;

import java.util.List;

public record RadicacionFechaExportRequest(
    String fechaInicio,
    String fechaFin,
    List<String> estadosAplicacion,
    List<String> nits) {
}
