import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CargaSimpleComponent } from '../carga-simple.component';

@Component({
  selector: 'app-radicacion-capita-view',
  standalone: true,
  imports: [CommonModule, CargaSimpleComponent],
  template: `
  <section class="radicacion-capita">
    <h3>Carga de radicación cápita</h3>
    <p>Cada carga reemplaza completamente la tabla <strong>radicacion_capita</strong>.</p>
    <app-carga-simple
      [titulo]="'Cargar radicación cápita'"
      [descripcion]="'Sube archivo CSV (;) o (,) con encabezado. Se reemplaza por completo la tabla final.'"
      [url]="base + '/api/v1/cargas/radicacion-capita'"
    ></app-carga-simple>
  </section>
  `,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RadicacionCapitaViewComponent {
  readonly base = (() => {
    try { return `${window.location.protocol}//${window.location.hostname}:8080`; } catch { return 'http://localhost:8080'; }
  })();
}
