package com.pagosyradicacion.backend.radicacion;

import java.util.List;

public record RadicacionPageResponse(
    List<RadicacionRegistro> content,
    long totalElements,
    int totalPages,
    int pageNumber,
    int pageSize) {}
