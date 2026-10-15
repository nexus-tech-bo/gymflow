import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PaymentResponse } from '../../core/models/api.models';
import { PAYMENT_METHOD, VOIDED_CHIP, neutralChip } from '../../core/utils/labels.util';
import { LimaDateTimePipe, MoneyPipe } from '../../shared/pipes/format.pipes';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';

/** Payments of a cash session (Caja and Detalle de caja). `canVoid` adds the void action. */
@Component({
  selector: 'app-payments-table',
  imports: [
    RouterLink,
    EmptyStateComponent,
    StatusChipComponent,
    UiIconComponent,
    MoneyPipe,
    LimaDateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './payments-table.component.html',
})
export class PaymentsTableComponent {
  readonly payments = input.required<PaymentResponse[]>();
  readonly canVoid = input(false);
  readonly voidPayment = output<PaymentResponse>();

  protected readonly methodChip = (p: PaymentResponse) => neutralChip(PAYMENT_METHOD[p.method]);
  protected readonly voidedChip = VOIDED_CHIP;
}
