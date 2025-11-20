package com.pagosyradicacion.backend.radicacion;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RadicacionRegistroRepository extends PagingAndSortingRepository<RadicacionRegistro, Long> {

  Page<RadicacionRegistro> findByNitIgnoreCase(String nit, Pageable pageable);

  List<RadicacionRegistro> findAllByNitIgnoreCaseOrderByIdAsc(String nit);
}
