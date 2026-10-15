import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { MembersApi } from '../../core/api/members.api';
import { applyServerErrors, fieldError } from '../../core/http/api-error';
import { MemberDetailResponse, MemberRequest } from '../../core/models/api.models';
import { addDays, todayLima } from '../../core/utils/format.util';
import { DialogFrameComponent } from '../../shared/ui/dialog-frame.component';
import { DatePickerComponent } from '../../shared/ui/date-picker.component';
import { FieldA11yDirective } from '../../shared/ui/field-a11y.directive';
import { pastDate, requiredText } from '../../core/utils/validators';

export interface MemberFormData {
  member?: MemberDetailResponse;
}

/** Create/edit a member. Shared by Socios, Detalle de socio and Dashboard. Closes with the saved member. */
@Component({
  selector: 'app-member-form-dialog',
  imports: [ReactiveFormsModule, DialogFrameComponent, DatePickerComponent, FieldA11yDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './member-form-dialog.component.html',
})
export class MemberFormDialogComponent {
  private readonly api = inject(MembersApi);
  protected readonly ref = inject<DialogRef<MemberDetailResponse>>(DialogRef);
  protected readonly member = inject<MemberFormData | null>(DIALOG_DATA)?.member;

  protected readonly maxBirthDate = addDays(todayLima(), -1);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: [this.member?.fullName ?? '', [requiredText, Validators.maxLength(120)]],
    dni: [this.member?.dni ?? '', [requiredText, Validators.pattern(/^\s*[A-Za-z0-9]{6,12}\s*$/)]],
    phone: [this.member?.phone ?? '', Validators.maxLength(20)],
    email: [this.member?.email ?? '', [Validators.email, Validators.maxLength(255)]],
    birthDate: [this.member?.birthDate ?? '', pastDate],
    notes: [this.member?.notes ?? '', Validators.maxLength(500)],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly err = fieldError;

  protected save(): void {
    if (this.saving()) return;
    if (this.form.invalid) return this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const request: MemberRequest = {
      fullName: v.fullName.trim(),
      dni: v.dni.trim(),
      phone: v.phone.trim() || null,
      email: v.email.trim() || null,
      birthDate: v.birthDate || null,
      notes: v.notes.trim() || null,
    };
    this.saving.set(true);
    this.error.set('');
    const request$ = this.member
      ? this.api.update(this.member.id, request)
      : this.api.create(request);
    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (saved) => this.ref.close(saved),
      error: (e: unknown) => this.error.set(applyServerErrors(this.form, e)),
    });
  }
}
