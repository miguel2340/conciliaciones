import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PagosApiService } from '../../../core/api/pagos-api.service';

@Component({
  selector: 'app-pagos-api',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <section class="pagos-api">
    <h3>Carga de pagos desde API</h3>
    <p>Consulta por fecha, revisa vouchers del día y aprueba para insertar en la tabla pagos (sin campo fecha).</p>

    <div class="filters">
      <label>
        Fecha:
        <input type="date" [(ngModel)]="fecha" />
      </label>
      <button type="button" (click)="buscar()" [disabled]="loading()">{{ loading() ? 'Cargando…' : 'Buscar' }}</button>
      <button type="button" (click)="aprobarDia()" [disabled]="!resumen() || approving()">{{ approving() ? 'Aprobando…' : 'Aprobar todo el día' }}</button>
      <span class="err" *ngIf="error()">{{ error() }}</span>
      <span class="ok" *ngIf="ok()">{{ ok() }}</span>
    </div>

    <div *ngIf="resumen()" class="summary">
      <h4>Resumen {{ resumen()?.fecha }}</h4>
      <p>Registros: <strong>{{ resumen()?.metricas?.cantidad || 0 }}</strong></p>
      <p>Total valor pagado: <strong>{{ (resumen()?.metricas?.total_valor_pagado || 0) | number:'1.0-2' }}</strong></p>
      <p>Total valor factura: <strong>{{ (resumen()?.metricas?.total_valor_factura || 0) | number:'1.0-2' }}</strong></p>
    </div>

    <div *ngIf="resumen()?.vouchers?.length" class="vouchers">
      <h4>Vouchers del día</h4>
      <div class="row head">
        <div>Voucher</div>
        <div>Lineas</div>
        <div>Valor Pagado</div>
        <div>Factura</div>
        <div>Acciones</div>
      </div>
      <div class="row" *ngFor="let v of resumen()?.vouchers">
        <div>{{ v.voucher }}</div>
        <div>{{ v.cantidad }}</div>
        <div>{{ v.total_valor_pagado | number:'1.0-2' }}</div>
        <div>{{ v.total_valor_factura | number:'1.0-2' }}</div>
        <div>
          <button type="button" (click)="aprobarVoucher(v.voucher)" [disabled]="approving()">Aprobar</button>
        </div>
      </div>
    </div>
  </section>
  `,
  styles: [
    `.filters{display:flex;gap:.75rem;align-items:center;flex-wrap:wrap;margin-bottom:1rem}`,
    `.ok{color:#059669}`,
    `.err{color:#dc2626}`,
    `.summary{display:grid;grid-template-columns:repeat(3,1fr);gap:.5rem;margin:.75rem 0}`,
    `.vouchers{display:block;margin-top:1rem}`,
    `.row{display:grid;grid-template-columns:2fr 1fr 1.5fr 1.5fr 1fr;gap:.5rem;align-items:center;padding:.35rem .25rem;border-bottom:1px solid #e5e7eb}`,
    `.row.head{font-weight:600;background:#f8fafc}`
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PagosApiComponent {
  fecha = new Date().toISOString().slice(0, 10);
  readonly resumen = signal<any | null>(null);
  readonly loading = signal(false);
  readonly approving = signal(false);
  readonly error = signal('');
  readonly ok = signal('');

  constructor(private readonly api: PagosApiService) {}

  buscar(): void {
    if (!this.fecha) return;
    this.loading.set(true); this.error.set(''); this.ok.set('');
    this.api.getResumenDia(this.fecha).subscribe({
      next: (res: any) => { this.resumen.set(res); },
      error: (err) => { this.error.set(err?.error?.message || err?.message || 'Error consultando'); this.resumen.set(null); },
      complete: () => this.loading.set(false)
    });
  }

  aprobarDia(): void {
    if (!this.resumen()) return;
    this.approving.set(true); this.error.set(''); this.ok.set('');
    this.api.aprobarDia(this.fecha).subscribe({
      next: (res: any) => { this.ok.set(`Insertados: ${res?.insertados || 0}. Saltados: ${res?.saltados || 0}.`); },
      error: (err) => { this.error.set(err?.error?.message || err?.message || 'Error aprobando'); },
      complete: () => this.approving.set(false)
    });
  }

  aprobarVoucher(voucher: string): void {
    if (!this.resumen() || !voucher) return;
    this.approving.set(true); this.error.set(''); this.ok.set('');
    this.api.aprobarVoucher(this.fecha, voucher).subscribe({
      next: (res: any) => { this.ok.set(`Voucher ${voucher} insertados: ${res?.insertados || 0}.`); },
      error: (err) => { this.error.set(err?.error?.message || err?.message || 'Error aprobando'); },
      complete: () => this.approving.set(false)
    });
  }
}

