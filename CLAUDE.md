# FraudLens — Implementation Guide for Claude Code

> **How**: Technical rules, patterns, and constraints that govern every implementation decision.
> For product requirements and acceptance criteria, see [SPEC.md](./SPEC.md).

---

## 1. Project Structure

```
fraudlens/
├── backend/                          # Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/fraudlens/
│   │   │   │   ├── controller/       # SessionController, EventController, AuthController
│   │   │   │   ├── service/          # SessionService, EventService, RiskScoringService, AIRiskSummaryService, AdminUserSeeder
│   │   │   │   ├── repository/       # SessionRepository, EventRepository, UserRepository
│   │   │   │   ├── domain/           # Session, Event, User (JPA entities + enums: SessionStatus, EventType)
│   │   │   │   ├── dto/              # All DTOs: SessionRequestDTO, SessionResponseDTO, EventRequestDTO,
│   │   │   │   │                     #   EventResponseDTO, LoginRequestDTO, LoginResponseDTO,
│   │   │   │   │                     #   SessionSearchRequestDTO, RiskSummaryResponseDTO, ErrorResponseDTO
│   │   │   │   ├── mapper/           # SessionMapper, EventMapper (MapStruct interfaces)
│   │   │   │   ├── security/         # JwtAuthenticationFilter, JwtService, UserDetailsServiceImpl
│   │   │   │   ├── exception/        # GlobalExceptionHandler, ResourceNotFoundException
│   │   │   │   └── config/           # SecurityConfig, CorsConfig, OpenApiConfig
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── db/changelog/
│   │   │           ├── db.changelog-master.yaml
│   │   │           └── 001-create-schema.sql
│   │   └── test/
│   │       └── java/com/fraudlens/
│   │           ├── controller/       # SessionControllerTest, EventControllerTest, AuthControllerTest
│   │           ├── service/          # SessionServiceTest, EventServiceTest, RiskScoringServiceTest
│   │           ├── mapper/           # SessionMapperTest
│   │           └── exception/        # GlobalExceptionHandlerTest
│   └── pom.xml
├── frontend/                         # React + Vite + TypeScript application
│   ├── src/
│   │   ├── api/                      # Axios instance + interceptors
│   │   │   ├── client.ts             # Axios instance with JWT interceptor
│   │   │   ├── sessions.ts           # Session API calls (CRUD + search)
│   │   │   └── events.ts             # Event API calls
│   │   ├── auth/                     # Authentication
│   │   │   ├── AuthContext.tsx       # JWT in-memory store + login/logout actions
│   │   │   ├── useAuth.ts            # Hook to consume AuthContext
│   │   │   └── ProtectedRoute.tsx    # Route guard — redirects to /login if unauthenticated
│   │   ├── components/               # Reusable UI components
│   │   │   ├── StatusBadge.tsx       # SAFE / SUSPICIOUS / DANGEROUS colored badge
│   │   │   ├── RiskScoreBadge.tsx    # Color-coded numeric score (green/amber/red)
│   │   │   ├── ConfirmModal.tsx      # Generic confirmation dialog (used for delete)
│   │   │   └── EventTimeline.tsx     # Chronological event list with type icons
│   │   ├── pages/                    # Route-level views
│   │   │   ├── LoginPage.tsx
│   │   │   ├── SessionListPage.tsx   # Table + status/country filter
│   │   │   ├── SessionDetailPage.tsx # Metadata + event timeline + AI summary button
│   │   │   └── SessionFormPage.tsx   # Create + Edit (same form, mode driven by route)
│   │   ├── types/                    # TypeScript interfaces mirroring backend DTOs
│   │   │   ├── session.ts
│   │   │   └── event.ts
│   │   ├── App.tsx                   # Router setup + AuthProvider wrapper
│   │   └── main.tsx
│   ├── .env.example                  # VITE_API_BASE_URL placeholder
│   ├── index.html
│   ├── tailwind.config.js
│   ├── tsconfig.json
│   └── package.json
├── scripts/
│   └── simulate.py                   # Fraud/legit simulation script
├── docker-compose.yml
└── README.md
```

