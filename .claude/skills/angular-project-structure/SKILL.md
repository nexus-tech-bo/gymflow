---
name: angular-project-structure
description: Feature-first folder structure and layering for an Angular SPA (standalone components, signals) that consumes a REST API - core/features/layout/shared layout, file placement rules, naming, API clients, feature services with signals and rxResource, pages, dialogs, presentational components, interceptors, guards, error handling and routing. Use when creating a new feature or page end to end, adding an API client, service, component, dialog or model, scaffolding a new Angular project, or deciding where a file belongs.
---

# Angular Project Structure

Feature-first structure for Angular SPAs backed by a REST API (the frontend counterpart of `java-mvc-project-structure`).
Stack: standalone components, signals, `rxResource`, functional interceptors and guards, Angular CDK, Tailwind CSS.
Examples use a fictional **library** domain (`Book`, `Author`, `Member`, `Loan`).

## Folder layout

```
src/
├── main.ts
├── index.html
├── styles.css                          # Tailwind + global classes (btn, field-input, chip, data-table…)
├── environments/
│   ├── environment.ts                  # { apiUrl: '/api/' }
│   └── environment.development.ts
└── app/
    ├── app.component.ts|html           # <router-outlet> + global overlays
    ├── app.config.ts                   # providers
    ├── app.routes.ts                   # all routes, lazy
    ├── core/                           # app-wide singletons, no UI
    │   ├── api/                        # books.api.ts, loans.api.ts, loans.socket.ts
    │   ├── auth/                       # auth.service.ts, auth.guards.ts, permissions.ts
    │   ├── http/                       # api.interceptors.ts, api-error.ts, http-params.util.ts
    │   ├── models/                     # api.models.ts (backend DTO types)
    │   ├── navigation/                 # navigation.ts (HOME_URL, ROLES, nav items)
    │   └── utils/                      # domain helpers: dates.util.ts, labels.util.ts
    ├── features/                       # one folder per screen
    │   └── books/
    │       ├── books-page.component.ts|html
    │       ├── books.service.ts
    │       ├── books.model.ts          # optional
    │       └── components/             # used only by this feature
    │           ├── book-card/
    │           │   └── book-card.component.ts|html
    │           └── book-form-dialog/
    │               ├── book-form-dialog.component.ts|html
    │               └── book-form.service.ts   # optional
    ├── layout/
    │   └── shell/                      # authenticated frame
    │       ├── shell.component.ts|html
    │       └── components/
    │           ├── sidebar-nav/
    │           └── topbar/
    └── shared/                         # reused by 2+ features
        ├── <domain>/                   # e.g. loans/
        │   ├── loan-actions.service.ts
        │   └── components/
        │       └── loan-detail-dialog/
        ├── ui/                         # presentational: page-header, pagination, empty-state, skeleton…
        │   ├── <name>/<name>.component.ts|html
        │   ├── <name>/<name>.directive.ts
        │   └── forms/                  # form controls: form-field, select, date-picker…
        ├── services/                   # app-wide: notification.service.ts, confirm.service.ts
        ├── pipes/
        └── utils/                      # UI helpers: validation-messages.util.ts
```

## Placement rules

1. **Services and models sit next to what owns them.** A `*.service.ts` or `*.model.ts` goes at the level of the feature folder or the component folder that uses it. There are no `services/` or `models/` subfolders. The only exceptions are `shared/services/` (app-wide services) and `core/models/` (backend DTO types).
2. **One folder per component** (`components/<name>/`), containing its `.ts`, `.html` and, if any, its own service.
3. **Feature first, promote later.** Code starts inside its feature. It moves to `shared/` only when a second feature needs it, and never gets imported directly from another feature.
4. **No loose files in `shared/ui`.** Every component or directive gets its own folder. Non-visual helpers go to `shared/utils/*.util.ts`.

