package com.pagosyradicacion.backend.radicacion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RadicacionFiltradaJobService {

  public enum Status { RUNNING, COMPLETED, FAILED }

  public static class JobState {
    public final String id;
    public final Instant startedAt = Instant.now();
    public volatile Status status = Status.RUNNING;
    public volatile String message = "";
    public volatile Integer inserted = null;
    public JobState(String id){ this.id = id; }
  }

  private final RadicacionFiltradaUpdateService updater;
  private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

  public RadicacionFiltradaJobService(RadicacionFiltradaUpdateService updater) {
    this.updater = updater;
  }

  public String start() {
    String id = UUID.randomUUID().toString();
    JobState st = new JobState(id);
    jobs.put(id, st);
    runAsync(st);
    return id;
  }

  public JobState get(String id) { return jobs.get(id); }

  @Async
  protected CompletableFuture<Void> runAsync(JobState st) {
    return CompletableFuture.runAsync(() -> {
      try {
        var res = updater.actualizar();
        st.inserted = (Integer) res.getOrDefault("inserted", 0);
        st.status = Status.COMPLETED;
      } catch (Exception ex) {
        st.status = Status.FAILED;
        st.message = ex.getMessage();
      }
    });
  }
}

