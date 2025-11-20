import { ChangeDetectionStrategy, Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ProductividadApiService, ProdUsuario, ProdCargaItem } from '../../core/api/productividad-api.service';

type ProdCargaConUsuario = ProdCargaItem & { usuario_id: string };

@Component({
  selector: 'app-productividad',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <section class="prod">
    <header class="toolbar">
      <h3>Productividad</h3>
    </header>

    <div class="grid">
      <aside class="left">
        <div class="search-bar">
          <button type="button" (click)="cargarUsuarios()" [disabled]="loadingUsuarios()">
            {{ loadingUsuarios() ? 'Cargando...' : 'Refrescar' }}
          </button>
          <span class="err" *ngIf="errorUsuarios()">{{ errorUsuarios() }}</span>
        </div>
        <ul class="usuarios">
          <li *ngFor="let u of usuarios()" (click)="selectUsuario(u)" [class.sel]="u.usuario_id===usuarioSel()">
            <div class="id">{{ u.usuario_id || '(Sin usuario)' }}</div>
            <div class="meta">Eventos: {{ u.total_eventos }} | Ultima: {{ u.fecha_ultima_carga }}</div>
          </li>
        </ul>
      </aside>

      <main class="right">
        <section class="block">
          <div class="filtros global">
            <div class="title">Buscar voucher en todos los usuarios</div>
            <input type="text" placeholder="Ingresa voucher" [(ngModel)]="voucherGlobal">
            <button type="button" (click)="buscarVoucherGlobal()" [disabled]="loadingGlobal()">
              {{ loadingGlobal() ? 'Buscando...' : 'Buscar' }}
            </button>
            <button type="button" (click)="descargarCsvCompleto()" [disabled]="downloadingGlobal()">
              {{ downloadingGlobal() ? 'Generando CSV...' : 'CSV completo' }}
            </button>
            <span class="meta" *ngIf="resultadosGlobal()?.length && !loadingGlobal()">Coincidencias: {{ resultadosGlobal().length }}</span>
            <span class="err" *ngIf="errorGlobal()">{{ errorGlobal() }}</span>
          </div>

          <p class="hint" *ngIf="loadingGlobal()">Buscando en todos los usuarios...</p>

          <div *ngIf="resultadosGlobal()?.length" class="tabla global">
            <div class="thead">
              <div>Usuario</div>
              <div>Fecha</div>
              <div>Voucher</div>
              <div class="num">Lineas</div>
              <div class="num">Valor Factura</div>
              <div class="num">Valor Pagado</div>
              <div class="num">Eventos</div>
            </div>
            <div class="row" *ngFor="let r of resultadosGlobal()">
              <div>{{ r.usuario_id }}</div>
              <div>{{ r.fecha }}</div>
              <div>{{ r.voucher }}</div>
              <div class="num">{{ r.lineas | number }}</div>
              <div class="num">{{ r.valor_factura | number:'1.0-2' }}</div>
              <div class="num">{{ r.valor_pagado | number:'1.0-2' }}</div>
              <div class="num">{{ r.eventos }}</div>
            </div>
          </div>
          <p class="hint" *ngIf="!loadingGlobal() && globalBuscado() && !resultadosGlobal()?.length && !errorGlobal()">Sin coincidencias para el voucher.</p>
        </section>

        <section class="block">
          <div class="filtros" *ngIf="usuarioSel(); else sinSeleccion">
            <strong>Usuario:</strong> {{ usuarioSel() }}
            <label>Desde <input type="date" [(ngModel)]="desde"></label>
            <label>Hasta <input type="date" [(ngModel)]="hasta"></label>
            <label>Voucher <input type="text" [(ngModel)]="voucherUsuario" placeholder="Opcional"></label>
            <button type="button" (click)="cargarCargas(1)" [disabled]="loadingCargas()">{{ loadingCargas() ? 'Consultando...' : 'Consultar' }}</button>
            <button type="button" (click)="descargarCsvUsuario()" [disabled]="downloadingUsuario()">
              {{ downloadingUsuario() ? 'Generando CSV...' : 'CSV usuario' }}
            </button>
            <span class="err" *ngIf="errorCargas()">{{ errorCargas() }}</span>
          </div>
          <ng-template #sinSeleccion>
            <p class="hint">Selecciona un usuario para ver sus cargas.</p>
          </ng-template>

          <div *ngIf="cargas()?.length" class="tabla">
            <div class="thead">
              <div>Fecha</div>
              <div>Voucher</div>
              <div class="num">Lineas</div>
              <div class="num">Valor Factura</div>
              <div class="num">Valor Pagado</div>
              <div class="num">Eventos</div>
            </div>
            <div class="row" *ngFor="let r of cargas()">
              <div>{{ r.fecha }}</div>
              <div>{{ r.voucher }}</div>
              <div class="num">{{ r.lineas | number }}</div>
              <div class="num">{{ r.valor_factura | number:'1.0-2' }}</div>
              <div class="num">{{ r.valor_pagado | number:'1.0-2' }}</div>
              <div class="num">{{ r.eventos }}</div>
            </div>
            <div class="paginacion" *ngIf="pages()>1">
              <button type="button" (click)="cargarCargas(page()-1)" [disabled]="page()<=1 || loadingCargas()">Anterior</button>
              <span>Pagina {{ page() }} / {{ pages() }}</span>
              <button type="button" (click)="cargarCargas(page()+1)" [disabled]="page()>=pages() || loadingCargas()">Siguiente</button>
            </div>
          </div>

          <p *ngIf="usuarioSel() && !loadingCargas() && (!cargas() || !cargas()?.length)" class="empty">No hay datos para el rango actual.</p>
        </section>
      </main>
    </div>
  </section>
  `,
  styles: [
    `.prod{display:flex;flex-direction:column;gap:1rem}`,
    `.toolbar{display:flex;justify-content:space-between;align-items:center}`,
    `.grid{display:grid;grid-template-columns:320px 1fr;gap:1rem;min-height:520px}`,
    `.left{border:1px solid #e5e7eb;border-radius:10px;padding:.5rem;background:#fff}`,
    `.right{border:1px solid #e5e7eb;border-radius:10px;padding:.75rem;background:#fff;display:flex;flex-direction:column;gap:1rem}`,
    `.block{border:1px solid #e5e7eb;border-radius:8px;padding:.5rem .75rem;background:#fdfdfd}`,
    `.usuarios{list-style:none;margin:0;padding:0;max-height:60vh;overflow:auto}`,
    `.usuarios li{padding:.5rem;border-radius:8px;cursor:pointer;border:1px solid transparent}`,
    `.usuarios li:hover{background:#f8fafc}`,
    `.usuarios li.sel{border-color:#3b82f6;background:#eff6ff}`,
    `.usuarios .id{font-weight:600}`,
    `.usuarios .meta{color:#64748b;font-size:.85rem}`,
    `.search-bar{display:flex;gap:.5rem;align-items:center;margin-bottom:.5rem}`,
    `.err{color:#dc2626}`,
    `.meta{color:#475569;font-size:.9rem}`,
    `.tabla{display:block;margin-top:.5rem}`,
    `.thead,.row{display:grid;grid-template-columns:1fr 2fr .8fr 1.2fr 1.2fr .7fr;gap:.5rem;align-items:center;padding:.35rem .25rem}`,
    `.tabla.global .thead,.tabla.global .row{grid-template-columns:1.1fr 1fr 1.6fr .8fr 1.2fr 1.2fr .8fr}`,
    `.thead{font-weight:600;background:#f8fafc;border-bottom:1px solid #e5e7eb}`,
    `.row{border-bottom:1px solid #f1f5f9}`,
    `.num{text-align:right}`,
    `.paginacion{display:flex;gap:.75rem;align-items:center;justify-content:flex-end;margin-top:.5rem}`,
    `.filtros{display:flex;flex-wrap:wrap;gap:.5rem;align-items:center}`,
    `.filtros input{padding:.25rem .35rem;border:1px solid #cbd5e1;border-radius:6px}`,
    `.title{font-weight:600}`,
    `.hint,.empty{color:#64748b}`,
    `.hint{margin-top:.25rem}`
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProductividadComponent implements OnInit {
  readonly usuarios = signal<ProdUsuario[]>([]);
  readonly loadingUsuarios = signal(false);
  readonly errorUsuarios = signal('');

  readonly usuarioSel = signal<string | null>(null);
  desde = '';
  hasta = '';
  voucherUsuario = '';
  voucherGlobal = '';
  readonly cargas = signal<ProdCargaItem[] | null>(null);
  readonly loadingCargas = signal(false);
  readonly errorCargas = signal('');
  readonly page = signal(1);
  readonly pages = signal(1);

  readonly resultadosGlobal = signal<ProdCargaConUsuario[]>([]);
  readonly loadingGlobal = signal(false);
  readonly errorGlobal = signal('');
  readonly globalBuscado = signal(false);

  readonly downloadingUsuario = signal(false);
  readonly downloadingGlobal = signal(false);

  constructor(private readonly api: ProductividadApiService) {}

  ngOnInit(): void {
    this.cargarUsuarios();
  }

  cargarUsuarios(): void {
    this.loadingUsuarios.set(true); this.errorUsuarios.set('');
    this.api.getUsuarios().subscribe({
      next: (res) => { this.usuarios.set(res?.data || []); },
      error: (err) => this.errorUsuarios.set(err?.error?.sql_error || err?.message || 'Error consultando usuarios'),
      complete: () => this.loadingUsuarios.set(false)
    });
  }

  selectUsuario(u: ProdUsuario): void {
    this.usuarioSel.set(String(u.usuario_id));
    this.cargarCargas(1);
  }

  cargarCargas(p = 1): void {
    const uid = this.usuarioSel(); if (!uid) return;
    this.loadingCargas.set(true); this.errorCargas.set(''); this.page.set(p);
    this.api.getCargas(uid, {
      desde: this.desde || undefined,
      hasta: this.hasta || undefined,
      voucher: this.voucherUsuario || undefined,
      page: p,
      per_page: 50
    }).subscribe({
      next: (res) => {
        this.cargas.set(res?.data || []);
        this.pages.set(res?.meta?.pages || 1);
        this.page.set(res?.meta?.page || p);
      },
      error: (err) => this.errorCargas.set(err?.error?.sql_error || err?.message || 'Error consultando cargas'),
      complete: () => this.loadingCargas.set(false)
    });
  }

  async buscarVoucherGlobal(): Promise<void> {
    const term = (this.voucherGlobal || '').trim();
    this.globalBuscado.set(false);
    this.errorGlobal.set('');
    this.resultadosGlobal.set([]);

    if (!term) {
      this.errorGlobal.set('Ingresa un voucher para buscar.');
      return;
    }
    if (!this.usuarios().length) {
      this.errorGlobal.set('Primero carga la lista de usuarios con el boton Refrescar.');
      return;
    }

    this.loadingGlobal.set(true);
    this.globalBuscado.set(true);

    const matches: ProdCargaConUsuario[] = [];
    let fallidos = 0;

    for (const u of this.usuarios()) {
      try {
        const rows = await firstValueFrom(this.api.getCargasAll(u.usuario_id, { voucher: term, per_page: 300 }));
        const filtered = this.filtrarPorVoucher(rows, term).map((r) => ({ ...r, usuario_id: u.usuario_id }));
        if (filtered.length) {
          matches.push(...filtered);
        }
      } catch (err) {
        fallidos++;
        console.error('Error consultando cargas de usuario', u.usuario_id, err);
      }
    }

    this.resultadosGlobal.set(matches);
    if (matches.length === 0) {
      const extra = fallidos ? ' Algunos usuarios no respondieron.' : '';
      this.errorGlobal.set('No se encontraron cargas para ese voucher.' + extra);
    }
    this.loadingGlobal.set(false);
  }

  async descargarCsvUsuario(): Promise<void> {
    const uid = this.usuarioSel();
    if (!uid) {
      this.errorCargas.set('Selecciona un usuario primero.');
      return;
    }
    this.downloadingUsuario.set(true);
    try {
      const rows = await firstValueFrom(this.api.getCargasAll(uid, {
        desde: this.desde || undefined,
        hasta: this.hasta || undefined,
        voucher: this.voucherUsuario || undefined,
        per_page: 300
      }));
      if (!rows.length) {
        this.errorCargas.set('No hay datos para exportar con los filtros actuales.');
        return;
      }
      this.exportarCsv(rows.map((r) => ({ ...r, usuario_id: uid })), this.buildCsvName('usuario', uid), false);
    } catch (err) {
      this.errorCargas.set('No se pudo generar el CSV del usuario.');
    } finally {
      this.downloadingUsuario.set(false);
    }
  }

  async descargarCsvCompleto(): Promise<void> {
    this.errorGlobal.set('');
    if (!this.usuarios().length) {
      this.errorGlobal.set('Primero carga la lista de usuarios con el boton Refrescar.');
      return;
    }

    this.downloadingGlobal.set(true);
    const rows: ProdCargaConUsuario[] = [];
    let fallidos = 0;

    for (const u of this.usuarios()) {
      try {
        const data = await firstValueFrom(this.api.getCargasAll(u.usuario_id, { per_page: 300 }));
        rows.push(...data.map((r) => ({ ...r, usuario_id: u.usuario_id })));
      } catch (err) {
        fallidos++;
      }
    }

    if (!rows.length) {
      const extra = fallidos ? ' Algunos usuarios no respondieron.' : '';
      this.errorGlobal.set('No hay datos de productividad para exportar.' + extra);
    } else {
      this.exportarCsv(rows, this.buildCsvName('completo'), true);
    }
    this.downloadingGlobal.set(false);
  }

  private filtrarPorVoucher(rows: ProdCargaItem[], voucher: string): ProdCargaItem[] {
    const needle = this.normalizarVoucher(voucher);
    return rows.filter((r) => this.normalizarVoucher(r.voucher).includes(needle));
  }

  private normalizarVoucher(v: string | null | undefined): string {
    return (v ?? '').toString().replace(/\s+/g, '').toLowerCase();
  }

  private exportarCsv(rows: ProdCargaConUsuario[], filename: string, includeUsuario = true): void {
    const headers = includeUsuario
      ? ['usuario', 'fecha', 'voucher', 'lineas', 'valor_factura', 'valor_pagado', 'eventos', 'evento_ids']
      : ['fecha', 'voucher', 'lineas', 'valor_factura', 'valor_pagado', 'eventos', 'evento_ids'];

    const lines = [headers.join(',')];
    for (const r of rows) {
      const eventoIds = Array.isArray(r.evento_ids) ? r.evento_ids.join('|') : '';
      const cols = includeUsuario
        ? [r.usuario_id, r.fecha, r.voucher, r.lineas, r.valor_factura, r.valor_pagado, r.eventos, eventoIds]
        : [r.fecha, r.voucher, r.lineas, r.valor_factura, r.valor_pagado, r.eventos, eventoIds];
      lines.push(cols.map((c) => this.csvValue(c)).join(','));
    }

    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  private csvValue(value: unknown): string {
    const str = value === null || value === undefined ? '' : String(value);
    return /[",\n]/.test(str) ? `"${str.replace(/"/g, '""')}"` : str;
  }

  private buildCsvName(scope: string, suffix?: string): string {
    const ts = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '');
    return `productividad_${scope}${suffix ? '_' + suffix : ''}_${ts}.csv`;
  }
}