| It is…                                       | Put it in                                                |
|----------------------------------------------|----------------------------------------------------------|
| A call to a backend endpoint                 | `core/api/<resource>.api.ts`                             |
| A type that mirrors a backend DTO            | `core/models/api.models.ts`                              |
| App-wide state (session, user)               | `core/auth/` or `core/<area>/`, `providedIn: 'root'`     |
| A routed screen                              | `features/<feature>/<feature>-page.component.ts`         |
| State + actions of a screen                  | `features/<feature>/<feature>.service.ts`                |
| A view model of one feature                  | `features/<feature>/<feature>.model.ts`                  |
| A dialog/subcomponent of one feature         | `features/<feature>/components/<name>/`                  |
| State of one dialog                          | `features/<feature>/components/<name>/<name>.service.ts` |
| Domain component/service used by 2+ features | `shared/<domain>/`                                       |
| Domain-agnostic visual component             | `shared/ui/<name>/`                                      |
| Form control                                 | `shared/ui/forms/<name>/`                                |
| App-wide UI service (toasts, confirm)        | `shared/services/`                                       |
| Pure helper                                  | `core/utils/` (domain) · `shared/utils/` (UI, forms)     |

## Layers

```
Template ─events─▶ Page ─▶ Feature service ─▶ core/api ─▶ HttpClient ─▶ interceptors ─▶ API
    ▲                            │
    └──────── signals ◀──────────┘ (rxResource, computed)
```

| Layer                             | Does                                                                                                    | Never does                      |
|-----------------------------------|---------------------------------------------------------------------------------------------------------|---------------------------------|
| `core/api`                        | Typed HTTP calls returning `Observable<T>`                                                              | Hold state, notify, subscribe   |
| Feature service                   | State in signals, loading with `rxResource`, filters, pagination, actions, confirmations, notifications | Open dialogs, touch the DOM     |
| Page                              | Binds the template to the service, opens dialogs, manages focus                                         | Call APIs, business rules       |
| Dialog                            | Form, submit through the feature service, show server errors                                            | Call APIs directly              |
| `shared/ui`                       | `input()` / `output()` / `model()`, rendering                                                           | Inject feature services or APIs |
| `shared/<domain>` actions service | Reusable domain flows across features (may open its own domain dialogs)                                 | Feature-specific state          |
| Interceptors / guards             | Cross-cutting HTTP and navigation rules                                                                 | Feature logic                   |

## Naming

| Thing            | File                             | Symbol                                     |
|------------------|----------------------------------|--------------------------------------------|
| API client       | `books.api.ts`                   | `BooksApi`                                 |
| WebSocket client | `loans.socket.ts`                | `LoansSocket`                              |
| Page             | `books-page.component.ts`        | `BooksPageComponent`, `app-books-page`     |
| Feature service  | `books.service.ts`               | `BooksService`                             |
| View model       | `books.model.ts`                 | `BookRow`, `BookColumn`…                   |
| Dialog           | `book-form-dialog.component.ts`  | `BookFormDialogComponent` + `BookFormData` |
| Dialog service   | `book-form.service.ts`           | `BookFormService`                          |
| Component        | `book-card.component.ts`         | `BookCardComponent`, `app-book-card`       |
| Directive        | `keyboard-activate.directive.ts` | `KeyboardActivateDirective`                |
| Pipe             | `due-date.pipe.ts`               | `DueDatePipe`, `dueDate`                   |
| Utils            | `dates.util.ts`                  | exported functions / consts                |
| Guards           | `auth.guards.ts`                 | `authGuard`, `guestGuard`, `roleGuard()`   |
| Interceptors     | `api.interceptors.ts`            | `apiUrlInterceptor`, `authInterceptor`     |
| Constants        | —                                | `UPPER_SNAKE_CASE`                         |

Files are kebab-case, there is one class per file, and templates live in their own `.html` file. Standalone entry pages outside the shell (e.g. `login.component.ts`) may drop the `-page` suffix.

## Models — `core/models/api.models.ts`

Mirror the backend DTOs exactly. Enums are string-literal unions.

