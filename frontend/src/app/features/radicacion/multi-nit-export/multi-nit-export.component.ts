import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { RadicacionApiService } from '../../../core/api/radicacion-api.service';

@Component({
  selector: 'app-multi-nit-export',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './multi-nit-export.component.html',
  styleUrl: './multi-nit-export.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MultiNitExportComponent {
  readonly form = this.fb.group({
    nits: ['', [Validators.required, Validators.minLength(4)]]
  });

  readonly downloading = signal(false);
  readonly error = signal('');
  readonly success = signal('');

  constructor(private readonly fb: FormBuilder, private readonly api: RadicacionApiService) {}

  get nitsControl() {
    return this.form.controls.nits;
  }

  descargar(): void {
    this.error.set('');
    this.success.set('');

    if (this.form.invalid) {
      this.nitsControl.markAsTouched();
      return;
    }

    const valores = this.parseNits(this.nitsControl.value ?? '');
    if (!valores.length) {
      this.error.set('Ingresa al menos un NIT válido.');
      return;
    }

    const unicos = Array.from(new Set(valores.map((v) => v.trim()).filter((v) => v.length >= 3)));
    this.downloading.set(true);
    this.api
      .descargarTxtPorNits(valores)
      .pipe(finalize(() => this.downloading.set(false)))
      .subscribe({
        next: async (blob) => {
          // Leer contenido para calcular métricas de NIT
          let exitosos = 0;
          try {
            const texto = await blob.text();
            exitosos = (texto.match(/^#\s+NIT\s+/gm) || []).length;
          } catch {
            exitosos = 0;
          }
          const sinRegistros = Math.max(0, unicos.length - exitosos);
          const timestamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '');
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = 'radicacion_multiples_' + timestamp + '.txt';
          anchor.click();
          URL.revokeObjectURL(url);
          this.success.set(`Descarga completa. Exitosos: ${exitosos}. Sin registros: ${sinRegistros}.`);
        },
        error: (err) => {
          const message = err?.error?.message ?? 'No fue posible generar el archivo. Verifica los NIT enviados.';
          this.error.set(message);
        }
      });
  }

  private parseNits(value: string): string[] {
    return value
      .split(/[,\n;\t\s]+/)
      .map((item) => item.trim())
      .filter((item) => item.length >= 3);
  }
}
