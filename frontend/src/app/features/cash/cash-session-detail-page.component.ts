import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CashApi } from '../../core/api/cash.api';
import { apiErrorMessage, isNotFound } from '../../core/http/api-error';
import { CASH_STATUS, differenceChip } from '../../core/utils/labels.util';
import { LimaDateTimePipe, MoneyPipe } from '../../shared/pipes/format.pipes';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { PaymentsTableComponent } from './payments-table.component';

@Component({
  selector: 'app-cash-session-detail-page',
  imports: [
    RouterLink,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
    UiIconComponent,
    PaymentsTableComponent,
    MoneyPipe,
    LimaDateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cash-session-detail-page.component.html',
})
export class CashSessionDetailPageComponent {
  private readonly api = inject(CashApi);
  readonly id = input.required({ transform: numberAttribute });
  /** '/x/abc' → not found without hitting the API. */
  private readonly validId = computed(() => Number.isInteger(this.id()) && this.id() > 0);

  private readonly detail = rxResource({
    params: () => (this.validId() ? this.id() : undefined),
    stream: ({ params }) => this.api.session(params),
  });
  protected readonly data = computed(() =>
    this.detail.hasValue() ? this.detail.value() : undefined,
  );
  protected readonly notFound = computed(() => !this.validId() || isNotFound(this.detail.error()));
  protected readonly errorMessage = computed(() =>
    this.detail.error() ? apiErrorMessage(this.detail.error(), 'No se pudo cargar la caja.') : '',
  );
  protected readonly reload = () => this.detail.reload();
  protected readonly statusChip = CASH_STATUS;
  protected readonly differenceChip = differenceChip;
}
