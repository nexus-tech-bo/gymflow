---
name: angular-project-structure
description: How to structure (or reorganize) a standalone Angular 19+ project — core/shared/features/layout folders, typed API layer, resource services with signals/rxResource, lazy routes with guards, functional interceptors, error handling and Tailwind styling. Use when creating a new Angular project, adding a feature/page, or when asked to organize/refactor the structure of an Angular frontend.
---

# Angular project structure

Based on a real production project (Angular 19, standalone, signals, Tailwind v4, npm). This is a guide, not a generator: only create the folders the project needs today.

## Tree

```
src/
  main.ts                      bootstrapApplication(AppComponent, appConfig)
  styles.css                   Tailwind + global design primitives (.btn, .card, .field-*, .data-table…)
  environments/                environment.ts / environment.development.ts (only apiUrl and real flags)
  app/
    app.config.ts              providers: router, httpClient + interceptors, locale
    app.routes.ts              ALL routes, lazy via loadComponent
    core/                      singletons, NO UI
      api/                     one service per backend resource (<resource>.api.ts) — the ONLY place that uses HttpClient
      models/                  DTO contracts (api.models.ts) mirroring the backend
      auth/                    AuthService (session as signals) + functional guards
      http/                    interceptors, apiErrorMessage(), toHttpParams()
      navigation/              ROLES + NAV_ITEMS for the menu (kept in sync with app.routes.ts)
      utils/                   pure helpers (dates, labels) + their spec
    shared/                    reusable across features, unaware of any feature's domain
      ui/<name>/               presentational components: input()/output() only, no API calls
      components/<name>/       form controls (ControlValueAccessor)
      services/                global UI services (notifications/toasts, loader)
      pipes/  utils/
    layout/shell/              sidebar + topbar + <router-outlet> for authenticated pages
    features/<feature>/        one folder per screen/domain
      <feature>-page.component.ts|html
      <thing>-form-dialog.component.ts|html
      services/<feature>-resource.service.ts
      components/<sub>/        only if the subcomponent is not used outside the feature
```

### Dependency rules

- `features` → `core`, `shared`. `shared` → `core` (models/utils only). `core` → no UI.
- A feature does not import another feature, except for explicit, documented exceptions (e.g. a shared actions service or detail dialog).
- If two features need the same thing → move it to `shared/` (UI) or `core/` (logic). Not before there are two usages.

## Conventions

- Standalone everywhere; no NgModules. `inject()` instead of constructor injection. `input()/output()/model()` instead of decorators.
- Local and UI state with `signal`/`computed`/`linkedSignal`. Server state with `rxResource` (v19: `request`/`loader`; v20+: `params`/`stream`).
- Naming: `kebab-case.component.ts`, classes `XxxComponent`, APIs `XxxApi`, state services `XxxResourceService`, utils `xxx.util.ts`, selector prefix `app-`.
- Templates in a separate `.html` file; styling with Tailwind utilities. Don't create a per-component `.scss` unless truly needed; if a pattern repeats, it goes in `styles.css`.
- Host layout via `host: { class: '...' }` in the decorator, not extra wrapper elements.
- Members used by the template: `protected`. Dependencies: `private readonly`.
- Prettier: `printWidth: 140`, `singleQuote`, `trailingComma: none`, `angular` parser for `*.html`.

## Templates

### app.config.ts
```ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([apiUrlInterceptor, authInterceptor]))
  ]
};
```

### Data layer (models, API classes, interceptors, resource services, errors)
See the `angular-data-services` skill. Summary: `core/api/<resource>.api.ts` is the only HttpClient user; each page owns a `<feature>-resource.service.ts` (`rxResource` + filters signal) provided in its `providers`; all user-facing errors go through `apiErrorMessage`.

### features/<f>/<f>-page.component.ts
The page orchestrates: injects the resource service, opens dialogs, runs mutations via `XxxApi`, notifies and reloads.
```ts
@Component({
  selector: 'app-patients-page',
  imports: [PageHeaderComponent, PaginationComponent, EmptyStateComponent],
  providers: [PatientsResourceService],
  templateUrl: './patients-page.component.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' }
})
export class PatientsPageComponent {
  private readonly api = inject(PatientsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifications = inject(NotificationService);
  protected readonly resource = inject(PatientsResourceService);

  protected deactivate(p: Patient): void {
    this.api.deactivate(p.id).subscribe({
      next: () => { this.notifications.success('Deactivated'); this.resource.reload(); },
      error: (e) => this.notifications.error(apiErrorMessage(e))
    });
  }
}
```
Dialogs: `@angular/cdk/dialog`, return their result through `.closed` (`filter(Boolean)` before reloading).

### app.routes.ts
```ts
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./layout/shell/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: homeRedirect },
      {
        path: 'patients',
        title: 'Patients',
        canActivate: [roleGuard(...ROLES.clinical)],
        loadComponent: () => import('./features/patients/patients-page.component').then((m) => m.PatientsPageComponent)
      }
    ]
  },
  { path: 'login', title: 'Sign in', canActivate: [guestGuard], loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent) },
  { path: '**', redirectTo: '' }
];
```
Every route: lazy, with a `title` and a role guard that mirrors backend authorization. Functional guards (`CanActivateFn`) returning `true` or `router.createUrlTree(...)`.

### shared/ui/<name>
```ts
@Component({ selector: 'app-page-header', templateUrl: './page-header.component.html', host: { class: 'mb-7 flex items-center justify-between' } })
export class PageHeaderComponent {
  title = input.required<string>();
  subtitle = input<string>();
}
```
Content projection with `<ng-content select="[slotName]">`; projected content must be a top-level node (not inside a multi-node `@if`).

## Checklist: new feature

1. Contract types in `core/models/api.models.ts` (same field names as the backend).
2. Methods in `core/api/<resource>.api.ts` (create the file if the resource is new).
3. `features/<f>/services/<f>-resource.service.ts` if the page lists/filters data.
4. Page + dialogs in `features/<f>/`, reusing `shared/ui` before creating new components.
5. Lazy route in `app.routes.ts` with `title` and guard; entry in `core/navigation` if it belongs in the menu.
6. `npm run format` and `ng build` with no errors.

## Gotchas

- Dates: never use `Date#toISOString()` for local dates (shifts to UTC). Centralize wire formats (`yyyy-MM-dd`, `HH:mm:ss`) in `core/utils/calendar.util.ts`.
- Tailwind v4 + `@apply` with nested CSS: enable `optimize` in `.postcssrc.json` (`"@tailwindcss/postcss": { "optimize": { "minify": false } }`) or the production optimizer may drop `:hover`/`:disabled` rules.
- `rxResource` changed API between v19 (`request`/`loader`) and v20 (`params`/`stream`); check the version in `package.json`.
- Budgets in `angular.json` (`initial` 500kB warn / 1MB error): keep everything lazy to stay within them.
