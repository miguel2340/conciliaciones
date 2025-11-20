import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { RadicacionApiService, RadicacionPageResponse, RadicacionRegistro } from '../../../core/api/radicacion-api.service';

interface ColumnDef {
  key: keyof RadicacionRegistro;
  label: string;
  type?: 'text' | 'currency' | 'percent' | 'date';
}

@Component({
  selector: 'app-nit-search',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './nit-search.component.html',
  styleUrl: './nit-search.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NitSearchComponent {
  readonly columns: ColumnDef[] = [
    { key: 'nit', label: 'NIT' },
    { key: 'nomPrestador', label: 'Prestador' },
    { key: 'factura', label: 'Factura' },
    { key: 'prefijo', label: 'Prefijo' },
    { key: 'mesRadicacion', label: 'Mes Radicación' },
    { key: 'estadoAplicacion', label: 'Estado Aplicación' },
    { key: 'valorFactura', label: 'Valor Factura', type: 'currency' },
    { key: 'valorPagado', label: 'Valor Pagado', type: 'currency' },
    { key: 'porcentajePago', label: '% Pago', type: 'percent' },
    { key: 'estado', label: 'Estado general' },
    { key: 'fechaRadicacion', label: 'Fecha Radicación', type: 'date' },
    { key: 'fechaPago', label: 'Fecha Pago', type: 'date' }
  ];

  readonly form = this.fb.group({
    nit: ['', [Validators.required, Validators.minLength(4)]]
  });

  readonly loading = signal(false);
  readonly error = signal('');
  readonly registros = signal<RadicacionRegistro[]>([]);
  readonly total = signal(0);
  readonly totalPages = signal(0);
  readonly page = signal(0);
  readonly pageSize = signal(100);
  readonly nitActual = signal('');
  readonly downloading = signal(false);

  constructor(private readonly fb: FormBuilder, private readonly api: RadicacionApiService) {}

  get nitControl() {
    return this.form.controls.nit;
  }

  onSubmit(): void {
    this.page.set(0);
    this.buscar();
  }

  buscar(pageOverride?: number): void {
    if (this.form.invalid && this.nitActual() === '') {
      this.nitControl.markAsTouched();
      return;
    }

    const nit = pageOverride !== undefined ? this.nitActual() : this.nitControl.value?.trim();
    if (!nit) {
      this.nitControl.markAsTouched();
      return;
    }

    const pageToRequest = pageOverride ?? this.page();
    this.loading.set(true);
    this.error.set('');

    this.api
      .buscarPorNit(nit, pageToRequest, this.pageSize())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response: RadicacionPageResponse) => this.procesarRespuesta(response, nit),
        error: (err) => {
          const message = err?.error?.message ?? 'No fue posible obtener la información del NIT indicado.';
          this.error.set(message);
          this.registros.set([]);
          this.total.set(0);
          this.totalPages.set(0);
        }
      });
  }

  private procesarRespuesta(response: RadicacionPageResponse, nit: string): void {
    this.registros.set(response.content);
    this.total.set(response.totalElements);
    this.totalPages.set(response.totalPages);
    this.page.set(response.pageNumber);
    this.pageSize.set(response.pageSize);
    this.nitActual.set(nit);

    if (response.totalElements === 0) {
      this.error.set('No se encontraron resultados para el NIT indicado.');
    }
  }

  paginaAnterior(): void {
    const current = this.page();
    if (current > 0) {
      this.buscar(current - 1);
    }
  }

  paginaSiguiente(): void {
    const current = this.page();
    if (current + 1 < this.totalPages()) {
      this.buscar(current + 1);
    }
  }

  descargarTxt(): void {
    const nit = this.nitActual();
    if (!nit) {
      return;
    }

    this.downloading.set(true);
    this.api
      .descargarTxtPorNit(nit)
      .pipe(finalize(() => this.downloading.set(false)))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = 'radicacion_' + nit + '.txt';
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: () => this.error.set('No fue posible generar el archivo TXT. Intenta nuevamente más tarde.')
      });
  }

  trackById(_: number, item: RadicacionRegistro): number {
    return item.id;
  }

  formatValue(item: RadicacionRegistro, column: ColumnDef): string {
    const value = item[column.key];
    if (value === null || value === undefined) {
      return '';
    }

    switch (column.type) {
      case 'currency':
        return new Intl.NumberFormat('es-CO', {
          style: 'currency',
          currency: 'COP',
          maximumFractionDigits: 0
        }).format(Number(value));
      case 'percent':
        return Number(value).toFixed(2) + '%';
      case 'date':
        return new Intl.DateTimeFormat('es-CO', {
          dateStyle: 'medium',
          timeStyle: 'short'
        }).format(new Date(value as string));
      default:
        return String(value);
    }
  }
}
