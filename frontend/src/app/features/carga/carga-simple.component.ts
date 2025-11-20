import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Input } from '@angular/core';

@Component({
  selector: 'app-carga-simple',
  standalone: true,
  imports: [CommonModule],
  template: `
  <section class="carga-simple">
    <h3>{{ titulo }}</h3>
    <p>{{ descripcion }}</p>

    <div class="panel">
      <input type="file" (change)="onFile($event)" [disabled]="subiendo()" />
      <button type="button" (click)="subir()" [disabled]="!archivo || subiendo()">
        <span class="spinner" *ngIf="subiendo()"></span>
        {{ subiendo() ? 'Subiendo…' : 'Subir' }}
      </button>
      <p class="archivo" *ngIf="archivo">Seleccionado: {{ archivo?.name }}</p>
      <p class="ok" *ngIf="ok()">{{ ok() }}</p>
      <p class="err" *ngIf="err()">{{ err() }}</p>
    </div>

    <div class="loading-overlay" *ngIf="subiendo()">
      <div class="overlay-spinner"></div>
      <p>Procesando archivo…</p>
    </div>
  </section>
  `,
  styles: [
    `.panel{display:flex;gap:.75rem;align-items:center;flex-wrap:wrap}`,
    `.archivo{color:#64748b}`,
    `.ok{color:#059669}`,
    `.err{color:#dc2626}`,
    `.spinner{width:1rem;height:1rem;border-radius:50%;border:2px solid rgba(255,255,255,.3);border-top-color:#fff;animation:spin .8s linear infinite;display:inline-block}`,
    `.loading-overlay{position:fixed;inset:0;background:rgba(15,23,42,.35);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:.75rem;color:#fff;z-index:1000}`,
    `.overlay-spinner{width:2.5rem;height:2.5rem;border-radius:50%;border:3px solid rgba(255,255,255,.35);border-top-color:#fff;animation:spin .8s linear infinite}`,
    `@keyframes spin{to{transform:rotate(360deg)}}`
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CargaSimpleComponent {
  constructor(private readonly http: HttpClient) {}
  @Input() titulo = 'Carga';
  @Input() descripcion = '';
  @Input() url: string | null = null;
  @Input() tipo: string | null = null;

  archivo: File | null = null;
  readonly subiendo = signal(false);
  readonly ok = signal('');
  readonly err = signal('');

  onFile(evt: Event): void {
    const input = evt.target as HTMLInputElement;
    this.archivo = input.files && input.files.length ? input.files[0] : null;
    this.ok.set('');
    this.err.set('');
  }

  subir(): void {
  if (!this.archivo) return;
  this.ok.set(''); this.err.set(''); this.subiendo.set(true);
  if (!this.url) {
    setTimeout(() => { this.subiendo.set(false); this.ok.set('Archivo cargado correctamente (simulación).'); }, 1200);
    return;
  }
  const form = new FormData();
  form.append('archivo', this.archivo);
  if (this.tipo) { form.append('tipo', this.tipo); }
  this.http.post<{ok:boolean,message:string}>(this.url, form, { headers: { Authorization: 'Bearer ' + (localStorage.getItem('access_token') || '') } }).subscribe({
    next: (res: {ok:boolean; message:string}) => { this.subiendo.set(false); this.ok.set(res?.message || 'Archivo cargado correctamente.'); },
    error: (err: any) => { this.subiendo.set(false); const msg = err?.error?.message || err?.message || 'Error en la carga'; this.err.set(msg); }
  });

  }
}
