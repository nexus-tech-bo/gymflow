import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** /login, /registro, /card/:token: blob background, centered content, no sidebar. */
@Component({
  selector: 'app-public-layout',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="relative min-h-dvh overflow-hidden bg-gradient-to-b from-[#eef2f7] via-[#f5f7fa] to-[#eef3f6]"
    >
      <div class="bg-blob -top-56 -left-28 h-[560px] w-[560px] bg-primary/20"></div>
      <div class="bg-blob top-16 -right-40 h-[520px] w-[520px] bg-secondary/[0.22]"></div>
      <div class="bg-blob -bottom-52 left-1/3 h-[460px] w-[460px] bg-tertiary/[0.13]"></div>
      <main class="relative z-10 flex min-h-dvh flex-col items-center justify-center px-4 py-8">
        <router-outlet />
      </main>
    </div>
  `,
})
export class PublicLayoutComponent {}
