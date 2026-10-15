import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Chip, Tone } from '../../core/utils/labels.util';

const TONE: Record<Tone, { badge: string; dot: string }> = {
  success: { badge: 'bg-secondary/10 text-secondary-ink', dot: 'bg-secondary' },
  info: { badge: 'bg-primary/10 text-primary-ink', dot: 'bg-primary' },
  warn: { badge: 'bg-tertiary/10 text-tertiary', dot: 'bg-tertiary' },
  danger: { badge: 'bg-red-50 text-red-600', dot: 'bg-red-500' },
  neutral: { badge: 'bg-gray-900/[0.06] text-gray-500', dot: 'bg-gray-400' },
};

/** The app-badge of the design system: `.chip` + tone (+ dot for statuses). */
@Component({
  selector: 'app-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="chip" [class]="style().badge" [attr.title]="title() || null">
      @if (chip().dot) {
        <span class="h-1.5 w-1.5 rounded-full" [class]="style().dot"></span>
      }
      {{ chip().label }}
    </span>
  `,
})
export class StatusChipComponent {
  readonly chip = input.required<Chip>();
  readonly title = input<string | null>();
  protected readonly style = computed(() => TONE[this.chip().tone]);
}
