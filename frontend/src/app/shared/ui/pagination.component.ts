import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { UiIconComponent } from './ui-icon.component';

/** Zero-based; hidden when there is a single page. */
@Component({
  selector: 'app-pagination',
  imports: [UiIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages() > 1) {
      <nav
        class="flex items-center justify-between gap-3 text-sm text-gray-500"
        aria-label="Paginación"
      >
        <p>
          Página <strong class="text-gray-900">{{ page() + 1 }}</strong> de {{ totalPages() }} ·
          {{ totalItems() }} registros
        </p>
        <div class="flex gap-1">
          <button
            type="button"
            class="btn-icon"
            aria-label="Página anterior"
            [disabled]="page() === 0"
            (click)="pageChange.emit(page() - 1)"
          >
            <app-ui-icon name="chevronLeft" class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="btn-icon"
            aria-label="Página siguiente"
            [disabled]="page() >= totalPages() - 1"
            (click)="pageChange.emit(page() + 1)"
          >
            <app-ui-icon name="chevronRight" class="h-4 w-4" />
          </button>
        </div>
      </nav>
    }
  `,
})
export class PaginationComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalItems = input.required<number>();
  readonly pageChange = output<number>();
}
