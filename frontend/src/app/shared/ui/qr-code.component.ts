import { ChangeDetectionStrategy, Component, input, resource } from '@angular/core';

/** Renders a QR payload as an image on a solid white square (scanners need contrast, never glass). */
@Component({
  selector: 'app-qr-code',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block aspect-square rounded-2xl bg-white p-3' },
  template: `
    @if (image.value(); as src) {
      <img [src]="src" [alt]="alt()" class="h-full w-full [image-rendering:pixelated]" />
    } @else if (image.error()) {
      <p
        class="flex h-full items-center justify-center text-center font-mono text-xs break-all text-gray-700"
      >
        {{ value() }}
      </p>
    } @else {
      <div class="skeleton h-full w-full"></div>
    }
  `,
})
export class QrCodeComponent {
  readonly value = input.required<string>();
  readonly alt = input.required<string>();

  protected readonly image = resource({
    params: () => this.value(),
    loader: async ({ params }) => {
      // qrcode is CJS: prod build nests exports under `default`, dev server doesn't.
      const mod = await import('qrcode');
      const { toDataURL } = mod.default ?? mod;
      return toDataURL(params, { margin: 1, width: 560, errorCorrectionLevel: 'M' });
    },
  });
}
