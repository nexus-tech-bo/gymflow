import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { ReportsApi } from '../../core/api/reports.api';
import { apiErrorMessage } from '../../core/http/api-error';
import { IncomeDayResponse } from '../../core/models/api.models';
import { rangeError, todayLima, weekdayShort } from '../../core/utils/format.util';
import { LocalDatePipe, MoneyPipe } from '../../shared/pipes/format.pipes';
import { DatePickerComponent } from '../../shared/ui/date-picker.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

@Component({
  selector: 'app-income-report-page',
  imports: [
    PageHeaderComponent,
    EmptyStateComponent,
    DatePickerComponent,
    MoneyPipe,
    LocalDatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './income-report-page.component.html',
})
export class IncomeReportPageComponent {
  private readonly api = inject(ReportsApi);
  private readonly router = inject(Router);
  protected readonly today = todayLima();

  /** ?from=&to= (defaults: first day of the month → today). */
  readonly from = input<string>();
  readonly to = input<string>();
  private readonly range = computed(() => ({
    from: this.from() || this.today.slice(0, 8) + '01',
    to: this.to() || this.today,
  }));

  // Editable copies for the filter bar; reset when the URL changes.
  protected readonly draftFrom = linkedSignal(() => this.range().from);
  protected readonly draftTo = linkedSignal(() => this.range().to);
  protected readonly draftError = computed(() => rangeError(this.draftFrom(), this.draftTo()));

  private readonly report = rxResource({
    params: () => (rangeError(this.range().from, this.range().to) ? undefined : this.range()),
    stream: ({ params }) => this.api.income(params.from, params.to),
  });
  protected readonly data = computed(() =>
    this.report.hasValue() ? this.report.value() : undefined,
  );
  protected readonly days = computed(() => this.data()?.days ?? ([] as IncomeDayResponse[]));
  protected readonly isLoading = this.report.isLoading;
  protected readonly errorMessage = computed(() =>
    this.report.error()
      ? apiErrorMessage(this.report.error(), 'No se pudo cargar el reporte.')
      : '',
  );
  protected readonly reload = () => this.report.reload();
  protected readonly weekday = weekdayShort;

  protected search(): void {
    if (this.draftError()) return;
    void this.router.navigate([], { queryParams: { from: this.draftFrom(), to: this.draftTo() } });
  }
}
