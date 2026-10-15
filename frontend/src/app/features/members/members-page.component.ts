import { Dialog } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { MembersApi } from '../../core/api/members.api';
import { apiErrorMessage } from '../../core/http/api-error';
import { MemberDetailResponse, MemberSummaryResponse } from '../../core/models/api.models';
import { initials } from '../../core/utils/format.util';
import { MEMBERSHIP_STATUS, activeChip, expiringChip } from '../../core/utils/labels.util';
import { LocalDatePipe } from '../../shared/pipes/format.pipes';
import { NotificationService } from '../../core/services/notification.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { MemberFormData, MemberFormDialogComponent } from './member-form-dialog.component';

const MEMBERS_PAGE_SIZE = 20;

@Component({
  selector: 'app-members-page',
  imports: [
    RouterLink,
    PageHeaderComponent,
    EmptyStateComponent,
    PaginationComponent,
    StatusChipComponent,
    UiIconComponent,
    LocalDatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './members-page.component.html',
})
export class MembersPageComponent {
  private readonly api = inject(MembersApi);
  private readonly router = inject(Router);
  private readonly dialog = inject(Dialog);
  private readonly notifications = inject(NotificationService);

  /** URL query params (?q=&page=) so back button and reload keep the state. */
  readonly q = input<string>();
  readonly page = input(0, { transform: (v: unknown) => numberAttribute(v, 0) });

  protected readonly query = computed(() => (this.q() ?? '').trim());
  private readonly members = rxResource({
    params: () => ({ q: this.query(), page: this.page() }),
    stream: ({ params }) =>
      this.api.search({ ...params, size: MEMBERS_PAGE_SIZE, sort: 'fullName,asc' }),
  });
  protected readonly result = computed(() =>
    this.members.hasValue() ? this.members.value() : undefined,
  );
  protected readonly rows = computed(() => this.result()?.items ?? ([] as MemberSummaryResponse[]));
  protected readonly isLoading = this.members.isLoading;
  protected readonly errorMessage = computed(() =>
    this.members.error()
      ? apiErrorMessage(this.members.error(), 'No se pudo cargar los socios.')
      : '',
  );
  protected readonly reload = () => this.members.reload();

  protected readonly statusChip = MEMBERSHIP_STATUS;
  protected readonly activeChip = activeChip;
  protected readonly expiringChip = expiringChip;
  protected readonly initials = initials;

  private searchTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.searchTimer));
  }

  protected onSearch(value: string): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(
      () => this.navigate({ q: value.trim() || null, page: null }),
      300,
    );
  }

  protected clearSearch(): void {
    clearTimeout(this.searchTimer);
    this.navigate({ q: null, page: null });
  }

  protected setPage(page: number): void {
    this.navigate({ page: page || null });
  }

  protected openCreate(): void {
    this.dialog
      .open<MemberDetailResponse, MemberFormData>(MemberFormDialogComponent, { data: {} })
      .closed.pipe(filter(Boolean))
      .subscribe((member) => {
        this.notifications.success('Socio creado');
        void this.router.navigate(['/socios', member.id]);
      });
  }

  private navigate(queryParams: Record<string, string | number | null>): void {
    void this.router.navigate([], {
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl: 'q' in queryParams,
    });
  }
}
