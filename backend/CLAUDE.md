# Backend - Claude Code config

Бэк-специфичные правила. Общие правила репо (стэк, layout,
команды, conventional commits) - в корневом `../CLAUDE.md`.
Правила документации - в `../docs/doc-hygiene.md`

## Контекст проекта

Платформа цифровых инструментов для исламских учёных и студентов
(см. `../docs/vision.md`). Бэкенд - один Spring Boot,
обслуживающий три приложения: argument-map (граф аргументации),
library (книги + цитирование), Q&A (планируется). API-first

Стратегическое решение - ADR-018 (platform pivot). Не «argument
mapping tool с исламским use-case», а **платформа** где
library - фундамент, argument-map / Q&A / будущие приложения
строятся поверх

Работа **только в пределах** `backend/`. Корень и `../frontend/`
не трогать без явного запроса

## Структура пакетов

```
ru.basnukaev.argumentmap/
├── config              Spring-конфигурация
├── domain              records предметной области
├── repository          JDBC-репозитории
├── service             бизнес-логика argument-map (TopicService и пр.)
├── web                 REST controllers + DTO + mappers
│   ├── controller
│   ├── dto
│   └── mapper
├── exception           кастомные исключения + GlobalExceptionHandler
└── library             library-домен
    ├── domain          Book / Chapter / Page / ImageRegion records
    ├── repository      BookRepository / ChapterRepository / etc
    ├── service         BookService и пр.
    ├── shamela         shamela ETL
    │   ├── api         ShamelaApiClient + dto
    │   ├── etl         readers + extractor + 6 staging DAO
    │   └── service     mapper (5 классов + DaoSupport)
    ├── pdf             PdfSourceProvider + PdfService + PdfController
    └── storage         ObjectStorageService + S3ClientConfig + MinIO
```

## После коммита - чек-лист документации

После **каждого** `feat`/`fix` коммита проверь:

| Что произошло | Что обновить |
|---|---|
| Закрыт пункт roadmap | `../docs/roadmap.md` `[x]` |
| Закрыт целый этап | `../docs/roadmap.md` - сжать в строку (см. `../docs/doc-hygiene.md` Принцип 3) |
| Принято решение между альтернативами | новый ADR в `../docs/decisions.md` |
| Миграция БД / новая колонка | ADR + `../docs/architecture.md` |
| Новый/изменённый REST endpoint, поле DTO | `../docs/api-contract.md` |
| Поймал баг через линтер/тесты/IT который может повториться | `../docs/gotchas.md` (симптом / причина / решение) |
| Новое доменное понятие | `../docs/glossary.md` |
| Изменились бэкенд-правила | `backend/docs/*` |

ADR / gotcha / api-contract пишутся **сразу**, не в конце сессии.
Принципы эволюции каждого документа - в `../docs/doc-hygiene.md`

### Триггеры для ADR

Почти наверняка нужен новый ADR если в коммите было:

- Выбор между ≥2 рассмотренных подходов (есть rejected
  alternatives)
- Изменение схемы БД (миграция Liquibase)
- Изменение контракта API (новое поле / эндпоинт)
- Решение «не делаем X сейчас, отложим до Y» (явный YAGNI)
- Введение новой инфраструктурной системы

### Триггеры для gotcha

Если что-то из этого ловили и потратили время:

- «Failsafe не запускает IT» / «Liquibase не применяет миграцию»
- Spring/Hibernate/JDBC ведёт себя не как ожидалось
- Странные ошибки типов в Java/Spring (`Instant` vs `OffsetDateTime`)
- Тесты ломаются от чего-то что выглядит несвязанным

Не должно быть так что фикс делается, gotcha не записан, через
две недели наступаем на тех же граблях

## Соглашения по Java/Spring

### Общие

- Все комментарии, логи, JavaDoc - на русском. Имена классов /
  методов / переменных - на английском
- JavaDoc только для нетривиальной логики - не ради JavaDoc
- Комментарии объясняют **почему**, не **что**. Если код
  самодокументируемый - комментарии не нужны
- Импорты вместо полных квалифицированных имён

### Liquibase