---

## 2. Tech Stack & Exact Versions

### Backend
| Dependency | Version / Artifact |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.6 |
| Spring Web | (via Spring Boot BOM) |
| Spring Security | 6.x (via Spring Boot BOM) |
| Spring Data JPA | (via Spring Boot BOM) |
| Spring Boot Actuator | (via Spring Boot BOM) |
| Spring Boot Validation | `spring-boot-starter-validation` (via Spring Boot BOM) |
| Spring AI Anthropic | `spring-ai-starter-model-anthropic:1.0.0` |
| SpringDoc OpenAPI | `springdoc-openapi-starter-webmvc-ui:2.x` |
| Liquibase | (via Spring Boot BOM) |
| PostgreSQL Driver | (via Spring Boot BOM) |
| JJWT | `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.12.x) |
| Lombok | `1.18.42` |
| MapStruct | `org.mapstruct:mapstruct` + `mapstruct-processor` (1.6.3) |
| Bucket4j | `com.bucket4j:bucket4j-core` (8.x) for rate limiting |

### Frontend
| Package | Version |
|---|---|
| `react` | 18 |
| `react-dom` | 18 |
| `react-router-dom` | 6 |
| `@tanstack/react-query` | 5 |
| `axios` | 1 |
| `tailwindcss` | 3 |
| `lucide-react` | latest |
| TypeScript | latest stable |
| Vite | latest stable |

### Build & Infrastructure
- Build: **Maven** (use `mvn wrapper`)
- Containers: Docker Compose v3.8+

---

## 3. Backend Rules

### 3.1 Spring Security 6 — Mandatory Syntax

**NEVER use Spring Security 5 patterns.** The following are all removed or deprecated:

```java
// FORBIDDEN — Spring Security 5 patterns
extends WebSecurityConfigurerAdapter          // class is removed in Security 6
http.csrf().disable()                          // old chained style
http.antMatchers(...)                          // replaced by requestMatchers
@EnableGlobalMethodSecurity(...)              // replaced

// REQUIRED — Spring Security 6 patterns
@EnableMethodSecurity                          // correct annotation
http.csrf(csrf -> csrf.disable())              // lambda DSL
http.requestMatchers(...)                      // correct method
SecurityFilterChain bean (not a subclass)     // correct bean style
```

Complete `SecurityConfig` pattern:

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/auth/login",
        "/actuator/health",
        "/actuator/health/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated()
            );
        // JwtAuthenticationFilter added here before UsernamePasswordAuthenticationFilter
        // when auth package is implemented.
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 3.2 PostgreSQL Datasource & Liquibase Configuration

`application.properties` — credentials default to local docker-compose values so no profile or env vars are needed for local development. Never hardcode production credentials:

```properties
# Server
server.port=${SERVER_PORT:8080}

# Datasource — local defaults match docker-compose.yml credentials
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/fraudlens}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:fraudlens}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:fraudlens}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=false
spring.jpa.show-sql=false

# Liquibase
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml

# JWT — read by JwtService via @Value
jwt.secret=${JWT_SECRET:dev-secret-change-in-production-min-32chars}
jwt.expiration=3600000

# Spring AI - Anthropic
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:not-configured}
spring.ai.anthropic.chat.options.model=claude-haiku-4-5-20251001
spring.ai.anthropic.chat.options.max-tokens=1024

# Actuator
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=when-authorized
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true

# Logging
logging.level.com.fraudlens=DEBUG
```

> **No `application-local.yml`**: there is a single `application.properties`. Local defaults are embedded via `${ENV_VAR:default}`. In production, set real env vars to override.

**Liquibase changelog master** (`db.changelog-master.yaml`):

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/001-create-schema.sql
      relativeToChangelogFile: false
```

**Migration files are plain SQL.** Each file must begin with the Liquibase SQL format header. Multiple changesets can live in a single file — Liquibase tracks each one independently by its changeset ID:

