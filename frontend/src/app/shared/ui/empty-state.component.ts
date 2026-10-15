import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconName, UiIconComponent } from './ui-icon.component';

@Component({
  selector: 'app-empty-state',
  imports: [UiIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'flex flex-col items-center rounded-2xl border-2 border-dashed border-gray-900/10 px-6 py-10 text-center',
  },
  template: `
    <span
      class="mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-white/60 text-gray-400"
    >
      <app-ui-icon [name]="icon()" class="h-6 w-6" />
    </span>
    <p class="font-bold text-gray-900">{{ title() }}</p>
    @if (message()) {
      <p class="mt-1 max-w-sm text-sm text-gray-500">{{ message() }}</p>
    }
    <div class="mt-4 empty:hidden"><ng-content /></div>
  `,
})
export class EmptyStateComponent {
  readonly icon = input<IconName>('alert');
  readonly title = input.required<string>();
  readonly message = input<string>();
}
