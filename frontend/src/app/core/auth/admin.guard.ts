import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  // Si aún no tenemos los roles (ej. al refrescar), intenta cargarlos una vez
  try { if (!auth.roles()?.length && auth.token()) auth.fetchMe(); } catch {}
  if (auth.isAdmin()) { return true; }
  router.navigate(['/app']);
  return false;
};

