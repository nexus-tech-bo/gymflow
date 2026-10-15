import { HttpErrorResponse, HttpInterceptorFn, HttpStatusCode } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';

/** API classes use relative paths ('api/members'); this prefixes the backend base URL. */
export const apiUrlInterceptor: HttpInterceptorFn = (req, next) => {
  if (/^(https?:)?\/\//.test(req.url) || req.url.startsWith('/')) return next(req);
  return next(req.clone({ url: environment.apiUrl + req.url }));
};

/** Auth and public endpoints never carry the token nor trigger the expired-session redirect. */
const isPublic = (url: string) => /\/api\/(auth|public)\//.test(url);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (isPublic(req.url)) return next(req);
  const auth = inject(AuthService);
  const token = auth.token();
  const authReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authReq).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === HttpStatusCode.Unauthorized)
        auth.expire();
      return throwError(() => error);
    }),
  );
};