```sql
-- 001-create-schema.sql
--liquibase formatted sql

--changeset fraudlens:001-create-sessions
CREATE TABLE IF NOT EXISTS sessions ( ... );

--changeset fraudlens:001-create-events
CREATE TABLE IF NOT EXISTS events ( ... );

--changeset fraudlens:001-create-users
CREATE TABLE IF NOT EXISTS users ( ... );
```

Add new migration files (e.g. `002-add-index.sql`) for future schema changes and include them in `db.changelog-master.yaml`. Never modify an already-applied changeset.

### 3.3 Entity Design — Cascade Delete & Bidirectional Relationship

Apply cascade at **both** the JPA and DB level. Both layers are intentional.

```java
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    private String id;   // String, not UUID — path variables stay @PathVariable String id

    // ... other fields ...

    @OneToMany(
        mappedBy = "session",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private List<Event> events = new ArrayList<>();

    @PrePersist
    private void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();   // server-generated UUID v4 as string
        }
    }

    // Convenience methods — always manage both sides of the relationship
    public void addEvent(Event event) {
        events.add(event);
        event.setSession(this);
    }

    public void removeEvent(Event event) {
        events.remove(event);
        event.setSession(null);
    }
}

@Entity
@Table(name = "events")
public class Event {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    // ENTITY RULE: do not add a bare `sessionId` String field here — navigate via session.getId()
    // DTO RULE: EventResponseDTO MUST include sessionId as a top-level field (PDF spec shows it explicitly)

    @PrePersist
    private void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
```

DB-level cascade in the Liquibase changeset (`V003__create_events.sql`):

```sql
ALTER TABLE events
  ADD CONSTRAINT fk_events_session
  FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE;
```

### 3.4 Naming Conventions

**DTOs must always carry the `DTO` suffix.** This makes it immediately clear that a class is a transport object and not a JPA entity.

| Purpose | Example Name |
|---|---|
| Response sent to client | `SessionResponseDTO`, `EventResponseDTO` |
| Request body for create/update | `SessionRequestDTO`, `EventRequestDTO` |
| Search filter body | `SessionSearchRequestDTO` |
| Auth request | `LoginRequestDTO` |
| Auth response | `LoginResponseDTO` |
| AI summary response | `RiskSummaryResponseDTO` |
| Error response | `ErrorResponseDTO` |

JPA entities use plain names with no suffix: `Session`, `Event`, `User`.

### 3.5 Entity ↔ DTO Mapping with MapStruct

Use **MapStruct** for all entity-to-DTO and DTO-to-entity conversions. Do not write manual mapping boilerplate.

```java
// mapper/SessionMapper.java
@Mapper(componentModel = "spring")
public interface SessionMapper {

    SessionResponseDTO toResponseDTO(Session session);

    Session toEntity(SessionRequestDTO dto);

    List<SessionResponseDTO> toResponseDTOList(List<Session> sessions);
}

// mapper/EventMapper.java
@Mapper(componentModel = "spring")
public interface EventMapper {

    // sessionId must be mapped from session.id — it is a required top-level field per PDF spec
    @Mapping(source = "session.id", target = "sessionId")
    EventResponseDTO toResponseDTO(Event event);

    List<EventResponseDTO> toResponseDTOList(List<Event> events);
}
```

For fields that require computation (e.g., `riskScore` is not on the entity), use `@AfterMapping` or wire the mapper through the service layer that enriches the DTO after mapping:

```java
// In SessionService:
public SessionResponseDTO toResponseDTO(Session session) {
    SessionResponseDTO dto = sessionMapper.toResponseDTO(session);
    dto.setRiskScore(riskScoringService.compute(session, session.getEvents()));
    return dto;
}
```

Add both `mapstruct` and `mapstruct-processor` to `pom.xml`. The processor must be in `annotationProcessorPaths` alongside `lombok-mapstruct-binding` to ensure correct annotation processing order:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>0.2.0</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>1.5.5.Final</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

### 3.6 POST /sessions/search — JSON Body Search

