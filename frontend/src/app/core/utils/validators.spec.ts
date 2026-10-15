import { FormControl } from '@angular/forms';
import { translateMessage } from '../http/api-error';
import { addDays, todayLima } from './format.util';
import { maxDecimals, pastDate, requiredText } from './validators';

describe('validators', () => {
  it('requiredText rejects whitespace-only text', () => {
    expect(requiredText(new FormControl('   '))).toEqual({ required: true });
    expect(requiredText(new FormControl(' a '))).toBeNull();
  });

  it('maxDecimals limits decimal places', () => {
    expect(maxDecimals(2)(new FormControl(10.25))).toBeNull();
    expect(maxDecimals(2)(new FormControl(0.1 + 0.2))).toBeNull(); // float noise is not a 3rd decimal
    expect(maxDecimals(2)(new FormControl(10.255))).toEqual({ decimals: { places: 2 } });
    expect(maxDecimals(0)(new FormControl(1.5))).toEqual({ decimals: { places: 0 } });
    expect(maxDecimals(2)(new FormControl(null))).toBeNull();
  });

  it('pastDate only accepts dates before today (Lima)', () => {
    expect(pastDate(new FormControl(addDays(todayLima(), -1)))).toBeNull();
    expect(pastDate(new FormControl(todayLima()))).toEqual({ pastDate: true });
    expect(pastDate(new FormControl(''))).toBeNull();
  });
});

describe('translateMessage', () => {
  it('maps Bean Validation English defaults to Spanish and keeps Spanish messages', () => {
    expect(translateMessage('must not be blank')).toBe('Este campo es obligatorio');
    expect(translateMessage('must be less than or equal to 730')).toBe(
      'Debe ser menor o igual a 730',
    );
    expect(translateMessage('El email ya está registrado')).toBe('Ese correo ya está registrado.');
    expect(translateMessage('La caja está cerrada')).toBe('La caja está cerrada');
  });
});
