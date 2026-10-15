import { differenceChip, expiringChip } from './labels.util';

describe('labels.util', () => {
  it('flags only ACTIVE memberships expiring within 7 days', () => {
    expect(expiringChip('ACTIVE', 8)).toBeNull();
    expect(expiringChip('FROZEN', 2)).toBeNull();
    expect(expiringChip('ACTIVE', 7)?.label).toBe('Vence en 7 días');
    expect(expiringChip('ACTIVE', 1)?.label).toBe('Vence en 1 día');
    expect(expiringChip('ACTIVE', 0)?.label).toBe('Vence hoy');
  });

  it('maps the cash difference sign to Cuadra / Faltante / Sobrante', () => {
    expect(differenceChip(null)).toBeNull();
    expect(differenceChip(0)).toMatchObject({ label: 'Cuadra', tone: 'success' });
    expect(differenceChip(-5)).toMatchObject({ tone: 'danger' });
    expect(differenceChip(-5)?.label).toMatch(/^Faltante Bs\s5,00$/);
    expect(differenceChip(2.5)).toMatchObject({ tone: 'warn' });
    expect(differenceChip(2.5)?.label).toMatch(/^Sobrante Bs\s2,50$/);
  });
});
