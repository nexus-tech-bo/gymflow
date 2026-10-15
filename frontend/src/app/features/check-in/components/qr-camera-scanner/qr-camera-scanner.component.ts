import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { UiIconComponent } from '../../../../shared/ui/ui-icon.component';

/** Not in lib.dom yet (Chromium/Android only). */
declare class BarcodeDetector {
  constructor(options: { formats: string[] });
  static getSupportedFormats(): Promise<string[]>;
  detect(source: HTMLVideoElement): Promise<{ rawValue: string }[]>;
}

type QrDecoder = (video: HTMLVideoElement) => Promise<string | null>;

const MAX_DECODE_FAILURES = 30;
const MAX_FRAME_WIDTH = 640;

/** Opens the rear camera on render, emits the first QR text read. Camera stops on any exit, including destroy. */
@Component({
  selector: 'app-qr-camera-scanner',
  imports: [UiIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block space-y-3' },
  template: `
    <video
      #video
      [hidden]="error()"
      class="aspect-square w-full rounded-3xl bg-gray-900 object-cover"
      playsinline
      muted
      aria-label="Vista previa de la cámara"
    ></video>
    @if (error()) {
      <p class="form-error" role="alert">{{ error() }}</p>
    }
    <button #cancelButton type="button" class="btn btn-secondary w-full" (click)="cancelled.emit()">
      <app-ui-icon name="x" class="h-5 w-5" />
      {{ error() ? 'Cerrar' : 'Cancelar escaneo' }}
    </button>
  `,
})
export class QrCameraScannerComponent {
  readonly scanned = output<string>();
  readonly cancelled = output<void>();

  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private readonly cancelButton = viewChild.required<ElementRef<HTMLButtonElement>>('cancelButton');
  protected readonly error = signal('');

  /** False once stopped (destroy, success, error): every await re-checks it. */
  private alive = true;
  private stream: MediaStream | null = null;
  private frame = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop());
    afterNextRender(() => {
      this.cancelButton().nativeElement.focus({ preventScroll: true });
      void this.start();
    });
  }

  private async start(): Promise<void> {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      return this.fail('La cámara solo funciona en una conexión segura (HTTPS o localhost).');
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false,
      });
      if (!this.alive) return stream.getTracks().forEach((t) => t.stop());
      this.stream = stream;
      const video = this.video()!.nativeElement;
      video.srcObject = stream;
      await video.play();
      if (!this.alive) return;
      const decode = await this.createDecoder();
      if (!this.alive) return;
      this.scanFrames(video, decode);
    } catch (e) {
      // closed mid-start (e.g. play() AbortError): not a user-facing error
      if (this.alive) this.fail(this.cameraErrorMessage(e));
    }
  }

  private stop(): void {
    this.alive = false;
    cancelAnimationFrame(this.frame);
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    const video = this.video()?.nativeElement;
    if (video) video.srcObject = null;
  }

  private fail(message: string): void {
    this.stop();
    this.error.set(message);
  }

  private scanFrames(video: HTMLVideoElement, decode: QrDecoder): void {
    let failures = 0;
    const tick = async () => {
      let text: string | undefined;
      try {
        text = (await decode(video))?.trim();
        failures = 0;
      } catch {
        if (++failures >= MAX_DECODE_FAILURES) {
          if (this.alive) this.fail('No se pudo leer la cámara. Intenta de nuevo.');
          return;
        }
      }
      if (!this.alive) return;
      if (!text) {
        this.frame = requestAnimationFrame(tick);
        return;
      }
      this.stop();
      this.scanned.emit(text);
    };
    this.frame = requestAnimationFrame(tick);
  }

  /** Native BarcodeDetector when it supports QR, otherwise jsQR (lazy-loaded) over downscaled canvas frames. */
  private async createDecoder(): Promise<QrDecoder> {
    if (
      'BarcodeDetector' in window &&
      (await BarcodeDetector.getSupportedFormats()).includes('qr_code')
    ) {
      const detector = new BarcodeDetector({ formats: ['qr_code'] });
      return async (video) => (await detector.detect(video))[0]?.rawValue ?? null;
    }
    const { default: jsQR } = await import('jsqr');
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true })!;
    return async (video) => {
      const { videoWidth, videoHeight } = video;
      if (!videoWidth || !videoHeight) return null;
      const scale = Math.min(1, MAX_FRAME_WIDTH / videoWidth);
      const w = Math.round(videoWidth * scale);
      const h = Math.round(videoHeight * scale);
      // resizing clears and reallocates the canvas: only on dimension change
      if (canvas.width !== w || canvas.height !== h) {
        canvas.width = w;
        canvas.height = h;
      }
      ctx.drawImage(video, 0, 0, w, h);
      return jsQR(ctx.getImageData(0, 0, w, h).data, w, h)?.data ?? null;
    };
  }

  private cameraErrorMessage(e: unknown): string {
    switch (e instanceof DOMException ? e.name : '') {
      case 'NotAllowedError':
      case 'SecurityError':
        return 'Permiso de cámara denegado. Habilítalo en la configuración del navegador.';
      case 'NotFoundError':
      case 'OverconstrainedError':
        return 'No se encontró una cámara en este dispositivo.';
      case 'NotReadableError':
        return 'La cámara está en uso por otra aplicación.';
      default:
        return 'No se pudo abrir la cámara. Intenta de nuevo.';
    }
  }
}
