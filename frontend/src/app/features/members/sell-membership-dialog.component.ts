import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize, map } from 'rxjs';
import { CashApi } from '../../core/api/cash.api';
import { MembershipsApi } from '../../core/api/memberships.api';
import { PlansApi } from '../../core/api/plans.api';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage, applyServerErrors, fieldError } from '../../core/http/api-error';
import {
  MembershipResponse,
  MembershipSaleRequest,
  PaymentMethod,
  PlanResponse,
} from '../../core/models/api.models';
import { formatMoney } from '../../core/utils/format.util';
import { PAYMENT_METHOD, PAYMENT_METHODS } from '../../core/utils/labels.util';
import { MoneyPipe } from '../../shared/pipes/format.pipes';
import { DialogFrameComponent } from '../../shared/ui/dialog-frame.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { FieldA11yDirective } from '../../shared/ui/field-a11y.directive';
import { maxDecimals } from '../../core/utils/validators';

export interface SellMembershipData {
  memberId: number;
  memberName: string;
}

/** Sells/renews a membership. Needs an open cash session (checked on every open). Closes with the new membership. */
@Component({
  selector: 'app-sell-membership-dialog',
  imports: [
    ReactiveFormsModule,
    DialogFrameComponent,
    EmptyStateComponent,
    FieldA11yDirective,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './sell-membership-dialog.component.html',
})
export class SellMembershipDialogComponent {
  private readonly cashApi = inject(CashApi);
  private readonly plansApi = inject(PlansApi);
  private readonly membershipsApi = inject(MembershipsApi);
  private readonly router = inject(Router);
  protected readonly ref = inject<DialogRef<MembershipResponse>>(DialogRef);
  protected readonly data = inject<SellMembershipData>(DIALOG_DATA);
  /** Price override is OWNER/ADMIN only. */
  protected readonly canOverridePrice = inject(AuthService).isManager;

  /** One key per dialog instance: retries after an error reuse it, so a double submit can't sell twice. */
  private readonly idempotencyKey = crypto.randomUUID();

  /** The form renders after cash/plans load, too late for the dialog autofocus: focus Plan once it appears. */
  private readonly planSelect = viewChild<ElementRef<HTMLSelectElement>>('planSelect');
  private planFocused = false;
  private readonly focusPlan = afterRenderEffect(() => {
    const select = this.planSelect();
    if (select && !this.planFocused) {
      this.planFocused = true;
      select.nativeElement.focus();
    }
  });

  private readonly cash = rxResource({ stream: () => this.cashApi.current() });
  private readonly plans = rxResource({ stream: () => this.plansApi.list(false) });
  protected readonly isLoading = computed(
    () =>
      (this.cash.isLoading() && !this.cash.hasValue()) ||
      (this.plans.isLoading() && !this.plans.hasValue()),
  );
  protected readonly loadError = computed(() => {
    const error = this.cash.error() ?? this.plans.error();
    return error ? apiErrorMessage(error, 'No se pudo verificar la caja.') : '';
  });
  protected readonly cashOpen = computed(() =>
    this.cash.hasValue() ? !!this.cash.value().current : false,
  );
  protected readonly planList = computed(() =>
    this.plans.hasValue() ? this.plans.value() : ([] as PlanResponse[]),
  );
  protected readonly reloadAll = () => {
    this.cash.reload();
    this.plans.reload();
  };

  protected readonly methods = PAYMENT_METHODS;
  protected readonly methodLabel = PAYMENT_METHOD;
  private readonly fb = inject(FormBuilder);
  protected readonly form = this.fb.group({
    planId: [null as number | null, Validators.required],
    price: [
      null as number | null,
      [Validators.required, Validators.min(0), Validators.max(99999.99), maxDecimals(2)],
    ],
    paymentMethod: this.fb.nonNullable.control<PaymentMethod>('CASH'),
    paymentReference: this.fb.nonNullable.control('', Validators.maxLength(40)),
  });
  private readonly value = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    {
      initialValue: this.form.getRawValue(),
    },
  );
  protected readonly selectedPlan = computed(
    () => this.planList().find((p) => p.id === this.value().planId) ?? null,
  );
  protected readonly total = computed(() => {
    const plan = this.selectedPlan();
    if (!plan) return null;
    return this.canOverridePrice() ? (this.value().price ?? null) : plan.price;
  });
  protected readonly method = computed(() => this.value().paymentMethod);

  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly err = fieldError;

  protected planLabel(plan: PlanResponse): string {
    return `${plan.name} · ${plan.durationDays} días · ${formatMoney(plan.price)}`;
  }

  protected onPlanChange(): void {
    this.form.controls.price.setValue(this.selectedPlan()?.price ?? null);
  }

  protected goToCash(): void {
    this.ref.close();
    void this.router.navigateByUrl('/caja');
  }

  protected save(): void {
    if (this.saving()) return;
    if (this.form.invalid) return this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const plan = this.selectedPlan()!;
    const reference = v.paymentReference.trim();
    const request: MembershipSaleRequest = {
      planId: plan.id,
      paymentMethod: v.paymentMethod,
      idempotencyKey: this.idempotencyKey,
      ...(v.paymentMethod !== 'CASH' && reference ? { paymentReference: reference } : {}),
      ...(this.canOverridePrice() && v.price !== null && v.price !== plan.price
        ? { price: v.price }
        : {}),
    };
    this.saving.set(true);
    this.error.set('');
    this.membershipsApi
      .sell(this.data.memberId, request)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (membership) => this.ref.close(membership),
        error: (e: unknown) => this.error.set(applyServerErrors(this.form, e)),
      });
  }
}
