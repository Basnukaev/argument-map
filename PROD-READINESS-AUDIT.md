# PROD-READINESS AUDIT — argument-map

**Дата:** 2026-06-18
**Аудитор:** senior-инженер (read-only по коду; отчёт записан в файл)
**Скоуп:** первый прод-релиз академической исламской платформы
**Метод:** прямое чтение бэк/фронт-кода + живые запросы к dev-БД
(`argumentmap-postgres`, креды argmap/argmap) + кросс-ссылки на `docs/`.
БД на момент аудита: **31999 хадисов**, **7789 рави**, корпус перекраулен
из alminasa.ai (ADR-060).

> **Статус-легенда:** `[ЕСТЬ И РАБОТАЕТ]` / `[ОТСУТСТВУЕТ]` / `[ЕСТЬ, НО РИСКОВАННО]`

---

## Оглавление

1. [TL;DR — главное за 60 секунд](#1-tldr)
2. [Возможность ручного фикса (по каждой hd_*-сущности)](#2-ручной-фикс)
3. [Поведение реимпорта при перезаписи (КРИТИЧНО)](#3-реимпорт-перезапись)
4. [Детекция сломанных/недозаполненных данных](#4-детекция-битых)
5. [Инвентарь admin-тулинга](#5-admin-тулинг)
6. [Общая прод-готовность (бэк + фронт + ops)](#6-прод-готовность)
7. [Приоритизированный чеклист P0/P1/P2](#7-чеклист)

---

<a name="1-tldr"></a>
## 1. TL;DR — главное за 60 секунд

**Главная боль владельца подтверждена кодом и она серьёзная:**

1. **Ручного фикса данных НЕТ почти нигде.** Единственный manual-edit
   эндпоинт во всём hadith-домене — только что добавленный
   `PATCH /api/v1/hadith/matns/{id}/translation` (правка перевода матна,
   ADMIN). Для **authenticity, рави (tabaqa/reliability/grade), цепей
   (sanads), рулингов, арабского текста матна** — **ни UI, ни REST, только
   сырой SQL**. Курируемые scholar-оценки (`hadith_grades`) — отдельная
   история (есть, но про другое и требуют привязки к citation-source).

2. **Реимпорт МОЛЧА ЗАТИРАЕТ всё.** `AlminasaHadithMapper.mapHadith`
   делает **upsert хадиса по `external_id` + delete-recreate ВСЕХ
   сателлитов** (matns/sanads/rulings/explanations/crossrefs). Повторный
   `POST /admin/alminasa/import/hadiths`:
   - **уничтожает строку `hd_matns` целиком** (`deleteByHadithId`) и
     создаёт новую с `text_ru=NULL, text_en=NULL` и **новым `id`** →
     **только что отредактированный перевод исчезает** (и даже
     `matnId`, по которому правили, перестаёт существовать);
   - **перезаписывает `authenticity`, `status`, `full_text_ar`,
     `chapter_ar`, `metadata`** — все из staging;
   - `AlminasaNarratorMapper.mapNarrator` перезаписывает **все 20
     содержательных колонок рави** (name, reliability_grade, tabaqa,
     grade_text, …) из staging.
   - **Защиты от затирания (`manually_edited`/lock/overrides) НЕТ
     нигде** — ни колонки, ни таблицы, ни флага в metadata (metadata
     тоже пересобирается из staging с нуля).

   **Итог: владелец починит запись на проде → запустит реимпорт/рекраул
   нового сборника → молча потеряет правку.** Это критический разрыв №1.

3. **Открытый вопрос про переэкспозицию — РЕАЛЕН (low severity).**
   Guest-view (ADR-064) сделал `GET /api/v1/topics/**` и
   `/api/v1/library/books/**` публичными для анонима. Список членов
   PUBLIC-темы/книги (`GET .../members`) гейтится только
   `assertCanRead`, который для **PUBLIC возвращает true даже анониму**.
   `lib_books.visibility` **по умолчанию `'PUBLIC'`** → **аноним может
   получить список user-UUID членов любой публичной книги/темы**. Утечка
   ограничена UUID (не email), но это enumeration-вектор.

**Что в хорошем состоянии:** Liquibase (77 чейнджсетов, 0 орфанов, чисто
с нуля), JWT prod fail-fast, RFC-7807 error-handling, swappable LLM с
graceful-degradation, frontend ErrorBoundary + ProtectedRoute(ADMIN),
auth/refresh (httpOnly cookie, XSS-safe), резюмируемый краул (checkpoint).

**Чего нет совсем:** бэкап/restore БД, CI, Dockerfile приложения,
прод-deploy документация, data-health дашборд, generic exception-handler.

---

<a name="2-ручной-фикс"></a>
## 2. Возможность ручного фикса (по каждой hd_*-сущности)

### Карта «что есть vs чего нет»

| Сущность | Поля, часто требующие фикса | Способ правки СЕГОДНЯ | Статус |
|---|---|---|---|
| **hd_matns** | `text_ru`/`text_en` (перевод) | `PATCH /api/v1/hadith/matns/{id}/translation` (ADMIN) | `[ЕСТЬ]` но затирается реимпортом (см. §3) |
| **hd_matns** | `text_ar` (битый арабский), `printed_number`, `page_no`, `volume`, `divergence_summary` | **только SQL** | `[ОТСУТСТВУЕТ]` |
| **hd_hadiths** | `authenticity` (2228 NULL = 7%), `status`, `primary_number` (2388 NULL), `full_text_ar`, `chapter_ar`, `hadith_type` | **только SQL** | `[ОТСУТСТВУЕТ]` |
| **hd_narrators** | `reliability_grade`, `tabaqa` (2404 NULL = 31%), `grade_text`, `name_ar`, `year_death_hijri`, `authority_id` | **только SQL** (нет даже linking-эндпоинта narrator↔authority) | `[ОТСУТСТВУЕТ]` |
| **hd_sanads / hd_sanad_narrators** | `chain_grade`, состав цепи, `transmission_phrase` | **только SQL** | `[ОТСУТСТВУЕТ]` |
| **hd_rulings** | `ruler_name`, `ruling_text`, `ruler_death_year` | **только SQL** | `[ОТСУТСТВУЕТ]` |
| **hd_explanations** (SHARH/ILAL/GHARIB) | `text`, `book_name`, `author` | **только SQL** | `[ОТСУТСТВУЕТ]` |
| **hd_narrator_commentaries** | джарх/таʿдиль-цитаты | **только SQL** | `[ОТСУТСТВУЕТ]` |
| **hd_narrator_relations** | `related_narrator_id` (резолв FK), `role` | **только SQL** | `[ОТСУТСТВУЕТ]` |
| **hd_collections** | `name_ru`/`name_en`, `book_id` (мост) | **только SQL** | `[ОТСУТСТВУЕТ]` |
| **hadith_grades** (курируемая scholar-оценка) | `grade`, `comment`, `grade_citation` | `POST /api/v1/hadith/hadiths/{id}/grades` (но требует `source_id` — хадис должен быть прикреплён к citation-узлу; 0 строк сейчас) | `[ЕСТЬ]` и **survives реимпорт** (отдельная таблица) |

### Ключевые наблюдения

- **`PATCH .../matns/{id}/translation` — единственный manual-edit эндпоинт
  во всём hadith-домене.** Все остальные hadith-контроллеры
  (`HadithController`, `NarratorController`, `HadithCollectionController`)
  — **GET-only**, единственное исключение `POST /hadiths/{id}/grades`.
  - Файлы: `MatnTranslationController.java:71` (PATCH),
    `HadithTranslationService.editTranslation` (service:162-185),
    `MatnRepository.updateTranslation` (repo:85-92).

- **Самые «болевые» NULL-поля БЕЗ пути правки** (живые цифры из БД):
  - `hd_hadiths.authenticity` — **2228 NULL** (выводится эвристикой по
    рулингам, см. `AlminasaHadithMapper.deriveAuthenticity:577`; у
    хадиса без рулингов остаётся NULL);
  - `hd_narrators.tabaqa` — **2404 NULL** (31% рави);
  - **996 хадисов с 0 цепей** (такхридж — `raw.hadith`-поле пустое →
    `insertSanad` ранний return, `AlminasaHadithMapper:334`);
  - переводы: на момент аудита **только 1 матн из 31999** имеет `text_ru`.

- **`hadith_grades` — единственный пример правильного паттерна** «курация
  поверх импорта»: отдельная таблица, `created_by` FK на `users`,
  ключ `(source_id, scholar_id)`, маппер alminasa её **не трогает** →
  она пережила бы реимпорт. Но она про scholar-атрибутированные оценки
  (ось `hadith_grades`), **не** про массовый фикс authenticity/рави/перевода,
  и завязана на citation-граф (нужен `source_id`).

### Код импорта/маппинга (для ориентира)

- Оркестрация: `hadith/alminasa/service/AlminasaImportService.java`
  (двухпроходный: рави → хадисы → resolve FK; per-док транзакции).
- Маппер хадисов: `hadith/alminasa/service/AlminasaHadithMapper.java`
  (`mapHadith:153`).
- Маппер рави: `hadith/alminasa/service/AlminasaNarratorMapper.java`
  (`mapNarrator:91`, `ensureNarrator:165`).
- Контроллер импорта: `hadith/alminasa/web/AlminasaAdminController.java`
  (`/import/narrators:151`, `/import/hadiths:163`, `/dry-run/{id}:177`),
  все ADMIN-only (`requireAdmin:268`).

---

<a name="3-реимпорт-перезапись"></a>
## 3. Поведение реимпорта при перезаписи (КРИТИЧНО)

### 3.1 Что делает повторный `import/hadiths`

`AlminasaHadithMapper.mapHadith(row)` (`AlminasaHadithMapper.java:153-221`):

```
1. find hd_hadiths по (external_source='alminasa', external_id)  → reuse id
2. hadithRepository.update(hadith)   // ПЕРЕЗАПИСЬ всех колонок (см. ниже)
3. matnRepository.deleteByHadithId(id)        // ← строка matn УДАЛЯЕТСЯ
   editionRepository.deleteByHadithId(id)
   sanadRepository.deleteByHadithId(id)
   crossrefRepository.deleteByHadithId(id)
   rulingRepository.deleteByHadithId(id)
   explanationRepository.deleteByHadithId(id)
4. insertMatn(...)   // ← новая строка: UUID.randomUUID(), text_ru=NULL, text_en=NULL
   insertEditions/insertSanad/insertCrossrefs/insertRulings/insertExplanations
```

**`HadithRepository.update` (`HadithRepository.java:79-95`)** перезаписывает:
`collection_id, primary_number, normalized_matn, status, source_id,
metadata, external_source, external_id, hadith_type, chapter_ar,
sub_chapter_ar, full_text_ar, authenticity`. Сохраняются только `id` и
`created_at` (+ `source_id` подтягивается из existing, mapper:187).

**`insertMatn` (`AlminasaHadithMapper.java:305-312`)** пишет
`new Matn(UUID.randomUUID(), …, null /*text_ru*/, null /*text_en*/, …)`.

→ **Любая ручная правка перевода через PATCH-эндпоинт уничтожается:**
строка matn удаляется по `hadith_id`, пересоздаётся с новым `id` и
пустыми переводами. Даже `matnId`, по которому правили, больше не
существует. `[ЕСТЬ, НО РИСКОВАННО]` — это самый острый разрыв.

### 3.2 Что делает повторный `import/narrators`

`AlminasaNarratorMapper.mapNarrator` → `NarratorRepository.update`
(`NarratorRepository.java:171-191`) перезаписывает **все 20 колонок**:
`authority_id, name_ar, name_ar_normalized, kunya, laqab,
year_birth_hijri, year_death_hijri, birthplace, death_place,
primary_residence, reliability_grade, reliability_comment,
transmitted_count_cached, metadata, external_source, external_id,
tabaqa, grade_text, born_on_text, died_on_text`. Сохраняются только
`id`, `authority_id` (из existing, mapper:116), `transmitted_count_cached`,
`created_at`.

→ **Вручную исправленный `reliability_grade`/`tabaqa`/`name_ar` рави
затирается** значением из staging при следующем `import/narrators`.
Relations и commentaries — delete-recreate (mapper:248-289).

> **Нюанс:** `authority_id` (привязка рави к authority-сущности)
> сохраняется при re-map — это единственное «ручное» поле, переживающее
> реимпорт. Но **эндпоинта, чтобы его выставить, нет** (AuthorityController
> CRUD-ит сами authorities, не линк narrator↔authority).

### 3.3 Механизм «do-not-overwrite» — ОТСУТСТВУЕТ ПОЛНОСТЬЮ

`[ОТСУТСТВУЕТ]`. Проверено:
- В схеме БД нет ни одной колонки `manual*/locked/override/curated/
  reviewed/edited/protected` на доменных таблицах (только служебные
  `databasechangeloglock`).
- В коде нет `manuallyEdited`/`doNotOverwrite`/`isLocked`/`preserveManual`.
- `metadata` jsonb **не спасает**: `buildHadithMetadata`/`buildMetadata`
  собирают свежий ObjectNode из staging — стороннее поле в metadata
  тоже затрётся.

**Что нужно построить (рекомендация, не реализация):** см. P0-1 в §7.
Кратко — три альтернативы:
- **(A) Overlay-таблица** `hd_field_overrides` (entity_table, entity_id,
  field_name, value, edited_by, edited_at), применяемая ПОСЛЕ маппинга в
  конце транзакции `mapHadith`/`mapNarrator`. Плюс: не трогает import-логику,
  легко аудитится, расширяется на любое поле. Минус: новый слой apply.
- **(B) Колонки-локи** `*_locked boolean` на каждой таблице + условные
  UPDATE (`SET col = CASE WHEN col_locked THEN col ELSE ? END`). Плюс:
  просто. Минус: N колонок × M таблиц, заметно раздувает мапперы.
- **(C) Merge-стратегия** — для перевода частный случай: **НЕ
  delete-recreate matn, а upsert по природному ключу** (hadith_id +
  printed_number/page), сохраняя `text_ru/text_en` существующей строки.
  Это минимальный фикс для самого острого случая (перевод), его стоит
  сделать в любом случае (P0-1a).

Решение между (A)/(B)/(C) — **архитектурное, требует ADR/спеки**.

### 3.4 Delete/orphan на реимпорте

- Хадисы/рави **никогда не удаляются** реимпортом (только upsert) → новый
  краул не осиротит существующие записи, но и не подчистит исчезнувшие из
  источника (приемлемо для append-only корпуса).
- Сателлиты — delete-recreate в **одной транзакции** на хадис → промежуточного
  осиротевшего состояния нет; FK `ON DELETE CASCADE` на matns/sanads/
  rulings/explanations корректны.
- `narrations_numbers`/crossrefs резолвятся глобальным SQL-проходом после
  цикла (`resolveCrossrefs`, обновляет только NULL FK — идемпотентно).
- **Backfill (علل/غريب + narrator-commentary):** «после pause рестарт с
  нуля» (`AlminasaAdminController.java:199,236`) — не резюмируется с
  середины, но идемпотентен (delete-recreate), так что дубли не плодит.
  Краул хадисов — **резюмируемый** (composite `search_after` checkpoint в
  `am_crawl_checkpoint`, `AmCrawlCheckpoint.java`).

---

<a name="4-детекция-битых"></a>
## 4. Детекция сломанных/недозаполненных данных

### Nullable-поля, часто пустые (живые цифры, БД на 2026-06-18)

| Таблица.поле | Nullable | Пусто сейчас | Причина |
|---|---|---|---|
| `hd_hadiths.authenticity` | да | **2228 / 31999** | нет рулингов или эвристика не сматчила |
| `hd_hadiths.primary_number` | да | **2388 / 31999** | `number[]` пуст ИЛИ номер занят другим external_id (collision → NULL, mapper:269) |
| `hd_hadiths.full_text_ar` | да | 0 | (заполнено) |
| `hd_hadiths` без `hd_sanads` | — | **996** | такхридж: `raw.hadith` пуст → цепь не парсится |
| `hd_narrators.reliability_grade` | да | 0 | (UNKNOWN-fallback вместо NULL) |
| `hd_narrators.tabaqa` | да | **2404 / 7789** | `level` отсутствует/honorific у сподвижников |
| `hd_matns.text_ru` | да | **31998 / 31999** | перевод on-demand, почти не запускался |

### Data-health вью / запрос для поиска битых записей — ОТСУТСТВУЕТ

`[ОТСУТСТВУЕТ]`. Нет ни admin-вью, ни SQL-вью, ни эндпоинта, который бы
перечислял «битые» хадисы (0 рави / NULL authenticity / проваленный
перевод / подозрительно короткий арабский). Админ не может СИСТЕМАТИЧЕСКИ
найти, что чинить — даже если бы умел чинить.

**Рекомендация (P1):** SQL-вью `hd_data_health` или admin-эндпоинт
`GET /admin/hadith/health`, отдающий counts + страницы по категориям:
`authenticity IS NULL`, `0 sanads`, `narrator.tabaqa IS NULL`,
`narrator.metadata->>'stub'='true'` (стабы из тегов),
`text_ar` короче N символов, нерезолвленные crossrefs/relations
(`related_*_id IS NULL`). Дёшево (всё на существующих индексах:
`idx_hd_hadiths_authenticity` и пр.).

---

<a name="5-admin-тулинг"></a>
## 5. Инвентарь admin-тулинга

### Admin-страницы (frontend, `frontend/src/apps/admin/pages/`)

| Страница | Что умеет | Эндпоинты | RBAC |
|---|---|---|---|
| **AdminHadithImportPage** | краул/пауза alminasa, импорт рави+хадисов, dry-run, backfill علل/غريب + narrator-commentary, поллинг прогресса | `/api/v1/admin/alminasa/*` | `requireRole="ADMIN"` (route) + `requireAdmin` (backend) |
| **AdminShamelaPage** | shamela ETL (книги) | `/api/v1/admin/shamela/*` | ADMIN |
| **AdminArchiveOrgPage** | archive.org импорт | `/api/v1/admin/archive-org/*` | ADMIN |
| **AdminPageEditorPage** | правка lib_pages (текст/AI-edit страниц книг) | library pages API | ADMIN |
| **AdminAuditPage** | просмотр audit-log мутаций | `/api/v1/admin/audit/*` | ADMIN |
| **AdminUsersPage** | управление пользователями/ролями | `/api/v1/admin/users/*` | ADMIN |
| **AdminDashboardPage** | дашборд-хаб | — | ADMIN |

- **RBAC-гейтинг (frontend):** все `/admin/*` маршруты обёрнуты
  `<ProtectedRoute requireRole="ADMIN">` (`App.tsx:183-233`), иерархия
  USER < STUDENT < SCHOLAR < ADMIN (`ProtectedRoute.tsx:30-57`).
- **RBAC-гейтинг (backend):** `/api/v1/admin/**` падает в
  `anyRequest().authenticated()` (SecurityConfig — guest-view matcher
  admin НЕ покрывает), плюс каждый admin-контроллер вызывает
  `requireAdmin` в service/контроллере. Defense-in-depth — хорошо.

### Разрывы для прод-дата-опс `[ОТСУТСТВУЕТ]`

- **Нет** страницы/эндпоинта правки одной hd_*-записи (кроме перевода матна).
- **Нет** bulk-фикса (например «проставить authenticity батчу по фильтру»).
- **Нет** ре-перевода одной записи из админки массово / mark-as-reviewed.
- **Нет** data-health дашборда (§4).
- Есть только **AdminPageEditorPage** — но это про **library lib_pages**
  (текст книг), НЕ про hadith-домен.

---

<a name="6-прод-готовность"></a>
## 6. Общая прод-готовность (бэк + фронт + ops)

### 6.1 Backend — секреты и валидация на старте

| Секрет / конфиг | Env | Валидация на старте | Статус |
|---|---|---|---|
| `auth.jwt.secret` | `AUTH_JWT_SECRET` | **fail-fast**: <32 байт → IllegalStateException; prod + dev-placeholder → IllegalStateException (`JwtService.java:66-90`) | `[ЕСТЬ И РАБОТАЕТ]` |
| Actuator basic-auth | `ACTUATOR_USERNAME/PASSWORD` | prod: пустые креды → IllegalStateException (`ActuatorSecurityConfig.java:80`) | `[ЕСТЬ И РАБОТАЕТ]` |
| **DB datasource** | — | **НЕТ `${ENV}` override**: `url/username/password` захардкожены `localhost / argmap / argmap` (`application.yml:371-374`) | `[ЕСТЬ, НО РИСКОВАННО]` |
| `ANTHROPIC/OPENAI/DEEPSEEK_API_KEY` | `*_API_KEY` | sentinel `"disabled"` → `isEnabled()=false`, **graceful**: AI-эндпоинты отдают 503, app стартует (`application.yml:177,193,202`) | `[ЕСТЬ И РАБОТАЕТ]` (by design) |
| `AI_HTTP_PROXY` | env | опционально, только на LLM-клиента | OK |
| `SHAMELA_PROXY` / `alminasa.httpProxy` | env | опционально (`AlminasaProperties.java`) | OK |
| MinIO `STORAGE_SECRET_KEY` | env | дефолт `minioadmin` | `[ЕСТЬ, НО РИСКОВАННО]` если object-storage в проде |

- **DB creds — главный конфиг-разрыв.** В `application.yml` нет
  `${SPRING_DATASOURCE_*}`-плейсхолдеров. Spring Boot relaxed-binding
  подхватит `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` из env, но
  это **не задокументировано и не очевидно** → риск задеплоить с
  `localhost/argmap/argmap`. Рекомендация: явные плейсхолдеры (P0-3).
- **Нет `application-prod.yml`** — прод-профиль конфигурируется только
  через env. Это нормально, но повышает риск пропущенной переменной (нет
  единого списка required).

### 6.2 Backend — Security (детально, главный вопрос владельца)

**SecurityConfig** (`auth/web/security/SecurityConfig.java`):

- **permitAll:** `/api/v1/auth/{login,register,refresh,logout}`,
  `/v3/api-docs/**`, `/swagger-ui/**`, OPTIONS `/**` (`:88-103`).
- **Guest-view (ADR-064, `:116-122`):** анониму открыт `GET` на
  `/api/v1/topics/**`, `/api/v1/hadith/**`, `/api/v1/library/books/**`,
  `/api/v1/library/pages/**`, `/api/v1/questions/**` **во всех профилях,
  включая prod**.
- `/api/v1/auth/me` — всегда `authenticated()` (`:86`).
- Мутации (POST/PATCH/DELETE) и `/admin/**` → `anyRequest().authenticated()`.

**`[ЕСТЬ, НО РИСКОВАННО]` — подтверждённая переэкспозиция member-list:**

- `GET /api/v1/topics/{id}/members` и
  `GET /api/v1/library/books/{id}/members` гейтятся в service через
  `assertCanRead`/`assertCanReadBook` (`TopicMemberService.java:99`,
  `BookMemberService.java:92`).
- Но `VisibilityPolicy.canRead` для **PUBLIC возвращает `true`
  независимо от actorId** (даже `null`/аноним) —
  `VisibilityPolicy.java:51-53`.
- **`lib_books.visibility` DEFAULT `'PUBLIC'`** (проверено в БД);
  `topics.visibility` DEFAULT `'PRIVATE'`.
- → **Аноним может вызвать `.../members` для любой PUBLIC-книги (а это
  дефолт) и получить список `user_id` членов.** Для тем — только если
  тема явно PUBLIC. Утечка = user-UUID (не email/имя), но это
  enumeration/PII-вектор. Это ровно «известный открытый вопрос»
  владельца. Рекомендация P1-4: member-list требует ≥ member-роли
  (не просто read), либо вынести из guest-view.

- **Export:** `GET /api/v1/topics/{id}/export` — gated
  `permissionService.assertCanRead` (`TopicExportImportController.java:86`)
  → для PUBLIC-темы аноним получит полный граф (это by design guest-view;
  PRIVATE/SHARED → 403). Приемлемо, но осознать что PUBLIC-тема
  экспортируется анонимно целиком.

**Actuator (ADR-048):** отдельный chain `ActuatorSecurityConfig`
(@Order(1)); в prod — basic-auth на всё кроме health/info, креды
fail-fast если пустые. `[ЕСТЬ И РАБОТАЕТ]`.

**CORS:** делегирован `WebMvcConfig.addCorsMappings` (SecurityConfig:79
`.cors(cors -> {})`). **Требует отдельной проверки** какие origin
разрешены в проде (не покрыто прямым чтением в этом аудите) — P1-5:
убедиться что не wildcard + allowCredentials.

**Rate limit (ADR-046):** `RateLimitFilter` перед JWT, только на
`/auth/login` + `/auth/register` (in-memory sliding window). AI-перевод и
crawl **не** rate-limited (AI гейтится ADMIN/force; перевод первого раза
доступен любому залогиненному — потенциальная стоимость, P2).

**CSRF:** disabled (JWT в header; refresh в HttpOnly cookie) — корректно
для stateless SPA.

### 6.3 Backend — error-handling и логирование

- **GlobalExceptionHandler** (`exception/GlobalExceptionHandler.java`):
  `@RestControllerAdvice`, RFC-7807 ProblemDetail, ~30 типизированных
  хендлеров. LLM-ошибки → 502/503 с logged WARN (не утечка трейса).
  `[ЕСТЬ И РАБОТАЕТ]`.
- **`[ЕСТЬ, НО РИСКОВАННО]`: нет generic `@ExceptionHandler(Exception.class)`.**
  Непокрытое исключение падает в Spring `/error` (500). По умолчанию
  `server.error.include-stacktrace=never` (в `application.yml` не
  переопределено) → трейс клиенту не утечёт, **но** ответ будет
  generic-500 без RFC-7807-обёртки. Рекомендация P2: добавить catch-all →
  500 ProblemDetail с logged trace + correlation-id.
- Логирование: секреты/токены в логах при беглом просмотре не светятся
  (переводы логируют только длину текста, `HadithTranslationService:137`).

### 6.4 Liquibase

`[ЕСТЬ И РАБОТАЕТ]`. **77 чейнджсет-файлов = 77 `<include>` в
db.changelog-master.xml, орфанов 0.** Смок чистой установки уже проходил
в Сессии 60 (75 миграций с нуля), сейчас +2 (76 narrator-commentary, 77
authenticity). Последние: `20260617-77-hd-hadiths-authenticity.xml`.
Контекст-гейтинг dev-сидов в прод не замечен (DevHadithSeeder — отдельный
бин, не Liquibase).

### 6.5 Frontend

| Аспект | Статус | Детали |
|---|---|---|
| **ErrorBoundary** | `[ЕСТЬ И РАБОТАЕТ]` | `shared/components/ErrorBoundary.tsx`, обёрнут в `App.tsx` (top-level, не white-screen) |
| **VITE_API_URL** | `[ЕСТЬ И РАБОТАЕТ]` | `shared/api/client.ts:21` — prod дефолт пустой (same-origin фронт+API); `.env.example` есть |
| **Auth/refresh** | `[ЕСТЬ И РАБОТАЕТ]` | access-token в памяти, user в localStorage, **refresh в httpOnly cookie (XSS-safe, ADR-040)**, single-flight refresh-on-401 (`authStore.ts:66-162`) |
| **Admin RBAC** | `[ЕСТЬ И РАБОТАЕТ]` | `ProtectedRoute requireRole="ADMIN"` на всех `/admin/*` (`App.tsx:183-233`) |
| **Build/typecheck** | `[ЕСТЬ И РАБОТАЕТ]` | `npx tsc --noEmit -p tsconfig.app.json` → **exit 0, 0 ошибок** (проверено в аудите). Полный `npm run build` не гонялся, но typecheck чист. |

### 6.6 Ops — бэкап / мониторинг / деплой

- **Бэкап/restore БД — `[ОТСУТСТВУЕТ]` ПОЛНОСТЬЮ.** Ни pg_dump-скрипта,
  ни cron, ни `*.dump`, ни документации нигде в репо. **Это критично
  именно из-за §3:** если реимпорт затрёт ручные правки, без бэкапа их
  не восстановить. P0-2.
- **CI — `[ОТСУТСТВУЕТ]`.** `.github/workflows/` содержит только
  `README.md` («Пока не настроены»). Нет автопрогона тестов/билда.
- **Dockerfile приложения — `[ОТСУТСТВУЕТ]`.** `docker-compose.yml` —
  только postgres/minio (инфра), не сам Spring Boot.
- **Прод-deploy документация — `[ОТСУТСТВУЕТ]` в этом репо** (по правилу
  проекта деплой-инфо живёт в repo `remblo`; здесь ссылок на remblo в
  markdown нет вообще). Перед релизом убедиться, что в remblo описаны:
  env-набор, миграции, бэкап, рестарт.
- **Health/мониторинг:** `/actuator/health` (+ info) — есть. Глубже
  (метрики, алерты) — не настроено в этом репо.
- **Рекраул:** резюмируемый и идемпотентный (checkpoint, upsert) —
  `[ЕСТЬ И РАБОТАЕТ]`, но без защиты ручных правок (§3).

---

<a name="7-чеклист"></a>
## 7. Приоритизированный чеклист

### P0 — БЛОКЕРЫ прода (без них релизить опасно)

- **P0-1. Защита ручных правок от реимпорта (главный разрыв №1).**
  *Почему:* `mapHadith`/`mapNarrator` молча затирают любой ручной фикс
  при следующем `import/*` или рекрауле нового сборника; защиты нет
  нигде. *Где:* `AlminasaHadithMapper.java:153-221` (delete-recreate +
  update), `AlminasaNarratorMapper.java:91-148`, `HadithRepository.update:79`,
  `NarratorRepository.update:171`. *Что:* выбрать стратегию (overlay-таблица
  / lock-колонки / merge) — **через ADR/спеку**. Минимум-минимум:
  - **P0-1a (быстрый, отдельно):** перестать терять перевод —
    `insertMatn` НЕ должен delete-recreate'ить matn с переводом; upsert по
    природному ключу с сохранением `text_ru/text_en`. `MatnRepository`,
    `AlminasaHadithMapper.java:206,213`.

- **P0-2. Бэкап/restore БД.** *Почему:* без бэкапа любая потеря данных
  (в т.ч. от P0-1) необратима; первый прод-релиз без бэкапа — табу.
  *Где:* нет нигде (`scripts/`, docker-compose, docs). *Что:* pg_dump по
  расписанию + проверенный restore-ранбук (в remblo).

- **P0-3. Явные env-плейсхолдеры для DB-кредов.** *Почему:* сейчас
  захардкожены `localhost/argmap/argmap`; риск задеплоить prod на dev-БД.
  *Где:* `backend/src/main/resources/application.yml:371-374`. *Что:*
  `${SPRING_DATASOURCE_URL}` / `${SPRING_DATASOURCE_USERNAME}` /
  `${SPRING_DATASOURCE_PASSWORD}` + документировать в required-env.

### P1 — ВАЖНОЕ (сделать до открытого релиза)

- **P1-1. Manual-edit эндпоинты для ключевых hd_*-полей.** *Почему:*
  кроме перевода матна ничего нельзя починить кроме как сырым SQL;
  владельцу нужно править authenticity, рави (reliability/tabaqa),
  возможно текст. *Где:* hadith-домен GET-only (`HadithController`,
  `NarratorController`). *Что:* `PATCH /hadiths/{id}` (authenticity,
  status), `PATCH /narrators/{id}` (reliability_grade, tabaqa, grade_text),
  ADMIN-only. **Зависит от P0-1** (иначе правки затрутся).

- **P1-2. Data-health вью/эндпоинт.** *Почему:* админ не может найти, что
  чинить (2228 NULL authenticity, 996 без цепей, 2404 NULL tabaqa).
  *Где:* нет. *Что:* `GET /admin/hadith/health` (counts + страницы по
  категориям битости) либо SQL-вью `hd_data_health`.

- **P1-3. Admin-UI для P1-1/P1-2** на фронте (страница «Качество данных»
  + инлайн-правка записи). *Где:* `frontend/src/apps/admin/` — нет
  hadith-record-editor (есть только lib-pages editor).

- **P1-4. Закрыть member-list для анонима.** *Почему:* аноним читает
  список user-UUID членов PUBLIC-книги (дефолт) /темы. *Где:*
  `TopicMemberService.java:99`, `BookMemberService.java:92`,
  `VisibilityPolicy.java:51`, SecurityConfig guest-view `:116`. *Что:*
  member-list требует ≥ member-роли (новый `assertIsMemberOrAdmin`), либо
  убрать `.../members` из-под guest permitAll.

- **P1-5. Проверить CORS в проде.** *Почему:* делегирован
  `WebMvcConfig.addCorsMappings`, не проверен прямым чтением; wildcard +
  allowCredentials = дыра. *Где:* `WebMvcConfig`. *Что:* подтвердить
  whitelist origin прод-домена, allowCredentials только с явными origin.

- **P1-6. CI (хотя бы backend+frontend build/test на PR).** *Где:*
  `.github/workflows/README.md` (заглушка). *Почему:* 77 миграций + два
  модуля без автопроверки — регрессии проскользнут.

### P2 — NICE-TO-HAVE (можно после первого релиза)

- **P2-1. Generic `@ExceptionHandler(Exception.class)`** → 500
  RFC-7807 + correlation-id + logged trace. *Где:*
  `GlobalExceptionHandler.java`. (Сейчас непокрытое → generic Spring 500;
  трейс не утекает т.к. дефолт `include-stacktrace=never`, но ответ
  не-единообразен.)

- **P2-2. Явно зафиксировать `server.error.include-stacktrace=never`** в
  application.yml (сейчас полагается на дефолт Spring Boot).

- **P2-3. Rate-limit / стоимостный гард на AI-перевод** (первый перевод
  доступен любому залогиненному; force уже ADMIN). *Где:*
  `MatnTranslationController`, `HadithTranslationService`.

- **P2-4. mark-as-reviewed / bulk-операции** для дата-опс (проставить
  authenticity батчу по фильтру, отметить рави проверенным). Зависит от
  P0-1 (overlay-таблица естественно несёт `reviewed` флаг).

- **P2-5. Резюмируемый backfill** (сейчас «после pause рестарт с нуля»,
  `AlminasaAdminController.java:199,236`). Идемпотентен, так что не
  критично, но на больших объёмах неэффективно.

- **P2-6. MinIO prod-креды** не дефолтные `minioadmin`
  (`application.yml:73`), если object-storage используется в проде.

---

### Сводка «есть / отсутствует / рискованно»

**Есть и работает:** Liquibase (77/77, чисто с нуля) · JWT prod
fail-fast · actuator prod basic-auth · RFC-7807 error-handling ·
swappable LLM с graceful-degradation · резюмируемый краул (checkpoint) ·
frontend ErrorBoundary · ProtectedRoute(ADMIN) на всех admin-routes ·
auth refresh в httpOnly cookie (XSS-safe) · `hadith_grades` как
survives-reimport overlay (паттерн-образец) · PATCH matn-translation
(сам эндпоинт).

**Есть, но рискованно:** PATCH-перевод **затирается реимпортом** ·
member-list PUBLIC-контента открыт анониму · DB-креды захардкожены без
env-override · нет generic exception-handler.

**Отсутствует:** защита ручных правок от реимпорта (P0) · бэкап/restore
БД (P0) · manual-edit для authenticity/рави/sanad/ruling (P1) ·
data-health детекция (P1) · CI · Dockerfile приложения · прод-deploy
докум. в этом репо.
