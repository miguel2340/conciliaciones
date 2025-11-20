import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse } from '../models/auth.models';

const ACCESS_TOKEN_KEY = 'access_token';
const REFRESH_TOKEN_KEY = 'refresh_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiHost = (typeof window !== 'undefined' && window.location && window.location.hostname)
    ? window.location.hostname
    : 'localhost';
  private readonly apiUrl = `http://${this.apiHost}:8080/api/v1/auth`;
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY));
  private readonly rolesSignal = signal<string[]>([]);

  readonly token = computed(() => this.tokenSignal());
  readonly isAuthenticated = computed(() => Boolean(this.token()));
  readonly roles = computed(() => this.rolesSignal());
  readonly isAdmin = computed(() => (this.rolesSignal() || []).includes('ADMIN'));

  constructor(private readonly http: HttpClient) {}
  // Al construir, si ya existe token, intenta cargar roles para controlar vistas protegidas
  // (esto cubre el caso de refresco del navegador)
  
  // Nota: los signals se inicializan arriba; aquí forzamos fetchMe si ya hay token persistido
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  private _init = (() => { try { if (this.token()) { this.fetchMe(); } } catch {} return true; })();

  login(payload: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.apiUrl + '/login', payload).pipe(
      tap((response) => this.persistSession(response)),
      tap(() => this.fetchMe())
    );
  }

  logout(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.tokenSignal.set(null);
  }

  private persistSession(response: LoginResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
    if (response.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    }

    this.tokenSignal.set(response.accessToken);
    // Cargar roles después
    this.fetchMe();
  }

  fetchMe(): void {
    this.http.get<{email:string; fullName:string; roles:string[]}>(this.apiUrl + '/me')
      .subscribe({ next: (me) => this.rolesSignal.set(me?.roles || []), error: () => this.rolesSignal.set([]) });
  }
}
