import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthApi } from '../../core/api/auth.api';
import { AuthService } from '../../core/auth/auth.service';
import { applyServerErrors, fieldError } from '../../core/http/api-error';
import { NotificationService } from '../../core/services/notification.service';
import { FieldA11yDirective } from '../../shared/ui/field-a11y.directive';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { requiredText } from '../../core/utils/validators';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, UiIconComponent, FieldA11yDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './register-page.component.html',
  host: { class: 'w-full max-w-sm' },
})
export class RegisterPageComponent {
  private readonly authApi = inject(AuthApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    gymName: ['', [requiredText, Validators.maxLength(120)]],
    ownerName: ['', [requiredText, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    // backend limit is 72 bytes (bcrypt); 72 chars covers ASCII, the server validates the rest
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly showPassword = signal(false);
  protected readonly err = fieldError;

  protected submit(): void {
    if (this.saving()) return;
    if (this.form.invalid) return this.form.markAllAsTouched();
    this.saving.set(true);
    this.error.set('');
    this.authApi
      .registerGym(this.form.getRawValue())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (session) => {
          this.auth.start(session);
          this.notifications.success('¡Bienvenido a GymFlow!');
          void this.router.navigateByUrl('/dashboard');
        },
        error: (e: unknown) => this.error.set(applyServerErrors(this.form, e)),
      });
  }
}
