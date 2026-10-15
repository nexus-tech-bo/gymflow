import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { StaffRequest, StaffResponse, StaffUpdateRequest } from '../models/api.models';

/** OWNER, ADMIN. ADMIN may only create/manage RECEPTIONIST; nobody changes their own role or deactivates themselves. */
@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);

  list(): Observable<StaffResponse[]> {
    return this.http.get<StaffResponse[]>('api/staff');
  }

  /** 409 when the email is taken. */
  create(request: StaffRequest): Observable<StaffResponse> {
    return this.http.post<StaffResponse>('api/staff', request);
  }

  update(id: number, request: StaffUpdateRequest): Observable<StaffResponse> {
    return this.http.patch<StaffResponse>(`api/staff/${id}`, request);
  }
}
