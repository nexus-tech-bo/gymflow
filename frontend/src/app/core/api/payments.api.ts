import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaymentResponse, PaymentVoidRequest } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class PaymentsApi {
  private readonly http = inject(HttpClient);

  /** OWNER, ADMIN. Only payments of the open session; also cancels its membership. */
  void(id: number, request: PaymentVoidRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`api/payments/${id}/void`, request);
  }
}
