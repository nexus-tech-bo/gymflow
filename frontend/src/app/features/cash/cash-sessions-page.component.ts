import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CashApi } from '../../core/api/cash.api';
import { apiErrorMessage } from '../../core/http/api-error';
import { CashSessionResponse } from '../../core/models/api.models';
import { CASH_STATUS, differenceChip } from '../../core/utils/labels.util';
import { LimaDateTimePipe, MoneyPipe } from '../../shared/pipes/format.pipes';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';

@Component({
  selector: 'app-cash-sessions-page',
  imports: [
    RouterLink,
    PageHeaderComponent,
    EmptyStateComponent,
    PaginationComponent,
    StatusChipComponent,
    UiIconComponent,
    MoneyPipe,
    LimaDateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cash-sessions-page.component.html',
})
export class CashSessionsPageComponent {
  private readonly api = inject(CashApi);

  protected readonly page = signal(0);
  private readonly sessions = rxResource({
    params: () => this.page(),
    stream: ({ params }) => this.api.sessions({ page: params, size: 20 }),
  });
  protected readonly result = computed(() =>
    this.sessions.hasValue() ? this.sessions.value() : undefined,
  );
  protected readonly rows = computed(() => this.result()?.items ?? ([] as CashSessionResponse[]));
  protected readonly isLoading = this.sessions.isLoading;
  protected readonly errorMessage = computed(() =>
    this.sessions.error()
      ? apiErrorMessage(this.sessions.error(), 'No se pudo cargar el historial.')
      : '',
  );
  protected readonly reload = () => this.sessions.reload();
  protected readonly statusChip = CASH_STATUS;
  protected readonly differenceChip = differenceChip;
}
