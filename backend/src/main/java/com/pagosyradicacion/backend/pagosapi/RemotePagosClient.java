package com.pagosyradicacion.backend.pagosapi;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RemotePagosClient {

  private final RestTemplate http = new RestTemplate();

  private final String baseUrl;
  private final String bearerToken;
  private final int perPageDefault;

  public RemotePagosClient(
      @Value("${pagos.api.base:http://200.116.57.140:8080/aplicacion/api/api_pagos.php}") String baseUrl,
      @Value("${pagos.api.bearer:}") String bearerToken,
      @Value("${PAGOS_API_BEARER:}") String envBearer, // fallback via env var if property empty
      @Value("${pagos.api.per-page:20000}") int perPageDefault
  ) {
    this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "http://200.116.57.140:8080/aplicacion/api/api_pagos.php";
    String candidate = (bearerToken != null && !bearerToken.isBlank()) ? bearerToken.trim() : null;
    if (candidate == null || candidate.isBlank()) {
      candidate = envBearer != null && !envBearer.isBlank() ? envBearer.trim() : null;
    }
    this.bearerToken = candidate;
    this.perPageDefault = perPageDefault > 0 ? perPageDefault : 500;
  }

  public boolean isConfigured() {
    return bearerToken != null && !bearerToken.isBlank();
  }

  private HttpHeaders headers() {
    HttpHeaders h = new HttpHeaders();
    if (bearerToken != null && !bearerToken.isBlank()) {
      h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
    }
    h.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    return h;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getVouchersForFecha(LocalDate fecha) {
    // Calls: GET {baseUrl}/api/pagos/vouchers?fecha=YYYY-MM-DD
    URI uri = UriComponentsBuilder
        .fromHttpUrl(baseUrl)
        .path("/api/pagos/vouchers")
        .queryParam("fecha", fecha.toString())
        .build()
        .toUri();
    ResponseEntity<Map> res = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
    return (Map<String, Object>) res.getBody();
  }

  @SuppressWarnings("unchecked")
  public RemotePagosPage fetchPagos(LocalDate fecha, int page, int perPage) {
    // Calls: GET {baseUrl}/api/pagos?desde=YYYY-MM-DD HH:mm:ss&hasta=YYYY-MM-DD HH:mm:ss&page=1&per_page=500
    LocalDateTime start = fecha.atStartOfDay();
    LocalDateTime end = fecha.atTime(23, 59, 59);
    DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("desde", start.format(f));
    params.add("hasta", end.format(f));
    params.add("page", String.valueOf(page));
    params.add("per_page", String.valueOf(perPage));

    URI uri = UriComponentsBuilder
        .fromHttpUrl(baseUrl)
        .path("/api/pagos")
        .queryParams(params)
        .build()
        .toUri();

    ResponseEntity<Map> res = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
    Map<String, Object> body = (Map<String, Object>) res.getBody();
    Map<String, Object> meta = (Map<String, Object>) body.getOrDefault("meta", new HashMap<>());
    List<Map<String, Object>> data = (List<Map<String, Object>>) body.getOrDefault("data", new ArrayList<>());
    int pages = ((Number) meta.getOrDefault("pages", 1)).intValue();
    int total = ((Number) meta.getOrDefault("total", data.size())).intValue();
    int per = ((Number) meta.getOrDefault("per_page", perPage)).intValue();
    int currentPage = ((Number) meta.getOrDefault("page", page)).intValue();
    return new RemotePagosPage(currentPage, pages, per, total, data);
  }

  public List<Map<String, Object>> fetchPagosAll(LocalDate fecha) {
    final int PER = this.perPageDefault; // configurable; el externo ya no limita
    RemotePagosPage first = fetchPagos(fecha, 1, PER);
    List<Map<String, Object>> rows = new ArrayList<>(first.data());
    int pages = Math.max(1, first.pages());
    if (pages > 1) {
      int threads = Math.min(8, pages - 1);
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      try {
        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (int p = 2; p <= pages; p++) {
          final int pageNo = p;
          futures.add(CompletableFuture.supplyAsync(() -> fetchPagos(fecha, pageNo, PER).data(), pool));
        }
        for (CompletableFuture<List<Map<String, Object>>> f : futures) {
          rows.addAll(f.join());
        }
      } finally {
        pool.shutdown();
        try { pool.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
      }
    }
    return rows;
  }

  public record RemotePagosPage(int page, int pages, int perPage, int total, List<Map<String, Object>> data) {}
}
