import { MeResponse, Role, StaffResponse } from '../../core/models/api.models';

/**
 * UI mirror of the backend staff rules (the server enforces them too): OWNER is never created, assigned,
 * demoted or deactivated; ADMIN only manages RECEPTIONIST and cannot change roles; nobody manages their own row.
 */
export function canManage(me: Pick<MeResponse, 'id' | 'role'> | null, row: StaffResponse): boolean {
  if (!me || row.id === me.id || row.role === 'OWNER') return false;
  if (me.role === 'OWNER') return true;
  return me.role === 'ADMIN' && row.role === 'RECEPTIONIST';
}

/** Roles the current user may pick (create) or change to (edit). A single option means "fixed, no selector". */
export function assignableRoles(me: Pick<MeResponse, 'role'> | null): Role[] {
  return me?.role === 'OWNER' ? ['ADMIN', 'RECEPTIONIST'] : ['RECEPTIONIST'];
}
