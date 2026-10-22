---
name: java-jwt-security
description: Stateless JWT authentication and role-based authorization for a Spring Boot REST API with Spring Security 6+ and jjwt - SecurityFilterChain, JWT generation/parsing utility, OncePerRequestFilter, login endpoint, BCrypt, @PreAuthorize roles, JSON 401/403 responses, public paths and revoking tokens of disabled users. Use when adding authentication/login, securing endpoints, adding roles, configuring Spring Security, or debugging 401/403 errors.
---

# JWT Security (Spring Security + jjwt)

Stateless bearer-token authentication. Examples use a fictional **library** app where a `Member` logs in with role `ADMIN`, `LIBRARIAN` or `READER`.

## Dependencies (Gradle)

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

## Configuration

```yaml
jwt:
  secret: ${JWT_SECRET}          # >= 32 bytes for HS256; never commit it
  expiration-ms: 86400000        # 24h

security:
  public-paths: /api/auth/login, /swagger-ui/**, /v3/api-docs/**
```

## Pieces

```
config/SecurityConfig.java         SecurityFilterChain, JSON error handlers
config/PasswordEncoderConfig.java  BCrypt bean
security/JwtUtil.java              generate + parse tokens
security/ActiveUserChecker.java    rejects tokens of disabled users / changed roles
security/SecurityUtils.java        helpers to read role/user from Authentication
middleware/JwtAuthFilter.java      reads "Authorization: Bearer …", fills SecurityContext
controller/AuthController.java     POST /api/auth/login, GET /api/auth/me
```

## JwtUtil

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

- `subject` = user id (stable even if the username changes).
- Put only what the client or the filter needs in claims (role, display name, tenant id). **Never** passwords or sensitive data — JWTs are only signed, not encrypted.
- `parse` returns `Optional.empty()` for expired, malformed or tampered tokens; the request then continues unauthenticated and hits the 401 entry point.

## Password encoder

```java
@Configuration
public class PasswordEncoderConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

Kept in its own class to avoid a circular dependency between `SecurityConfig` and services that hash passwords.

## JwtAuthFilter

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ActiveUserChecker activeUserChecker;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtUtil.parse(header.substring(7)).ifPresent(claims -> {
                var role = claims.get("role", String.class);
                var userId = Long.valueOf(claims.getSubject());
                try {
                    if (!activeUserChecker.isActive(userId, role)) return;
                } catch (DataAccessException e) {
                    log.warn("Active user lookup failed for user {}: {}", userId, e.getMessage());
                    return;
                }
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
            });
        }
        chain.doFilter(request, response);
    }
}
```

- The filter **never writes an error itself**. It either authenticates or leaves the context empty; Spring Security decides 401/403.
- `ROLE_` prefix so `hasRole('ADMIN')` matches.
- Principal = user id string → `auth.getName()` in controllers.
- If you keep request-scoped state (e.g. a tenant `ThreadLocal`), set it here and clear it in a `finally` block.

## ActiveUserChecker — make deactivation effective immediately

JWTs are valid until they expire. To revoke access when an admin disables a user or changes their role, check the DB on each request:

```java
@Component
@RequiredArgsConstructor
public class ActiveUserChecker {
    private final MemberRepository memberRepository;

    public boolean isActive(Long userId, String role) {
        return Arrays.stream(Role.values())
                .filter(r -> r.name().equals(role))
                .findFirst()
                .map(r -> memberRepository.existsByIdAndActiveTrueAndRole(userId, r))
                .orElse(false);
    }
}
```

It's a cheap indexed `exists` query. Skip it only if you accept that disabled users keep access until token expiry (then use short-lived tokens + refresh tokens).

## SecurityConfig

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
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    if (publicPaths.length > 0)
                        a.requestMatchers(publicPaths).permitAll();
                    a.requestMatchers("/ws/**").permitAll();   // WebSocket handshake; STOMP CONNECT is authenticated separately
                    a.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(req, res, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> writeError(req, res, HttpStatus.FORBIDDEN, "Access denied")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String message) throws IOException {
        var body = ErrorResponse.of(status, message, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
```

- **CSRF disabled** is correct only because auth uses the `Authorization` header (not cookies). If you store the JWT in a cookie, re-enable CSRF.
- `.cors(Customizer.withDefaults())` picks up the CORS config from `WebMvcConfigurer` / `CorsConfigurationSource` (see `java-common-config`). Without it, preflight `OPTIONS` requests get 401.
- Public paths are configurable per environment via `security.public-paths`.
- 401/403 bodies use the same `ErrorResponse` record as the `GlobalExceptionHandler`.
- Spring Boot 4 / Jackson 3 uses `tools.jackson.databind.ObjectMapper`; Boot 3 uses `com.fasterxml.jackson.databind.ObjectMapper`.

## Login flow

```java
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
public record LoginResponse(String token) {}
```

```java
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        var member = memberRepository.findByUsername(request.username().toLowerCase(Locale.ROOT))
                .filter(Member::isActive)
                .filter(m -> passwordEncoder.matches(request.password(), m.getPassword()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        return new LoginResponse(jwtUtil.generate(member.getId(), member.getRole().name(), member.getUsername()));
    }
}
```

- Same generic message for unknown user, wrong password and disabled account — don't reveal which one failed.
- Store only BCrypt hashes: `passwordEncoder.encode(raw)` on create/change password.

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<MemberResponse> me(Authentication auth) {
        return ResponseEntity.ok(authService.findProfile(Long.parseLong(auth.getName())));
    }
}
```

## Authorization

Method-level, on controllers:

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
@PreAuthorize("isAuthenticated()")
```

Ownership rules ("a reader can only see their own loans") go in the service, using the authenticated id:

```java
public static String extractRole(Authentication auth) {
    return auth.getAuthorities().stream()
            .map(a -> a.getAuthority().replace("ROLE_", ""))
            .findFirst()
            .orElse(null);
}
```

```java
if ("READER".equals(SecurityUtils.extractRole(auth)) && !loan.getMember().getId().equals(Long.parseLong(auth.getName())))
    throw new ForbiddenException("You can only view your own loans");
```

Also add an `AccessDeniedException` handler in `GlobalExceptionHandler` → 403, because `@PreAuthorize` failures inside controllers reach the advice before the security `accessDeniedHandler`.

## Client usage

```
POST /api/auth/login  {"username":"jane","password":"secret"}  → {"token":"eyJ..."}
GET  /api/books       Authorization: Bearer eyJ...
```

## Checklist

- [ ] Secret from env var, ≥ 32 bytes
- [ ] `STATELESS` sessions, CSRF off only with header-based tokens
- [ ] CORS enabled in the chain
- [ ] Filter registered before `UsernamePasswordAuthenticationFilter`
- [ ] JSON 401/403 via entry point / access denied handler
- [ ] `@EnableMethodSecurity` + `@PreAuthorize` on every non-public endpoint
- [ ] Disabled users rejected (DB check or short token TTL)
- [ ] Passwords hashed with BCrypt; generic login error
