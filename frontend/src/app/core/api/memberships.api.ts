import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MembershipResponse, MembershipSaleRequest } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class MembershipsApi {
  private readonly http = inject(HttpClient);

  /** Sells/renews. Requires an open cash session; the `price` override is OWNER/ADMIN only. */
  sell(memberId: number, request: MembershipSaleRequest): Observable<MembershipResponse> {
    return this.http.post<MembershipResponse>(`api/members/${memberId}/memberships`, request);
  }

  /** OWNER, ADMIN. */
  freeze(id: number): Observable<MembershipResponse> {
    return this.http.post<MembershipResponse>(`api/memberships/${id}/freeze`, null);
  }

  /** OWNER, ADMIN. */
  unfreeze(id: number): Observable<MembershipResponse> {
    return this.http.post<MembershipResponse>(`api/memberships/${id}/unfreeze`, null);
  }

  /** OWNER, ADMIN. Does not void the payment. */
  cancel(id: number): Observable<MembershipResponse> {
    return this.http.post<MembershipResponse>(`api/memberships/${id}/cancel`, null);
  }
}
