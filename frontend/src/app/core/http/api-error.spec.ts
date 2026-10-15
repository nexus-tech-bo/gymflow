import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';
import { FORBIDDEN_ERROR, NETWORK_ERROR, apiErrorMessage, applyServerErrors } from './api-error';
import { toHttpParams } from './http-params';

const httpError = (status: number, error: unknown = null) =>
  new HttpErrorResponse({ status, error });

describe('api-error', () => {
  it('translates status 0 and 403, otherwise uses the server message', () => {
    expect(apiErrorMessage(httpError(0))).toBe(NETWORK_ERROR);
    expect(apiErrorMessage(httpError(403, { message: 'Acceso denegado' }))).toBe(FORBIDDEN_ERROR);
    expect(apiErrorMessage(httpError(409, { status: 409, message: 'La caja está cerrada' }))).toBe(
      'La caja está cerrada',
    );
    expect(apiErrorMessage(httpError(500, 'not json'), 'fallback')).toBe('fallback');
    expect(apiErrorMessage(new Error('boom'), 'fallback')).toBe('fallback');
  });

  it('puts validation errors on matching controls and returns the rest', () => {
    const form = new FormGroup({ dni: new FormControl(''), fullName: new FormControl('') });
    const message = applyServerErrors(
      form,
      httpError(400, {
        status: 400,
        message: 'Datos inválidos',
        errors: { dni: 'formato inválido', other: 'otro error' },
      }),
    );
    expect(form.controls.dni.errors).toEqual({ server: 'formato inválido' });
    expect(form.controls.dni.touched).toBe(true);
    expect(form.controls.fullName.errors).toBeNull();
    expect(message).toBe('otro error');
  });

  it('maps a 409 duplicate DNI to the dni field', () => {
    const form = new FormGroup({ dni: new FormControl('') });
    const message = applyServerErrors(
      form,
      httpError(409, { status: 409, message: 'Ya existe un socio con el DNI 12345678' }),
    );
    expect(form.controls.dni.errors).toEqual({ server: 'Ya existe un socio con el DNI 12345678' });
    expect(message).toBe('');
  });

  it('skips empty values when building query params', () => {
    expect(toHttpParams({ q: '', page: 0, size: 20, date: undefined, x: null }).toString()).toBe(
      'page=0&size=20',
    );
  });
});
