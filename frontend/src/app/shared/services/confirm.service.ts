import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { DialogFrameComponent } from '../ui/dialog-frame.component';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [DialogFrameComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog-frame [title]="data.title">
      <p class="text-sm font-medium text-gray-700">{{ data.message }}</p>
      <div dialogFooter class="contents">
        <button type="button" class="btn btn-secondary" (click)="ref.close(false)">
          {{ data.cancelLabel ?? 'Cancelar' }}
        </button>
        <button
          type="button"
          class="btn"
          [class]="data.danger ? 'btn-danger' : 'btn-primary'"
          (click)="ref.close(true)"
        >
          {{ data.confirmLabel ?? 'Confirmar' }}
        </button>
      </div>
    </app-dialog-frame>
  `,
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmOptions>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<boolean>>(DialogRef);
}

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(Dialog);

  /** Emits once: true when confirmed, false otherwise. */
  ask(options: ConfirmOptions): Observable<boolean> {
    return this.dialog
      .open<boolean, ConfirmOptions>(ConfirmDialogComponent, { data: options, role: 'alertdialog' })
      .closed.pipe(map(Boolean));
  }
}
