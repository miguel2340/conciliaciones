import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { EMPTY, Observable, expand, map, reduce } from 'rxjs';

export interface ProdUsuario {
  usuario_id: string;
  total_eventos: number;
  fecha_ultima_carga: string;
  detalle_url?: string;
}

export interface ProdCargaItem {
  fecha: string;
  voucher: string;
  lineas: number;
  valor_pagado: number;
  valor_factura: number;
  eventos: number;
  evento_ids: string[];
}

export interface ProdCargasResponse {
  meta: { usuario_id: string; page: number; per_page: number; total: number; pages: number; filter: { desde?: string; hasta?: string; voucher?: string; } };
  data: ProdCargaItem[];
}

export interface ProdCargasFilters {
  desde?: string;
  hasta?: string;
  voucher?: string;
  page?: number;
  per_page?: number;
}

@Injectable({ providedIn: 'root' })
export class ProductividadApiService {
  // Base publica (se puede sobrescribir con localStorage key 'productividad_api_base')
  private readonly defaultBase = `http://200.116.57.140:8080/aplicacion/api/usurios_api.php`;

  constructor(private readonly http: HttpClient) {}

  get base(): string {
    const stored = localStorage.getItem('productividad_api_base');
    return stored && stored.trim() ? stored.trim() : this.defaultBase;
  }

  setBase(url: string) { localStorage.setItem('productividad_api_base', url || ''); }

  getUsuarios(): Observable<{ data: ProdUsuario[] }> {
    // API publica: no requiere Authorization; el interceptor de la app no aplica si el host no es el mismo
    return this.http.get<{ data: ProdUsuario[] }>(`${this.base}/api/public/usuarios`);
  }

  getCargas(usuarioId: string, opts?: ProdCargasFilters): Observable<ProdCargasResponse> {
    let params = new HttpParams();
    if (opts?.desde) params = params.set('desde', opts.desde);
    if (opts?.hasta) params = params.set('hasta', opts.hasta);
    if (opts?.voucher) params = params.set('voucher', opts.voucher);
    if (opts?.page) params = params.set('page', String(opts.page));
    if (opts?.per_page) params = params.set('per_page', String(opts.per_page));
    return this.http
      .get<any>(`${this.base}/api/public/usuarios/${encodeURIComponent(usuarioId)}/cargas`, { params })
      .pipe(map((raw: any) => this.normalizeCargas(raw)));
  }

  getCargasAll(usuarioId: string, opts?: ProdCargasFilters): Observable<ProdCargaItem[]> {
    const perPage = Math.max(10, Math.min(opts?.per_page ?? 200, 1000));
    const baseFilters = { ...opts, page: 1, per_page: perPage };
    return this.getCargas(usuarioId, baseFilters).pipe(
      expand((res) => {
        const current = res?.meta?.page ?? 1;
        const totalPages = res?.meta?.pages ?? 1;
        const next = current + 1;
        return next <= totalPages ? this.getCargas(usuarioId, { ...baseFilters, page: next }) : EMPTY;
      }),
      map((res) => res?.data || []),
      reduce((acc, curr) => acc.concat(curr), [] as ProdCargaItem[])
    );
  }

  private normalizeCargas(raw: any): ProdCargasResponse {
    // Admite variantes en espanol: datos/meta con campos acentuados
    const meta = raw?.meta || raw?.Meta || {};
    const dataArr = raw?.data || raw?.datos || [];

    const page = meta.page ?? meta['p\u00e1gina'] ?? meta['pagina'] ?? 1;
    const per = meta.per_page ?? meta['por_p\u00e1gina'] ?? meta['por_pagina'] ?? 50;
    const total = meta.total ?? 0;
    const pages = meta.pages ?? meta['p\u00e1ginas'] ?? meta['paginas'] ?? 1;
    const filtro = meta.filter || meta.filtro || {};

    const normData: ProdCargaItem[] = (Array.isArray(dataArr) ? dataArr : []).map((r: any) => ({
      fecha: r.fecha ?? r.Fecha ?? '',
      voucher: r.voucher ?? r.vale ?? r.bono ?? 'No se tiene voucher',
      lineas: Number(r.lineas ?? r['l\u00edneas'] ?? 0),
      valor_pagado: Number(r.valor_pagado ?? 0),
      valor_factura: Number(r.valor_factura ?? 0),
      eventos: Number(r.eventos ?? 0),
      evento_ids: r.evento_ids || r['identificadores de eventos'] || []
    }));

    return {
      meta: {
        usuario_id: meta.usuario_id ?? '',
        page: Number(page) || 1,
        per_page: Number(per) || 50,
        total: Number(total) || 0,
        pages: Number(pages) || 1,
        filter: { desde: filtro.desde ?? undefined, hasta: filtro.hasta ?? undefined, voucher: filtro.voucher ?? undefined }
      },
      data: normData
    } as any;
  }
}
