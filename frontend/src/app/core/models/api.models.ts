/** DTOs mirroring the GymFlow backend (same field names). Money = number, LocalDate = 'YYYY-MM-DD', Instant = ISO string. */

export type Role = 'OWNER' | 'ADMIN' | 'RECEPTIONIST';
export type CashSessionStatus = 'OPEN' | 'CLOSED';
export type CheckInDenialReason =
  | 'INVALID_CODE'
  | 'UNKNOWN'
  | 'MEMBER_INACTIVE'
  | 'NO_MEMBERSHIP'
  | 'NOT_STARTED'
  | 'FROZEN'
  | 'EXPIRED';
export type CheckInMethod = 'QR' | 'DNI';
export type CheckInResult = 'ALLOWED' | 'DENIED';
export type MembershipStatus = 'SCHEDULED' | 'ACTIVE' | 'FROZEN' | 'EXPIRED' | 'CANCELLED';
export type PaymentMethod = 'CASH' | 'QR';

export interface ApiError {
  status: number;
  message: string;
  timestamp: string;
  errors?: Record<string, string>;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
}

// Auth
export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterGymRequest {
  gymName: string;
  ownerName: string;
  email: string;
  password: string;
}

export interface MeResponse {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  gymId: number;
  gymName: string;
}

export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
  user: MeResponse;
}

// Dashboard
export interface ExpiringMembershipResponse {
  memberId: number;
  memberName: string;
  planName: string;
  endDate: string;
  daysLeft: number;
}

export interface DashboardSummaryResponse {
  activeMembers: number;
  frozenMembers: number;
  checkInsToday: number;
  expiringSoon: ExpiringMembershipResponse[];
}

// Members & memberships
export interface MembershipPaymentResponse {
  id: number;
  method: PaymentMethod;
  amount: number;
  paidAt: string;
  voided: boolean;
}

export interface MembershipResponse {
  id: number;
  planName: string;
  price: number;
  startDate: string;
  endDate: string;
  status: MembershipStatus;
  frozenSince: string | null;
  frozenDays: number;
  daysLeft: number;
  payment: MembershipPaymentResponse | null;
}

export interface MemberSummaryResponse {
  id: number;
  fullName: string;
  dni: string;
  phone: string | null;
  active: boolean;
  currentMembership: MembershipResponse | null;
}

export interface MemberDetailResponse {
  id: number;
  fullName: string;
  dni: string;
  phone: string | null;
  email: string | null;
  birthDate: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  currentMembership: MembershipResponse | null;
  memberships: MembershipResponse[];
}

export interface MemberRequest {
  fullName: string;
  dni: string;
  phone: string | null;
  email: string | null;
  birthDate: string | null;
  notes: string | null;
}

export interface MemberSearchQuery extends PageQuery {
  q?: string;
}

export interface MemberQrResponse {
  qrToken: string;
  payload: string;
}

export interface MemberCardResponse {
  gymName: string;
  memberName: string;
  payload: string;
  planName: string | null;
  endDate: string | null;
  status: MembershipStatus | null;
}

export interface MembershipSaleRequest {
  planId: number;
  price?: number;
  paymentMethod: PaymentMethod;
  paymentReference?: string;
  idempotencyKey: string;
}

// Plans
export interface PlanResponse {
  id: number;
  name: string;
  durationDays: number;
  price: number;
  active: boolean;
}

export interface PlanRequest {
  name: string;
  durationDays: number;
  price: number;
  active?: boolean;
}

// Cash & payments
export interface PaymentTotals {
  cash: number;
  qr: number;
  total: number;
  count: number;
}

export interface PaymentResponse {
  id: number;
  memberId: number;
  memberName: string;
  planName: string;
  amount: number;
  method: PaymentMethod;
  reference: string | null;
  receivedByName: string;
  paidAt: string;
  voided: boolean;
  voidReason: string | null;
}

export interface CashSessionResponse {
  id: number;
  status: CashSessionStatus;
  openedAt: string;
  openedByName: string;
  openingAmount: number;
  expectedCash: number | null;
  totals: PaymentTotals | null;
  closedAt: string | null;
  closedByName: string | null;
  countedCash: number | null;
  difference: number | null;
  notes: string | null;
}

export interface CashSessionDetailResponse {
  session: CashSessionResponse;
  payments: PaymentResponse[];
}

export interface CashCurrentResponse {
  current: CashSessionDetailResponse | null;
}

export interface CashOpenRequest {
  openingAmount: number;
}

export interface CashCloseRequest {
  countedCash: number;
  notes?: string;
}

export interface PaymentVoidRequest {
  reason: string;
}

// Check-ins
export interface CheckInRequest {
  code: string;
}

export interface CheckInResponse {
  result: CheckInResult;
  duplicate: boolean;
  reason: CheckInDenialReason | null;
  message: string | null;
  member: { id: number; fullName: string } | null;
  membership: {
    planName: string;
    endDate: string;
    daysLeft: number;
    status: MembershipStatus;
  } | null;
  checkedAt: string;
}

export interface CheckInEntryResponse {
  id: number;
  checkedAt: string;
  memberId: number | null;
  memberName: string | null;
  method: CheckInMethod;
  result: CheckInResult;
  reason: CheckInDenialReason | null;
}

export interface CheckInQuery extends PageQuery {
  date?: string;
}

// Reports
export interface IncomeDayResponse {
  date: string;
  total: number;
  count: number;
}

export interface IncomeReportResponse {
  from: string;
  to: string;
  totals: PaymentTotals;
  days: IncomeDayResponse[];
}

// Staff
export interface StaffResponse {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface StaffRequest {
  fullName: string;
  email: string;
  password: string;
  role: Role;
}

export interface StaffUpdateRequest {
  fullName?: string;
  role?: Role;
  active?: boolean;
}
