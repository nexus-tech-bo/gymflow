import { Routes } from '@angular/router';
import { MANAGER_ROLES } from './core/auth/auth.service';
import { authGuard, guestGuard, landingRedirect, roleGuard } from './core/auth/guards';

const managers = roleGuard(...MANAGER_ROLES);

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: landingRedirect },
  {
    path: '',
    loadComponent: () =>
      import('./layout/public-layout.component').then((m) => m.PublicLayoutComponent),
    children: [
      {
        path: 'login',
        title: 'Ingresar · GymFlow',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/login-page.component').then((m) => m.LoginPageComponent),
      },
      {
        path: 'registro',
        title: 'Registra tu gimnasio · GymFlow',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/register-page.component').then((m) => m.RegisterPageComponent),
      },
      {
        path: 'card/:token',
        title: 'Mi tarjeta · GymFlow',
        loadComponent: () =>
          import('./features/card/public-card-page.component').then(
            (m) => m.PublicCardPageComponent,
          ),
      },
    ],
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: landingRedirect },
      {
        path: 'dashboard',
        title: 'Dashboard · GymFlow',
        loadComponent: () =>
          import('./features/dashboard/dashboard-page.component').then(
            (m) => m.DashboardPageComponent,
          ),
      },
      {
        path: 'check-in',
        title: 'Check-in · GymFlow',
        loadComponent: () =>
          import('./features/check-in/check-in-page.component').then((m) => m.CheckInPageComponent),
      },
      {
        path: 'socios',
        title: 'Socios · GymFlow',
        loadComponent: () =>
          import('./features/members/members-page.component').then((m) => m.MembersPageComponent),
      },
      {
        path: 'socios/:id',
        title: 'Socio · GymFlow',
        loadComponent: () =>
          import('./features/members/member-detail-page.component').then(
            (m) => m.MemberDetailPageComponent,
          ),
      },
      {
        path: 'caja',
        title: 'Caja · GymFlow',
        loadComponent: () =>
          import('./features/cash/cash-page.component').then((m) => m.CashPageComponent),
      },
      {
        path: 'planes',
        title: 'Planes · GymFlow',
        loadComponent: () =>
          import('./features/plans/plans-page.component').then((m) => m.PlansPageComponent),
      },
      {
        path: 'cajas',
        title: 'Historial de cajas · GymFlow',
        canActivate: [managers],
        loadComponent: () =>
          import('./features/cash/cash-sessions-page.component').then(
            (m) => m.CashSessionsPageComponent,
          ),
      },
      {
        path: 'cajas/:id',
        title: 'Detalle de caja · GymFlow',
        canActivate: [managers],
        loadComponent: () =>
          import('./features/cash/cash-session-detail-page.component').then(
            (m) => m.CashSessionDetailPageComponent,
          ),
      },
      {
        path: 'reportes/ingresos',
        title: 'Ingresos · GymFlow',
        canActivate: [managers],
        loadComponent: () =>
          import('./features/reports/income-report-page.component').then(
            (m) => m.IncomeReportPageComponent,
          ),
      },
      {
        path: 'staff',
        title: 'Staff · GymFlow',
        canActivate: [managers],
        loadComponent: () =>
          import('./features/staff/staff-page.component').then((m) => m.StaffPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: landingRedirect },
];
