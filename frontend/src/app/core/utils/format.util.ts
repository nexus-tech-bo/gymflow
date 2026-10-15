/**
 * Wire-format helpers. LocalDate ('YYYY-MM-DD') is handled as plain text/UTC math, never `new Date('YYYY-MM-DD')`;
 * Instants are always shown in America/Lima.
 */
export const TIME_ZONE = 'America/Lima';
const LOCALE = 'es-PE';

const money = new Intl.NumberFormat('es-BO', { style: 'currency', currency: 'BOB' });
const isoDay = new Intl.DateTimeFormat('en-CA', {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export function formatMoney(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : money.format(value);
}

/** Today in Lima as 'YYYY-MM-DD'. */
export function todayLima(now = new Date()): string {
  return isoDay.format(now);
}

/** 'YYYY-MM-DD' → Date at UTC midnight (read it with getUTC* / timeZone 'UTC'). */
export function parseLocalDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

function toLocalDate(date: Date): string {
  return date.toISOString().slice(0, 10); // safe: the Date is built in UTC
}

export function addDays(value: string, days: number): string {
  const date = parseLocalDate(value);
  date.setUTCDate(date.getUTCDate() + days);
  return toLocalDate(date);
}

export function addYears(value: string, years: number): string {
  const date = parseLocalDate(value);
  const day = date.getUTCDate();
  date.setUTCFullYear(date.getUTCFullYear() + years);
  if (date.getUTCDate() !== day) date.setUTCDate(0); // Feb 29 → Feb 28, like java.time plusYears
  return toLocalDate(date);
}

/** 'YYYY-MM-DD' → 'dd/MM/yyyy' ('dd/MM' when short). */
export function formatLocalDate(value: string | null | undefined, short = false): string {
  if (!value) return '—';
  const [y, m, d] = value.split('-');
  return short ? `${d}/${m}` : `${d}/${m}/${y}`;
}

export function weekdayShort(value: string): string {
  return new Intl.DateTimeFormat(LOCALE, { weekday: 'short', timeZone: 'UTC' }).format(
    parseLocalDate(value),
  );
}

/** Whole years between a LocalDate and today (Lima). */
export function ageFrom(birthDate: string, today = todayLima()): number {
  const [by, bm, bd] = birthDate.split('-').map(Number);
  const [ty, tm, td] = today.split('-').map(Number);
  return ty - by - (tm < bm || (tm === bm && td < bd) ? 1 : 0);
}

export type InstantFormat = 'datetime' | 'date' | 'time' | 'timeSeconds' | 'longDate';

const INSTANT_OPTIONS: Record<InstantFormat, Intl.DateTimeFormatOptions> = {
  datetime: {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  },
  date: { day: '2-digit', month: '2-digit', year: 'numeric' },
  time: { hour: '2-digit', minute: '2-digit', hour12: false },
  timeSeconds: { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false },
  longDate: { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' },
};
const instantFormatters = new Map<InstantFormat, Intl.DateTimeFormat>();

export function formatInstant(
  value: string | Date | null | undefined,
  format: InstantFormat = 'datetime',
): string {
  if (!value) return '—';
  let formatter = instantFormatters.get(format);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(LOCALE, {
      ...INSTANT_OPTIONS[format],
      timeZone: TIME_ZONE,
    });
    instantFormatters.set(format, formatter);
  }
  return formatter.format(typeof value === 'string' ? new Date(value) : value);
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

/** Validates an income report range (inclusive, max 1 year as the backend). Returns '' when valid. */
export function rangeError(from: string, to: string): string {
  if (!from || !to) return 'Selecciona ambas fechas';
  if (from > to) return 'La fecha inicial debe ser anterior a la final';
  if (to > addDays(addYears(from, 1), -1)) return 'El rango máximo es de 1 año';
  return '';
}
