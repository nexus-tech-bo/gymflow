import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { toHttpParams } from '../http/http-params';
import {
  CheckInEntryResponse,
  CheckInQuery,
  CheckInRequest,
  CheckInResponse,
  Page,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class CheckInsApi {
  private readonly http = inject(HttpClient);

  /** Always 200, denied entries included (see `result`). */
  checkIn(request: CheckInRequest): Observable<CheckInResponse> {
    return this.http.post<CheckInResponse>('api/check-ins', request);
  }

  list(query: CheckInQuery): Observable<Page<CheckInEntryResponse>> {
    return this.http.get<Page<CheckInEntryResponse>>('api/check-ins', {
      params: toHttpParams(query),
    });
  }
}
