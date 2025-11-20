import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CargaSimpleComponent } from '../carga-simple.component';

@Component({
  selector: 'app-carga-correcciones-pagos',
  standalone: true,
  imports: [CommonModule, CargaSimpleComponent],
  template: `
    <app-carga-simple
      [titulo]="'Corrección de pagos'"
      [descripcion]="'Reemplaza filas existentes por id_fomag (última columna) usando CSV (;) con encabezado. Usa coma (,) para montos; el porcentaje puede usar punto (.) o coma (,). Se hace respaldo previo.'"
      [url]="base + '/api/v1/cargas/correcciones-pagos'"
    ></app-carga-simple>
  `,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CargaCorreccionesPagosComponent {
  readonly base = (() => {
    try { return `${window.location.protocol}//${window.location.hostname}:8080`; } catch { return 'http://localhost:8080'; }
  })();
}

