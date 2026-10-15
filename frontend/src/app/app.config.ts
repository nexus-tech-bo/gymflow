import { DEFAULT_DIALOG_CONFIG, DialogConfig } from '@angular/cdk/dialog';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { apiUrlInterceptor, authInterceptor } from './core/http/interceptors';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([apiUrlInterceptor, authInterceptor])),
    provideAppInitializer(() => inject(AuthService).hydrate()),
    {
      provide: DEFAULT_DIALOG_CONFIG,
      // keep CDK defaults (backdrop, role, closeOnNavigation…) and override only what the design needs
      useValue: {
        ...new DialogConfig(),
        backdropClass: 'glass-backdrop',
        autoFocus: 'first-tabbable',
        restoreFocus: true,
        ariaModal: true,
      } satisfies DialogConfig,
    },
  ],
};
