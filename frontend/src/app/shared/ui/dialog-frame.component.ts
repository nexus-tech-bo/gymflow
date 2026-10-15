import { CdkDialogContainer, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, input } from '@angular/core';
import { UiIconComponent } from './ui-icon.component';

let nextId = 0;

/**
 * Shell of every dialog: title, close button, scrollable body and a `[dialogFooter]` slot
 * (must be a top-level child). Put `cdkFocusInitial` on the first field.
 */
@Component({
  selector: 'app-dialog-frame',
  imports: [UiIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'glass-modal flex max-h-[90vh] w-[calc(100vw-2rem)] max-w-lg flex-col rounded-3xl p-6',
  },
  template: `
    <header class="mb-5 flex items-start justify-between gap-4">
      <div class="min-w-0">
        <h2 [id]="titleId" class="text-xl font-extrabold text-gray-900">{{ title() }}</h2>
        @if (subtitle()) {
          <p class="mt-0.5 truncate text-sm font-medium text-gray-500">{{ subtitle() }}</p>
        }
      </div>
      <button type="button" class="btn-icon -mt-1 -mr-2" aria-label="Cerrar" (click)="ref.close()">
        <app-ui-icon name="x" class="h-[18px] w-[18px]" />
      </button>
    </header>
    <div class="-mx-1 min-h-0 flex-1 overflow-y-auto px-1 pb-1">
      <ng-content />
    </div>
    <footer class="mt-6 flex flex-col-reverse gap-2 empty:hidden sm:flex-row sm:justify-end">
      <ng-content select="[dialogFooter]" />
    </footer>
  `,
})
export class DialogFrameComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>();
  protected readonly ref = inject(DialogRef);
  protected readonly titleId = `dialog-title-${++nextId}`;

  constructor() {
    // Same hook Material's dialog title uses, so role=dialog gets aria-labelledby.
    const container = this.ref.containerInstance as CdkDialogContainer;
    container._addAriaLabelledBy?.(this.titleId);
    inject(DestroyRef).onDestroy(() => container._removeAriaLabelledBy?.(this.titleId));
  }
}
