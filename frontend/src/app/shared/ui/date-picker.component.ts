import { CdkTrapFocus } from '@angular/cdk/a11y';
import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  inject,
  Injector,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { addDays, formatLocalDate, parseLocalDate, todayLima } from '../../core/utils/format.util';
import { UiIconComponent } from './ui-icon.component';

export interface DateRange {
  from: string;
  to: string;
}

interface DayCell {
  iso: string;
  day: number;
  inMonth: boolean;
  isStart: boolean;
  isEnd: boolean;
  inRange: boolean;
  isToday: boolean;
  disabled: boolean;
}

/**
 * Below the trigger, flipping above / aligning to the end when the viewport is short.
 * No push: on short viewports the panel shrinks and scrolls (flexible dimensions) instead of covering its trigger.
 */
const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 8 },
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 8 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -8 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -8 },
];

const monthName = new Intl.DateTimeFormat('es-PE', { month: 'long', timeZone: 'UTC' });
const longDate = new Intl.DateTimeFormat('es-PE', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

/** 'YYYY-MM-01' shifted by `months`. */
function shiftMonth(firstOfMonth: string, months: number): string {
  const [y, m] = firstOfMonth.split('-').map(Number);
  const index = y * 12 + (m - 1) + months;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}-01`;
}

/** Same day of month `months` away, clamped to that month's length (Jan 31 + 1 → Feb 28). */
function shiftDay(iso: string, months: number): string {
  const first = shiftMonth(iso.slice(0, 8) + '01', months);
  const lastDay = +addDays(shiftMonth(first, 1), -1).slice(8);
  return first.slice(0, 8) + String(Math.min(+iso.slice(8), lastDay)).padStart(2, '0');
}

/**
 * Calendar popover that replaces native date inputs (values are LocalDate 'YYYY-MM-DD').
 * - `single` (default): bind with `formControlName` / `[value]` + `(valueChange)`; one click selects and closes.
 * - `range`: `[from]`/`[to]` + `(rangeChange)`.
 * Rendered in a CDK overlay so scrolling dialog bodies don't clip it.
 */
@Component({
  selector: 'app-date-picker',
  imports: [CdkTrapFocus, CdkConnectedOverlay, CdkOverlayOrigin, UiIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'relative block' },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DatePickerComponent), multi: true },
  ],
  template: `
    <button
      type="button"
      cdkOverlayOrigin
      #trigger="cdkOverlayOrigin"
      class="field-input flex cursor-pointer items-center gap-2 whitespace-nowrap text-left"
      [class]="triggerClass()"
      [id]="inputId()"
      [disabled]="disabled()"
      [attr.aria-expanded]="isOpen()"
      [attr.aria-label]="ariaLabel() ? ariaLabel() + ': ' + label() : null"
      [attr.aria-invalid]="invalid() || null"
      [attr.aria-describedby]="invalid() ? describedBy() : null"
      aria-haspopup="dialog"
      (click)="isOpen() ? close() : open()"
    >
      <app-ui-icon name="calendar" class="h-4 w-4 text-gray-500" />
      <span class="min-w-0 flex-1 truncate" [class.text-gray-500]="isEmpty()">{{ label() }}</span>
      <app-ui-icon
        name="chevronDown"
        class="h-3.5 w-3.5 text-gray-500 transition-transform"
        [class.rotate-180]="isOpen()"
      />
    </button>

    <ng-template
      cdkConnectedOverlay
      [cdkConnectedOverlayOrigin]="trigger"
      [cdkConnectedOverlayOpen]="isOpen()"
      [cdkConnectedOverlayPositions]="positions"
      [cdkConnectedOverlayViewportMargin]="16"
      [cdkConnectedOverlayFlexibleDimensions]="true"
      [cdkConnectedOverlayHasBackdrop]="true"
      cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
      (backdropClick)="close()"
      (detach)="close()"
    >
      <div
        class="glass-modal max-h-full w-[min(19rem,calc(100vw-2rem))] overflow-y-auto rounded-3xl p-4"
        role="dialog"
        aria-modal="true"
        cdkTrapFocus
        [cdkTrapFocusAutoCapture]="true"
        [attr.aria-label]="
          mode() === 'single' ? 'Seleccionar fecha' : 'Seleccionar rango de fechas'
        "
      >
        <div class="mb-3 flex items-center justify-between gap-2">
          <button
            type="button"
            class="btn-icon"
            aria-label="Mes anterior"
            [disabled]="!canPrev()"
            (click)="moveMonth(-1)"
          >
            <app-ui-icon name="chevronLeft" class="h-4 w-4" />
          </button>
          <div class="flex items-center gap-1 text-sm font-extrabold text-gray-900">
            <span class="capitalize" aria-live="polite"
              >{{ monthLabel() }}<span class="sr-only"> {{ viewYear() }}</span></span
            >
            <select
              class="cursor-pointer rounded-lg bg-transparent px-1 py-0.5 font-extrabold hover:bg-white/60 focus-visible:ring-2 focus-visible:ring-primary-ink focus-visible:outline-none"
              aria-label="Año"
              (change)="moveMonth((+$any($event.target).value - viewYear()) * 12)"
            >
              @for (year of years(); track year) {
                <option [value]="year" [selected]="year === viewYear()">{{ year }}</option>
              }
            </select>
          </div>
          <button
            type="button"
            class="btn-icon"
            aria-label="Mes siguiente"
            [disabled]="!canNext()"
            (click)="moveMonth(1)"
          >
            <app-ui-icon name="chevronRight" class="h-4 w-4" />
          </button>
        </div>

        <div
          aria-hidden="true"
          class="mb-1.5 grid grid-cols-7 text-center text-[11px] font-bold tracking-wide text-gray-500"
        >
          @for (weekday of weekdayLabels; track $index) {
            <span>{{ weekday }}</span>
          }
        </div>

        <div
          class="grid grid-cols-7 gap-y-1"
          (mouseleave)="hoverIso.set(null)"
          (keydown)="onGridKeydown($event)"
        >
          @for (cell of cells(); track cell.iso) {
            <button
              type="button"
              class="mx-auto flex h-9 w-9 items-center justify-center rounded-full text-[13px] font-semibold transition-colors focus-visible:ring-2 focus-visible:ring-primary-ink focus-visible:outline-none"
              [class]="cellClass(cell)"
              [disabled]="cell.disabled"
              [tabindex]="cell.iso === tabStop() ? 0 : -1"
              [attr.cdk-focus-initial]="cell.iso === tabStop() ? '' : null"
              [attr.data-iso]="cell.iso"
              [attr.aria-label]="longLabel(cell.iso)"
              [attr.aria-pressed]="cell.isStart || cell.isEnd"
              [attr.aria-current]="cell.isToday ? 'date' : null"
              (click)="pick(cell.iso)"
              (mouseenter)="mode() === 'range' && hoverIso.set(cell.iso)"
              (focus)="focusIso.set(cell.iso); mode() === 'range' && hoverIso.set(cell.iso)"
            >
              {{ cell.day }}
            </button>
          }
        </div>

        @if (mode() === 'range' && draftStart() && !draftEnd()) {
          <p class="mt-2 text-center text-xs text-gray-500">Elige la fecha final</p>
        }

        <div class="mt-3.5 flex flex-wrap gap-1.5 border-t border-white/60 pt-3.5">
          @if (todayAllowed()) {
            <button type="button" class="btn btn-secondary btn-sm" (click)="presetToday()">
              Hoy
            </button>
          }
          @if (mode() === 'range') {
            <button type="button" class="btn btn-secondary btn-sm" (click)="presetLastDays(7)">
              Últimos 7 días
            </button>
            <button type="button" class="btn btn-secondary btn-sm" (click)="presetThisMonth()">
              Este mes
            </button>
          } @else if (clearable() && value()) {
            <button type="button" class="btn btn-ghost btn-sm" (click)="clear()">
              Quitar fecha
            </button>
          }
        </div>
      </div>
    </ng-template>
  `,
})
export class DatePickerComponent implements ControlValueAccessor {
  readonly mode = input<'single' | 'range'>('single');
  /** Single mode without a form control. */
  readonly initialValue = input('', { alias: 'value' });
  readonly from = input('');
  readonly to = input('');
  /** Inclusive LocalDate bounds; days outside are disabled. */
  readonly min = input<string | null>(null);
  readonly max = input<string | null>(null);
  /** Id for the trigger, so `<label for>` works. */
  readonly inputId = input<string | null>(null);
  /**
   * Field name for the trigger's accessible name, e.g. "Fecha" → "Fecha: 06/10/2026".
   * Set it even with a `<label for>`: the label alone would hide the selected value from screen readers.
   */
  readonly ariaLabel = input<string | null>(null);
  readonly placeholder = input('Seleccionar fecha');
  /** Extra classes for the trigger (e.g. `py-2`). */
  readonly triggerClass = input('');
  /** Shows a "Quitar fecha" action (single mode) for optional fields. */
  readonly clearable = input(false);
  /** Error state for the trigger (`aria-invalid`), linked to the error text's id via `describedBy`. */
  readonly invalid = input(false);
  readonly describedBy = input<string | null>(null);

  readonly valueChange = output<string>();
  readonly rangeChange = output<DateRange>();

  protected readonly positions = POSITIONS;
  protected readonly weekdayLabels = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];
  /** Refreshed on open, so a page left open past midnight highlights the right day. */
  protected readonly today = signal(todayLima());
  private readonly injector = inject(Injector);

  protected readonly isOpen = signal(false);
  protected readonly disabled = signal(false);
  /** Follows `[value]`; overwritten by the form control (writeValue) or a pick. */
  protected readonly value = linkedSignal(() => this.initialValue());
  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  /** First day of the visible month, 'YYYY-MM-01'. */
  private readonly viewMonth = signal(this.today().slice(0, 8) + '01');
  /** Roving-tabindex day: the only day in the Tab order, moved with the arrow keys. */
  protected readonly focusIso = signal(this.today());
  protected readonly viewYear = computed(() => +this.viewMonth().slice(0, 4));
  protected readonly monthLabel = computed(() =>
    monthName.format(parseLocalDate(this.viewMonth())),
  );
  protected readonly canPrev = computed(
    () => !this.min() || this.viewMonth() > this.min()!.slice(0, 8) + '01',
  );
  protected readonly canNext = computed(
    () => !this.max() || this.viewMonth() < this.max()!.slice(0, 8) + '01',
  );
  protected readonly years = computed(() => {
    const thisYear = +this.today().slice(0, 4);
    const first = this.min() ? +this.min()!.slice(0, 4) : thisYear - 100;
    const last = this.max() ? +this.max()!.slice(0, 4) : thisYear + 10;
    return Array.from({ length: last - first + 1 }, (_, i) => last - i);
  });

  protected readonly draftStart = signal<string | null>(null);
  protected readonly draftEnd = signal<string | null>(null);
  protected readonly hoverIso = signal<string | null>(null);

  /** 6 full Mon–Sun weeks so the grid height is stable across months. */
  protected readonly cells = computed<DayCell[]>(() => {
    const first = this.viewMonth();
    const month = first.slice(0, 7);
    const lead = (parseLocalDate(first).getUTCDay() + 6) % 7;
    const gridStart = addDays(first, -lead);
    const start = this.draftStart();
    const end = this.draftEnd();
    const other = end ?? this.hoverIso();
    const lo = start && other ? (start < other ? start : other) : null;
    const hi = start && other ? (start < other ? other : start) : null;
    const today = this.today();
    return Array.from({ length: 42 }, (_, i) => {
      const iso = addDays(gridStart, i);
      return {
        iso,
        day: +iso.slice(8),
        inMonth: iso.startsWith(month),
        isStart: iso === start,
        isEnd: iso === end,
        inRange: !!lo && !!hi && iso > lo && iso < hi,
        isToday: iso === today,
        disabled: this.outOfBounds(iso),
      };
    });
  });

  /** Focused day if visible and enabled, else the month's first enabled day. */
  protected readonly tabStop = computed(() => {
    const cells = this.cells();
    const focus = this.focusIso();
    const cell =
      cells.find((c) => c.iso === focus && !c.disabled) ??
      cells.find((c) => c.inMonth && !c.disabled);
    return cell?.iso ?? null;
  });

  protected readonly isEmpty = computed(() => this.mode() === 'single' && !this.value());
  protected readonly label = computed(() => {
    if (this.mode() === 'single') {
      return this.value() ? formatLocalDate(this.value()) : this.placeholder();
    }
    const from = this.from();
    const to = this.to();
    return from === to
      ? formatLocalDate(from)
      : `${formatLocalDate(from)} – ${formatLocalDate(to)}`;
  });
  protected readonly todayAllowed = computed(() => !this.outOfBounds(this.today()));

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected open(): void {
    const single = this.mode() === 'single';
    const start = (single ? this.value() : this.from()) || null;
    this.draftStart.set(start);
    this.draftEnd.set(single ? start : this.to() || null);
    this.today.set(todayLima());
    const anchor = this.clamp(start || this.today());
    this.focusIso.set(anchor);
    this.viewMonth.set(anchor.slice(0, 8) + '01');
    this.isOpen.set(true);
  }

  protected close(): void {
    if (!this.isOpen()) return;
    this.isOpen.set(false);
    this.hoverIso.set(null);
    this.onTouched();
  }

  /** Moves the view (and the roving day with it), never past min/max. */
  protected moveMonth(months: number): void {
    const day = this.viewMonth().slice(0, 8) + this.focusIso().slice(8);
    const iso = this.clamp(shiftDay(day, months));
    this.focusIso.set(iso);
    this.viewMonth.set(iso.slice(0, 8) + '01');
  }

  /** ARIA date-grid keys; Enter/Space are the buttons' native click. */
  protected onGridKeydown(event: KeyboardEvent): void {
    const from = this.tabStop();
    if (!from) return;
    const weekday = (parseLocalDate(from).getUTCDay() + 6) % 7;
    const targets: Partial<Record<string, string>> = {
      ArrowLeft: addDays(from, -1),
      ArrowRight: addDays(from, 1),
      ArrowUp: addDays(from, -7),
      ArrowDown: addDays(from, 7),
      PageUp: shiftDay(from, -1),
      PageDown: shiftDay(from, 1),
      Home: addDays(from, -weekday),
      End: addDays(from, 6 - weekday),
    };
    const target = targets[event.key];
    if (!target) return;
    event.preventDefault();
    const iso = this.clamp(target);
    this.focusIso.set(iso);
    if (!iso.startsWith(this.viewMonth().slice(0, 8))) this.viewMonth.set(iso.slice(0, 8) + '01');
    const grid = event.currentTarget as HTMLElement;
    afterNextRender(() => grid.querySelector<HTMLElement>(`[data-iso="${iso}"]`)?.focus(), {
      injector: this.injector,
    });
  }

  protected pick(iso: string): void {
    if (this.outOfBounds(iso)) return;
    if (this.mode() === 'single') {
      this.emitValue(iso);
      this.close();
      return;
    }
    const start = this.draftStart();
    if (!start || this.draftEnd()) {
      this.draftStart.set(iso);
      this.draftEnd.set(null);
      return;
    }
    this.commit(iso < start ? { from: iso, to: start } : { from: start, to: iso });
  }

  protected clear(): void {
    this.emitValue('');
    this.close();
  }

  protected presetToday(): void {
    if (this.mode() === 'single') this.pick(this.today());
    else this.commit({ from: this.today(), to: this.today() });
  }

  // Range presets look backwards: gym-flow ranges are reports over past data.
  protected presetLastDays(days: number): void {
    const to = this.clamp(this.today());
    this.commit({ from: this.clamp(addDays(to, 1 - days)), to });
  }

  protected presetThisMonth(): void {
    const to = this.clamp(this.today());
    this.commit({ from: this.clamp(to.slice(0, 8) + '01'), to });
  }

  protected longLabel(iso: string): string {
    return longDate.format(parseLocalDate(iso));
  }

  /** Yellow fills carry dark text; yellow as text uses primary-ink (see @theme in styles.css). */
  protected cellClass(cell: DayCell): string {
    if (cell.disabled) return 'cursor-not-allowed text-gray-300 line-through';
    if (cell.isStart || cell.isEnd) return 'cursor-pointer bg-primary font-bold text-gray-900';
    if (cell.inRange) return 'cursor-pointer bg-primary/15 font-bold text-primary-ink';
    if (cell.isToday) {
      return 'cursor-pointer font-extrabold text-primary-ink ring-1 ring-primary-ink/40 hover:bg-white/70';
    }
    return cell.inMonth
      ? 'cursor-pointer text-gray-700 hover:bg-white/70'
      : 'cursor-pointer font-normal text-gray-500 hover:bg-white/40';
  }

  private emitValue(iso: string): void {
    this.value.set(iso);
    this.onChange(iso);
    this.valueChange.emit(iso);
  }

  private commit(range: DateRange): void {
    this.draftStart.set(range.from);
    this.draftEnd.set(range.to);
    this.rangeChange.emit(range);
    this.close();
  }

  private clamp(iso: string): string {
    const min = this.min();
    const max = this.max();
    return min && iso < min ? min : max && iso > max ? max : iso;
  }

  private outOfBounds(iso: string): boolean {
    const min = this.min();
    const max = this.max();
    return (!!min && iso < min) || (!!max && iso > max);
  }
}
