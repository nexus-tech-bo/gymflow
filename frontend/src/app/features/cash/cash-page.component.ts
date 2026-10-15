import { Dialog } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { filter, finalize } from 'rxjs';
import { CashApi } from '../../core/api/cash.api';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage, applyServerErrors, fieldError } from '../../core/http/api-error';
import {
  CashSessionDetailResponse,
  CashSessionResponse,
  PaymentResponse,
} from '../../core/models/api.models';
import { formatMoney } from '../../core/utils/format.util';
import { CASH_STATUS } from '../../core/utils/labels.util';
import { LimaDateTimePipe, MoneyPipe } from '../../shared/pipes/format.pipes';
import { NotificationService } from '../../core/services/notification.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { FieldA11yDirective } from '../../shared/ui/field-a11y.directive';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { CloseCashDialogComponent } from './close-cash-dialog.component';
import { PaymentsTableComponent } from './payments-table.component';
import { VoidPaymentDialogComponent } from './void-payment-dialog.component';
import { maxDecimals } from '../../core/utils/validators';

@Component({
  selector: 'app-cash-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
    UiIconComponent,
    FieldA11yDirective,
    PaymentsTableComponent,
    MoneyPipe,
    LimaDateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cash-page.component.html',
})
export class CashPageComponent {
  private readonly api = inject(CashApi);
  private readonly dialog = inject(Dialog);
  private readonly notifications = inject(NotificationService);
  protected readonly isManager = inject(AuthService).isManager;

  private readonly currentResource = rxResource({ stream: () => this.api.current() });
  protected readonly loaded = computed(() => this.currentResource.hasValue());
  protected readonly current = computed(() =>
    this.currentResource.hasValue() ? this.currentResource.value().current : null,
  );
  protected readonly isLoading = this.currentResource.isLoading;
  protected readonly errorMessage = computed(() =>
    this.currentResource.error()
      ? apiErrorMessage(this.currentResource.error(), 'No se pudo cargar la caja.')
      : '',
  );
  protected readonly reload = () => this.currentResource.reload();
  protected readonly openChip = CASH_STATUS.OPEN;

  protected readonly openForm = inject(FormBuilder).group({
    openingAmount: [
      0 as number | null,
      [Validators.required, Validators.min(0), Validators.max(99999999.99), maxDecimals(2)],
    ],
  });
  protected readonly opening = signal(false);
  protected readonly preparingClose = signal(false);
  protected readonly openError = signal('');
  protected readonly err = fieldError;

  protected open(): void {
    if (this.opening()) return;
    if (this.openForm.invalid) return this.openForm.markAllAsTouched();
    this.opening.set(true);
    this.openError.set('');
    this.api
      .open({ openingAmount: this.openForm.getRawValue().openingAmount! })
      .pipe(finalize(() => this.opening.set(false)))
      .subscribe({
        next: (opened) => {
          this.notifications.success('Caja abierta');
          this.currentResource.set({ current: opened });
          this.openForm.reset({ openingAmount: 0 });
        },
        error: (e: unknown) => this.openError.set(applyServerErrors(this.openForm, e)),
      });
  }

  protected close(): void {
    if (this.preparingClose()) return;
    // Refetch so the dialog gets the current expectedCash (payments may have arrived since the page loaded).
    this.preparingClose.set(true);
    this.api
      .current()
      .pipe(finalize(() => this.preparingClose.set(false)))
      .subscribe({
        next: (fresh) => {
          this.currentResource.set(fresh);
          if (!fresh.current) return;
          this.dialog
            .open<CashSessionDetailResponse, CashSessionResponse>(CloseCashDialogComponent, {
              data: fresh.current.session,
            })
            .closed.subscribe((closed) => {
              if (closed?.session.expectedCash === null) {
                this.notifications.success(
                  `Caja cerrada. Se registró ${formatMoney(closed.session.countedCash)} contado.`,
                );
              }
              // always: Esc/backdrop after a successful close must not leave the page "open"
              this.reload();
            });
        },
        error: (e: unknown) => this.notifications.error(apiErrorMessage(e)),
      });
  }

  protected voidPayment(payment: PaymentResponse): void {
    this.dialog
      .open<PaymentResponse, PaymentResponse>(VoidPaymentDialogComponent, { data: payment })
      .closed.pipe(filter(Boolean))
      .subscribe(() => {
        this.notifications.success('Pago anulado');
        this.reload();
      });
  }
}
