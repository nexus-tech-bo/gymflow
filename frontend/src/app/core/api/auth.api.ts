import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthResponse, LoginRequest, MeResponse, RegisterGymRequest } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  /** Public. 401 on wrong credentials. */
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('api/auth/login', request);
  }

  /** Public. Creates the gym and its OWNER, returns a session. */
  registerGym(request: RegisterGymRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('api/auth/register-gym', request);
  }

  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>('api/me');
  }
}
