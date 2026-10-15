import { Clipboard } from '@angular/cdk/clipboard';
import { Dialog } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Params, RouterLink } from '@angular/router';
import { Observable, filter, switchMap } from 'rxjs';
import { MembersApi } from '../../core/api/members.api';
import { MembershipsApi } from '../../core/api/memberships.api';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage, isNotFound } from '../../core/http/api-error';
import {
  MemberDetailResponse,
  MembershipResponse,
  MembershipStatus,
} from '../../core/models/api.models';
import { ageFrom, formatMoney } from '../../core/utils/format.util';
import {
  MEMBERSHIP_STATUS,
  PAYMENT_METHOD,
  VOIDED_CHIP,
  activeChip,
  expiringChip,
} from '../../core/utils/labels.util';
import { LimaDateTimePipe, LocalDatePipe, MoneyPipe } from '../../shared/pipes/format.pipes';
import { ConfirmOptions, ConfirmService } from '../../shared/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { QrCodeComponent } from '../../shared/ui/qr-code.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { MemberFormData, MemberFormDialogComponent } from './member-form-dialog.component';
import {
  SellMembershipData,
  SellMembershipDialogComponent,
} from './sell-membership-dialog.component';

const CANCELLABLE: MembershipStatus[] = ['ACTIVE', 'FROZEN', 'SCHEDULED'];

