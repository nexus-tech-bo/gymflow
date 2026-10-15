---
name: spring-security
description: 'Implement stateless JWT authentication and role-based authorization in a Spring Boot REST API with Spring Security 6/7 and jjwt. Use when the user asks to add login, JWT auth, a security filter chain, protect endpoints, role checks (@PreAuthorize), public paths, or JSON 401/403 responses.'
---

# Spring Security (Stateless JWT + Roles)

Your goal is to secure a REST API with:
- A login endpoint that verifies credentials (BCrypt) and returns a signed JWT (HS256).
- A `OncePerRequestFilter` that parses `Authorization: Bearer <token>` and populates the `SecurityContext`.
- A stateless `SecurityFilterChain` with configurable public paths; everything else requires authentication.
- Role checks with `@PreAuthorize` and ownership checks in the service layer.
- JSON error bodies for 401/403 consistent with the app's global exception handler.

Before writing code, inspect the target project and adapt:
- Base package, Spring Boot version (3.x vs 4.x, see "Version notes"), Lombok usage, Java version.
- Existing error DTO (e.g. `ErrorResponse`) and `@RestControllerAdvice`; reuse them.
- User/role model: where the user id and role come from, which extra claims are needed (tenant id, username).
- Existing CORS config (`WebMvcConfigurer.addCorsMappings` or a `CorsConfigurationSource` bean).

## Architecture

```
Request
  └─ JwtAuthFilter (before UsernamePasswordAuthenticationFilter)
        ├─ no/invalid token  -> continue unauthenticated
        └─ valid token       -> SecurityContext: principal = userId, authorities = [ROLE_<role>]
  └─ authorizeHttpRequests: public paths permitAll, anyRequest authenticated
        └─ not authenticated -> AuthenticationEntryPoint -> 401 JSON
  └─ @PreAuthorize("hasAnyRole(...)") on controller methods
        └─ denied -> AuthorizationDeniedException -> 403 JSON (via @ExceptionHandler)
  └─ Service: ownership check (userId + role) -> ForbiddenException -> 403
```

| File                             | Package                                  | Role                                             |
|----------------------------------|------------------------------------------|--------------------------------------------------|
| `JwtUtil`                        | `<base>.security`                        | Generate and parse tokens                        |
| `JwtAuthFilter`                  | `<base>.security` or `<base>.middleware` | Bearer token -> `SecurityContext`                |
| `SecurityConfig`                 | `<base>.config`                          | Filter chain, entry point, access denied handler |
| `PasswordEncoderConfig`          | `<base>.config`                          | `BCryptPasswordEncoder` bean                     |
| `SecurityUtils`                  | `<base>.security`                        | Extract role from `Authentication`               |
| `AuthController` / `AuthService` | per project                              | `POST /api/auth/login`                           |

## 1. Dependencies (Gradle)

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

## 2. Configuration

```yaml
jwt:
  secret: ${JWT_SECRET}            # >= 32 bytes for HS256, never committed
  expiration-ms: 86400000          # 24h

security:
  public-paths: /api/auth/login, /swagger-ui/**, /v3/api-docs/**

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}
```

`Keys.hmacShaKeyFor` throws `WeakKeyException` if the secret is shorter than 256 bits, so startup fails fast with a bad secret. Generate one with `openssl rand -base64 48`.

## 3. JwtUtil

```java
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generate(Long userId, String role, String username) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("username", username)
                // add domain claims here, e.g. .claim("tenantId", tenantId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
```

`parseSignedClaims` verifies signature and expiration; expired or tampered tokens return `Optional.empty()`.

## 4. JwtAuthFilter

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtUtil.parse(header.substring(7)).ifPresent(claims -> {
                var role = claims.get("role", String.class);
                List<SimpleGrantedAuthority> authorities = role != null
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        : List.of();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
            });
        }
        chain.doFilter(request, response);
    }
}
```

- The filter never rejects: an invalid token simply leaves the request unauthenticated and the chain answers 401 for protected paths. Public paths keep working even with a stale token.
- Principal is the user id (`auth.getName()` returns it as a string).
- If the project sets per-request ThreadLocal state from claims (e.g. tenant via the `spring-multi-tenancy` skill), wrap the body in `try { ... } finally { TenantContext.clear(); }`.
- Because it is a `@Component`, Spring Boot also registers it as a servlet filter; `OncePerRequestFilter` prevents double execution. If you want it only in the security chain, add a `FilterRegistrationBean` with `setEnabled(false)`.

## 5. SecurityConfig

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Value("${security.public-paths:}")
    private String[] publicPaths;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)            // stateless, no cookies
                .cors(Customizer.withDefaults())                  // uses the MVC CORS config
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    if (publicPaths.length > 0)
                        a.requestMatchers(publicPaths).permitAll();
                    a.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, HttpStatus.UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, HttpStatus.FORBIDDEN, "Forbidden")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        var body = ErrorResponse.builder()
                .status(status.value())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
```

