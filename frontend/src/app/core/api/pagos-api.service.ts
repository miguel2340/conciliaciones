import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PagosApiService {
  private readonly apiHost = (typeof window !== 'undefined' && window.location && window.location.hostname)
    ? window.location.hostname
    : 'localhost';
  private readonly apiBase = `http://${this.apiHost}:8080`;
  private readonly baseUrl = this.apiBase + '/api/v1/pagos-api';

  constructor(private readonly http: HttpClient) {}

  getResumenDia(fecha: string): Observable<any> {
    return this.http.get(`${this.baseUrl}/dia`, {
      params: { fecha },
      headers: { Authorization: 'Bearer ' + (localStorage.getItem('access_token') || '') }
    });
  }

  aprobarDia(fecha: string, observacion?: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/aprobar`, { fecha, observacion }, {
      headers: { Authorization: 'Bearer ' + (localStorage.getItem('access_token') || '') }
    });
  }

  aprobarVoucher(fecha: string, voucher: string, observacion?: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/aprobar`, { fecha, voucher, observacion }, {
      headers: { Authorization: 'Bearer ' + (localStorage.getItem('access_token') || '') }
    });
  }
}

