---
name: spring-websocket
description: 'Implement real-time messaging in a Spring Boot REST API with WebSocket + STOMP: broker config, JWT authentication on the STOMP CONNECT frame, per-user and per-topic push from services (after commit), inbound @MessageMapping handlers, error frames, tenant context, and client usage with @stomp/stompjs. Use when the user asks to add websockets, STOMP, real-time updates, live notifications, push to the browser, a chat, or to fix websocket auth/CORS/handshake problems.'
---

# Spring WebSocket (STOMP + JWT)

Your goal is to add server-to-browser real-time messaging that:
- Exposes one STOMP endpoint (`/ws`) with an in-memory simple broker.
- Authenticates on the STOMP `CONNECT` frame with the same JWT the REST API uses (browsers can't send headers on the WebSocket handshake).
- Pushes events from the service layer **after the transaction commits**, to a topic or to one user.
- Keeps controllers/services as the only place with business logic; the WebSocket layer is transport only.

Before writing code, inspect the target project and adapt: the JWT helper (e.g. `JwtUtil.parse(token)` returning claims), what the JWT subject is (user id vs username), how roles are mapped (`ROLE_` prefix), the security filter chain's public paths, CORS allowed origins, and whether there is a tenant context (`TenantContext`) that must be set per message. Examples use placeholder `Appointment` events; rename to the project's own resources.

## Decide first: do you need WebSocket at all?

| Need                                                                 | Use                                                                           |
|----------------------------------------------------------------------|-------------------------------------------------------------------------------|
| Server pushes updates, client only reads (notifications, live lists) | WebSocket/STOMP (this skill), or SSE (`SseEmitter`) if one-way and few events |
| Bidirectional, low-latency (chat, collaborative editing)             | WebSocket/STOMP (this skill)                                                  |
| Client checks occasionally (every minute or more)                    | Plain polling of the REST endpoint; no WebSocket                              |

Prefer STOMP over raw `WebSocketHandler`: it gives destinations, subscriptions, per-user queues and a mature JS client for free. Use raw `TextWebSocketHandler` only for a custom binary/text protocol.

## Requirements and assumptions

| Item        | Assumed                                                          | If different                                                                                           |
|-------------|------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| Spring Boot | 3.x / 4.x, Spring MVC (servlet)                                  | WebFlux: use `WebSocketHandler` reactive API instead; this skill does not apply                        |
| Auth        | Stateless JWT (`Authorization: Bearer ...`) via a servlet filter | Session/cookie auth: the handshake already carries the session; skip the CONNECT interceptor           |
| Broker      | In-memory simple broker, single instance                         | Several instances behind a load balancer: switch to `enableStompBrokerRelay` (RabbitMQ/ActiveMQ STOMP) |
| Client      | Browser using `@stomp/stompjs` over native WebSocket             | Legacy browsers/proxies blocking WebSocket: add `.withSockJS()` and the `sockjs-client` package        |

## Flow

```
Client: new Client({ brokerURL: 'ws://host/ws', connectHeaders: { Authorization: 'Bearer <jwt>' } })
  └─ HTTP handshake GET /ws  (permitAll in the security chain; no auth possible here)
  └─ STOMP CONNECT  ──> JwtChannelInterceptor: parse JWT, set accessor.setUser(auth), reject if invalid
  └─ STOMP SUBSCRIBE /topic/... or /user/queue/...  ──> interceptor checks destination is allowed
Server: Service @Transactional write -> publishEvent(...)
  └─ @TransactionalEventListener(AFTER_COMMIT) -> SimpMessagingTemplate.convertAndSend / convertAndSendToUser
```

## 1. Dependency

```groovy
implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

(Maven: `spring-boot-starter-websocket`.) It brings `spring-messaging` and `spring-websocket`; no extra library is needed on the server.

## 2. Broker configuration

```java
package com.example.app.config.websocket;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }

    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
