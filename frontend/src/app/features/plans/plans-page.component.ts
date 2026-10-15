import { Dialog } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { PlansApi } from '../../core/api/plans.api';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage } from '../../core/http/api-error';
import { PlanResponse } from '../../core/models/api.models';
import { activeChip } from '../../core/utils/labels.util';
import { MoneyPipe } from '../../shared/pipes/format.pipes';
import { NotificationService } from '../../core/services/notification.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { PlanFormDialogComponent } from './plan-form-dialog.component';

@Component({
  selector: 'app-plans-page',
  imports: [
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
    UiIconComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './plans-page.component.html',
})
export class PlansPageComponent {
  private readonly api = inject(PlansApi);
  private readonly dialog = inject(Dialog);
  private readonly notifications = inject(NotificationService);
  /** RECEPTIONIST: read-only. */
  protected readonly canEdit = inject(AuthService).isManager;

  protected readonly includeInactive = signal(false);
  private readonly plans = rxResource({
    params: () => this.includeInactive() && this.canEdit(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly rows = computed(() =>
    this.plans.hasValue() ? this.plans.value() : ([] as PlanResponse[]),
  );
  protected readonly isLoading = this.plans.isLoading;
  protected readonly errorMessage = computed(() =>
    this.plans.error() ? apiErrorMessage(this.plans.error(), 'No se pudo cargar los planes.') : '',
  );
  protected readonly reload = () => this.plans.reload();
  protected readonly activeChip = activeChip;

  protected open(plan: PlanResponse | null = null): void {
    this.dialog
      .open<PlanResponse, PlanResponse | null>(PlanFormDialogComponent, { data: plan })
      .closed.pipe(filter(Boolean))
      .subscribe(() => {
        this.notifications.success('Plan guardado');
        this.reload();
      });
  }
}
