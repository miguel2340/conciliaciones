import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CargaSimpleComponent } from '../carga-simple.component';

@Component({
  selector: 'app-carga-correcciones-pagos-capita',
  standalone: true,
  imports: [CommonModule, CargaSimpleComponent],
  template: `
    <app-carga-simple
      [titulo]="'Corrección de pagos cápita'"
      [descripcion]="'Reemplaza filas existentes por id_fomag (última columna). CSV (;) con encabezado.'"
      [url]="base + '/api/v1/cargas/correcciones-pagos'"
      [tipo]="'capita'"
    ></app-carga-simple>
  `,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CargaCorreccionesPagosCapitaComponent {
  readonly base = (() => {
    try { return `${window.location.protocol}//${window.location.hostname}:8080`; } catch { return 'http://localhost:8080'; }
  })();
}

