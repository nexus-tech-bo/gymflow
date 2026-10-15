package com.example.gymflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * Generates and parses HS256 access tokens.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    @Getter
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Creates a signed access token.
     *
     * @param userId   the staff id, stored as subject
     * @param role     the staff role
     * @param gymId    the gym (tenant) id
     * @param userName the staff full name
     * @return the compact JWT
     */
    public String generate(Long userId, String role, Long gymId, String userName) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("gymId", gymId)
                .claim("username", userName)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature and expiration.
     *
     * @param token the compact JWT
     * @return the claims, or empty when the token is invalid or expired
     */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
