import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { NotificationService } from '../services/notification.service';
import { Role } from '../models/api.models';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return (
    auth.isLoggedIn() ||
    inject(Router).createUrlTree(['/login'], { queryParams: { redirect: state.url } })
  );
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return !auth.isLoggedIn() || inject(Router).parseUrl(auth.landingUrl());
};

/** Mirrors backend authorization; hidden nav items are not enough. */
export const roleGuard =
  (...roles: Role[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    if (auth.hasRole(roles)) return true;
    inject(NotificationService).error('No tienes permiso para ver esa sección.');
    return inject(Router).parseUrl(auth.landingUrl());
  };

export const landingRedirect = () => inject(AuthService).landingUrl();
