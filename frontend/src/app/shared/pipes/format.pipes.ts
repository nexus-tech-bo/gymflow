import { Pipe, PipeTransform } from '@angular/core';
import {
  InstantFormat,
  formatInstant,
  formatLocalDate,
  formatMoney,
} from '../../core/utils/format.util';

/** Bs 1.234,50 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatMoney(value);
  }
}

/** Instant (ISO) shown in America/Lima. */
@Pipe({ name: 'limaDateTime' })
export class LimaDateTimePipe implements PipeTransform {
  transform(value: string | null | undefined, format: InstantFormat = 'datetime'): string {
    return formatInstant(value, format);
  }
}

/** LocalDate 'YYYY-MM-DD' → dd/MM/yyyy (no timezone conversion). */
@Pipe({ name: 'localDate' })
export class LocalDatePipe implements PipeTransform {
  transform(value: string | null | undefined, short = false): string {
    return formatLocalDate(value, short);
  }
}
