# Auth Security: Rate Limiting + Actuator

Spring Security 6 общая конфигурация (JWT, jjwt, BCrypt, access/refresh
tokens, roles USER/ADMIN) — в `backend/CLAUDE.md` секция «Security
(ADR-040)». Этот файл — детали по двум **дополнительным** ADR'ам:
rate limit на auth endpoints (ADR-046) и actuator behind basic auth
в prod (ADR-048).

## Rate Limiting (ADR-046)

`/auth/login` и `/auth/register` защищены custom in-memory
sliding-window filter (`RateLimitFilter`). Применяется ПЕРЕД JWT в
SecurityFilterChain (`addFilterBefore(rateLimitFilter,
UsernamePasswordAuthenticationFilter.class)`) — блокирует brute force
до bcrypt / DB lookup.

### Property `auth.rate-limit.enabled=false` по умолчанию

dev/test/local работают без настройки. В prod opt-in через env
`AUTH_RATE_LIMIT_ENABLED=true`. Конфиг limits/lockout/whitelist через
`auth.rate-limit.*` (см. `application.yml`).

### Sliding window

1 минута per (IP, endpoint). При превышении лимита — lockout
`auth.rate-limit.lockout-duration` (default `PT15M`). Lockout expiry
→ reset attempts → unblock.

### IP extraction

`X-Forwarded-For` (first) > `X-Real-IP` > `remoteAddr` с port-stripping
(защита от bypass через `1.2.3.4:9999`).

### Clock injection

`AuthClockConfig.systemClock` — тесты override через
`@TestConfiguration` + `MutableClock` для fast-forward через lockout
без `Thread.sleep`.

### Когда расширять на другие endpoints

Сейчас 2 path hardcoded в filter. При защите `/search`, `/upload`
(или `/auth/refresh` при злоупотреблении) — либо вынести path-конфигу
в properties, либо ввести annotation-based marking. До того — не
делать preemptively.

### НЕ применять к `/auth/refresh`

Frontend может legit ретрать при просрочке access token, легко
достичь 5 attempts/min при нескольких открытых tabs.

## Actuator Security (ADR-048)

`/actuator/**` обрабатывается **отдельным `SecurityFilterChain`**
(`ActuatorSecurityConfig`, `@Order(1)`,
`securityMatcher("/actuator/**")`). Главный `SecurityConfig`
actuator-правил больше не содержит.

### В prod profile

Basic auth для всех actuator endpoints кроме `/actuator/health` +
`/actuator/health/**` + `/actuator/info`. Credentials через env
`ACTUATOR_USERNAME` / `ACTUATOR_PASSWORD`, in-memory user с ролью
`ACTUATOR`. Fail-fast при пустых значениях в prod.

### В dev/test/local profile

Chain `permitAll` на всё actuator — curl без креденшалов работает
как раньше.

### HTTP security headers

(HSTS / CSP / Referrer / Permissions) — mirror'ятся из main chain в
actuator chain, чтобы /actuator/health отдавал тот же набор
header'ов что и API.

### Локальный AuthenticationManager

Через `ProviderManager` + `DaoAuthenticationProvider` с
`DelegatingPasswordEncoder` (понимает `{noop}` prefix для plain text
из env). Не конфликтует с глобальным `BCryptPasswordEncoder`
(используется для основной JWT auth).

### Тесты для prod profile

Требуют `@TestPropertySource` с `actuator.security.username` +
`password` — иначе fail-fast при loading ApplicationContext (ловится
в `SecurityHeadersProdProfileIT`, `ActuatorSecurityProdProfileIT`).

## Datasource creds в prod (P0-3)

Default/prod датасорс читается **только из env**, без небезопасного
fallback (`application.yml`, top-док):

| Env | Назначение |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<prod-host>:5432/<prod-db>` |
| `SPRING_DATASOURCE_USERNAME` | прод-пользователь БД |
| `SPRING_DATASOURCE_PASSWORD` | прод-пароль БД |

Placeholder без default → если переменная не выставлена, Spring падает
на старте (fail-fast), а не молча коннектится к dev-БД. `local` profile
переопределяет эти три значения захардкоженными dev-кредами
(`localhost:5432/argumentmap`, `argmap`/`argmap`) — `./mvnw
spring-boot:run` (default profile=`local`) работает без env. `test`
profile получает датасорс через Testcontainers `@ServiceConnection`.

`DatasourceConfigValidator` (`config/DatasourceConfigValidator.java`) —
defense-in-depth: если prod-profile активен, но resolved URL указывает на
`localhost` / `127.0.0.1` / `/argumentmap` (или username = `argmap`),
падает с `IllegalStateException`. Ловит сценарий «деплой случайно поднялся
под default profile=local». Тот же fail-fast паттерн, что у
`AUTH_JWT_SECRET` (`JwtService`) и `ACTUATOR_USERNAME/PASSWORD`
(`ActuatorSecurityConfig`). Тесты — `DatasourceConfigValidatorTest`.

### Сводный список required prod env

`AUTH_JWT_SECRET` (≥256 бит, `openssl rand -hex 32`),
`ACTUATOR_USERNAME` / `ACTUATOR_PASSWORD`, `SPRING_DATASOURCE_URL` /
`SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`. Все три
группы fail-fast на старте в prod profile, если не заданы.
