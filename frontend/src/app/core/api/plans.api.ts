import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PlanRequest, PlanResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class PlansApi {
  private readonly http = inject(HttpClient);

  list(includeInactive = false): Observable<PlanResponse[]> {
    return this.http.get<PlanResponse[]>('api/plans', { params: { includeInactive } });
  }

  /** OWNER, ADMIN. */
  create(request: PlanRequest): Observable<PlanResponse> {
    return this.http.post<PlanResponse>('api/plans', request);
  }

  /** OWNER, ADMIN. */
  update(id: number, request: PlanRequest): Observable<PlanResponse> {
    return this.http.put<PlanResponse>(`api/plans/${id}`, request);
  }
}
