package com.example.gymflow.middleware;

import com.example.gymflow.config.tenant.TenantContext;
import com.example.gymflow.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <token>}, binds the gym schema to {@link TenantContext} and
 * authenticates the staff user (principal = staff id, authority = {@code ROLE_<role>}).
 * An invalid token leaves the request unauthenticated.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        try {
            var header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                jwtUtil.parse(header.substring(7)).ifPresent(claims -> {
                    TenantContext.setCurrentTenant(TenantContext.schemaOf(claims.get("gymId", Long.class)));
                    var role = claims.get("role", String.class);
                    List<SimpleGrantedAuthority> authorities = role != null
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            : List.of();
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
                });
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
