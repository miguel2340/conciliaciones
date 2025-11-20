import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RadicacionApiService } from '../../core/api/radicacion-api.service';

@Component({
  selector: 'app-conciliacion',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <section class="conciliacion">
    <h3>Conciliación por NIT</h3>
    <p>Primero verificaremos si el NIT aparece en alguna lista de tareas. Luego podrás confirmar la descarga.</p>
    <div class="bar">
      <label>NIT <input [(ngModel)]="nit" placeholder="900123456" (input)="reset()" /></label>
      <button type="button" (click)="precheck()" [disabled]="!nit || checking()">{{ checking() ? 'Verificando…' : 'Verificar' }}</button>
      <button type="button" (click)="confirmarDescarga()" [disabled]="!puedeConfirmar() || loading()">{{ loading() ? 'Generando…' : 'Descargar' }}</button>
      <span class="err" *ngIf="err()">{{ err() }}</span>
    </div>

    <div *ngIf="pre()" class="panel">
      <div class="panel-h">
        <strong>Resultado de verificación</strong>
        <button type="button" class="link" (click)="reset()">Limpiar</button>
      </div>
      <div *ngIf="pre()?.matches?.length; else noMatches">
        <div class="warn">El NIT aparece en las siguientes listas:</div>
        <ul class="lists">
          <li *ngFor="let m of pre()?.matches">{{ mapNombre(m) }}</li>
        </ul>
        <div class="actions">
          <button type="button" (click)="confirmarDescarga()" [disabled]="loading()">Continuar y descargar</button>
        </div>
      </div>
      <ng-template #noMatches>
        <div class="ok">No aparece en ninguna lista de tareas.</div>
        <div class="actions">
          <button type="button" (click)="confirmarDescarga()" [disabled]="loading()">Descargar</button>
        </div>
      </ng-template>
    </div>
  </section>
  `,
  styles: [
    `.bar{display:flex;gap:.5rem;align-items:center}`,
    `input{padding:.5rem .6rem;border:1px solid #cbd5e1;border-radius:8px}`,
    `.err{color:#dc2626}`,
    `.panel{margin-top:.75rem;border:1px solid #e2e8f0;border-radius:10px;background:#f8fafc;padding:.75rem}`,
    `.panel-h{display:flex;justify-content:space-between;align-items:center;margin-bottom:.5rem}`,
    `.link{background:none;border:none;color:#2563eb;cursor:pointer}`,
    `.lists{margin:.25rem 0 .75rem .75rem}`,
    `.ok{color:#0a7f39}`,
    `.warn{color:#92400e}`,
    `.actions{margin-top:.5rem}`
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConciliacionComponent {
  nit = '';
  readonly loading = signal(false);
  readonly err = signal('');
  readonly checking = signal(false);
  readonly pre = signal<{nit:string; nitNorm:string; matches:string[]; found:boolean} | null>(null);
  readonly puedeConfirmar = computed(() => !!this.pre());

  constructor(private readonly api: RadicacionApiService) {}

  reset(): void {
    this.err.set('');
    this.pre.set(null);
  }

  mapNombre(code: string): string {
    const map: Record<string,string> = {
      faltantes: 'Faltantes (sin radicación)'.trim(),
      pagado_mayor: 'Pagado mayor (traza > causado)'.trim(),
      no_en_traza: 'No en traza (radicado sin traza)'.trim(),
      nit_no_en_traza: 'NIT sin traza'.trim(),
      pagado_mayor_fact: 'Pagado mayor a factura'.trim(),
      pagos_no_cruzan: 'Pagos que no cruzan con radicación'.trim(),
    };
    return map[code] ?? code;
  }

  precheck(): void {
    if (!this.nit) return;
    this.err.set('');
    this.checking.set(true);
    this.api.precheckConciliacionPorNit(this.nit).subscribe({
      next: (res) => this.pre.set(res),
      error: (e) => this.err.set(e?.error?.message || e?.message || 'Error verificando NIT'),
      complete: () => this.checking.set(false)
    });
  }

  confirmarDescarga(): void {
    if (!this.puedeConfirmar() || !this.nit) return;
    this.descargar();
  }

  private descargar(): void {
    if (!this.nit) return;
    this.loading.set(true); this.err.set('');
    this.api.descargarConciliacionPorNit(this.nit).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'conciliacion_' + this.nit + '.xlsx';
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: (e) => this.err.set(e?.error?.message || e?.message || 'Error generando conciliación'),
      complete: () => this.loading.set(false)
    });
  }
}
