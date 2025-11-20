import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { finalize } from 'rxjs';
import { RadicacionApiService } from '../../../core/api/radicacion-api.service';

@Component({
  selector: 'app-reportes-hub',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './reportes-hub.component.html',
  styleUrl: './reportes-hub.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReportesHubComponent {
  readonly downloading = signal(false);
  readonly downloadingTipo = signal<'voucher' | 'gerencial' | ''>('');
  readonly error = signal('');
  readonly success = signal('');

  constructor(private readonly api: RadicacionApiService) {}

  descargarVoucher(): void {
    this.error.set('');
    this.success.set('');
    this.downloading.set(true);
    this.downloadingTipo.set('voucher');

    this.api
      .descargarReporteVoucher()
      .pipe(finalize(() => { this.downloading.set(false); this.downloadingTipo.set(''); }))
      .subscribe({
        next: (blob: Blob) => {
          const ts = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '');
          const a = document.createElement('a');
          a.href = URL.createObjectURL(blob);
          a.download = `reporte_voucher_${ts}.txt`;
          a.click();
          URL.revokeObjectURL(a.href);
          this.success.set('Reporte de voucher generado correctamente.');
        },
        error: () => this.error.set('No fue posible generar el reporte. Verifica sesión y vuelve a intentar.')
      });
  }

  descargarGerencial(): void {
    this.error.set('');
    this.success.set('');
    this.downloading.set(true);
    this.downloadingTipo.set('gerencial');

    this.api
      .descargarReporteGerencial()
      .pipe(finalize(() => { this.downloading.set(false); this.downloadingTipo.set(''); }))
      .subscribe({
        next: (blob: Blob) => {
          const ts = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '');
          const a = document.createElement('a');
          a.href = URL.createObjectURL(blob);
          a.download = `reporte_gerencial_${ts}.txt`;
          a.click();
          URL.revokeObjectURL(a.href);
          this.success.set('Reporte gerencial generado correctamente.');
        },
        error: () => this.error.set('No fue posible generar el reporte gerencial. Verifica sesión y vuelve a intentar.')
      });
  }
}