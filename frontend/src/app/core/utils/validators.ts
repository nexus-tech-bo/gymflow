import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { todayLima } from './format.util';

/** Like Validators.required but whitespace-only counts as empty (the backend uses @NotBlank). */
export const requiredText: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim() ? null : { required: true };

/** Max decimal places of a number input (money = 2, integers = 0). */
export const maxDecimals =
  (places: number): ValidatorFn =>
  (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as number | null;
    if (value === null || value === undefined || Number.isNaN(value)) return null;
    return Math.abs(value * 10 ** places - Math.round(value * 10 ** places)) < 1e-6
      ? null
      : { decimals: { places } };
  };

/** LocalDate ('YYYY-MM-DD') strictly before today in Lima (@Past). Empty is valid. */
export const pastDate: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as string | null;
  return !value || value < todayLima() ? null : { pastDate: true };
};