The search endpoint receives a JSON body, not query parameters. Route must not conflict with `POST /sessions` (create):

```java
@RestController
@RequestMapping("/sessions")
public class SessionController {

    @PostMapping                          // POST /sessions — create
    public ResponseEntity<SessionResponseDTO> create(...) { ... }

    @PostMapping("/search")               // POST /sessions/search — filter
    public ResponseEntity<List<SessionResponseDTO>> search(
        @RequestBody @Valid SessionSearchRequestDTO request) { ... }
}
```

`SessionSearchRequestDTO` — all fields optional:

```java
public record SessionSearchRequestDTO(
    SessionStatus status,
    String country,
    String userId,
    String ip,
    @Pattern(regexp = "timestamp|id") String sortBy,
    @Pattern(regexp = "asc|desc")     String sortDir
) {}
```

Implement filtering using JPA `Specification<Session>` for composable, null-safe predicates.

### 3.7 Global Exception Handler

All exceptions must be caught and translated to the standard `ErrorResponse` format. **Never let a raw exception or stack trace reach the client.**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(404, "Not Found", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(400, "Bad Request", message, req.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(409, "Conflict", "Data integrity violation", req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        // log ex internally but never expose message or class name to client
        log.error("Unhandled exception on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(500, "Internal Server Error", "An unexpected error occurred", req.getRequestURI()));
    }
}
```

`ErrorResponseDTO` must be a Java record:

```java
public record ErrorResponseDTO(Instant timestamp, int status, String error, String message, String path) {
    public static ErrorResponseDTO of(int status, String error, String message, String path) {
        return new ErrorResponseDTO(Instant.now(), status, error, message, path);
    }
}
```

### 3.8 Environment Variables — No Hardcoded Secrets

**Never hardcode** the following in any source file or `application.yml` without `${}` placeholder:

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `JWT_SECRET` | HMAC-SHA256 signing key (min 256 bits) |
| `JWT_EXPIRATION_MS` | Token validity period (e.g. `3600000` = 1h) |
| `ANTHROPIC_API_KEY` | API key for Claude (AI bonus) |
| `CORS_ALLOWED_ORIGIN` | Frontend origin (e.g. `http://localhost:5173`) |
| `APP_ADMIN_USERNAME` | Seeded demo user login |
| `APP_ADMIN_PASSWORD` | Seeded demo user password (plaintext; hashed on startup) |

Provide `.env.example` at repo root with all variables listed but no real values.

### 3.9 Spring AI — ChatClient Fluent API (1.0.x Style)

Use the `ChatClient` fluent builder pattern. Do **not** use the older `ChatModel` direct invocation.

```java
@Service
public class AIRiskSummaryService {

    private final ChatClient chatClient;

    public AIRiskSummaryService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generateRiskSummary(Session session, List<Event> events) {
        String prompt = buildPrompt(session, events);
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }

    private String buildPrompt(Session session, List<Event> events) {
        // Use Java text block
        return """
            Analyze the following user session for fraud risk and provide a concise 2-3 sentence
            natural language assessment. End with "Risk level: LOW | MEDIUM | HIGH".

            Session:
            - Country: %s
            - Device: %s
            - Status: %s
            - Risk Score: %d/100

            Events (chronological):
            %s
            """.formatted(
                session.getCountry(),
                session.getDevice(),
                session.getStatus(),
                session.getRiskScore(),
                formatEvents(events)
            );
    }
}
```

Rate-limit the AI endpoint using Bucket4j (max 10 requests/minute per user):

```java
@PostMapping("/{id}/risk-summary")
public ResponseEntity<RiskSummaryResponse> generateSummary(
    @PathVariable UUID id,
    Authentication auth) {
    // check bucket before calling AI service
}
```

### 3.10 CORS — Restricted to Frontend Origin

Never use `*` as the allowed origin. Read from environment variable:

```java
@Configuration
public class CorsConfig {

    @Value("${CORS_ALLOWED_ORIGIN}")
    private String allowedOrigin;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

### 3.11 SpringDoc OpenAPI — Required Configuration

Use `springdoc-openapi-starter-webmvc-ui` version **2.x** (not 1.x which is Spring Boot 2 only):

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

Configure JWT Bearer auth scheme in the OpenAPI bean:

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fraudLensOpenAPI() {
        return new OpenAPI()
            .info(new Info().title("FraudLens API").version("1.0"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
```

Swagger UI is available at `/swagger-ui.html`. Permit this path in Spring Security (already included in §3.1 example). Add `@Operation` and `@ApiResponse` annotations on all controller methods.

### 3.12 Actuator — Health Endpoint Only

Expose only `health`. All other actuator endpoints must be disabled or require authentication:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health           # only health — nothing else
  endpoint:
    health:
      show-details: always        # shows DB indicator details
      probes:
        enabled: true
  server:
    port: ${MANAGEMENT_PORT:8081} # optional: separate port for actuator
```

Never expose `/actuator/env`, `/actuator/beans`, `/actuator/mappings`, or `/actuator/metrics` publicly. They leak sensitive configuration and internal structure.

### 3.13 Database Schema & User Seeding

Schema is owned entirely by Liquibase. `ddl-auto: validate` ensures Spring fails fast if entities and schema drift.

**User seeding strategy**: Do NOT put the password hash in a Liquibase changeset (it would be in source control). Instead, use a Spring `ApplicationRunner` that:
1. Reads `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` from environment
2. BCrypt-encodes the password at runtime
3. Inserts the user if no user with that username exists (idempotent)

```java
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${APP_ADMIN_USERNAME}")
    private String adminUsername;

    @Value("${APP_ADMIN_PASSWORD}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            userRepository.save(new User(adminUsername, passwordEncoder.encode(adminPassword)));
        }
    }
}
```

---

## 4. Frontend Rules

### 4.1 JWT — Store in Memory Only (NOT localStorage)

**Never** write the JWT to `localStorage` or `sessionStorage`. Store it exclusively in React state via `AuthContext`:

```tsx
// auth/AuthContext.tsx
const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [token, setToken] = useState<string | null>(null);   // in-memory only

    const login = async (username: string, password: string) => {
        const { data } = await axios.post('/auth/login', { username, password });
        setToken(data.token);   // stored in React state, lost on refresh — by design
    };

    const logout = () => setToken(null);

    return (
        <AuthContext.Provider value={{ token, login, logout, isAuthenticated: !!token }}>
            {children}
        </AuthContext.Provider>
    );
}
```

**Trade-off to document in README**: Token is lost on page refresh, requiring re-login. This is an acceptable trade-off for this scope and is far safer than XSS-vulnerable localStorage storage. In production, a refresh token in an `HttpOnly` cookie would solve this.

### 4.2 Axios — Attach JWT via Interceptor

```typescript
// api/client.ts
const apiClient = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL });

