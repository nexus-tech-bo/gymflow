import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { PaymentsApi } from '../../core/api/payments.api';
import { applyServerErrors, fieldError } from '../../core/http/api-error';
import { PaymentResponse } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/pipes/format.pipes';
import { DialogFrameComponent } from '../../shared/ui/dialog-frame.component';
import { FieldA11yDirective } from '../../shared/ui/field-a11y.directive';
import { requiredText } from '../../core/utils/validators';

@Component({
  selector: 'app-void-payment-dialog',
  imports: [ReactiveFormsModule, DialogFrameComponent, FieldA11yDirective, MoneyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog-frame
      title="Anular pago"
      [subtitle]="payment.memberName + ' · ' + payment.planName + ' · ' + (payment.amount | money)"
    >
      <form id="void-form" [formGroup]="form" (ngSubmit)="save()" class="space-y-4" novalidate>
        <p class="rounded-2xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
          También se cancelará la membresía asociada.
        </p>
        <div>
          <label class="field-label" for="reason">Motivo *</label>
          <textarea
            id="reason"
            rows="3"
            class="field-input resize-none"
            formControlName="reason"
            maxlength="200"
            cdkFocusInitial
          ></textarea>
          <div class="flex justify-between gap-2">
            <p id="reason-error" class="field-error">{{ err(form.controls.reason) }}</p>
            <p class="mt-1.5 text-xs text-gray-500 tabular-nums">
              {{ form.controls.reason.value.length }}/200
            </p>
          </div>
        </div>
        @if (error()) {
          <p class="form-error" role="alert">{{ error() }}</p>
        }
      </form>
      <div dialogFooter class="contents">
        <button type="button" class="btn btn-secondary" (click)="ref.close()">Cancelar</button>
        <button type="submit" form="void-form" class="btn btn-danger" [disabled]="saving()">
          {{ saving() ? 'Anulando…' : 'Anular pago' }}
        </button>
      </div>
    </app-dialog-frame>
  `,
})
export class VoidPaymentDialogComponent {
  private readonly api = inject(PaymentsApi);
  protected readonly ref = inject<DialogRef<PaymentResponse>>(DialogRef);
  protected readonly payment = inject<PaymentResponse>(DIALOG_DATA);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    reason: ['', [requiredText, Validators.maxLength(200)]],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly err = fieldError;

  protected save(): void {
    if (this.saving()) return;
    if (this.form.invalid) return this.form.markAllAsTouched();
    this.saving.set(true);
    this.error.set('');
    this.api
      .void(this.payment.id, { reason: this.form.getRawValue().reason.trim() })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (voided) => this.ref.close(voided),
        error: (e: unknown) => this.error.set(applyServerErrors(this.form, e)),
      });
  }
}
