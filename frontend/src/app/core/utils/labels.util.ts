import {
  CashSessionStatus,
  CheckInDenialReason,
  CheckInMethod,
  CheckInResult,
  MembershipStatus,
  PaymentMethod,
  Role,
} from '../models/api.models';
import { formatMoney } from './format.util';

export type Tone = 'success' | 'info' | 'warn' | 'danger' | 'neutral';

export interface Chip {
  label: string;
  tone: Tone;
  /** Status chips carry a dot. */
  dot?: boolean;
}

export const MEMBERSHIP_STATUS: Record<MembershipStatus, Chip> = {
  ACTIVE: { label: 'Activa', tone: 'success', dot: true },
  SCHEDULED: { label: 'Programada', tone: 'info', dot: true },
  FROZEN: { label: 'Congelada', tone: 'warn', dot: true },
  EXPIRED: { label: 'Vencida', tone: 'danger', dot: true },
  CANCELLED: { label: 'Cancelada', tone: 'neutral', dot: true },
};

export const PAYMENT_METHOD: Record<PaymentMethod, string> = {
  CASH: 'Efectivo',
  QR: 'QR',
};
export const PAYMENT_METHODS = Object.keys(PAYMENT_METHOD) as PaymentMethod[];

export const CASH_STATUS: Record<CashSessionStatus, Chip> = {
  OPEN: { label: 'Abierta', tone: 'success', dot: true },
  CLOSED: { label: 'Cerrada', tone: 'neutral', dot: true },
};

export const CHECK_IN_RESULT: Record<CheckInResult, Chip> = {
  ALLOWED: { label: 'Permitido', tone: 'success', dot: true },
  DENIED: { label: 'Denegado', tone: 'danger', dot: true },
};

export const CHECK_IN_METHOD: Record<CheckInMethod, string> = { QR: 'QR', DNI: 'DNI' };

export const DENIAL_REASON: Record<CheckInDenialReason, string> = {
  INVALID_CODE: 'Código inválido',
  UNKNOWN: 'Socio no encontrado',
  MEMBER_INACTIVE: 'Socio inactivo',
  NO_MEMBERSHIP: 'Sin membresía',
  NOT_STARTED: 'La membresía aún no inicia',
  FROZEN: 'Membresía congelada',
  EXPIRED: 'Membresía vencida',
};

export const ROLE: Record<Role, Chip> = {
  OWNER: { label: 'Propietario', tone: 'info' },
  ADMIN: { label: 'Administrador', tone: 'warn' },
  RECEPTIONIST: { label: 'Recepcionista', tone: 'neutral' },
};

export const neutralChip = (label: string): Chip => ({ label, tone: 'neutral' });

export const activeChip = (active: boolean): Chip =>
  active
    ? { label: 'Activo', tone: 'success', dot: true }
    : { label: 'Inactivo', tone: 'neutral', dot: true };

export const VOIDED_CHIP: Chip = { label: 'Anulado', tone: 'danger' };

/** "Vence en N días" chip for an ACTIVE membership about to expire (≤ 7 days), else null. */
export function expiringChip(status: MembershipStatus, daysLeft: number): Chip | null {
  if (status !== 'ACTIVE' || daysLeft > 7) return null;
  if (daysLeft <= 0) return { label: 'Vence hoy', tone: 'warn' };
  return { label: `Vence en ${daysLeft} ${daysLeft === 1 ? 'día' : 'días'}`, tone: 'warn' };
}

/** Cash close difference (counted − expected). */
export function differenceChip(difference: number | null): Chip | null {
  if (difference === null) return null;
  if (difference === 0) return { label: 'Cuadra', tone: 'success', dot: true };
  if (difference < 0)
    return { label: `Faltante ${formatMoney(-difference)}`, tone: 'danger', dot: true };
  return { label: `Sobrante ${formatMoney(difference)}`, tone: 'warn', dot: true };
}
