import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  RadicacionApiService,
  RadicacionFechaExportPayload
} from '../../../core/api/radicacion-api.service';

@Component({
  selector: 'app-fecha-export',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './fecha-export.component.html',
  styleUrl: './fecha-export.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FechaExportComponent implements OnInit {
  private readonly rangoFechasValidator = (control: AbstractControl): ValidationErrors | null => {
    const inicio = control.get('fechaInicio')?.value as string | null;
    const fin = control.get('fechaFin')?.value as string | null;
    if (!inicio || !fin) {
      return null;
    }

    return fin >= inicio ? null : { rangoFechas: true };
  };

  readonly form = this.fb.group(
      {
        fechaInicio: [''],
        fechaFin: [''],
        estados: [[]],
        nits: ['']
      },
      { validators: [this.rangoFechasValidator] }
    );

  readonly downloading = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly estadosOpciones = signal<string[]>([]);

  constructor(private readonly fb: FormBuilder, private readonly api: RadicacionApiService) {}
  ngOnInit(): void {
    this.api.getEstadosAplicacion().subscribe({
      next: (items: string[]) => this.estadosOpciones.set(items ?? []),
      error: () => this.estadosOpciones.set([])
    });
  }

  get fechaInicioControl() {
    return this.form.controls.fechaInicio;
  }

  get fechaFinControl() {
    return this.form.controls.fechaFin;
  }

  get estadosControl() {
    return this.form.controls.estados;
  }

  get nitsControl() {
    return this.form.controls.nits;
  }

  get rangoFechasInvalido(): boolean {
    return (
      this.form.hasError('rangoFechas') &&
      (this.fechaInicioControl.touched || this.fechaFinControl.touched)
    );
  }

  descargar(): void {
    this.error.set('');
    this.success.set('');

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const payload = this.buildPayload();

    this.downloading.set(true);
    this.api
      .descargarTxtPorFecha(payload)
      .pipe(finalize(() => this.downloading.set(false)))
      .subscribe({
        next: (blob) => {
          const filename = this.buildFilename(payload);
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = filename;
          anchor.click();
          URL.revokeObjectURL(url);
          this.success.set('Reporte generado correctamente.');
        },
        error: (err) => {
          const message = err?.error?.message ?? 'No fue posible generar el archivo con los filtros seleccionados.';
          this.error.set(message);
        }
      });
  }

  private buildPayload(): RadicacionFechaExportPayload {
    const payload: RadicacionFechaExportPayload = {};
    const fechaInicio = this.fechaInicioControl.value?.trim();
    const fechaFin = this.fechaFinControl.value?.trim();
    const estadosSel = (this.estadosControl.value as string[] | null) ?? [];
    const nits = this.nitsControl.value ?? '';

    if (fechaInicio) {
      payload.fechaInicio = fechaInicio;
    }

    if (fechaFin) {
      payload.fechaFin = fechaFin;
    }

    const estadosLista = estadosSel;
    if (estadosLista.length) {
      payload.estadosAplicacion = estadosLista;
    }

    const nitsLista = this.parseListado(nits, false);
    if (nitsLista.length) {
      payload.nits = nitsLista;
    }

    return payload;
  }

  private parseListado(value: string, preservarEspaciosInternos: boolean): string[] {
    if (!value) {
      return [];
    }

    const regex = preservarEspaciosInternos ? /[\n,;|]+/ : /[\s,;|]+/;
    return value
      .split(regex)
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
  }

  private buildFilename(payload: RadicacionFechaExportPayload): string {
    const parts = ['radicacion_fecha'];
    if (payload.fechaInicio) {
      parts.push(payload.fechaInicio.replace(/-/g, ''));
    }
    if (payload.fechaFin) {
      parts.push(payload.fechaFin.replace(/-/g, ''));
    }
    const timestamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '');
    parts.push(timestamp);
    return parts.join('_') + '.txt';
  }
}
