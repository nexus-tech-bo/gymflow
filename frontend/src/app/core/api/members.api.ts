import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { toHttpParams } from '../http/http-params';
import {
  MemberCardResponse,
  MemberDetailResponse,
  MemberQrResponse,
  MemberRequest,
  MemberSearchQuery,
  MemberSummaryResponse,
  Page,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class MembersApi {
  private readonly http = inject(HttpClient);

  search(query: MemberSearchQuery): Observable<Page<MemberSummaryResponse>> {
    return this.http.get<Page<MemberSummaryResponse>>('api/members', {
      params: toHttpParams(query),
    });
  }

  get(id: number): Observable<MemberDetailResponse> {
    return this.http.get<MemberDetailResponse>(`api/members/${id}`);
  }

  /** 409 when the DNI already exists. */
  create(request: MemberRequest): Observable<MemberDetailResponse> {
    return this.http.post<MemberDetailResponse>('api/members', request);
  }

  update(id: number, request: MemberRequest): Observable<MemberDetailResponse> {
    return this.http.put<MemberDetailResponse>(`api/members/${id}`, request);
  }

  /** OWNER, ADMIN. */
  setActive(id: number, active: boolean): Observable<MemberDetailResponse> {
    return this.http.patch<MemberDetailResponse>(`api/members/${id}/active`, { active });
  }

  qr(id: number): Observable<MemberQrResponse> {
    return this.http.get<MemberQrResponse>(`api/members/${id}/qr`);
  }

  /** OWNER, ADMIN. Invalidates the previous QR and public card link. */
  rotateQr(id: number): Observable<MemberQrResponse> {
    return this.http.post<MemberQrResponse>(`api/members/${id}/qr/rotate`, null);
  }

  /** Public (no auth). The token is the member's qrToken. */
  publicCard(token: string): Observable<MemberCardResponse> {
    return this.http.get<MemberCardResponse>(`api/public/member-card/${encodeURIComponent(token)}`);
  }
}
