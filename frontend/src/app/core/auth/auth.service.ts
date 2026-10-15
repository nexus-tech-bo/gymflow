import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationService } from '../services/notification.service';
import { AuthApi } from '../api/auth.api';
import { AuthResponse, MeResponse, Role } from '../models/api.models';

interface Session {
  token: string;
  /** epoch ms */
  expiresAt: number;
  user: MeResponse;
}

const STORAGE_KEY = 'gymflow.session';

export const MANAGER_ROLES: Role[] = ['OWNER', 'ADMIN'];

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const session = raw ? (JSON.parse(raw) as Session) : null;
    return session && session.expiresAt > Date.now() ? session : null;
  } catch {
    return null;
  }
}

function writeSession(session: Session | null): void {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // storage unavailable (private mode / quota): session lives in memory only
  }
}

/** Session state. No refresh/logout endpoint: logout just drops the token client-side. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly router = inject(Router);
  private readonly authApi = inject(AuthApi);
  private readonly notifications = inject(NotificationService);
  private readonly session = signal<Session | null>(readSession());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly role = computed(() => this.user()?.role ?? null);
  readonly isManager = computed(() => this.hasRole(MANAGER_ROLES));

  /** Pure read: an expired session is kept until expire()/logout() so a 401 can still redirect. */
  token(): string | null {
    const s = this.session();
    return s && s.expiresAt > Date.now() ? s.token : null;
  }

  isLoggedIn(): boolean {
    return !!this.token();
  }

  hasRole(roles: Role[]): boolean {
    const role = this.role();
    return !!role && roles.includes(role);
  }

  landingUrl(): string {
    if (!this.isLoggedIn()) return '/login';
    return this.role() === 'RECEPTIONIST' ? '/check-in' : '/dashboard';
  }

  start(auth: AuthResponse): void {
    this.set({
      token: auth.accessToken,
      expiresAt: Date.now() + auth.expiresIn * 1000,
      user: auth.user,
    });
  }

  /** Boot: refresh the stored user (role/gym may have changed). 401 is handled by the interceptor. */
  hydrate(): void {
    if (!this.isLoggedIn()) return;
    this.authApi.me().subscribe({
      next: (user) => {
        const session = this.session();
        if (session) this.set({ ...session, user });
      },
      error: () => undefined,
    });
  }

  logout(): void {
    this.clear();
    void this.router.navigateByUrl('/login');
  }

  /** Called on a 401 from a protected endpoint. */
  expire(): void {
    if (!this.session()) return;
    this.clear();
    this.notifications.error('Tu sesión expiró. Ingresa de nuevo.');
    const target = this.router.currentNavigation()?.finalUrl;
    const url = target ? this.router.serializeUrl(target) : this.router.url;
    const keep = url !== '/' && !url.startsWith('/login');
    void this.router.navigate(['/login'], { queryParams: keep ? { redirect: url } : {} });
  }

  private set(session: Session): void {
    this.session.set(session);
    writeSession(session);
  }

  private clear(): void {
    this.session.set(null);
    writeSession(null);
  }
}
