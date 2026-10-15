import { Role, StaffResponse } from '../../core/models/api.models';
import { assignableRoles, canManage } from './staff-rules';

const row = (id: number, role: Role): StaffResponse => ({
  id,
  role,
  fullName: 'X',
  email: 'x@x.pe',
  active: true,
  createdAt: '',
});

describe('staff-rules', () => {
  it('OWNER manages everyone except owners and themselves', () => {
    const owner = { id: 1, role: 'OWNER' as Role };
    expect(canManage(owner, row(1, 'OWNER'))).toBe(false);
    expect(canManage(owner, row(2, 'OWNER'))).toBe(false);
    expect(canManage(owner, row(3, 'ADMIN'))).toBe(true);
    expect(canManage(owner, row(4, 'RECEPTIONIST'))).toBe(true);
  });

  it('ADMIN manages only receptionists, never their own row', () => {
    const admin = { id: 5, role: 'ADMIN' as Role };
    expect(canManage(admin, row(5, 'ADMIN'))).toBe(false);
    expect(canManage(admin, row(6, 'ADMIN'))).toBe(false);
    expect(canManage(admin, row(7, 'RECEPTIONIST'))).toBe(true);
    expect(canManage(null, row(7, 'RECEPTIONIST'))).toBe(false);
  });

  it('never offers OWNER as an assignable role', () => {
    expect(assignableRoles({ role: 'OWNER' })).toEqual(['ADMIN', 'RECEPTIONIST']);
    expect(assignableRoles({ role: 'ADMIN' })).toEqual(['RECEPTIONIST']);
  });
});
