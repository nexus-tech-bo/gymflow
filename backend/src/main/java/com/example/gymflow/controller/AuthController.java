package com.example.gymflow.controller;

import com.example.gymflow.dto.auth.AuthResponse;
import com.example.gymflow.dto.auth.LoginRequest;
import com.example.gymflow.dto.auth.RegisterGymRequest;
import com.example.gymflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Auth Controller")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register-gym")
    @Operation(summary = "POST /api/auth/register-gym — register a gym and its OWNER")
    public ResponseEntity<AuthResponse> registerGym(@Valid @RequestBody RegisterGymRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerGym(request));
    }

    @PostMapping("/login")
    @Operation(summary = "POST /api/auth/login — authenticate a staff user and return a JWT")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