```ts
export type Role = 'ADMIN' | 'LIBRARIAN' | 'READER';
export type LoanStatus = 'ACTIVE' | 'RETURNED' | 'OVERDUE';

export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
}

export interface ApiError {
  status: number;
  message: string;
  errors?: { field: string; message: string }[] | null;
}

export interface Book {
  id: number;
  title: string;
  isbn: string;
  authorName: string;
  active: boolean;
}

export interface BookRequest {
  title: string;
  isbn: string;
  authorId: number;
}

export interface BookFilters extends PageQuery {
  search?: string;
  isActive?: boolean;
}
```

Request, query and filter types also live here. Never declare them inline in API clients. Derive related shapes (`Omit<…>`, `extends`) instead of duplicating them.

## API client — `core/api/books.api.ts`

```ts
@Injectable({ providedIn: 'root' })
export class BooksApi {
  private readonly http = inject(HttpClient);

  list(query: BookFilters = {}): Observable<Page<Book>> {
    return this.http.get<Page<Book>>('books', { params: toHttpParams(query) });
  }

  create(request: BookRequest): Observable<Book> {
    return this.http.post<Book>('books', request);
  }

  update(id: number, request: BookRequest): Observable<Book> {
    return this.http.put<Book>(`books/${id}`, request);
  }

  deactivate(id: number): Observable<void> {
    return this.http.delete<void>(`books/${id}`);
  }

  activate(id: number): Observable<Book> {
    return this.http.patch<Book>(`books/${id}/activate`, null);
  }
}
```

- Use relative URLs. `apiUrlInterceptor` adds `environment.apiUrl`.
- `toHttpParams` (in `core/http/http-params.util.ts`) skips `null`, `undefined` and `''`, so optional filters are left out of the query string.

## Feature service — `features/books/books.service.ts`

`@Injectable()` without `providedIn`, provided by the page (`providers: [BooksService]`). State lives and dies with the screen.

```ts
const PAGE_SIZE = 12;

@Injectable()
export class BooksService {
  private readonly booksApi = inject(BooksApi);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);

  private readonly _search = signal('');
  private readonly _page = signal(0);
  readonly search = this._search.asReadonly();

  private readonly debouncedSearch = toSignal(
    toObservable(this._search).pipe(
      debounceTime(500),
      map((s) => s.trim()),
      distinctUntilChanged(),
      tap(() => this._page.set(0)),
    ),
    { initialValue: '' },
  );

  private readonly resource = rxResource({
    request: () => ({ search: this.debouncedSearch(), page: this._page() }),
    loader: ({ request }) => this.booksApi.list({ ...request, size: PAGE_SIZE, sort: 'id' }),
  });

  readonly books = computed(() => this.resource.value()?.content ?? []);
  readonly pagination = computed(() => this.resource.value()?.page);
  readonly isLoading = this.resource.isLoading;
  readonly errorMessage = resourceError(this.resource, 'Could not load books');

  reload(): void {
    this.resource.reload();
  }

  setSearch(search: string): void {
    this._search.set(search);
  }

  setPage(page: number): void {
    this._page.set(page);
  }

  save(book: Book | undefined, request: BookRequest): Observable<Book> {
    const save$ = book ? this.booksApi.update(book.id, request) : this.booksApi.create(request);
    return save$.pipe(
      tap(() => this.notifications.success(book ? 'Book updated' : 'Book created')),
      finalize(() => this.reload()),
    );
  }

  deactivate(book: Book): Observable<unknown> {
    return this.confirm
      .confirmThen(
        { title: 'Deactivate book', message: `${book.title} can no longer be lent.`, confirmLabel: 'Deactivate', danger: true },
        () => this.booksApi.deactivate(book.id),
        'Book deactivated',
        'Could not deactivate the book',
      )
      .pipe(tap(() => this.reload()));
  }
}
```

