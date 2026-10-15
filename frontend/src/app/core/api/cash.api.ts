import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { toHttpParams } from '../http/http-params';
import {
  CashCloseRequest,
  CashCurrentResponse,
  CashOpenRequest,
  CashSessionDetailResponse,
  CashSessionResponse,
  Page,
  PageQuery,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class CashApi {
  private readonly http = inject(HttpClient);

  current(): Observable<CashCurrentResponse> {
    return this.http.get<CashCurrentResponse>('api/cash/current');
  }

  open(request: CashOpenRequest): Observable<CashSessionDetailResponse> {
    return this.http.post<CashSessionDetailResponse>('api/cash/open', request);
  }

  /** Blind close for RECEPTIONIST: expectedCash/totals/difference come back null. */
  close(request: CashCloseRequest): Observable<CashSessionDetailResponse> {
    return this.http.post<CashSessionDetailResponse>('api/cash/close', request);
  }

  /** OWNER, ADMIN. */
  sessions(query: PageQuery): Observable<Page<CashSessionResponse>> {
    return this.http.get<Page<CashSessionResponse>>('api/cash/sessions', {
      params: toHttpParams(query),
    });
  }

  /** OWNER, ADMIN. */
  session(id: number): Observable<CashSessionDetailResponse> {
    return this.http.get<CashSessionDetailResponse>(`api/cash/sessions/${id}`);
  }
}
