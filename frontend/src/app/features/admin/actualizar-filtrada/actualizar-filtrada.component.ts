import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-actualizar-filtrada',
  standalone: true,
  imports: [CommonModule],
  template: `
  <section class="upd">
    <h3>Actualizar tabla radicacion_filtrada</h3>
    <p>Ejecuta un reemplazo completo de la tabla radicacion_filtrada a partir de radicacion3 + pagos.</p>

    <button type="button" (click)="actualizar()" [disabled]="loading()">
      {{ loading() ? 'Actualizando…' : 'Actualizar ahora' }}
    </button>
    <span class="ok" *ngIf="ok()">{{ ok() }}</span>
    <span class="err" *ngIf="err()">{{ err() }}</span>
  </section>

  <!-- Overlay de bloqueo total -->
  <div class="overlay" *ngIf="loading()" aria-busy="true" aria-live="polite">
    <div class="overlay__box">
      <div class="spinner" aria-hidden="true"></div>
      <p>Actualizando radicacion_filtrada…</p>
    </div>
  </div>
  `,
  styles: [
    `.upd{display:flex;flex-direction:column;gap:.75rem}`,
    `.ok{color:#059669}`,
    `.err{color:#dc2626}`,
    `.overlay{position:fixed;inset:0;background:rgba(2,6,23,.55);display:flex;align-items:center;justify-content:center;z-index:2147483647}`,
    `.overlay__box{background:#0b1220;border:1px solid rgba(148,163,184,.25);color:#e2e8f0;padding:1.25rem 1.5rem;border-radius:12px;display:flex;flex-direction:column;align-items:center;gap:.75rem;min-width:280px;box-shadow:0 10px 30px rgba(0,0,0,.35)}`,
    `.spinner{width:42px;height:42px;border-radius:50%;display:inline-block;box-sizing:border-box;will-change:transform;border:3px solid rgba(226,232,240,.25);border-top-color:#60a5fa;border-right-color:transparent;animation:spin .8s linear infinite;-webkit-animation:spin .8s linear infinite}`,
    `@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}`,
    `@-webkit-keyframes spin{0%{-webkit-transform:rotate(0deg)}100%{-webkit-transform:rotate(360deg)}}`
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActualizarFiltradaComponent {
  readonly loading = signal(false);
  readonly ok = signal('');
  readonly err = signal('');

  private readonly base = (() => {
    try { return `${window.location.protocol}//${window.location.hostname}:8080`; } catch { return 'http://localhost:8080'; }
  })();

  constructor(private readonly http: HttpClient) {}

  actualizar(): void {
    this.loading.set(true); this.ok.set(''); this.err.set('');
    this.lockBody(true);
    // Inicia job asíncrono y luego hace polling del estado para evitar timeouts
    this.http.post<{jobId:string}>(`${this.base}/api/v1/radicacion/actualizar-filtrada/start`, {})
      .subscribe({
        next: (res) => this.pollStatus(res?.jobId),
        error: (e) => { this.err.set(e?.error?.sql_error || e?.message || 'No se pudo iniciar el proceso'); this.loading.set(false); this.lockBody(false); }
      });
  }

  private pollStatus(jobId?: string, intent = 0): void {
    if (!jobId) { this.err.set('ID de proceso no recibido'); this.loading.set(false); this.lockBody(false); return; }
    const maxIntents = 600; // hasta ~50 min si interval=5s
    const intervalMs = 5000;
    this.http.get<any>(`${this.base}/api/v1/radicacion/actualizar-filtrada/status`, { params: { jobId } as any })
      .subscribe({
        next: (s) => {
          const st = (s?.status || '').toUpperCase();
          if (st === 'COMPLETED') {
            this.ok.set(`Actualización completada. Filas insertadas: ${s?.inserted ?? 0}`);
            this.loading.set(false); this.lockBody(false);
          } else if (st === 'FAILED') {
            this.err.set(s?.message || 'Proceso fallido');
            this.loading.set(false); this.lockBody(false);
          } else {
            setTimeout(() => this.pollStatus(jobId, intent + 1), intervalMs);
          }
        },
        error: () => {
          if (intent < maxIntents) { setTimeout(() => this.pollStatus(jobId, intent + 1), intervalMs); }
          else { this.err.set('Tiempo de espera agotado consultando estado'); this.loading.set(false); this.lockBody(false); }
        }
      });
  }

  private lockBody(v: boolean): void {
    try {
      if (v) {
        document.body.style.overflow = 'hidden';
      } else {
        document.body.style.overflow = '';
      }
    } catch {}
  }
}