// Set in AuthProvider after login:
apiClient.interceptors.request.use(config => {
    const token = getTokenFromContext();   // via context ref or param
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

apiClient.interceptors.response.use(
    res => res,
    err => {
        if (err.response?.status === 401) {
            // clear token from context, redirect to login
        }
        return Promise.reject(err);
    }
);
```

### 4.3 Protected Routes

```tsx
// auth/ProtectedRoute.tsx
export function ProtectedRoute({ children }: { children: ReactNode }) {
    const { isAuthenticated } = useAuth();
    return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}
```

### 4.4 Environment Variables (Frontend)

```
VITE_API_BASE_URL=http://localhost:8080
```

Never hardcode the API base URL. Prefix all frontend env vars with `VITE_`.

---

## 5. Risk Scoring Service

Implement as a pure stateless Spring `@Service` with no external dependencies. The score is computed on demand when building a `SessionResponse` DTO. It is never persisted.

Rules must be defined as named constants. Each rule must have a corresponding unit test.

```java
@Service
public class RiskScoringService {

    private static final int MAX_SCORE = 100;

    // Rule weights — all defined as named constants, not magic numbers
    static final int WEIGHT_UNUSUAL_COUNTRY         = 15;
    static final int WEIGHT_LOGIN_THEN_FORM_FAST    = 25;
    static final int WEIGHT_SENSITIVE_FORM_FIELDS   = 20;
    static final int WEIGHT_MULTIPLE_LOGIN_ATTEMPTS = 15;
    static final int WEIGHT_BOT_SPEED_SUBMISSION    = 10;
    static final int WEIGHT_DANGEROUS_STATUS        = 10;
    static final int WEIGHT_SUSPICIOUS_STATUS       = 5;

    public int compute(Session session, List<Event> events) {
        int score = 0;
        // apply rules...
        return Math.min(score, MAX_SCORE);
    }
}
```

---

## 6. Test Coverage Targets

| Layer | Approach | Target Coverage | Done |
|---|---|---|---|
| `RiskScoringService` | Unit tests, mocked event lists | **100%** — every rule branch tested | ✅ 31 tests |
| `SessionService` + `EventService` | Unit tests with mocked repositories (`@ExtendWith(MockitoExtension.class)`) | **80%+** | ✅ 13 + 8 tests |
| `GlobalExceptionHandler` | Unit tests for each exception type | **100%** | ✅ 7 tests (all 6 handlers + AuthenticationException) |
| `SessionController` | `@WebMvcTest` + `MockMvc` — all endpoints, all HTTP methods, auth scenarios | **80%+** | ✅ 23 tests |
| `EventController` | `@WebMvcTest` + `MockMvc` | **80%+** | ✅ 11 tests |
| `AuthController` | `MockMvc` tests: valid login, invalid login, missing token, expired token | **100%** | ✅ 6 tests |
| `SessionMapper` (MapStruct) | Unit tests verifying field mapping correctness | Required | ✅ 10 tests |
| Overall line coverage | Jacoco enforced in Maven build | **70%+** | ✅ 109 tests total |

For `@WebMvcTest` slice tests, mock the service layer with `@MockBean`. This keeps controller tests fast and isolated without requiring a database connection.

Configure Jacoco to **fail the build** if coverage drops below target:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <rules>
            <rule>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.70</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

---

## 7. Anti-Patterns — Never Do These

| Anti-pattern | Why | What to Do Instead |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto=create-drop` | Destroys schema on restart | Use `validate`; let Liquibase manage DDL |
| `extends WebSecurityConfigurerAdapter` | Removed in Spring Security 6 | Use `SecurityFilterChain` bean |
| `http.csrf().disable()` | Spring Security 5 chained API | Use `http.csrf(csrf -> csrf.disable())` |
| JWT in `localStorage` | Vulnerable to XSS | Store in React state (AuthContext) |
| Hardcoded secrets in `application.yml` | Secrets in source control | Use `${ENV_VAR_NAME}` with `.env.example` |
| `show-sql: true` in production | Leaks schema and query structure | Only in `application-local.yml` |
| Returning entity objects directly from controllers | Leaks internal fields, breaks encapsulation | Always map to response DTOs using MapStruct |
| Manual entity↔DTO mapping | Verbose, error-prone, hard to maintain | Use MapStruct `@Mapper` interfaces |
| DTO classes without `DTO` suffix | Ambiguous — is it an entity or a transport object? | Always suffix DTOs: `SessionResponseDTO`, `SessionRequestDTO` |
| `@Transactional` on controller methods | Transaction scope too broad | Apply on service methods only |
| H2 in tests | Doesn't support PostgreSQL-specific features (JSONB, UUID) | Use `@WebMvcTest` with mocked services for controller tests |
| Exposing raw exception messages to clients | Leaks internal details | Catch in `GlobalExceptionHandler`, return generic message |
| CORS `allowedOrigins("*")` | Allows any origin | Restrict to `${CORS_ALLOWED_ORIGIN}` |
| Exposing all actuator endpoints | Leaks config and bean wiring | Expose only `health` |
