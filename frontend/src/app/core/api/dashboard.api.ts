import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DashboardSummaryResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  summary(): Observable<DashboardSummaryResponse> {
    return this.http.get<DashboardSummaryResponse>('api/dashboard/summary');
  }
}
