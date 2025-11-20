import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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
  private readonly baseUrl = 'http://localhost:8080/api/v1/radicacion';

  constructor(private readonly http: HttpClient) {}

  buscarPorNit(nit: string, page = 0, size = 100): Observable<RadicacionPageResponse> {
    return this.http.get<RadicacionPageResponse>(this.baseUrl, {
      params: {
        nit,
        page,
        size
      }
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
}
