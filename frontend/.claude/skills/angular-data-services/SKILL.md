---
name: angular-data-services
description: How to consume backend endpoints in Angular 19+ — typed DTO models, one `XxxApi` class per resource (the only HttpClient user), functional interceptors (base URL, auth, 401), query-param builder, a single error-message translator, component-scoped `XxxResourceService` with rxResource/signals for reads, and mutation patterns in components/dialogs. Use when adding or changing an endpoint call, creating a service, loading/filtering/paginating server data, or when a page component holds too much server state.
---

# Angular data services

Three layers, each with one job:

| Layer            | File                                            | Job                                                                                               |
|------------------|-------------------------------------------------|---------------------------------------------------------------------------------------------------|
| Model            | `core/models/api.models.ts`                     | DTO interfaces mirroring the backend (identical field names)                                      |
| API              | `core/api/<resource>.api.ts`                    | Typed HTTP calls. **Only** place that injects `HttpClient`. No state, no logic, no error handling |
| Resource service | `features/<f>/services/<f>-resource.service.ts` | Server state of one page: filters → `rxResource` → derived signals                                |

Components read signals from the resource service and run mutations through the API class.

> `rxResource` API differs by version: v19 `request`/`loader: ({ request })`; v20+ `params`/`stream: ({ params })`. Check `package.json` before writing it.

## 1. Models

```ts
export interface Page<T> {
  content: T[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
}
export interface PageQuery { page?: number; size?: number; sort?: string }

export type Role = 'ADMIN' | 'DOCTOR' | 'PATIENT';          // backend enums → string unions
export interface Staff { id: number; name: string; email: string; active: boolean; specialtyName: string | null }
export interface StaffCreateRequest { name: string; email: string; roleId: number }
export interface StaffFilters extends PageQuery { roleName?: Role; isActive?: boolean }
export interface ApiError { status: number; message: string; errors?: string[] }
```
Nullable backend fields are `T | null`, not optional. Request and response types are separate interfaces even if similar.

## 2. HTTP plumbing (`core/http/`)

```ts
// app.config.ts
provideHttpClient(withInterceptors([apiUrlInterceptor, authInterceptor]))

/** API classes use relative paths ('staff'); this prefixes the backend base URL. */
export const apiUrlInterceptor: HttpInterceptorFn = (req, next) => {
  if (/^(https?:)?\/\//.test(req.url) || req.url.startsWith('/')) return next(req);
  return next(req.clone({ url: environment.apiUrl + req.url }));
};

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const authReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authReq).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === HttpStatusCode.Unauthorized && token) auth.logout();
      return throwError(() => error);
    })
  );
};
```
`environment.apiUrl = '/api/'`; in dev, `proxy.conf.json` forwards `/api` to the backend (no CORS, no hard-coded hosts).

```ts
/** Builds HttpParams skipping null/undefined/'' so optional filters are simply omitted. */
export function toHttpParams(query: object = {}): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined || value === '') continue;
    params = params.set(key, String(value));
  }
  return params;
}
```

### Errors — one translator
```ts
const KNOWN_MESSAGES: [RegExp, string][] = [[/invalid credentials/i, 'Wrong email or password']];
const STATUS_FALLBACK: Record<number, string> = { 0: 'Cannot reach the server', 403: 'Not allowed', 404: 'Not found', 409: 'Conflict, try again' };

export function apiErrorMessage(error: unknown, fallback = 'Unexpected error'): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  const body = error.error as Partial<ApiError> | null;
  const translate = (m: string) => KNOWN_MESSAGES.find(([re]) => re.test(m))?.[1] ?? null;
  if (body?.errors?.length) return body.errors.map((e) => translate(e) ?? e).join('. ');
  if (body?.message) { const known = translate(body.message); if (known) return known; }
  return STATUS_FALLBACK[error.status] ?? fallback;
}
```
UI never shows raw `error.message`. Every user-facing error goes through `apiErrorMessage` + a notification/toast service.

## 3. API class

```ts
@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);

  /** ADMIN. */
  list(filters: StaffFilters = {}): Observable<Page<Staff>> {
    return this.http.get<Page<Staff>>('staff', { params: toHttpParams(filters) });
  }

  get(id: number): Observable<Staff> {
    return this.http.get<Staff>(`staff/${id}`);
  }

  create(request: StaffCreateRequest): Observable<Staff> {
    return this.http.post<Staff>('staff', request);
  }

  update(id: number, request: StaffUpdateRequest): Observable<Staff> {
    return this.http.put<Staff>(`staff/${id}`, request);
  }

  /** Soft delete. */
  deactivate(id: number): Observable<void> {
    return this.http.delete<void>(`staff/${id}`);
  }
}
```
Rules: one class per backend resource/controller, named `XxxApi`; methods return `Observable<T>` (cold, the caller subscribes); relative paths; JSDoc the roles allowed and any non-obvious behavior. Wire-format conversions (dates, enum display names) go in `core/utils`, called by the API or resource service — never in templates.