- Writable signals are private (`_x`) and exposed with `asReadonly()`. Change them only through methods.
- Derived state uses `computed`. Never copy it into another signal.
- Load server data with `rxResource`. A `request` that returns `undefined` skips loading. In Angular 20+, `request`/`loader` are renamed to `params`/`stream`.
- Expose `isLoading`, `errorMessage`, the data and the pagination.
- Actions return an `Observable` so the caller can react (close the dialog, move focus).
- Show user-facing errors through `apiErrorMessage(error, fallback)`.

## Page — `features/books/books-page.component.ts`

```ts
@Component({
  selector: 'app-books-page',
  imports: [FormsModule, PageHeaderComponent, PaginationComponent, EmptyStateComponent, SkeletonComponent, LoadErrorComponent],
  providers: [BooksService],
  templateUrl: './books-page.component.html',
  host: { class: 'flex min-h-0 flex-1 flex-col' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BooksPageComponent {
  private readonly dialog = inject(Dialog);
  private readonly injector = inject(Injector);
  protected readonly books = inject(BooksService);

  protected openForm(book?: Book): void {
    this.dialog.open<void, BookFormData>(BookFormDialogComponent, {
      ...labelledByFrame(),
      data: { book },
      injector: this.injector, // the dialog shares the page's BooksService
    });
  }
}
```

The template always covers loading, error, empty and content:

```html
<app-page-header title="Books">
  <button type="button" class="btn btn-primary" (click)="openForm()">New book</button>
</app-page-header>

@if (books.isLoading() && !books.books().length) {
  <app-skeleton [rows]="4" />
} @else if (books.errorMessage(); as error) {
  <app-load-error [message]="error" (retry)="books.reload()" />
} @else if (!books.books().length) {
  <app-empty-state icon="book" title="No books" />
} @else {
  @for (book of books.books(); track book.id) { … }
}

@if (books.pagination(); as info) {
  <app-pagination [page]="info.number" [totalPages]="info.totalPages" (pageChange)="books.setPage($event)" />
}
```

## Dialog — `features/books/components/book-form-dialog/`

```ts
export interface BookFormData {
  book?: Book;
}

@Component({
  selector: 'app-book-form-dialog',
  imports: [ReactiveFormsModule, DialogFrameComponent, FormFieldComponent, AlertComponent],
  templateUrl: './book-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookFormDialogComponent {
  private readonly books = inject(BooksService);
  protected readonly ref = inject(DialogRef);
  protected readonly book = inject<BookFormData>(DIALOG_DATA).book;

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    title: [this.book?.title ?? '', notBlank],
    isbn: [this.book?.isbn ?? '', [notBlank, isbnValidator]],
  });

  protected save(): void {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { title, isbn } = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.books
      .save(this.book, { title: title.trim(), isbn: isbn.trim(), authorId: 1 })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => this.ref.close(),
        error: (e) => this.error.set(apiErrorMessage(e, 'Could not save the book')),
      });
  }
}
```

- The same dialog handles create and edit (`book` undefined means create).
- A `saving` signal blocks double submits. Server errors are shown inside the dialog.
- Validators and their messages come from `shared/utils/validation-messages.util.ts`. `app-form-field` shows the message and wires the ARIA attributes.
- A dialog with substantial state of its own gets `book-form.service.ts` next to it, provided in the dialog. That service types `DIALOG_DATA` with `import type { BookFormData }`.

## Presentational component — `shared/ui/empty-state/`

```ts
@Component({
  selector: 'app-empty-state',
  imports: [UiIconComponent],
  templateUrl: './empty-state.component.html',
  host: { class: 'block' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly message = input<string>();
  readonly icon = input<UiIconName>('inbox');
}
```

## HTTP — `core/http/`

```ts
export const apiUrlInterceptor: HttpInterceptorFn = (req, next) => {
  if (/^(https?:)?\/\//.test(req.url) || req.url.startsWith('/')) return next(req);
  return next(req.clone({ url: environment.apiUrl + req.url }));
};

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === HttpStatusCode.Unauthorized && token) auth.logout();
      return throwError(() => error);
    }),
  );
};
```

