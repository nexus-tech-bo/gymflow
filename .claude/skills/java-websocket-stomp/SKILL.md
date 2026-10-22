---
name: java-websocket-stomp
description: Real-time push with Spring WebSocket + STOMP - broker configuration, endpoint, heartbeats, JWT authentication on STOMP CONNECT, per-topic subscription authorization, and publishing domain events only after the transaction commits. Use when adding live updates/notifications, configuring WebSockets or STOMP in Spring Boot, securing WebSocket connections, or sending messages to clients from services.
---

# WebSocket + STOMP (Spring Boot)

Server → client push of domain events over STOMP. Examples use a fictional **library** app that pushes loan changes to a per-branch board: `/topic/branch/{branchId}/loans`.

## Dependency

```groovy
implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

## Architecture

```
Service (@Transactional)
   └─ eventPublisher.publishEvent(new LoanChangedEvent(branchId, payload))
                │  (held until commit)
                ▼
LoanBoardPublisher  @TransactionalEventListener(AFTER_COMMIT)
   └─ SimpMessagingTemplate.convertAndSend("/topic/branch/{id}/loans", payload)
                ▼
Simple broker → subscribed STOMP clients

Client CONNECT  ──▶ JwtChannelInterceptor (validate token, store branchId in session)
Client SUBSCRIBE ─▶ JwtChannelInterceptor (allow only its own branch topic)
Client SEND     ──▶ rejected (push-only design)
```

## Files

```
config/WebSocketConfig.java
websocket/LoanChangedEvent.java      event + topic naming
websocket/LoanBoardEvent.java        payload sent to clients
websocket/LoanBoardPublisher.java    AFTER_COMMIT listener
websocket/JwtChannelInterceptor.java auth on CONNECT/SUBSCRIBE
```

## WebSocketConfig

```java
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Value("${cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
        // add .withSockJS() only if you must support clients without native WebSocket
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

- `/topic/**` = broadcast (one-to-many), `/queue/**` + `/user/**` = per-user messages.
- `/app/**` = messages handled by `@MessageMapping` controllers (only if clients send).
- Heartbeats need a `TaskScheduler`; they keep proxies/load balancers from closing idle connections and detect dead clients.
- Reuse the same allowed origins as CORS.
- In `SecurityConfig`, `permitAll()` on `/ws/**` — browsers can't send an `Authorization` header on the WebSocket handshake, so authentication happens on the STOMP `CONNECT` frame instead.
- The simple broker is in-memory: with several app instances, switch to `enableStompBrokerRelay(...)` (RabbitMQ/ActiveMQ) so every instance sees every message.

## Event and payload

```java
public record LoanBoardEvent(Long loanId, LoanStatus status) {}

public record LoanChangedEvent(Long branchId, LoanBoardEvent payload) {
    public static String boardTopic(Long branchId) {
        return "/topic/branch/" + branchId + "/loans";
    }
}
```

- Topic naming lives in one static method used by both the publisher and the interceptor, so they can't drift.
- Keep payloads small (ids + status). Clients refetch details via REST — this avoids leaking data and keeps one source of truth.

## Publishing after commit

```java
@Component
@RequiredArgsConstructor
public class LoanBoardPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoanChanged(LoanChangedEvent event) {
        messagingTemplate.convertAndSend(LoanChangedEvent.boardTopic(event.branchId()), event.payload());
    }
}
```

In the service:

```java
private final ApplicationEventPublisher eventPublisher;

@Transactional
public LoanResponse returnLoan(Long loanId) {
    var loan = getLoanOrThrowById(loanId);
    loan.setStatus(LoanStatus.RETURNED);
    eventPublisher.publishEvent(new LoanChangedEvent(loan.getBranchId(), new LoanBoardEvent(loan.getId(), loan.getStatus())));
    return loanMapper.toResponse(loan);
}
```

Why not call `SimpMessagingTemplate` directly from the service? If the transaction rolls back, clients would already have been told about a change that never happened, or would refetch before the data is committed. `AFTER_COMMIT` fixes both. (If the publisher must run without a transaction too, add `fallbackExecution = true`.)

## JwtChannelInterceptor

```java
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final ActiveUserChecker activeUserChecker;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new MessageDeliveryException("Sending is not supported");
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        var header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer "))
            throw new MessageDeliveryException("Missing bearer token");
        var claims = jwtUtil.parse(header.substring(7))
                .orElseThrow(() -> new MessageDeliveryException("Invalid token"));
        var role = claims.get("role", String.class);
        if (!activeUserChecker.isActive(Long.valueOf(claims.getSubject()), role))
            throw new MessageDeliveryException("Invalid token");

        var attrs = accessor.getSessionAttributes();
        if (attrs == null) throw new MessageDeliveryException("Invalid token");
        attrs.put("branchId", claims.get("branchId", Long.class));

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        var attrs = accessor.getSessionAttributes();
        if (accessor.getUser() == null || attrs == null || !(attrs.get("branchId") instanceof Long branchId))
            throw new MessageDeliveryException("Not authenticated");
        if (!LoanChangedEvent.boardTopic(branchId).equals(accessor.getDestination()))
            throw new MessageDeliveryException("Forbidden destination");
    }
}
```

- **CONNECT**: validate the JWT from the STOMP native header, store what later checks need in session attributes, set the user principal.
- **SUBSCRIBE**: whitelist destinations — a client must not be able to subscribe to another branch's topic by guessing the id.
- **SEND**: rejected when the design is push-only. If clients do send, authorize per destination here and handle with `@MessageMapping`.
- Throwing `MessageDeliveryException` makes Spring send a STOMP `ERROR` frame and close the session.

## Per-user messages (optional)

```java
messagingTemplate.convertAndSendToUser(memberId.toString(), "/queue/notifications", payload);
```

The client subscribes to `/user/queue/notifications`; Spring resolves the user from the principal set on CONNECT. Allow `/user/queue/**` in `authorizeSubscription`.

## Client side (JavaScript, @stomp/stompjs)

```js
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: `${wsBaseUrl}/ws`,
  connectHeaders: { Authorization: `Bearer ${token}` },
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  reconnectDelay: 5000,
  onConnect: () => {
    client.subscribe(`/topic/branch/${branchId}/loans`, (msg) => {
      const event = JSON.parse(msg.body); // { loanId, status }
      refetchLoans();
    });
  },
});
client.activate();
```

Recreate the client with a fresh token after re-login; on reconnect, refetch state since events may have been missed.

## Checklist

- [ ] `/ws/**` permitted in HTTP security; auth enforced on STOMP CONNECT
- [ ] Allowed origins match CORS config
- [ ] Heartbeats configured with a task scheduler
- [ ] Subscriptions restricted to destinations the user may see
- [ ] Events published via `ApplicationEventPublisher` and sent `AFTER_COMMIT`
- [ ] Small payloads; clients refetch via REST
- [ ] External broker relay if running more than one instance