```

Conventions:
- Destination prefixes: `/topic/**` = broadcast (many subscribers), `/queue/**` = point-to-point, reached by the client as `/user/queue/**`; `/app/**` = messages the client sends to `@MessageMapping` handlers.
- Reuse the **same allowed origins** as the REST CORS config (same property). Never `setAllowedOrigins("*")` with credentials; WebSocket is not covered by the `CorsConfigurationSource` of the filter chain, it has its own origin check.
- Heartbeats on the simple broker only work with a `TaskScheduler`; without them, dead connections (closed laptops, mobile networks) linger until TCP times out.
- If the project already defines a `TaskScheduler` bean (e.g. for `@Scheduled`), reuse it instead of declaring a second one.

## 3. Security: open the handshake, authenticate the CONNECT frame

Browsers can't set `Authorization` on the WebSocket handshake, so the HTTP upgrade request must be public and authentication happens on the STOMP `CONNECT` frame.

In the existing `SecurityFilterChain`, add the endpoint to the public paths:

```java
.requestMatchers("/ws/**").permitAll()
```

Do **not** put the JWT in the URL (`/ws?token=...`): it ends up in proxy and access logs.

The interceptor:

```java
package com.example.app.config.websocket;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> requireUser(accessor);
            default -> { }
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        var header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        var claims = jwtUtil.parse(header.substring(7))
                .orElseThrow(() -> new MessageDeliveryException("Invalid token"));
        var role = claims.get("role", String.class);
        var authorities = role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) : List.<GrantedAuthority>of();
        // Keep claims the handlers need later (tenant, etc.) in the session attributes.
        accessor.getSessionAttributes().put("clinicId", claims.get("clinicId", Long.class));
        return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        var user = requireUser(accessor);
        var destination = accessor.getDestination();
        if (destination == null) throw new MessageDeliveryException("Missing destination");
        if (destination.startsWith("/user/queue/")) return;                       // own queue, always allowed
        if (destination.startsWith("/topic/admin/") && !hasRole(user, "ADMIN")) {
            throw new MessageDeliveryException("Forbidden destination");
        }
        // Add one rule per protected topic; deny unknown prefixes rather than allow them.
        if (!destination.startsWith("/topic/")) throw new MessageDeliveryException("Forbidden destination");
    }

    private Authentication requireUser(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication auth)) {
            throw new MessageDeliveryException("Not authenticated");
        }
        return auth;
    }

    private static boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
```

Why this shape:
- `accessor.setUser(...)` on `CONNECT` binds the principal to the whole WebSocket session; later frames (`SUBSCRIBE`, `SEND`) carry it automatically, and `convertAndSendToUser(principal.getName(), ...)` routes by it.
- Throwing in `preSend` makes Spring send a STOMP `ERROR` frame and, on `CONNECT`, close the connection. The client sees it in `onStompError`.
- Use the **same JWT parser** as the REST filter so token rules (expiry, signature, claims) stay in one place.
- The principal name must match what the service passes to `convertAndSendToUser`. If the JWT subject is the user id, push with `userId.toString()`.
- Token expiry: the JWT is checked once at `CONNECT`. A connection stays authenticated until it closes. If that matters, store `claims.getExpiration()` in the session attributes and reject `SEND`/`SUBSCRIBE` after it; the client reconnects with a fresh token.

Option: Spring Security's `@EnableWebSocketSecurity` with an `AuthorizationManager<Message<?>>` can replace `authorizeSubscription`. It enforces a CSRF token on `CONNECT` that stateless JWT clients don't have, so only adopt it if the project already handles that; otherwise the interceptor above is the simpler, explicit option.

## 4. Pushing from the service layer (main use case)

Services publish a domain event; a listener sends it **after commit**, so clients never see data that was rolled back, and the service stays free of transport code.

```java
public record AppointmentChangedEvent(Long appointmentId, Long patientUserId, String status) { }
```

```java
// In the service, inside the @Transactional write method:
eventPublisher.publishEvent(new AppointmentChangedEvent(saved.getId(), saved.getPatient().getId(), saved.getStatus().name()));
```

```java
package com.example.app.websocket;

@Component
@RequiredArgsConstructor
public class AppointmentWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentChanged(AppointmentChangedEvent event) {
        var payload = new AppointmentNotification(event.appointmentId(), event.status());
        messagingTemplate.convertAndSendToUser(event.patientUserId().toString(), "/queue/appointments", payload);
        messagingTemplate.convertAndSend("/topic/admin/appointments", payload);
    }
}
```

Conventions:
- Payloads are **records** (a response DTO or a small notification record), never entities: entities serialize lazy proxies and leak fields. Build the payload from the event's data, not by reloading entities after commit (the session is closed).
- `convertAndSendToUser(name, "/queue/x", payload)` delivers to every session of that user on `/user/queue/x`. `convertAndSend("/topic/x", payload)` broadcasts to all subscribers of `/topic/x`.
- Topics that carry tenant data must include the tenant in the destination (`/topic/clinic/{clinicId}/appointments`) and the interceptor must check the subscriber's `clinicId` session attribute matches. A bare `/topic/appointments` in a multi-tenant app leaks data across tenants.
- Sending is fire-and-forget: if no one is subscribed, the message is dropped. Clients must load the current state over REST on (re)connect, then apply pushed updates. WebSocket is not a durable queue.
- `@TransactionalEventListener` does nothing if there is no active transaction. If the event can be published outside a transaction, add `fallbackExecution = true`.

## 5. Inbound messages (only if the client sends data)

```java
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")                     // client sends to /app/chat.send
    public void send(@Payload @Valid ChatMessageRequest request, Principal principal) {
        chatService.send(Long.parseLong(principal.getName()), request);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponse handle(Exception ex) {
        return ErrorResponse.of(ex);                   // reuse the REST error body shape
    }
}
```

Conventions:
- `@MessageMapping` methods are thin like REST controllers: validate the payload, take the caller's identity from `Principal` (never from the payload), delegate to one service call.
- `@Valid` on `@Payload` needs the validation starter (already present in most projects); violations raise `MethodArgumentNotValidException` from `org.springframework.messaging.handler.annotation.support`, handled by `@MessageExceptionHandler`, not by the REST `@RestControllerAdvice`.
- Don't return internal exception messages to the client; map them to the same error shape the REST API uses.
- Prefer REST for writes and WebSocket only for push, unless latency really requires inbound STOMP. It keeps validation, auth and error handling in one well-tested path.

## 6. Thread-local context (tenant, security) in inbound handlers

Inbound frames are handled on the `clientInboundChannel` executor, not on the servlet thread, so the servlet filter's `TenantContext` / `SecurityContextHolder` are **not** set there. If `@MessageMapping` handlers hit tenant-scoped repositories, set the context on the handling thread with an `ExecutorChannelInterceptor`:

```java
@Component
public class TenantChannelInterceptor implements ExecutorChannelInterceptor {

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        var attrs = SimpMessageHeaderAccessor.getSessionAttributes(message.getHeaders());
        if (attrs != null && attrs.get("clinicId") instanceof Long clinicId) {
            TenantContext.setCurrentTenant("clinic_" + clinicId);
        }
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        TenantContext.clear();
    }
}
```

Register it after the JWT interceptor: `registration.interceptors(jwtChannelInterceptor, tenantChannelInterceptor);`. Always clear in `afterMessageHandled`; executor threads are reused across users.

Outbound pushes from `@TransactionalEventListener` run on the thread of the committing request, so the tenant is already set there; nothing extra is needed.

## 7. Client (browser)

```ts
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: `${import.meta.env.VITE_WS_URL}/ws`,          // ws://localhost:8080/ws, wss:// in production
  connectHeaders: { Authorization: `Bearer ${token}` },
  reconnectDelay: 5000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  onConnect: () => {
    reloadFromRest();                                      // resync state missed while disconnected
    client.subscribe('/user/queue/appointments', (msg) => onAppointment(JSON.parse(msg.body)));
    client.subscribe('/user/queue/errors', (msg) => onError(JSON.parse(msg.body)));
  },
  onStompError: (frame) => console.error(frame.headers['message']),
});

client.activate();
// on logout / unmount:
client.deactivate();
```

Conventions:
- Use `wss://` in production (same TLS as the API). Behind a reverse proxy, it must forward `Upgrade` / `Connection` headers and allow long-lived connections.
- Refresh the token in `beforeConnect` if tokens are short-lived, so reconnects don't use an expired one.
- One client per app (a singleton/hook), not one per component.

## 8. Testing

Integration test with a real STOMP client on a random port:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppointmentWebSocketIT {

    @LocalServerPort int port;
    @Autowired JwtUtil jwtUtil;

    @Test
    void rejectsConnectWithoutToken() {
        var stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        var future = stomp.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() { });
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }
}
```

Use `JacksonJsonMessageConverter` on Spring Framework 7 (Boot 4), `MappingJackson2MessageConverter` on Framework 6 (Boot 3). For the happy path, connect with `StompHeaders` containing `Authorization`, subscribe, trigger the REST write, and await the frame with a `BlockingQueue` + `poll(5, SECONDS)`. Unit-test the interceptor directly by building messages with `StompHeaderAccessor.create(StompCommand.CONNECT)` and `MessageBuilder.createMessage(...)`.

## Checklist

1. `spring-boot-starter-websocket` added; `WebSocketConfig` with `/ws`, simple broker on `/topic` + `/queue`, `/app` and `/user` prefixes, heartbeats with a `TaskScheduler`.
2. `/ws/**` is `permitAll` in the filter chain; allowed origin patterns reuse the REST CORS property.
3. `JwtChannelInterceptor` authenticates `CONNECT` with the same JWT parser as REST, rejects missing/invalid tokens, and checks `SUBSCRIBE` destinations (tenant and role).
4. Services publish domain events; a `@TransactionalEventListener(AFTER_COMMIT)` sends record payloads via `SimpMessagingTemplate`.
5. Principal name used in `convertAndSendToUser` matches the JWT subject.
6. Tenant-scoped topics include the tenant id; inbound handlers set and clear `TenantContext`.
7. Client sends the token in `connectHeaders`, resyncs over REST in `onConnect`, uses `wss://` in production.
8. Smoke test: connect without token fails; connect with token, subscribe to `/user/queue/...`, trigger a write over REST, the frame arrives.

## Antipatterns to reject

- JWT in the WebSocket URL query string.
- Sending from inside the `@Transactional` method (clients see rolled-back data, or receive the push before the row is visible to their follow-up REST call).
- Entities as payloads.
- `setAllowedOrigins("*")` or leaving `/ws` protected by the HTTP filter chain (handshake fails with 401/403 because browsers can't send the header).
- Taking the user id from the message payload instead of `Principal`.
- Broadcasting tenant or user data on a shared `/topic` without a destination check.
- Treating WebSocket as reliable delivery (no resync on reconnect).
- Simple broker with several app instances: users connected to instance A never get messages sent from instance B. Use a broker relay before scaling out.
- `ThreadLocal` context set in the servlet filter and assumed to exist in `@MessageMapping` handlers.
