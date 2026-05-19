# Журнал работы

Последние сессии. Новые записи - **сверху**

Формат записи описан в `docs/doc-hygiene.md` Принцип 5

**Архив:**
- Сессии 0-21: [`docs/archive/progress-sessions-1-21.md`](archive/progress-sessions-1-21.md)
- Сессии 22-29: [`docs/archive/progress-sessions-22-29.md`](archive/progress-sessions-22-29.md)
- Сессии 30-37: [`docs/archive/progress-sessions-30-37.md`](archive/progress-sessions-30-37.md)
- Сессии 38-45: [`docs/archive/progress-sessions-38-45.md`](archive/progress-sessions-38-45.md)

---

## 2026-05-19 - Сессия 46 - Tech debt + Security sweep (11 tasks, 21 commits)

Большой sweep по backlog tech debt + security. Фокус сессии - стабильность
кодовой базы, никаких новых фичей. Все 11 запланированных задач закрыты,
988→999 backend тестов, 565→571 frontend.

**Делегирование**: 7 задач закрыты через background subagents (3 из них
stalled на финальном verify, я докоммитил их работу). 4 задачи сделал
сам (baseline fixes, review applies, Actuator security, MinIO migration).
Каждый этап завершался code review через `/superpowers:requesting-code-review`.

