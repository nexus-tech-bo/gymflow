import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { IncomeReportResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ReportsApi {
  private readonly http = inject(HttpClient);

  /** OWNER, ADMIN. Inclusive range, max 1 year. */
  income(from: string, to: string): Observable<IncomeReportResponse> {
    return this.http.get<IncomeReportResponse>('api/reports/income', { params: { from, to } });
  }
}
