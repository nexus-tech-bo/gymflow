import { Dialog } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { CashApi } from '../../core/api/cash.api';
import { DashboardApi } from '../../core/api/dashboard.api';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage } from '../../core/http/api-error';
import { ExpiringMembershipResponse, MemberDetailResponse } from '../../core/models/api.models';
import { formatInstant } from '../../core/utils/format.util';
import { CASH_STATUS, Chip } from '../../core/utils/labels.util';
import { LimaDateTimePipe, LocalDatePipe } from '../../shared/pipes/format.pipes';
import { NotificationService } from '../../core/services/notification.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { MemberFormData, MemberFormDialogComponent } from '../members/member-form-dialog.component';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    RouterLink,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
    UiIconComponent,
    LimaDateTimePipe,
    LocalDatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-page.component.html',
})
export class DashboardPageComponent {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly cashApi = inject(CashApi);
  private readonly dialog = inject(Dialog);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  protected readonly user = inject(AuthService).user;

  protected readonly greeting = computed(
    () => `Hola, ${this.user()?.fullName.split(' ')[0] ?? ''}`,
  );
  protected readonly subtitle = computed(
    () => `${this.user()?.gymName ?? ''} · ${formatInstant(new Date(), 'longDate')}`,
  );

  private readonly summaryResource = rxResource({ stream: () => this.dashboardApi.summary() });
  private readonly cashResource = rxResource({ stream: () => this.cashApi.current() });

  protected readonly summary = computed(() =>
    this.summaryResource.hasValue() ? this.summaryResource.value() : undefined,
  );
  protected readonly cash = computed(() =>
    this.cashResource.hasValue() ? this.cashResource.value().current : undefined,
  );
  protected readonly cashLoaded = computed(() => this.cashResource.hasValue());
  protected readonly cashFailed = computed(() => !!this.cashResource.error());
  protected readonly expiring = computed(
    () => this.summary()?.expiringSoon ?? ([] as ExpiringMembershipResponse[]),
  );
  protected readonly isLoading = computed(
    () => this.summaryResource.isLoading() && !this.summary(),
  );
  protected readonly errorMessage = computed(() => {
    const error = this.summaryResource.error();
    return error ? apiErrorMessage(error, 'No se pudo cargar el resumen.') : '';
  });
  protected readonly openChip = CASH_STATUS.OPEN;
  protected readonly closedChip = CASH_STATUS.CLOSED;

  protected daysChip(days: number): Chip {
    return { label: String(days), tone: days <= 3 ? 'warn' : 'neutral' };
  }

  protected reload(): void {
    this.summaryResource.reload();
    this.cashResource.reload();
  }

  protected newMember(): void {
    this.dialog
      .open<MemberDetailResponse, MemberFormData>(MemberFormDialogComponent, { data: {} })
      .closed.pipe(filter(Boolean))
      .subscribe((member) => {
        this.notifications.success('Socio creado');
        void this.router.navigate(['/socios', member.id]);
      });
  }
}
