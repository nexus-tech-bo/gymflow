import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { PlansApi } from '../../core/api/plans.api';
import { applyServerErrors, fieldError } from '../../core/http/api-error';
import { PlanResponse } from '../../core/models/api.models';
import { DialogFrameComponent } from '../../shared/ui/dialog-frame.component';
import { FieldA11yDirective } from '../../shared/ui/field-a11y.directive';
import { maxDecimals, requiredText } from '../../core/utils/validators';

@Component({
  selector: 'app-plan-form-dialog',
  imports: [ReactiveFormsModule, DialogFrameComponent, FieldA11yDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './plan-form-dialog.component.html',
})
export class PlanFormDialogComponent {
  private readonly api = inject(PlansApi);
  protected readonly ref = inject<DialogRef<PlanResponse>>(DialogRef);
  protected readonly plan = inject<PlanResponse | null>(DIALOG_DATA);

  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.group({
    name: this.fb.nonNullable.control(this.plan?.name ?? '', [
      requiredText,
      Validators.maxLength(60),
    ]),
    durationDays: [
      this.plan?.durationDays ?? (null as number | null),
      [Validators.required, Validators.min(1), Validators.max(730), maxDecimals(0)],
    ],
    price: [
      this.plan?.price ?? (null as number | null),
      [Validators.required, Validators.min(0), Validators.max(99999.99), maxDecimals(2)],
    ],
    active: this.fb.nonNullable.control(this.plan?.active ?? true),
  });
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly err = fieldError;

  protected save(): void {
    if (this.saving()) return;
    if (this.form.invalid) return this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const request = { name: v.name.trim(), durationDays: v.durationDays!, price: v.price! };
    this.saving.set(true);
    this.error.set('');
    const request$ = this.plan
      ? this.api.update(this.plan.id, { ...request, active: v.active })
      : this.api.create(request);
    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (saved) => this.ref.close(saved),
      error: (e: unknown) => this.error.set(applyServerErrors(this.form, e)),
    });
  }
}
