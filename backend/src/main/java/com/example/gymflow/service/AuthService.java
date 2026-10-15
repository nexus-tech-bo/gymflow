package com.example.gymflow.service;

import com.example.gymflow.dto.auth.AuthResponse;
import com.example.gymflow.dto.auth.LoginRequest;
import com.example.gymflow.dto.auth.RegisterGymRequest;

/**
 * Gym registration and staff login.
 */
public interface AuthService {
    /**
     * Registers a gym, provisions its schema and creates its OWNER.
     *
     * @param request the gym and owner data
     * @return a token for the new OWNER
     * @throws com.example.gymflow.exception.BusinessException when the email is already registered
     */
    AuthResponse registerGym(RegisterGymRequest request);

    /**
     * Authenticates a staff user by email and password.
     *
     * @param request the credentials
     * @return a token and the user
     * @throws com.example.gymflow.exception.UnauthorizedException when the credentials are invalid or the user is inactive
     */
    AuthResponse login(LoginRequest request);
}
