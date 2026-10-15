import { HttpParams } from '@angular/common/http';

/** Builds HttpParams skipping null/undefined/'' so optional filters are simply omitted. */
export function toHttpParams(query: object = {}): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined || value === '') continue;
    params = params.set(key, String(value));
  }
  return params;
}
