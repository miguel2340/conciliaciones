import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RadicacionApiService } from '../../core/api/radicacion-api.service';
import { finalize, from, mergeMap, of, tap, catchError } from 'rxjs';

type ReportType = 'general' | 'faltantes' | 'pagado_mayor' | 'no_en_traza' | 'nit_no_en_traza' | 'pagos_no_cruzan' | 'pagado_mayor_fact';

interface ReportDef { type: ReportType; title: string; subtitle: string; badge?: 'ok' | 'warn' | 'alert' | 'none'; }

@Component({
  selector: 'app-lista-tareas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './lista-tareas.component.html',
  styleUrl: './lista-tareas.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ListaTareasComponent implements OnInit {
  // Cards (ASCII labels to avoid encoding issues)
  readonly reports: ReportDef[] = [
    { type: 'general',           title: 'General',             subtitle: 'Pagos traza vs radicacion/pagos (con coincidencia)', badge: 'ok' },
    { type: 'faltantes',         title: 'Voucher faltantes',   subtitle: 'En traza pero sin coincidencia en radicacion/pagos',  badge: 'warn' },
    { type: 'pagado_mayor',      title: 'Pagado > Causado',    subtitle: 'valor_pagado_pagos > valor_causado',                 badge: 'alert' },
    { type: 'no_en_traza',       title: 'No estan en pagos_traza', subtitle: 'NIT/Voucher en radicacion sin registro en pagos_traza', badge: 'alert' },
    { type: 'nit_no_en_traza',   title: 'NIT no en pagos_traza', subtitle: 'NIT sin ningun registro en pagos_traza',           badge: 'none' },
    { type: 'pagado_mayor_fact', title: 'Pagado > Factura',    subtitle: 'Radicacion (servicios + capita) con pagado > factura', badge: 'alert' },
  ];

  readonly metricsMap = signal<Record<string, any>>({});
  readonly loadingMap = signal<Record<string, boolean>>({});
  readonly downloadingMap = signal<Record<string, boolean>>({});
  readonly errorMap = signal<Record<string, string>>({});
  readonly error = signal('');

  constructor(private readonly api: RadicacionApiService) {}

  ngOnInit(): void { this.loadAllMetrics(); }

  private setLoading(type: ReportType, v: boolean) {
    const m = { ...(this.loadingMap()) }; m[type] = v; this.loadingMap.set(m);
  }
  private setDownloading(type: ReportType, v: boolean) {
    const m = { ...(this.downloadingMap()) }; m[type] = v; this.downloadingMap.set(m);
  }
  private setMetrics(type: ReportType, data: any) {
    const m = { ...(this.metricsMap()) }; m[type] = data ?? null; this.metricsMap.set(m);
  }
  private setError(type: ReportType, msg: string) {
    const e = { ...(this.errorMap()) }; e[type] = msg; this.errorMap.set(e);
  }

  loadAllMetrics(): void {
    this.error.set('');
    from(this.reports)
      .pipe(
        mergeMap((r) => {
          this.setLoading(r.type, true);
          // Usar cache de backend por defecto (fresh=false). Si cache no existe, el backend lo llenará.
          return this.api.getListaMetrics(r.type, false).pipe(
            tap((res: any) => { this.setMetrics(r.type, res?.metrics ?? null); this.setError(r.type, ''); }),
            catchError((err) => { this.setMetrics(r.type, null); this.setError(r.type, err?.error?.trace_id || `${err?.status || ''} ${err?.statusText || 'Error'}`.trim()); return of(null); }),
            finalize(() => this.setLoading(r.type, false))
          );
        }, 2)
      )
      .subscribe();
  }

  reload(type: ReportType): void {
    this.setLoading(type, true);
    // Recalcula forzando backend (fresh=true) y actualiza cache global
    this.api.getListaMetrics(type, true)
      .pipe(finalize(() => this.setLoading(type, false)))
      .subscribe({
        next: (res: any) => { this.setMetrics(type, res?.metrics ?? null); this.setError(type, ''); },
        error: (err) => { this.setMetrics(type, null); this.setError(type, err?.error?.trace_id || `${err?.status || ''} ${err?.statusText || 'Error'}`.trim()); }
      });
  }

  descargarCsv(type: ReportType): void {
    this.setDownloading(type, true);
    this.api.downloadListaCsv(type)
      .pipe(finalize(() => this.setDownloading(type, false)))
      .subscribe({
        next: (blob: Blob) => {
          const ts = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '');
          const a = document.createElement('a');
          a.href = URL.createObjectURL(blob);
          a.download = `reporte_${type}_${ts}.csv`;
          a.click();
          URL.revokeObjectURL(a.href);
        },
        error: () => this.error.set('No se pudo descargar el CSV')
      });
  }

  metricsOf(type: ReportType) { return this.metricsMap()[type] ?? null; }

  refreshAll(): void {
    // Marca todas como cargando
    for (const r of this.reports) this.setLoading(r.type, true);

    this.api.refreshListaMetrics()
      .pipe(finalize(() => { for (const r of this.reports) this.setLoading(r.type, false); }))
      .subscribe({
        next: (res: any) => {
          const refreshed = res?.refreshed ?? {};
          for (const r of this.reports) {
            if (refreshed[r.type]) {
              this.setMetrics(r.type, refreshed[r.type]);
              this.setError(r.type, '');
            }
          }
        },
        error: () => {
          // Fallback: si falla el refresh endpoint, forzamos fresh por cada tipo
          from(this.reports)
            .pipe(
              mergeMap((r) => this.api.getListaMetrics(r.type, true).pipe(
                tap((ans: any) => { this.setMetrics(r.type, ans?.metrics ?? null); this.setError(r.type, ''); }),
                catchError(() => { this.setError(r.type, ''); return of(null); })
              ), 1)
            ).subscribe();
        }
      });
  }
}
