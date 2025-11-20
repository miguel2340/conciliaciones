import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CargaSimpleComponent } from '../carga-simple.component';

@Component({
  selector: 'app-carga-pagos',
  standalone: true,
  imports: [CommonModule, CargaSimpleComponent],
  template: `
    <app-carga-simple
      [titulo]="'Carga de pagos'"
      [descripcion]="'Sube archivo CSV (;) de pagos.'"
      [url]="base + '/api/v1/cargas/pagos'"
      [tipo]="'pagos'"
    ></app-carga-simple>
  `,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CargaPagosComponent {
  readonly base = (() => {
    try { return `${window.location.protocol}//${window.location.hostname}:8080`; } catch { return 'http://localhost:8080'; }
  })();
}
