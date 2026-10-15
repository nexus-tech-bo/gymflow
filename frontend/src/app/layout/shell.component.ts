import { CdkTrapFocus } from '@angular/cdk/a11y';
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, tap } from 'rxjs';
import { AuthService, MANAGER_ROLES } from '../core/auth/auth.service';
import { Role } from '../core/models/api.models';
import { ROLE } from '../core/utils/labels.util';
import { initials } from '../core/utils/format.util';
import { StatusChipComponent } from '../shared/ui/status-chip.component';
import { IconName, UiIconComponent } from '../shared/ui/ui-icon.component';

interface NavItem {
  label: string;
  icon: IconName;
  path: string;
  /** Omitted = every role. Keep in sync with the role guards in app.routes.ts. */
  roles?: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
  { label: 'Check-in', icon: 'scan', path: '/check-in' },
  { label: 'Socios', icon: 'users', path: '/socios' },
  { label: 'Caja', icon: 'wallet', path: '/caja' },
  { label: 'Planes', icon: 'tag', path: '/planes' },
  { label: 'Historial de cajas', icon: 'history', path: '/cajas', roles: MANAGER_ROLES },
  { label: 'Reportes', icon: 'chart', path: '/reportes/ingresos', roles: MANAGER_ROLES },
  { label: 'Staff', icon: 'staff', path: '/staff', roles: MANAGER_ROLES },
];

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    NgTemplateOutlet,
    CdkTrapFocus,
    UiIconComponent,
    StatusChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
})
export class ShellComponent {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly drawerOpen = signal(false);
  protected readonly roleChip = ROLE;
  protected readonly initials = initials;

  protected readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) => !item.roles || this.auth.hasRole(item.roles)),
  );

  /** Deepest route title without the " · GymFlow" suffix, for the mobile top bar. */
  protected readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      tap(() => this.drawerOpen.set(false)),
      map(() => this.currentTitle()),
    ),
    { initialValue: this.currentTitle() },
  );

  private currentTitle(): string {
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild) route = route.firstChild;
    return (route.title ?? 'GymFlow').split(' · ')[0];
  }
}
