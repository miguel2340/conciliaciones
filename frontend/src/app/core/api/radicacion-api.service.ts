import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, tap } from 'rxjs';

export interface RadicacionRegistro {
  id: number;
  modalidadPago: string | null;
  nit: string | null;
  nomPrestador: string | null;
  tipoRed: string | null;
  departamento: string | null;
  municipio: string | null;
  prefijo: string | null;
  factura: string | null;
  prefijoFactura: string | null;
  mesRadicacion: string | null;
  estadoAplicacion: string | null;
  fechaRadicacion: string | null;
  rangoDias: number | null;
  fechaFactura: string | null;
  valorFactura: number | null;
  valorIva: number | null;
  notaCredito: number | null;
  valorGlosaInicial: number | null;
  valorNoGlosadoInicial: number | null;
  valorAceptadoPrimeraRespuesta: number | null;
  valorLevantadoPrimeraRespuesta: number | null;
  valorRatificadoPrimeraRespuesta: number | null;
  valorAceptadoSegundaRespuesta: number | null;
  valorLevantadoSegundaRespuesta: number | null;
  valorRatificadoSegundaRespuesta: number | null;
  valorAceptadoConciliacion: number | null;
  valorLevantadoConciliacion: number | null;
  valorRatificadoConciliacion: number | null;
  valorActualAceptado: number | null;
  valorActualReconocido: number | null;
  valorFinalRatificado: number | null;
  valorPagado: number | null;
  porcentajePago: number | null;
  estado: string | null;
  voucher: string | null;
  fechaPago: string | null;
  idFomag: string | null;
  nitNorm: string | null;
  voucherNorm: string | null;
}

export interface RadicacionPageResponse {
  content: RadicacionRegistro[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export interface RadicacionFechaExportPayload {
  fechaInicio?: string;
  fechaFin?: string;
  estadosAplicacion?: string[];
  nits?: string[];
}

@Injectable({ providedIn: 'root' })
export class RadicacionApiService {
  // Resolve API host from current page host (works en equipos remotos)
  private readonly apiHost = (typeof window !== 'undefined' && window.location && window.location.hostname)
    ? window.location.hostname
    : 'localhost';
  private readonly apiBase = `http://${this.apiHost}:8080`;

  private readonly baseUrl = this.apiBase + '/api/v1/radicacion';
  private readonly listaBaseUrl = this.apiBase + '/api/v1/lista-tareas';
  private readonly METRICS_TTL_MS = 5 * 60 * 1000; // 5 minutos
  private readonly metricsCache = new Map<string, { ts: number; data: any }>();

  constructor(private readonly http: HttpClient) {}

  buscarPorNit(nit: string, page = 0, size = 100): Observable<RadicacionPageResponse> {
    return this.http.get<RadicacionPageResponse>(this.baseUrl, {
      params: { nit, page, size }
    });
  }

  descargarTxtPorNit(nit: string): Observable<Blob> {
    return this.http.get(this.baseUrl + '/export', {
      params: { nit },
      responseType: 'blob'
    });
  }

  descargarTxtPorNits(nits: string[]): Observable<Blob> {
    return this.http.post(this.baseUrl + '/export/multiple', { nits }, { responseType: 'blob' });
  }

  descargarTxtPorFecha(payload: RadicacionFechaExportPayload): Observable<Blob> {
    return this.http.post(this.baseUrl + '/export/fecha', payload ?? {}, { responseType: 'blob' });
  }

  getEstadosAplicacion(): Observable<string[]> {
    return this.http.get<string[]>(this.baseUrl + '/opciones/estados-aplicacion');
  }

  descargarReporteVoucher(): Observable<Blob> {
    return this.http.get(this.baseUrl.replace('/radicacion','/reportes') + '/voucher', { responseType: 'blob' });
  }

  descargarReporteGerencial(): Observable<Blob> {
    return this.http.get(this.baseUrl.replace('/radicacion','/reportes') + '/gerencial', { responseType: 'blob' });
  }

  // Conciliación
  descargarConciliacionPorNit(nit: string): Observable<Blob> {
    const base = this.baseUrl.replace('/radicacion','/conciliacion');
    return this.http.get(base + '/export', { params: { nit }, responseType: 'blob' });
  }

  precheckConciliacionPorNit(nit: string): Observable<{ nit: string; nitNorm: string; matches: string[]; found: boolean }>{
    const base = this.baseUrl.replace('/radicacion','/conciliacion');
    return this.http.get<{ nit: string; nitNorm: string; matches: string[]; found: boolean }>(base + '/precheck', { params: { nit } });
  }

  // Lista de Tareas (métricas, datos y descarga CSV)
  getListaMetrics(type: string, fresh = false): Observable<any> {
    if (!fresh) {
      const cached = this.metricsCache.get(type);
      if (cached && Date.now() - cached.ts < this.METRICS_TTL_MS) {
        return of({ metrics: cached.data });
      }
    }
    return this.http
      .get<any>(`${this.listaBaseUrl}/metrics/${encodeURIComponent(type)}`, { params: { fresh } as any })
      .pipe(tap((res: any) => this.metricsCache.set(type, { ts: Date.now(), data: res?.metrics ?? null })));
  }

  getListaData(type: string, limit = 200): Observable<any> {
    return this.http.get(`${this.listaBaseUrl}/data/${encodeURIComponent(type)}`, { params: { limit } as any });
  }

  downloadListaCsv(type: string): Observable<Blob> {
    return this.http.get(`${this.listaBaseUrl}/download`, { params: { type }, responseType: 'blob' });
  }

  refreshListaMetrics(types?: string[]): Observable<any> {
    const params: any = {};
    if (types && types.length) { params.types = types.join(','); }
    return this.http.get(`${this.listaBaseUrl}/metrics/refresh`, { params });
  }
}
