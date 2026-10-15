import { Directive, ElementRef, inject } from '@angular/core';
import { NgControl } from '@angular/forms';

/**
 * Sets aria-invalid and links the field to its `<p id="{id}-error">` while it shows an error.
 * Applies to every `.field-input` bound with formControlName in components that import it.
 * `app-date-picker` does not match (class is on its inner button): use its `invalid`/`describedBy` inputs.
 */
@Directive({
  selector: '.field-input[formControlName]',
  host: {
    '[attr.aria-invalid]': 'invalid() || null',
    '[attr.aria-describedby]': 'invalid() ? id + "-error" : null',
  },
})
export class FieldA11yDirective {
  private readonly control = inject(NgControl);
  protected readonly id = (inject(ElementRef).nativeElement as HTMLElement).id;

  protected invalid(): boolean {
    return !!this.control.invalid && !!this.control.touched;
  }
}
