import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { CheckInsApi } from '../../core/api/check-ins.api';
import { HttpErrorResponse } from '@angular/common/http';
import { apiErrorMessage } from '../../core/http/api-error';
import { CheckInEntryResponse, CheckInResponse } from '../../core/models/api.models';
import { formatLocalDate, todayLima } from '../../core/utils/format.util';
import {
  CHECK_IN_METHOD,
  CHECK_IN_RESULT,
  Chip,
  DENIAL_REASON,
  neutralChip,
} from '../../core/utils/labels.util';
import { LimaDateTimePipe, LocalDatePipe } from '../../shared/pipes/format.pipes';
import { DatePickerComponent } from '../../shared/ui/date-picker.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { UiIconComponent } from '../../shared/ui/ui-icon.component';
import { QrCameraScannerComponent } from './components/qr-camera-scanner/qr-camera-scanner.component';

@Component({
  selector: 'app-check-in-page',
  imports: [
    RouterLink,
    PageHeaderComponent,
    EmptyStateComponent,
    DatePickerComponent,
    PaginationComponent,
    StatusChipComponent,
    UiIconComponent,
    QrCameraScannerComponent,
    LimaDateTimePipe,
    LocalDatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './check-in-page.component.html',
})
export class CheckInPageComponent {
  private readonly api = inject(CheckInsApi);
  private readonly codeInput = viewChild.required<ElementRef<HTMLInputElement>>('codeInput');

  // Scanner (USB/keyboard-wedge readers type "GF1:…" + Enter)
  protected readonly code = signal('');
  protected readonly submitting = signal(false);
  protected readonly result = signal<CheckInResponse | null>(null);
  protected readonly submitError = signal('');
  // Camera scanner (the child owns the camera; destroying it stops the stream)
  protected readonly scanning = signal(false);

  // Log
  protected readonly date = signal(todayLima());
  protected readonly page = signal(0);
  private readonly log = rxResource({
    params: () => ({ date: this.date(), page: this.page() }),
    stream: ({ params }) => this.api.list({ ...params, size: 20 }),
  });
  protected readonly logResult = computed(() =>
    this.log.hasValue() ? this.log.value() : undefined,
  );
  protected readonly rows = computed(
    () => this.logResult()?.items ?? ([] as CheckInEntryResponse[]),
  );
  protected readonly logLoading = this.log.isLoading;
  protected readonly logError = computed(() =>
    this.log.error() ? apiErrorMessage(this.log.error(), 'No se pudo cargar los ingresos.') : '',
  );
  protected readonly reloadLog = () => this.log.reload();
  protected readonly dateLabel = computed(() => formatLocalDate(this.date()));

  protected readonly resultChip = CHECK_IN_RESULT;
  protected readonly reasonLabel = DENIAL_REASON;
  protected readonly renewChip: Chip = { label: 'Vence pronto: recuérdale renovar', tone: 'warn' };
  protected readonly methodChip = (e: CheckInEntryResponse) =>
    neutralChip(CHECK_IN_METHOD[e.method]);

  /** Computed on use so a page left open past midnight rolls over. */
  protected get today(): string {
    return todayLima();
  }

  constructor() {
    afterNextRender(() => this.focusInput());
  }

  protected submit(): void {
    const code = this.code().trim();
    if (!code || this.submitting()) return;
    this.submitting.set(true);
    this.submitError.set('');
    this.api
      .checkIn({ code })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (response) => {
          this.result.set(response);
          beep(
            response.result === 'DENIED' ? 'denied' : response.duplicate ? 'duplicate' : 'allowed',
          );
          // a reader may have typed the next code meanwhile: only clear what was submitted
          if (this.code().trim() === code) this.code.set('');
          this.focusInput();
          if (this.date() === todayLima()) {
            if (this.page() === 0) this.log.reload();
            else this.page.set(0);
          }
        },
        error: (e: unknown) => {
          // 4xx carries a useful server message; network/5xx get the generic retry text
          const clientError = e instanceof HttpErrorResponse && e.status >= 400 && e.status < 500;
          this.submitError.set(
            clientError ? apiErrorMessage(e) : 'No se pudo registrar el ingreso. Intenta de nuevo.',
          );
          beep('denied');
          this.focusInput();
        },
      });
  }

  /** Camera read feeds the same flow as typing / USB reader. */
  protected onScanned(text: string): void {
    this.scanning.set(false);
    this.code.set(text);
    this.submit();
  }

  protected closeScanner(): void {
    this.scanning.set(false);
    this.focusInput();
  }

  protected setDate(value: string): void {
    this.date.set(value || todayLima());
    this.page.set(0);
  }

  private focusInput(): void {
    this.codeInput().nativeElement.focus();
  }
}

type CheckInOutcome = 'allowed' | 'duplicate' | 'denied';

let audio: AudioContext | null = null;

/** Tono corto: agudo si pasa, medio si ya estaba, grave si no puede pasar. */
function beep(outcome: CheckInOutcome): void {
  try {
    audio ??= new AudioContext();
    const osc = audio.createOscillator();
    const gain = audio.createGain();
    osc.frequency.value = outcome === 'allowed' ? 880 : outcome === 'duplicate' ? 600 : 220;
    gain.gain.value = 0.15;
    osc.connect(gain).connect(audio.destination);
    osc.start();
    osc.stop(audio.currentTime + (outcome === 'denied' ? 0.35 : 0.12));
  } catch {
    // sin audio (política del navegador): no pasa nada
  }
}
