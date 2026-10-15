import { Dialog } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Observable, filter } from 'rxjs';
import { StaffApi } from '../../core/api/staff.api';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage } from '../../core/http/api-error';
import { StaffResponse } from '../../core/models/api.models';
import { initials } from '../../core/utils/format.util';
import { ROLE, activeChip } from '../../core/utils/labels.util';
import { LimaDateTimePipe } from '../../shared/pipes/format.pipes';
import { ConfirmService } from '../../shared/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { StaffFormDialogComponent } from './staff-form-dialog.component';
import { canManage } from './staff-rules';

@Component({
  selector: 'app-staff-page',
  imports: [
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
    UiIconComponent,
    LimaDateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff-page.component.html',
})
export class StaffPageComponent {
  private readonly api = inject(StaffApi);
  private readonly dialog = inject(Dialog);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);
  protected readonly me = inject(AuthService).user;

  private readonly staff = rxResource({ stream: () => this.api.list() });
  protected readonly rows = computed(() =>
    this.staff.hasValue() ? this.staff.value() : ([] as StaffResponse[]),
  );
  protected readonly isLoading = this.staff.isLoading;
  protected readonly errorMessage = computed(() =>
    this.staff.error() ? apiErrorMessage(this.staff.error(), 'No se pudo cargar el staff.') : '',
  );
  protected readonly reload = () => this.staff.reload();
  protected readonly roleChip = ROLE;
  protected readonly activeChip = activeChip;
  protected readonly initials = initials;
  protected readonly youChip = { label: 'Tú', tone: 'info' } as const;

  protected canManage(row: StaffResponse): boolean {
    return canManage(this.me(), row);
  }

  protected open(row: StaffResponse | null = null): void {
    this.dialog
      .open<StaffResponse, StaffResponse | null>(StaffFormDialogComponent, { data: row })
      .closed.pipe(filter(Boolean))
      .subscribe(() => {
        this.notifications.success(row ? 'Usuario actualizado' : 'Usuario creado');
        this.reload();
      });
  }

  protected toggleActive(row: StaffResponse): void {
    if (!row.active) return this.run(this.api.update(row.id, { active: true }), 'Usuario activado');
    this.confirm
      .ask({
        title: 'Desactivar usuario',
        message: `¿Desactivar a ${row.fullName}? Ya no podrá iniciar sesión.`,
        confirmLabel: 'Desactivar',
        danger: true,
      })
      .pipe(filter(Boolean))
      .subscribe(() => this.run(this.api.update(row.id, { active: false }), 'Usuario desactivado'));
  }

  private run(request$: Observable<StaffResponse>, success: string): void {
    request$.subscribe({
      next: () => {
        this.notifications.success(success);
        this.reload();
      },
      error: (e: unknown) => this.notifications.error(apiErrorMessage(e)),
    });
  }
}
