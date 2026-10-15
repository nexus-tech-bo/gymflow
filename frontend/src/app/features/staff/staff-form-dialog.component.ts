import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { StaffApi } from '../../core/api/staff.api';
import { AuthService } from '../../core/auth/auth.service';
import { applyServerErrors, fieldError } from '../../core/http/api-error';
import { Role, StaffResponse } from '../../core/models/api.models';
import { ROLE } from '../../core/utils/labels.util';
import { DialogFrameComponent } from '../../shared/ui/dialog-frame.component';
import { FieldA11yDirective } from '../../shared/ui/field-a11y.directive';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { assignableRoles } from './staff-rules';
import { requiredText } from '../../core/utils/validators';

/** Create (data null) or edit name/role of a staff user. Closes with the saved user. */
@Component({
  selector: 'app-staff-form-dialog',
  imports: [ReactiveFormsModule, DialogFrameComponent, FieldA11yDirective, UiIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff-form-dialog.component.html',
})
export class StaffFormDialogComponent {
  private readonly api = inject(StaffApi);
  protected readonly ref = inject<DialogRef<StaffResponse>>(DialogRef);
  protected readonly staff = inject<StaffResponse | null>(DIALOG_DATA);
  protected readonly roles = assignableRoles(inject(AuthService).user());
  protected readonly roleLabel = ROLE;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: [this.staff?.fullName ?? '', [requiredText, Validators.maxLength(120)]],
    email: [
      '',
      this.staff ? [] : [Validators.required, Validators.email, Validators.maxLength(255)],
    ],
    password: [
      '',
      this.staff ? [] : [Validators.required, Validators.minLength(8), Validators.maxLength(72)],
    ],
    role: [this.staff?.role ?? this.roles[this.roles.length - 1]] as [Role],
  });
  protected readonly role = toSignal(this.form.controls.role.valueChanges, {
    initialValue: this.form.controls.role.value,
  });
  protected readonly showPassword = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly err = fieldError;

  protected save(): void {
    if (this.saving()) return;
    if (this.form.invalid) return this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const fullName = v.fullName.trim();
    // ADMIN cannot change roles: only send it when there is a real choice
    const canChooseRole = this.roles.length > 1;
    const request$ = this.staff
      ? this.api.update(this.staff.id, { fullName, ...(canChooseRole ? { role: v.role } : {}) })
      : this.api.create({ fullName, email: v.email.trim(), password: v.password, role: v.role });
    this.saving.set(true);
    this.error.set('');
    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (saved) => this.ref.close(saved),
      error: (e: unknown) => this.error.set(applyServerErrors(this.form, e)),
    });
  }
}
