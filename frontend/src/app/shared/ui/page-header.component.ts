import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'mb-6 flex flex-wrap items-end justify-between gap-4' },
  template: `
    <div class="min-w-0">
      <div class="flex flex-wrap items-center gap-3">
        <h1 class="text-[28px] font-extrabold tracking-tight text-gray-900 sm:text-[34px]">
          {{ title() }}
        </h1>
        <ng-content select="[headerChips]" />
      </div>
      @if (subtitle()) {
        <p class="mt-1 text-[15px] font-medium text-gray-500">{{ subtitle() }}</p>
      }
    </div>
    <div class="flex w-full flex-wrap gap-2 sm:w-auto [&>*]:flex-1 sm:[&>*]:flex-none">
      <ng-content />
    </div>
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>();
}
