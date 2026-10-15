import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';
import { ApiError } from '../models/api.models';

export const NETWORK_ERROR = 'No se pudo conectar con el servidor. Intenta de nuevo.';
export const FORBIDDEN_ERROR = 'No tienes permiso para esta acción.';

/** 409 business messages that belong to a specific form field. */
const CONFLICT_FIELDS: [RegExp, string][] = [
  [/DNI/i, 'dni'],
  [/email ya está registrado/i, 'email'],
];

/** Fallback translations for server messages that are not already user-friendly Spanish. */
const KNOWN_MESSAGES: [RegExp, string][] = [
  [/must not be (blank|empty|null)/i, 'Este campo es obligatorio'],
  [/must be a past date/i, 'Debe ser una fecha pasada'],
  [/must be a well-formed email address/i, 'Ingresa un correo válido'],
  [/numeric value out of bounds/i, 'Máximo 2 decimales y dentro del rango permitido'],
  [/must be greater than or equal to (\S+)/i, 'Debe ser mayor o igual a $1'],
  [/must be less than or equal to (\S+)/i, 'Debe ser menor o igual a $1'],
  [/size must be between (\d+) and (\d+)/i, 'Debe tener entre $1 y $2 caracteres'],
  [/email ya está registrado/i, 'Ese correo ya está registrado.'],
];

export function translateMessage(message: string): string {
  for (const [re, text] of KNOWN_MESSAGES) {
    const match = message.match(re);
    if (match) return text.replace(/\$(\d)/g, (_, i: string) => match[Number(i)] ?? '');
  }
  return message;
}

function errorBody(error: HttpErrorResponse): Partial<ApiError> | null {
  return error.error && typeof error.error === 'object' ? (error.error as Partial<ApiError>) : null;
}

/** The one translator from an HTTP error to a user-facing message (backend messages are already Spanish). */
export function apiErrorMessage(error: unknown, fallback = 'Ocurrió un error inesperado.'): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  if (error.status === 0) return NETWORK_ERROR;
  if (error.status === 403) return FORBIDDEN_ERROR;
  const body = errorBody(error);
  if (body?.errors) {
    const messages = Object.values(body.errors);
    if (messages.length) return messages.map(translateMessage).join('. ');
  }
  return body?.message ? translateMessage(body.message) : fallback;
}

export function isNotFound(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 404;
}

/**
 * Puts server validation errors on the matching controls (`errors.server`) and returns the
 * form-level message for whatever could not be attached to a field ('' when everything matched).
 */
export function applyServerErrors(form: FormGroup, error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return apiErrorMessage(error);
  const body = errorBody(error);
  const fieldErrors: [string, string][] = body?.errors ? Object.entries(body.errors) : [];
  if (!fieldErrors.length && error.status === 409 && body?.message) {
    const field = CONFLICT_FIELDS.find(([re]) => re.test(body.message!))?.[1];
    if (field) fieldErrors.push([field, body.message]);
  }
  if (!fieldErrors.length) return apiErrorMessage(error);

  const unmatched: string[] = [];
  for (const [field, message] of fieldErrors) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ server: translateMessage(message) });
      control.markAsTouched();
    } else {
      unmatched.push(translateMessage(message));
    }
  }
  return unmatched.join('. ');
}

/** Message for the first failing validator of a touched control, or '' when valid/untouched. */
export function fieldError(
  control: AbstractControl | null,
  patternMessage = 'Formato inválido',
): string {
  if (!control || !control.touched || !control.errors) return '';
  const e = control.errors;
  if (e['server']) return e['server'];
  if (e['required']) return 'Este campo es obligatorio';
  if (e['email']) return 'Ingresa un correo válido';
  if (e['minlength']) return `Mínimo ${e['minlength'].requiredLength} caracteres`;
  if (e['maxlength']) return `Máximo ${e['maxlength'].requiredLength} caracteres`;
  if (e['min']) return `Debe ser mayor o igual a ${e['min'].min}`;
  if (e['max']) return `Debe ser menor o igual a ${e['max'].max}`;
  if (e['pattern']) return patternMessage;
  if (e['decimals'])
    return e['decimals'].places
      ? `Máximo ${e['decimals'].places} decimales`
      : 'Debe ser un número entero';
  if (e['pastDate']) return 'Debe ser una fecha pasada';
  return 'Valor inválido';
}