- Автор миграций всегда `Abdula Basnukaev`
- Формат changeset id: `YYYYMMDD-NN-short-description` (например,
  `20260413-01-create-topics-table`)
- Каждая миграция - отдельный файл в
  `src/main/resources/db/changelog/changes/`
- Мастер-файл: `src/main/resources/db/changelog/db.changelog-master.xml`
- `<rollback>` там где имеет смысл
- Индексы в той же миграции что и таблица, если очевидны
- Символ `&` в comment / SQL экранировать `&amp;` или оборачивать
  в `<![CDATA[ ... ]]>` (gotcha)

### База данных

- **Без JPA/Hibernate**. Только JDBC Template + ручной маппинг
  через `RowMapper`
- snake_case для таблиц и колонок
- Первичные ключи - UUID (`uuid` PostgreSQL)
- Timestamps - `timestamptz`, с дефолтом `now()` где уместно
- Soft delete только там где явно требуется. История изменений -
  через `revisions`

### REST API

Подробно - в `backend/docs/api-design.md` и `../docs/api-contract.md`

- DTO `*Request` / `*Response` (выбранная конвенция)
- Problem Details RFC 7807 через `@ControllerAdvice` глобально
- Валидация через Bean Validation (`@Valid` + аннотации)
- `@CurrentUser UUID userId` извлекается из SecurityContext (Bearer JWT
  через `JwtAuthenticationFilter`, или X-User-Id fallback в dev/test
  через `XUserIdAuthenticationFilter`). ADR-040 заменил ADR-006
  заглушку. API аннотации не изменилось

### Security (ADR-040)

- **Spring Security 6** + **jjwt 0.12.x** (HS256)
- Auth endpoints под `/api/v1/auth/*` - публичные
- Все mutating endpoints требуют principal (Bearer JWT в prod, либо
  X-User-Id в dev/test/local profile)
- `auth.jwt.secret` в prod через env `AUTH_JWT_SECRET` минимум 256 бит
  (`openssl rand -hex 32` для генерации). dev placeholder в
  `application.yml` падает при попытке shipping в prod через
  IllegalStateException на старте
- Access token TTL 15 мин, refresh TTL 7 дней (HttpOnly+Secure+
  SameSite=Strict cookie)
- Roles: `USER` / `ADMIN` (CHECK constraint). RBAC permissions
  per-entity - Этап 22

### Транзакции

- `@Transactional` только на сервисном слое
- `@Transactional(readOnly = true)` для read-only методов
- **НИКОГДА** не ставить `@Transactional` на `@Scheduled` напрямую
  (см. antipatterns.md)
- Избегать вложенных транзакций

### Тесты

- **Интеграционные тесты** - через Testcontainers (PostgreSQL),
  **не** H2
- Именование `ClassNameTest` / `ClassNameIT`
- `@Tag("live")` для тестов работающих с внешним API (shamela,
  archive.org) - исключаются из обычного `verify`
- DBRider при необходимости простой подготовки фикстур
- Минимум: покрыть сервисы бизнес-логики и репозитории

Подробно - в `backend/docs/coding-standards.md` и
`backend/docs/testing-strategy.md`

## Что НЕ делать

- Не использовать JPA/Hibernate (только JDBC Template)
- Не использовать Lombok без крайней необходимости (Java records)
- Не использовать H2 в тестах (только Testcontainers)
- Не ставить `@Transactional` на `@Scheduled` методы напрямую
- Не добавлять зависимости без обсуждения
- Не писать бесполезные комментарии вида `// увеличиваем счётчик`
- Не коммитить закомментированный код
- Не лезть в `../frontend/` и корень репы без явного запроса

Полный список - в `backend/docs/antipatterns.md`

## Когда запускать `./mvnw verify`

См. корневой `../CLAUDE.md` раздел «Когда что запускать (cadence)»
- правило применяется к бэку без специфики. Кратко: **не на каждом
чихе**, только в конце логической фазы / перед коммитом крупного
изменения / при конкретном сигнале о возможной поломке (миграция,
изменение DTO/контроллера, рефакторинг >1 слоя). Мелкие правки одного
класса - не запускать