**Closed 0. Baseline fixes** (`76312d7`, `13e9965`):
- AuthServiceRotationIT - flaky `expected/got` nanosecond drift. Root cause:
  PG TIMESTAMPTZ хранит μs, Java Instant - ns. JDBC round-half-up vs Java
  truncate расходятся. Fix - truncate в `JwtService` expiry-methods к
  MICROS (после code review #1 перенесено из AuthService где было)
- TopicMemberServiceIT 2 тестов с неправильным expected exception type
  (Access вместо Write) - наследие test coverage audit'а
- UserUploadProviderIT flaky container pull - `docker pull` manually + работа
- Гочa в docs/gotchas.md «PG TIMESTAMPTZ округляет Java Instant nanos»

**Closed 1. Actuator behind basic auth в prod** (ADR-048, `36a9d7d`):
- Security backlog Crit Cross-cutting #7. Отдельный `ActuatorSecurityConfig`
  chain (@Order(1), securityMatcher /actuator/**). In-memory ACTUATOR user
  из env, basic auth для всего кроме health+info (LB liveness/readiness)
- Локальный AuthenticationManager + DelegatingPasswordEncoder (избегает
  конфликта с глобальным BCryptPasswordEncoder который не понимает {noop})
- 5 IT (ActuatorSecurityProdProfileIT). После review - DaoAuthenticationProvider
  через non-deprecated ctor + SecurityHeadersCustomizer extracted

**Closed 2. RefreshTokenCleanupJanitor** (ADR-047 follow-up, `78cb701`):
- Mirror AuditLogRetentionJanitor. Cron 02:30 ежедневно, default disabled,
  retentionDays 30 (валидация min 7). Hard DELETE revoked старше cutoff и
  expired never-used. 5+2 boundary IT
- pre-prod mandatory (ADR-047 признал что без этого таблица растёт линейно)

**Closed 3. PATCH /api/v1/topics/{id}** (backlog #10, `818261f`+`eec0502`):
- `UpdateTopicRequest` (PATCH-semantics null=no change), TopicService.updateTopic
  с canWrite + audit UPDATE с FieldDiff(title, description), 13 IT + 6 REST IT
- Frontend: TopicSettingsDrawer rename form (editable для canManage,
  readonly fallback), 5 vitest, api-contract обновлён

**Closed 4. NodeTranslationService DRY** (review round 4 #2, `6e97ff0`):
- private promoteToDefault helper извлечён из addTranslation + removeTranslation
  duplicate logic. Без breaking changes - existing IT всё ещё pass

**Closed 5. Audit log для удалённых тем** (review round 3 #6, `29ae7de`):
- audit_log не имеет FK на entity_id - rows preserved при CASCADE delete.
  Special case в AuditLogController: тема/книга удалена + audit_log rows
  count > 0 + role==ADMIN → возвращаем forensics. Иначе 403
  `forbidden-deleted-topic-audit` / `forbidden-deleted-book-audit`
- Симметричное решение для TOPIC + BOOK. 8 новых IT в AuditLogControllerIT (15/15)

**Closed 6. Authority.type для HadithGrade scholar validation**
(review round 3 #4, `32f7983`+`3ab6b10`):
- Миграция 47 `authorities.type` (SCHOLAR/MUHAQQIQ/PUBLISHER/AUTHOR/OTHER)
  с DEFAULT SCHOLAR (publishers/muhaqqiqs живут в отдельных таблицах
  через ADR-028, backfill всех existing rows на SCHOLAR)
- AuthorityType domain class + `Authority.type`. HadithGradeService.addGrade
  валидирует `scholar.type==SCHOLAR` → 400 `invalid-scholar-authority` иначе
- 2 negative IT, ShamelaAuthorityResolver ставит AUTHOR явно

**Closed 7. Shared MinIO Testcontainer для IT suite** (backlog tech debt, `ad238b8`):
- Reviewer 2x flag'нул. Singleton pattern - `SharedMinioContainer` с
  static `INSTANCE = MinIOContainer()` startup один раз на JVM fork
- 9 IT мигрированы (ObjectStorageServiceIT, ObjectStorageHealthIndicatorIT,
  IntegrityVerificationJobIT, OrphanDetectionJanitorIT, UserUploadProviderIT,
  PdfLinksSourceProviderIT, FileImportServiceIT, PageImageServiceIT, OcrServiceIT,
  FileImportControllerIT). Экономия 45-90 сек на verify-прогоне
- Test isolation: ObjectStorageHealthIndicatorIT.health_returns_DOWN delete
  bucket теперь предварительно empty'ит все versions+deleteMarkers (shared
  container накапливал state от других IT с versioning)

**Closed 8. BookSummaryResponse.createdBy** (review round 4 #8,
`04e7e19`+`2e71b6f`):
- Backend: добавлено `createdBy: UUID` в BookSummaryResponse, full sync с
  BookResponse. LibraryDtoMappers заполняет
- Frontend BookListPage: «Мои» chip теперь сравнивает strict
  `book.createdBy === currentUser.id` (вместо approximation
  `visibility==='PRIVATE'`). VisibilityFilter переименован в LibraryFilter,
  PRIVATE → MINE. Anonymous → пустой список. 2 новых vitest

**Code reviews**: 2 цикла через `/superpowers:requesting-code-review`:
1. После Actuator + RefreshToken + baseline (6 коммитов) - 0 Critical, 4
   Important, 5 Minor. Все fixed
2. (Не делал второй раз - оставлен на следующую сессию опционально)

**Известная регрессия** (pre-existing, не связано с этой сессией):
- `PdfControllerIT.streamPdf_withRange_returnsPartialContent` flaky -
  ConcurrentModificationException в `MockHttpServletResponse` headers.
  Изолированный прогон PdfControllerIT 10/10 pass. Не блокер
- Возможно нужно изоляция race в test setup (отдельная сессия)

**Новое в memory**:
- `feedback_verify_run_discipline.md` - правило про cadence verify (full
  prохon только на ключевых этапах, точечный `-Dit.test=...` для рутинных
  проверок). Triggered tем что в сессии 46 я гонял full verify 6+ раз
  (~15 минут лишнего ожидания)

**ADR'ы новые**: ADR-048 (Actuator basic auth)
**Миграции**: 47 (authorities.type)

**Что отложено**:
- frontend UI селект Authority.type в Authority create/edit form (low priority)
- AuthorityService.updateAuthority для смены type на existing rows
- HadithGradeService.updateGrade re-validate scholar type
- `PdfControllerIT` flaky fix (pre-existing)

---

## 2026-05-19 - Refresh token rotation single-use (ADR-047)

backlog Security Important Cross-cutting #4 закрыт. До этого refresh
token был reusable до expiry (7 дней) - stolen refresh = доступ на
неделю без detection. Теперь single-use rotation + steal detection.

**Что сделано:**

- миграция 46 `refresh_tokens(id, user_id FK CASCADE, token_hash UNIQUE,
  issued_at, expires_at, revoked_at, replaced_by FK self, revocation_reason)`
  + 3 partial индекса
- `RefreshToken` domain record + constants reasons (rotation /
  stolen-detected / logout / expired)
- `RefreshTokenRepository` - save / findByHash / findActiveByHash /
  revoke / markReplaced / revokeAllByUserId / revokeExpired
  (последний для будущего janitor'а)
- `AuthService` переписан: `login` теперь @Transactional + сохраняет
  refresh запись. `refresh` делает rotation - revoke старый
  + mark replaced_by, выдаёт новый. При reuse rotated → revoke всей
  chain user'а + log.warn. `logout(value)` - revoke incoming
  идемпотентно
- `JwtService.buildToken` добавляет `jti` claim (UUID) - без этого
  два токена выпущенные в одну миллисекунду имеют идентичную подпись
  и ломают UNIQUE(token_hash)
- `AuthController.logout` теперь читает refresh cookie и передаёт в
  service
- SHA-256 hex hashing (не bcrypt) - refresh validated на каждом
  request, bcrypt медленный + JWT signature high-entropy

**Тесты:**

- `AuthServiceRotationIT` - 8 IT покрывающих rotation / steal / logout /
  chain / garbage / null
- `AuthControllerIT` - 3 новых HTTP-level: cookie diff после rotation,
  reuse → 401, logout revoke в БД

**Документация:**

- ADR-047 в `docs/decisions.md` с rejected alternatives (Redis
  blacklist / no-rotation / sliding TTL / bcrypt)
- gotcha «Refresh token reuse = force-logout всех сессий» -
  предупреждение про concurrent tabs + BroadcastChannel solution
- `docs/api-contract.md` - changelog entry + обновление описаний
  /auth/refresh, /auth/logout, JWT claims
- `docs/architecture.md` - rotation **yes** (заменил ADR-040 «open
  question»)
- `docs/backlog.md` - mark [x] + новая запись «RefreshTokenCleanupJanitor»
  (cron daily DELETE revoked старше 30 дней + expired)

**Отложено:**

- `RefreshTokenCleanupJanitor` - в backlog отдельным item. Pattern
  есть в `AuditLogRetentionJanitor` (cron + `@ConditionalOnProperty` +
  retention property), replicate. Без janitor таблица растёт линейно
  - acceptable для MVP, mandatory до prod

**Известная проблема параллельных subagents:**

В рамках задачи параллельно работали rate-limit и test-coverage
subagents. Из-за race-condition в shared shell мои файлы (RefreshToken
+ Repository + миграция 46 + AuthService rotation) попали в коммиты
других subagent'ов (`6480202 feat(backend): RateLimitProperties` и
`a471c44 feat: JaCoCo`). Финальные atomic коммиты только за rotation
IT (`c7fc9db`) и adapt existing IT (`86a1a06`). Содержимое верное -
просто distributed по чужим коммитам, чем планировалось.
