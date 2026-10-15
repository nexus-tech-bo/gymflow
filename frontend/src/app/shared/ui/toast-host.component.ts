import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationService } from '../../core/services/notification.service';
import { UiIconComponent } from './ui-icon.component';

@Component({
  selector: 'app-toast-host',
  imports: [UiIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'pointer-events-none fixed top-4 right-4 z-[1100] flex w-[calc(100vw-2rem)] max-w-sm flex-col gap-2',
  },
  template: `
    @for (toast of notifications.toasts(); track toast.id) {
      <div
        class="glass-modal pointer-events-auto flex items-start gap-3 rounded-2xl p-4"
        [attr.role]="toast.type === 'error' ? 'alert' : 'status'"
      >
        <span
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
          [class]="
            toast.type === 'error' ? 'bg-red-100 text-red-600' : 'bg-emerald-100 text-emerald-600'
          "
        >
          <app-ui-icon [name]="toast.type === 'error' ? 'alert' : 'check'" class="h-4 w-4" />
        </span>
        <p class="min-w-0 flex-1 pt-1 text-sm font-semibold text-gray-800">{{ toast.message }}</p>
        <button
          type="button"
          class="btn-icon h-7 w-7"
          aria-label="Cerrar aviso"
          (click)="notifications.dismiss(toast.id)"
        >
          <app-ui-icon name="x" class="h-4 w-4" />
        </button>
      </div>
    }
  `,
})
export class ToastHostComponent {
  protected readonly notifications = inject(NotificationService);
}
