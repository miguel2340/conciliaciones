import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { finalize } from 'rxjs';
import { RadicacionApiService } from '../../../core/api/radicacion-api.service';

@Component({
  selector: 'app-voucher-report',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './voucher-report.component.html',
  styleUrl: './voucher-report.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VoucherReportComponent {
  readonly downloading = signal(false);
  readonly error = signal('');
  readonly success = signal('');

  constructor(private readonly api: RadicacionApiService) {}

  descargar(): void {
    this.error.set('');
    this.success.set('');
    this.downloading.set(true);

    this.api
      .descargarReporteVoucher()
      .pipe(finalize(() => this.downloading.set(false)))
      .subscribe({
        next: (blob) => {
          const ts = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '');
          const a = document.createElement('a');
          a.href = URL.createObjectURL(blob);
          a.download = "reporte_voucher_.txt";
          a.click();
          URL.revokeObjectURL(a.href);
          this.success.set('Reporte generado correctamente.');
        },
        error: () => this.error.set('No fue posible generar el reporte. Verifica sesión y vuelve a intentar.')
      });
  }
}
