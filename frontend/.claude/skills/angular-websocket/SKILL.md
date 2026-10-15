---
name: angular-websocket
description: How to configure and consume WebSocket connections in Angular 19+ — dev proxy (`ws -> true`), `ws(s)://` URL derived from the current host, JWT auth on the handshake, one `XxxSocket` class per channel built on `rxjs/webSocket` (no new deps), typed message DTOs, reconnect with backoff, `toSignal` in components, cleanup on destroy/logout, and nginx config for production. Use when adding real-time updates, live notifications, push events, or any `ws://`/`wss://` connection.
---

# Angular WebSocket

Same layering as `angular-data-services`: the socket class is the **only** place that opens a connection; components read signals.

| Layer    | File                           | Job                                                  |
|----------|--------------------------------|------------------------------------------------------|
| Model    | `core/models/api.models.ts`    | Message DTOs as a discriminated union on `type`      |
| Socket   | `core/api/<channel>.socket.ts` | Open/close, auth, reconnect, typed `messages$`       |
| Consumer | resource service / component   | `toSignal`, or trigger `resource.reload()` on events |

> Use `rxjs/webSocket` (already installed). Add `@stomp/rx-stomp` **only** if the backend uses STOMP (Spring `@EnableWebSocketMessageBroker`) — check before choosing.

## 1. Dev proxy and URL

```js
// proxy.conf.js — add the socket path next to /api (or set ws: true on /api if the socket lives under it)
"/ws": { target: process.env.BACKEND_URL || "http://localhost:8080", ws: true, secure: false, changeOrigin: true },
```

```ts
/** Relative paths become ws(s)://<current host>/... so the dev proxy and prod nginx both work. */
export function wsUrl(path: string): string {
  const url = new URL(path, location.href);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.toString();
}
```
Never hard-code `ws://localhost:8080`. Never use `ws://` on an `https` page (browsers block it).

## 2. Messages

```ts
export type AppointmentEvent =
  | { type: 'APPOINTMENT_CREATED'; appointment: Appointment }
  | { type: 'APPOINTMENT_UPDATED'; appointment: Appointment }
  | { type: 'APPOINTMENT_DELETED'; id: number };
```
Field names identical to the backend. Narrow with `switch (msg.type)`.

## 3. Socket class

```ts
@Injectable({ providedIn: 'root' })
export class AppointmentSocket {
  private readonly auth = inject(AuthService);

  /** Cold, shared: connects on first subscriber, closes when the last one leaves. */
  readonly messages$: Observable<AppointmentEvent> = defer(() =>
    webSocket<AppointmentEvent>({
      // Browsers can't set headers on the handshake → token goes in the query string (backend must read it there).
      url: wsUrl(`/ws/appointments?token=${encodeURIComponent(this.auth.token() ?? '')}`),
    })
  ).pipe(
    retry({ delay: (_, attempt) => timer(Math.min(1000 * 2 ** attempt, 30_000)) }),
    share()
  );
}
```
Rules:
- `defer` so the token is read on every (re)connect, not once at construction.
- `retry` with exponential backoff, capped. No infinite tight loops.
- `share()` → one connection no matter how many subscribers.
- Sending: keep the `WebSocketSubject` in a field and call `.next(msg)`; only if the channel is bidirectional.
- Don't connect when logged out: `filter(() => !!this.auth.token())` upstream or guard the consumer.
- Close codes: if the server closes with an auth error (e.g. 4401/1008), call `auth.logout()` instead of retrying.

## 4. Consuming

Prefer **events → reload** over patching local state; the REST resource stays the source of truth:

```ts
// in XxxResourceService / component
private readonly socket = inject(AppointmentSocket);
constructor() {
  this.socket.messages$.pipe(takeUntilDestroyed()).subscribe(() => this.appointments.reload());
}
```
Or as a signal for a live value: `readonly last = toSignal(this.socket.messages$, { initialValue: null });` (auto-unsubscribes on destroy).

Debounce bursts before reloading: `debounceTime(300)`.

## 5. Production (nginx)

```nginx
location /ws/ {
  proxy_pass http://backend;
  proxy_http_version 1.1;
  proxy_set_header Upgrade $http_upgrade;
  proxy_set_header Connection "upgrade";
  proxy_read_timeout 3600s;
}
```
Free-tier hosts (e.g. Render) idle-kill connections → the backoff retry above handles it; add a server ping if drops are frequent.

## Checklist

- [ ] Proxy entry with `ws: true`; nginx `Upgrade`/`Connection` headers
- [ ] URL from `wsUrl()`, `wss:` under https
- [ ] Token read inside `defer`, sent as query param (or first message, if the backend expects that)
- [ ] Typed discriminated-union messages
- [ ] `retry` with capped backoff + `share()`
- [ ] Unsubscribed via `takeUntilDestroyed`/`toSignal`; no connection when logged out
