import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

const apiBase = (() => {
  try {
    if (typeof window !== 'undefined' && window.location) {
      return `${window.location.protocol}//${window.location.hostname}:8080`;
    }
  } catch {}
  return 'http://localhost:8080';
})();

const isApiUrl = (url: string) => url.startsWith(apiBase);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiUrl(req.url)) {
    return next(req);
  }

  const token = localStorage.getItem('access_token');
  const authService = inject(AuthService);
  const router = inject(Router);

  const authorizedRequest = token
    ? req.clone({ setHeaders: { Authorization: 'Bearer ' + token } })
    : req;

  return next(authorizedRequest).pipe(
    catchError((error) => {
      if (error.status === 401 || error.status === 403) {
        authService.logout();
        router.navigate(['/auth/login']);
      }
      return throwError(() => error);
    })
  );
};

