import {
  addDays,
  ageFrom,
  formatInstant,
  formatLocalDate,
  rangeError,
  todayLima,
} from './format.util';

describe('format.util', () => {
  it('formats LocalDate without timezone shifts', () => {
    expect(formatLocalDate('2026-01-01')).toBe('01/01/2026');
    expect(formatLocalDate('2026-12-31', true)).toBe('31/12');
    expect(formatLocalDate(null)).toBe('—');
  });

  it('computes today in Lima (UTC-5), not UTC', () => {
    // 2026-03-01 03:00 UTC is still Feb 28 in Lima
    expect(todayLima(new Date('2026-03-01T03:00:00Z'))).toBe('2026-02-28');
  });

  it('shows instants in Lima time', () => {
    expect(formatInstant('2026-03-01T03:05:00Z', 'time')).toBe('22:05');
  });

  it('adds days across month/leap boundaries', () => {
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('computes age from a birth date', () => {
    expect(ageFrom('2000-06-15', '2026-06-14')).toBe(25);
    expect(ageFrom('2000-06-15', '2026-06-15')).toBe(26);
  });

  it('validates the income report range like the backend (max 1 year inclusive)', () => {
    expect(rangeError('2026-01-01', '2026-12-31')).toBe('');
    // backend: 2024-02-29.plusYears(1).minusDays(1) = 2025-02-27
    expect(rangeError('2024-02-29', '2025-02-27')).toBe('');
    expect(rangeError('2024-02-29', '2025-02-28')).toBe('El rango máximo es de 1 año');
    expect(rangeError('2026-01-01', '2027-01-01')).toBe('El rango máximo es de 1 año');
    expect(rangeError('2026-02-01', '2026-01-31')).toBe(
      'La fecha inicial debe ser anterior a la final',
    );
  });
});