@Component({
  selector: 'app-member-detail-page',
  imports: [
    RouterLink,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusChipComponent,
    UiIconComponent,
    QrCodeComponent,
    MoneyPipe,
    LocalDatePipe,
    LimaDateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './member-detail-page.component.html',
})
export class MemberDetailPageComponent {
  private readonly membersApi = inject(MembersApi);
  private readonly membershipsApi = inject(MembershipsApi);
  private readonly dialog = inject(Dialog);
  private readonly confirm = inject(ConfirmService);
  private readonly notifications = inject(NotificationService);
  private readonly clipboard = inject(Clipboard);
  protected readonly isManager = inject(AuthService).isManager;

  readonly id = input.required({ transform: numberAttribute });
  /** '/x/abc' → not found without hitting the API. */
  private readonly validId = computed(() => Number.isInteger(this.id()) && this.id() > 0);

  /** List filters handed over by the Socios link (history state), so "← Socios" restores them. */
  protected readonly listQuery: Params =
    (history.state as { listQuery?: Params } | null)?.listQuery ?? {};

  private readonly memberResource = rxResource({
    params: () => (this.validId() ? this.id() : undefined),
    stream: ({ params }) => this.membersApi.get(params),
  });
  private readonly qrResource = rxResource({
    params: () => (this.validId() ? this.id() : undefined),
    stream: ({ params }) => this.membersApi.qr(params),
  });

  protected readonly member = computed(() =>
    this.memberResource.hasValue() ? this.memberResource.value() : undefined,
  );
  protected readonly isLoading = this.memberResource.isLoading;
  protected readonly notFound = computed(
    () => !this.validId() || isNotFound(this.memberResource.error()),
  );
  protected readonly errorMessage = computed(() =>
    this.memberResource.error()
      ? apiErrorMessage(this.memberResource.error(), 'No se pudo cargar el socio.')
      : '',
  );
  protected readonly qr = computed(() =>
    this.qrResource.hasValue() ? this.qrResource.value() : undefined,
  );
  protected readonly qrError = computed(() => !!this.qrResource.error());
  protected readonly cardUrl = computed(() => {
    const qr = this.qr();
    return qr ? `${location.origin}/card/${qr.qrToken}` : '';
  });

  protected readonly current = computed(() => this.member()?.currentMembership ?? null);
  protected readonly sellLabel = computed(() => (this.current() ? 'Renovar' : 'Vender membresía'));

  protected readonly statusChip = MEMBERSHIP_STATUS;
  protected readonly methodLabel = PAYMENT_METHOD;
  protected readonly voidedChip = VOIDED_CHIP;
  protected readonly activeChip = activeChip;
  protected readonly expiringChip = expiringChip;
  protected readonly ageFrom = ageFrom;
  protected readonly reload = () => this.memberResource.reload();
  protected readonly reloadQr = () => this.qrResource.reload();

  protected canCancel(m: MembershipResponse): boolean {
    return CANCELLABLE.includes(m.status);
  }

  protected edit(member: MemberDetailResponse): void {
    this.dialog
      .open<MemberDetailResponse, MemberFormData>(MemberFormDialogComponent, { data: { member } })
      .closed.pipe(filter(Boolean))
      .subscribe(() => {
        this.notifications.success('Socio actualizado');
        this.reload();
      });
  }

  protected toggleActive(member: MemberDetailResponse): void {
    if (!member.active)
      return this.run(this.membersApi.setActive(member.id, true), 'Socio activado');
    this.confirmThen(
      {
        title: 'Desactivar socio',
        message: `¿Desactivar a ${member.fullName}? No podrá ingresar al gimnasio hasta que lo reactives.`,
        confirmLabel: 'Desactivar',
        danger: true,
      },
      () => this.membersApi.setActive(member.id, false),
      'Socio desactivado',
    );
  }

  protected sell(member: MemberDetailResponse): void {
    this.dialog
      .open<MembershipResponse, SellMembershipData>(SellMembershipDialogComponent, {
        data: { memberId: member.id, memberName: member.fullName },
      })
      .closed.pipe(filter(Boolean))
      .subscribe((m) => {
        const method = m.payment ? ` (${PAYMENT_METHOD[m.payment.method]})` : '';
        this.notifications.success(
          `Membresía vendida · ${formatMoney(m.payment?.amount ?? m.price)}${method}`,
        );
        this.reload();
      });
  }

  protected freeze(m: MembershipResponse): void {
    this.confirmThen(
      {
        title: 'Congelar membresía',
        message: 'Se pausarán los días restantes hasta que la descongeles.',
        confirmLabel: 'Congelar',
      },
      () => this.membershipsApi.freeze(m.id),
      'Membresía congelada',
    );
  }

  protected unfreeze(m: MembershipResponse): void {
    this.run(this.membershipsApi.unfreeze(m.id), 'Membresía descongelada');
  }

  protected cancel(m: MembershipResponse): void {
    this.confirmThen(
      {
        title: 'Cancelar membresía',
        message: `¿Cancelar la membresía ${m.planName}? Esta acción no se puede deshacer. El pago no se anula automáticamente.`,
        confirmLabel: 'Cancelar membresía',
        cancelLabel: 'Volver',
        danger: true,
      },
      () => this.membershipsApi.cancel(m.id),
      'Membresía cancelada',
    );
  }

  protected copyLink(): void {
    if (this.clipboard.copy(this.cardUrl())) this.notifications.success('Enlace copiado');
    else this.notifications.error('No se pudo copiar el enlace.');
  }

  protected rotateQr(member: MemberDetailResponse): void {
    this.confirm
      .ask({
        title: 'Regenerar QR',
        message: 'El QR y el enlace actuales dejarán de funcionar.',
        confirmLabel: 'Regenerar QR',
        danger: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.membersApi.rotateQr(member.id)),
      )
      .subscribe({
        next: (qr) => {
          this.qrResource.set(qr);
          this.notifications.success('QR regenerado');
        },
        error: (e: unknown) => this.notifications.error(apiErrorMessage(e)),
      });
  }

  private confirmThen<T>(
    options: ConfirmOptions,
    action: () => Observable<T>,
    success: string,
  ): void {
    this.confirm
      .ask(options)
      .pipe(filter(Boolean))
      .subscribe(() => this.run(action(), success));
  }

  private run<T>(request$: Observable<T>, success: string): void {
    request$.subscribe({
      next: () => {
        this.notifications.success(success);
        this.reload();
      },
      error: (e: unknown) => this.notifications.error(apiErrorMessage(e)),
    });
  }
}
