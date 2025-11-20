import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CargaSimpleComponent } from '../carga-simple.component';

@Component({
  selector: 'app-carga-pagos-traza',
  standalone: true,
  imports: [CommonModule, CargaSimpleComponent],
  template: `
    <app-carga-simple
      [titulo]="'Carga pagos traza'"
      [descripcion]="'Sube archivo CSV (;) con columnas: identificacion;nombre;voucher;id_pago;fecha_pago;valor_pagado;valor_causado. Reemplaza toda la tabla pagos_traza.'"
      [url]="base + '/api/v1/cargas/pagos-traza'"
    ></app-carga-simple>
  `,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CargaPagosTrazaComponent {
  readonly base = (() => {
    try { return `${window.location.protocol}//${window.location.hostname}:8080`; } catch { return 'http://localhost:8080'; }
  })();
}