## 4. Resource service (reads)

```ts
export const STAFF_PAGE_SIZE = 12;
export const getDefaultStaffFilters = (): StaffFilters => ({ page: 0, size: STAFF_PAGE_SIZE, sort: 'id', isActive: true });

@Injectable() // NOT providedIn root: provided by the page, dies with it, shared with its dialogs/children
export class StaffResourceService {
  private readonly _staffApi = inject(StaffApi);

  // 1. Request inputs — the only thing the UI writes to trigger a fetch.
  public filters = signal<StaffFilters>(getDefaultStaffFilters());

  // 2. The resource. Re-runs when filters() changes; the previous request is cancelled.
  private _staffResource = rxResource({
    request: () => this.filters(),
    loader: ({ request }) => this._staffApi.list(request)
  });

  // 3. Projections. linkedSignal if the UI edits the list locally (optimistic), computed if read-only.
  public staff = linkedSignal(() => this._staffResource.value()?.content ?? ([] as Staff[]));
  public pagination = computed(() => this._staffResource.value()?.page);
  public isLoading = this._staffResource.isLoading;
  public isError = computed(() => !!this._staffResource.error());
  public errorMessage = computed(() => {
    const error = this._staffResource.error();
    return error ? apiErrorMessage(error, 'Could not load staff') : '';
  });

  // 4. Intent methods (arrow props so they can be passed as callbacks).
  public reloadStaff = () => this._staffResource.reload();
  public updateFilters = (patch: Partial<StaffFilters>) => this.filters.update((c) => ({ ...c, page: 0, ...patch }));
  public setPage = (page: number) => this.filters.update((c) => ({ ...c, page }));
  public resetResource = () => {
    this.filters.set(getDefaultStaffFilters());
    this.staff.set([]);
  };
}
```
```ts
@Component({ selector: 'app-staff-page', providers: [StaffResourceService], templateUrl: './staff-page.component.html' })
export class StaffPageComponent {
  protected readonly resource = inject(StaffResourceService);
}
```

Rules:
- One `filters` signal per resource holding everything the request depends on; changing a filter resets `page` to 0.
- Skip the load when a required input is missing: return `undefined` from `request` (v19) / `params` (v20+), e.g. `request: () => this.doctorId() ?? undefined`.
- Several independent requests → several private resources in the same service. Dependent request → its `request` reads the other resource's value.
- Client-side filtering/paging (backend lacks it): fetch once per server-relevant key, filter in a `computed`; leave a comment with the size ceiling.
- No `subscribe` for loading data, no dialogs/router/notifications in the service.
- Defaults that depend on the session: read `AuthService` signals inside `request`.
- Simple one-off read in a dialog (e.g. a catalog list): an `rxResource` directly in the component is fine; no service needed.

## 5. Mutations (writes)

In the component or dialog, through the API class, then reload or patch locally:
```ts
protected deactivate(staff: Staff): void {
  this.confirm.ask({ title: 'Deactivate', message: `${staff.name} will lose access.`, danger: true })
    .pipe(filter(Boolean), switchMap(() => this.staffApi.deactivate(staff.id)))
    .subscribe({
      next: () => { this.notifications.success('Deactivated'); this.resource.reloadStaff(); },
      error: (error) => this.notifications.error(apiErrorMessage(error))
    });
}
```
Form dialog save:
```ts
protected readonly saving = signal(false);
protected readonly error = signal('');

protected save(): void {
  if (this.form.invalid) return this.form.markAllAsTouched();
  this.saving.set(true);
  const request$ = this.isEdit ? this.api.update(this.data.staff!.id, this.form.getRawValue()) : this.api.create(this.form.getRawValue());
  request$.pipe(finalize(() => this.saving.set(false))).subscribe({
    next: (saved) => this.ref.close(saved),
    error: (error) => this.error.set(apiErrorMessage(error))
  });
}
```
Caller: `dialog.open(...).closed.pipe(filter(Boolean)).subscribe(() => resource.reloadX())`.
Optimistic change: `resource.staff.update((list) => list.filter((s) => s.id !== id))`, and reload on error.

## Checklist: new endpoint

1. Read the backend controller/DTO (source of truth, not docs): path, verb, params, body, response, allowed roles.
2. Add/extend interfaces in `api.models.ts`.
3. Add the method to `core/api/<resource>.api.ts`.
4. Reads → resource service; writes → component/dialog with `saving` + `apiErrorMessage`.
5. New backend business error messages → entry in `KNOWN_MESSAGES`.
6. `ng build` (strict templates), then check in the network tab that each filter change fires exactly one request.

## Gotchas

- Never `Date#toISOString()` for local dates/times — shifts to UTC. Use `yyyy-MM-dd` / `HH:mm:ss` helpers.
- `rxResource` keeps the last `value()` while reloading: show the skeleton only when `isLoading() && !items().length`.
- A new object in `filters` on every keystroke refetches every keystroke: debounce search input or filter client-side.
