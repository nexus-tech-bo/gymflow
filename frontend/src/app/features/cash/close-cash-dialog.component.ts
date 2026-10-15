import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { CashApi } from '../../core/api/cash.api';
import { AuthService } from '../../core/auth/auth.service';
import { applyServerErrors, fieldError } from '../../core/http/api-error';
import { CashSessionDetailResponse, CashSessionResponse } from '../../core/models/api.models';
import { differenceChip } from '../../core/utils/labels.util';
import { MoneyPipe } from '../../shared/pipes/format.pipes';
import { DialogFrameComponent } from '../../shared/ui/dialog-frame.component';
import { FieldA11yDirective } from '../../shared/ui/field-a11y.directive';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { maxDecimals } from '../../core/utils/validators';

/**
 * Closes the open cash session. Blind for RECEPTIONIST (expectedCash comes null, nothing is computed client-side).
 * OWNER/ADMIN see the live difference and, after closing, a result summary in the same dialog.
 */
@Component({
  selector: 'app-close-cash-dialog',
  imports: [
    ReactiveFormsModule,
    DialogFrameComponent,
    FieldA11yDirective,
    StatusChipComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './close-cash-dialog.component.html',
})
export class CloseCashDialogComponent {
  private readonly api = inject(CashApi);
  private readonly router = inject(Router);
  protected readonly ref = inject<DialogRef<CashSessionDetailResponse>>(DialogRef);
  protected readonly session = inject<CashSessionResponse>(DIALOG_DATA);
  protected readonly blind = !inject(AuthService).isManager() || this.session.expectedCash === null;

  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.group({
    countedCash: [
      null as number | null,
      [Validators.required, Validators.min(0), Validators.max(99999999.99), maxDecimals(2)],
    ],
    notes: this.fb.nonNullable.control('', Validators.maxLength(500)),
  });
  private readonly counted = toSignal(this.form.controls.countedCash.valueChanges, {
    initialValue: null,
  });
  protected readonly liveDifference = computed(() => {
    const counted = this.counted();
    if (this.blind || counted === null || counted === undefined) return null;
    return differenceChip(Math.round((counted - this.session.expectedCash!) * 100) / 100);
  });

  protected readonly result = signal<CashSessionDetailResponse | null>(null);
  protected readonly resultDifference = computed(() =>
    differenceChip(this.result()?.session.difference ?? null),
  );
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly err = fieldError;

  protected save(): void {
    if (this.saving()) return;
    if (this.form.invalid) return this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const notes = v.notes.trim();
    this.saving.set(true);
    this.error.set('');
    this.api
      .close({ countedCash: v.countedCash!, ...(notes ? { notes } : {}) })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (closed) =>
          closed.session.expectedCash === null ? this.ref.close(closed) : this.result.set(closed),
        error: (e: unknown) => this.error.set(applyServerErrors(this.form, e)),
      });
  }

  protected viewDetail(id: number): void {
    this.ref.close(this.result()!);
    void this.router.navigate(['/cajas', id]);
  }
}
