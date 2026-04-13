# Журнал работы

Хронологический лог сессий. Новые записи — **сверху**.

Формат записи:
```
## YYYY-MM-DD — Сессия N
### Сделано
### Решения
### Проблемы
### Следующий шаг
```

---

## 2026-04-13 — Сессия 1 (backend)

### Сделано
- Установлены инструменты в WSL: OpenJDK 21.0.10, Maven 3.8.7
- Сгенерирован Spring Boot проект через Spring Initializr (версия 3.5.0):
  - `pom.xml` с зависимостями: web, jdbc, validation, liquibase, postgresql,
    testcontainers (включая `spring-boot-testcontainers` для `@ServiceConnection`)
  - Maven Wrapper (`mvnw`)
  - Главный класс `ArgumentMapApplication`
  - Тестовая конфигурация Testcontainers (`TestcontainersConfiguration`,
    `TestArgumentMapApplication`, `ArgumentMapApplicationTests`)
- Настроен `application.yml`:
  - Профиль `local` — подключение к Postgres из `docker-compose.yml`
  - Профиль `test` — заглушка, datasource через Testcontainers `@ServiceConnection`
  - Сервер на порту 9090 (8080 занят)
- Создан пустой `db.changelog-master.xml` с валидной структурой
- Создана папка `db/changelog/changes/` для будущих миграций
- Проверен успешный запуск: Tomcat на :9090, HikariPool подключился
  к Postgres, Liquibase прочитал changelog — "Database is up to date"
- Добавлен ADR-004 (Maven vs Gradle) в `decisions.md`
- Проставлены `[x]` на пунктах Этапа 0 в `roadmap.md`
- В `CLAUDE.md` добавлен раздел "Git-коммиты" с Conventional Commits

### Решения
- ADR-004: Maven вместо Gradle — привычный стек, совместимость с экосистемой
- Spring Boot 3.5.0 вместо 3.3.x — Initializr требует >=3.5.0 (3.3/3.4
  больше не поддерживаются на start.spring.io)
- Порт 9090 вместо дефолтного 8080 — порт 8080 занят на машине разработчика
- Добавлена зависимость `spring-boot-testcontainers` — идёт автоматически
  из Initializr при выборе Testcontainers, предоставляет `@ServiceConnection`

### Проблемы
- Java и Maven не были установлены в WSL — установлены через apt
- Spring Initializr больше не поддерживает Spring Boot 3.3/3.4 — использовали 3.5.0

### Следующий шаг
**Этап 1 из `roadmap.md`: Liquibase-миграции схемы БД.**

Создать миграции по списку из roadmap (extensions, users, topics, nodes,
edges, sources, authorities, node_sources, node_authorities, revisions).
Каждая миграция — отдельный файл в `src/main/resources/db/changelog/changes/`.
Smoke-тест через Testcontainers.

---

## 2026-04-13 — Сессия 0.5 (reorg → монорепа)

### Сделано
- Реорганизована структура проекта в монорепу с независимыми подпапками:
  - Корень: `README.md`, `docker-compose.yml`, `.gitignore`, `docs/` (общее)
  - `backend/` — Java/Spring Boot часть со своим `CLAUDE.md` и `docs/`
  - `frontend/` — появится на Этапе 7
- Документация разделена на общую (продуктовую) и специфичную для технологии:
  - Общее (`docs/`): architecture, er-diagram, glossary, roadmap, progress,
    decisions, gotchas, api-contract
  - Бэкенд (`backend/docs/`): coding-standards, antipatterns, api-design
- Создан `docs/api-contract.md` — пустой шаблон источника истины для
  контракта между беком и фронтом
- Добавлен **ADR-005** в `decisions.md` — решение о монорепе
- Расширен Этап 7 в `roadmap.md` — вместо заглушки полноценный план
  фронтенда (выбор фреймворка, библиотеки графов, подготовка, MVP)
- Обновлён `backend/CLAUDE.md`:
  - Добавлен раздел "Контекст: это монорепа" с правилами границ
  - Пути к общей документации через `../docs/`
  - Сессии помечаются префиксом `(backend)` в общем journal
- Создан корневой `README.md` с описанием структуры и принципов