`@Value` with a comma-separated string binds directly to `String[]`, so public paths are changed per environment without code changes.

## 6. Password encoder

```java
@Configuration
public class PasswordEncoderConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

Keep it in its own config class so services can inject it without a circular dependency on `SecurityConfig`.

## 7. CORS

`.cors(Customizer.withDefaults())` picks up the Spring MVC CORS mappings:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)   // token goes in a header, not a cookie
                .maxAge(3600);
    }
}
```

Do not default `allowed-origins` to `*` in production; set explicit origins.

## 8. Login

```java
public LoginResponse login(LoginRequest request) {
    var user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new UnauthorizedException("Invalid Credentials"));
    if (!passwordEncoder.matches(request.password(), user.getPassword()))
        throw new UnauthorizedException("Invalid Credentials");
    return new LoginResponse(jwtUtil.generate(user.getId(), user.getRole().name(), user.getName()));
}
```

Use the same message for unknown user and wrong password to avoid user enumeration. Add the login path to `security.public-paths`.

## 9. Protecting endpoints

Three layers, from coarse to fine:

1. **Authentication** — anything not in `security.public-paths` requires a valid token (filter chain).
2. **Role** — `@PreAuthorize` on controller methods (or the class for a default):

```java
@PatchMapping("/{id}/confirm")
@PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")   // matches authority ROLE_DOCTOR / ROLE_ASSISTANT
public ResponseEntity<AppointmentResponse> confirm(@PathVariable Long id, Authentication auth) {
    return ResponseEntity.ok(service.confirm(id, Long.parseLong(auth.getName()), SecurityUtils.extractRole(auth)));
}
```

3. **Ownership** — in the service, pass `userId` and `role` from `Authentication` and check the resource belongs to the caller:

```java
private void verifyPermission(Appointment a, Long userId, String role) {
    if (role.equals(ERole.DOCTOR.name()) && !a.getDoctor().getId().equals(userId))
        throw new ForbiddenException("Not authorized to do this");
    if (role.equals(ERole.PATIENT.name()) && !a.getPatient().getId().equals(userId))
        throw new ForbiddenException("Not authorized to do this");
}
```

Helper to read the role back without the prefix:

```java
public final class SecurityUtils {
    private SecurityUtils() {}

    public static String extractRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(a -> Objects.requireNonNull(a.getAuthority()).replace("ROLE_", ""))
                .findFirst()
                .orElse(null);
    }
}
```

Never take the user id or role from the request body or path when it can come from the token.

## 10. Exception handling

`@PreAuthorize` failures throw `AuthorizationDeniedException`, which reaches `@RestControllerAdvice` **before** the `accessDeniedHandler`. If the advice has a catch-all `@ExceptionHandler(Exception.class)`, denied requests become 500 unless you map it explicitly:

```java
@ExceptionHandler(AuthorizationDeniedException.class)
public ResponseEntity<ErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
    return build(HttpStatus.FORBIDDEN, "Forbidden");
}

@ExceptionHandler(UnauthorizedException.class)   // login failures -> 401
@ExceptionHandler(ForbiddenException.class)      // ownership failures -> 403
```

## 11. OpenAPI (if springdoc is present)

```java
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
}
```

Add `/swagger-ui/**, /v3/api-docs/**` to public paths only in environments where docs should be exposed.

## Version notes

- Spring Boot 4 uses Jackson 3: `tools.jackson.databind.ObjectMapper`. Spring Boot 3 uses `com.fasterxml.jackson.databind.ObjectMapper`.
- Spring Security 6.3+ throws `AuthorizationDeniedException`; older versions throw `AccessDeniedException`.
- Unnamed lambda params (`(_, res, _) -> ...`) need Java 22+; use named params otherwise.
- `@EnableMethodSecurity` replaces the deprecated `@EnableGlobalMethodSecurity`.

## Checklist

- [ ] JWT secret from env, >= 32 bytes, not in the repo.
- [ ] Session policy `STATELESS`, CSRF disabled only because there are no auth cookies.
- [ ] Login path in `security.public-paths`; nothing else public by accident.
- [ ] Role stored as `ROLE_<NAME>` authority, checked with `hasRole/hasAnyRole('<NAME>')`.
- [ ] User id taken from `auth.getName()`, not from the client payload.
- [ ] Ownership checks in services for resources scoped to a user.
- [ ] 401 and 403 return the same JSON shape as other errors; `AuthorizationDeniedException` mapped to 403.
- [ ] CORS origins explicit per environment.
- [ ] Tests: `@WebMvcTest` with no token (401), wrong role (403), right role (200); a `JwtUtil` round-trip and expired-token test.
