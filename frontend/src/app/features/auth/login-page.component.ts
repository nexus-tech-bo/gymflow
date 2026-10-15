import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthApi } from '../../core/api/auth.api';
import { AuthService } from '../../core/auth/auth.service';
import { applyServerErrors, fieldError } from '../../core/http/api-error';
import { FieldA11yDirective } from '../../shared/ui/field-a11y.directive';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, UiIconComponent, FieldA11yDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login-page.component.html',
  host: { class: 'w-full max-w-sm' },
})
export class LoginPageComponent {
  private readonly authApi = inject(AuthApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** ?redirect= set by the auth guard / expired session. */
  readonly redirect = input<string>();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
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
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (session) => {
          this.auth.start(session);
          const redirect = this.redirect();
          // only same-app paths, never an absolute URL from the query string
          void this.router.navigateByUrl(
            redirect?.startsWith('/') && !redirect.startsWith('//')
              ? redirect
              : this.auth.landingUrl(),
          );
        },
        error: (e: unknown) =>
          this.error.set(
            e instanceof HttpErrorResponse && e.status === 401
              ? 'Correo o contraseña incorrectos.'
              : applyServerErrors(this.form, e),
          ),
      });
  }
}
