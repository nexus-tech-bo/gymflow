import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { MembersApi } from '../../core/api/members.api';
import { MEMBERSHIP_STATUS } from '../../core/utils/labels.util';
import { LocalDatePipe } from '../../shared/pipes/format.pipes';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { QrCodeComponent } from '../../shared/ui/qr-code.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';

/** Public member card (no auth, mobile-first). The token is the member's qrToken. */
@Component({
  selector: 'app-public-card-page',
  imports: [EmptyStateComponent, QrCodeComponent, StatusChipComponent, LocalDatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './public-card-page.component.html',
  host: { class: 'w-full max-w-sm' },
})
export class PublicCardPageComponent {
  private readonly api = inject(MembersApi);
  readonly token = input.required<string>();

  private readonly card = rxResource({
    params: () => this.token(),
    stream: ({ params }) => this.api.publicCard(params),
  });
  protected readonly data = computed(() => (this.card.hasValue() ? this.card.value() : undefined));
  protected readonly failed = computed(() => !!this.card.error());
  protected readonly inactive = computed(() => {
    const status = this.data()?.status;
    return !status || status === 'EXPIRED' || status === 'FROZEN' || status === 'CANCELLED';
  });
  protected readonly statusChip = MEMBERSHIP_STATUS;

  constructor() {
    const title = inject(Title);
    effect(() => {
      const gym = this.data()?.gymName;
      if (gym) title.setTitle(`${gym} · Mi tarjeta`);
    });
  }
}