### Решения
- ADR-005: монорепа с двумя независимыми папками, без специализированных
  инструментов (Nx/Turborepo). Простая модель, каждая часть независима.
- Claude Code запускается внутри подпапки (`cd backend && claude`),
  не в корне репы — читает свой локальный `CLAUDE.md`
- Сессии в общем `progress.md` помечаются префиксом `(backend)` / `(frontend)`
  для визуального разделения

### Проблемы
- Нет

### Следующий шаг
**Этап 0 из `docs/roadmap.md`: инициализация Spring Boot проекта.**

Важно: работать **внутри `backend/`**. Код Spring Boot проекта создаётся
в `backend/`, не в корне репы.

1. `cd backend`
2. Сгенерировать Maven-проект: Java 21, Spring Boot 3.3+, зависимости:
   `spring-boot-starter-web`, `spring-boot-starter-jdbc`, `liquibase-core`,
   `postgresql`, `spring-boot-starter-validation`, `spring-boot-starter-test`,
   `testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`
3. Настроить `application.yml` с профилями `local` и `test`:
   - `local`: подключение к Postgres из корневого `docker-compose.yml`
     (`jdbc:postgresql://localhost:5432/argumentmap`, user/pass `argmap/argmap`)
   - `test`: заглушка, настоящая конфигурация Testcontainers появится на Этапе 1
4. Создать пустой `db.changelog-master.xml` с валидной структурой
5. Убедиться что `./mvnw spring-boot:run` поднимает приложение и Liquibase
   успешно подключается
6. Первый коммит: `chore(backend): initial spring boot project setup`

Также добавить ADR-004 (Maven vs Gradle) в `../docs/decisions.md`.

После Этапа 0 — переход к Этапу 1 (Liquibase-миграции схемы БД).

---

## 2026-04-13 — Сессия 0 (инициализация)

### Сделано
- Обсуждена идея проекта: API-first инструмент для argument mapping
- Выбран стек: Java 21, Spring Boot 3.3+, PostgreSQL 16, Liquibase, JDBC Template, Testcontainers
- Спроектирована архитектура и доменная модель (Topic, Node, Edge, Source, Authority, Revision)
- Создана полная документация проекта:
  - `CLAUDE.md` — конфиг для Claude Code
  - `docs/architecture.md`, `docs/er-diagram.md`, `docs/glossary.md` — архитектура и термины
  - `docs/roadmap.md` — план работ по этапам
  - `docs/decisions.md` — три первых ADR
  - `docs/gotchas.md` — шаблон + первые ловушки
  - `docs/progress.md` — журнал сессий (этот файл)
  - `docs/coding-standards.md` — принципы, SOLID, правила Java-кода, комментариев, тестов
  - `docs/antipatterns.md` — что не делаем в Java/SQL/REST
  - `docs/api-design.md` — правила дизайна REST API
- Настроен `docker-compose.yml` с Postgres 16

### Решения
- См. `docs/decisions.md`:
  - ADR-001: JDBC Template вместо JPA
  - ADR-002: Source и Authority как отдельные справочники, не узлы графа
  - ADR-003: Граф в двух таблицах (nodes + edges) с дискриминатором

### Проблемы
- Нет

### Следующий шаг
**Этап 0 из `docs/roadmap.md`: инициализация Spring Boot проекта.**

Конкретно:
1. Сгенерировать Maven-проект (Spring Initializr или вручную): Java 21,
   Spring Boot 3.3+, зависимости: `spring-boot-starter-web`,
   `spring-boot-starter-jdbc`, `liquibase-core`, `postgresql`,
   `spring-boot-starter-validation`, `spring-boot-starter-test`,
   `testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`
2. Настроить `application.yml` с профилями `local` и `test`:
   - `local`: подключение к Postgres из `docker-compose.yml`
     (`jdbc:postgresql://localhost:5432/argumentmap`, user/pass `argmap/argmap`)
   - `test`: Testcontainers поднимает свой Postgres
3. Создать пустой `db.changelog-master.xml`
4. Убедиться что `./mvnw spring-boot:run` поднимает приложение и Liquibase
   успешно подключается (без миграций — это Этап 1)
5. Создать первый коммит: `chore: initial spring boot project setup`

После этого — переход к Этапу 1 (Liquibase-миграции схемы БД).