```ts
export function apiErrorMessage(error: unknown, fallback = 'Unexpected error'): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  if (error.status === 0) return 'Could not reach the server';
  const body = error.error as Partial<ApiError> | null;
  const details = [...new Set((body?.errors ?? []).map((e) => e.message).filter(Boolean))];
  return details.length ? details.join('. ') : (body?.message ?? fallback);
}

export function resourceError(resource: { error(): unknown }, fallback: string): Signal<string | null> {
  return computed(() => (resource.error() ? apiErrorMessage(resource.error(), fallback) : null));
}
```

## Auth and navigation — `core/auth/`, `core/navigation/`

- `AuthService` (`providedIn: 'root'`) keeps the session decoded from the JWT in a signal and persists it in `localStorage` inside try/catch. `token()` discards an expired session. It also exposes `hasRole(...roles)`, `login()` and `logout()`.
- Guards are functional:

```ts
export const authGuard: CanActivateFn = () =>
  inject(AuthService).token() ? true : inject(Router).createUrlTree(['/login']);

export const guestGuard: CanActivateFn = () =>
  inject(AuthService).token() ? inject(Router).createUrlTree([HOME_URL]) : true;

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => (inject(AuthService).hasRole(...roles) ? true : inject(Router).createUrlTree([HOME_URL]));
}
```

- `navigation.ts` defines `HOME_URL`, the role groups (`ROLES.admin`…) and the nav items with their roles. Routes and the sidebar read the same source.
- `permissions.ts` holds pure UI rules (`canReturnLoan(status)`). The backend enforces them too.

## Routing — `app.routes.ts`

```ts
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./layout/shell/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: HOME_URL },
      {
        path: 'books',
        title: 'Books',
        loadComponent: () => import('./features/books/books-page.component').then((m) => m.BooksPageComponent),
      },
      {
        path: 'members',
        title: 'Members',
        canActivate: [roleGuard(...ROLES.admin)],
        loadComponent: () => import('./features/members/members-page.component').then((m) => m.MembersPageComponent),
      },
    ],
  },
  {
    path: 'login',
    title: 'Sign in',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  { path: '**', redirectTo: '' },
];
```

Every route is lazy and has a `title`. Authenticated pages are children of the shell.

## App config — `app.config.ts`

```ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([apiUrlInterceptor, authInterceptor])),
    { provide: LOCALE_ID, useValue: 'en' },
    { provide: DEFAULT_DIALOG_CONFIG, useValue: { ...new DialogConfig(), ariaModal: true } },
  ],
};
```

In development, `proxy.conf.js` forwards `/api` and `/ws` (`ws: true`) to the backend, so URLs stay relative and you don't need CORS locally.

## Real-time (optional)

`core/api/<name>.socket.ts` is `@Injectable()` without `providedIn` and is provided by the page that uses it. It sends the JWT in `connectHeaders`, runs outside the zone and deactivates in `DestroyRef`. The feature service listens to its events and calls `resource.reload()` (debounced); the data always comes from REST.

## Conventions

- Use `ChangeDetectionStrategy.OnPush`, `inject()` and `readonly` dependencies everywhere. Use `protected` for members only the template uses.
- Use built-in control flow (`@if`, `@for` with `track`). Put host classes in `host: { class }`.
- Strict TypeScript, no `any`. Prettier settings: `printWidth: 100`, `singleQuote: true`.
- Accessibility: `type="button"`, `aria-label` on icon buttons, labelled dialogs, focus restored after actions.
- Style with Tailwind. Repeated patterns become global classes in `styles.css`.
- Put tests `*.spec.ts` next to the file they test.

## New feature checklist

1. DTO types in `core/models/api.models.ts`
2. `core/api/<resource>.api.ts`
3. `features/<feature>/<feature>.service.ts`
4. `features/<feature>/<feature>-page.component.ts|html` with all 4 states
5. Dialogs and subcomponents in `features/<feature>/components/<name>/`
6. Lazy route with `title` and guards, plus a nav item in `core/navigation/navigation.ts`
7. Reuse `shared/`; promote code there only when a second feature needs it
