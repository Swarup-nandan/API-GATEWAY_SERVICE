# API Gateway & Authentication Service

A secure API Gateway / Auth service built with **Java 17 + Spring Boot 3**, featuring:

- **JWT authentication** — stateless access + refresh tokens (HS256)
- **Role-Based Access Control (RBAC)** — `ROLE_USER` / `ROLE_ADMIN`, enforced both at the URL level (`SecurityFilterChain`) and per-method (`@PreAuthorize`)
- **Redis-backed rate limiting** — atomic token-bucket algorithm implemented as a Lua script, keyed per authenticated user (falls back to client IP for anonymous requests)
- Scalable REST APIs with Spring Boot, H2 in-memory DB for easy local dev (swap for Postgres/MySQL in prod)

## Stack

| Concern         | Choice                          |
|------------------|---------------------------------|
| Language         | Java 17                         |
| Framework        | Spring Boot 3.2.5               |
| Security         | Spring Security 6 + jjwt 0.11.5 |
| Rate limiting    | Redis (Lettuce) + Lua script    |
| Persistence      | Spring Data JPA + H2 (dev)      |
| Build            | Maven                           |

## Project layout

```
src/main/java/com/gateway/
├── ApiGatewayApplication.java
├── bootstrap/DataSeeder.java        # seeds demo admin/user on startup
├── config/
│   ├── SecurityConfig.java          # filter chain, RBAC rules, filter ordering
│   └── RedisConfig.java
├── controller/
│   ├── AuthController.java          # /api/auth/register, /login, /refresh
│   ├── UserController.java          # /api/user/** (any authenticated user)
│   └── AdminController.java         # /api/admin/** (ROLE_ADMIN only)
├── dto/                             # request/response payloads + validation
├── entity/                          # User, Role
├── exception/GlobalExceptionHandler.java
├── ratelimit/
│   ├── RateLimiterService.java      # Redis Lua token-bucket implementation
│   └── RateLimitFilter.java         # wired into the security chain after JWT auth
├── repository/UserRepository.java
└── security/
    ├── JwtService.java              # token generation/parsing/validation
    ├── JwtAuthenticationFilter.java # Bearer token -> SecurityContext
    ├── CustomUserDetails.java
    └── CustomUserDetailsService.java
```

## Running locally

**Prerequisites:** JDK 17+, Maven 3.9+, a Redis instance reachable at `localhost:6379` (or set `REDIS_HOST` / `REDIS_PORT`).

```bash
# start Redis (example via Docker)
docker run -d --name redis -p 6379:6379 redis:7-alpine

# run the app
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. On first boot, `DataSeeder` creates two demo accounts:

| Username | Password   | Roles              |
|----------|------------|---------------------|
| `admin`  | `Admin@123`| ROLE_ADMIN, ROLE_USER |
| `demo`   | `Demo@123` | ROLE_USER            |

Change the JWT secret (`jwt.secret` in `application.yml`, or the `JWT_SECRET` env var) and these seeded credentials before deploying anywhere real.

## API

### Register
```
POST /api/auth/register
{
  "username": "swarup",
  "email": "swarup@example.com",
  "password": "StrongPass123"
}
```

### Login
```
POST /api/auth/login
{
  "username": "swarup",
  "password": "StrongPass123"
}
```
Returns `accessToken` (15 min expiry) + `refreshToken` (7 day expiry).

### Refresh
```
POST /api/auth/refresh
{ "refreshToken": "<token>" }
```

### Call a protected endpoint
```
GET /api/user/profile
Authorization: Bearer <accessToken>
```

### Admin-only endpoint
```
GET /api/admin/dashboard
Authorization: Bearer <accessToken>   # must belong to a ROLE_ADMIN user
```

## Rate limiting

Every request (after JWT auth runs) is checked against a Redis token bucket keyed by `user:<username>` (or `ip:<address>` for unauthenticated calls to `/api/auth/**`). Defaults, in `application.yml`:

```yaml
rate-limit:
  capacity: 20              # burst size
  refill-tokens: 20         # tokens added per period
  refill-period-seconds: 60 # i.e. ~20 requests/minute/user, refilled continuously
```

Responses include `X-RateLimit-Limit` and `X-RateLimit-Remaining` headers. Exceeding the limit returns `429 Too Many Requests`.

The bucket state lives in Redis (`rate_limit:<key>` hash), so the limit is shared correctly across multiple instances of this service behind a load balancer — the check-and-decrement happens atomically via a Lua script (`EVAL`), avoiding race conditions.

## Notes / next steps

- Swap H2 for Postgres/MySQL by changing `spring.datasource.*` and adding the driver dependency.
- Add a token-revocation/blacklist (e.g. a Redis set of revoked JWT IDs) for a proper logout flow.
- Consider short-lived access tokens + refresh-token rotation for stronger security in production.
- Add integration tests (Spring Security Test + Testcontainers for Redis/Postgres) before shipping.
