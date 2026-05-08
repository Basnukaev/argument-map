# Платформа для исламской науки

> Внутреннее технического имя репозитория - `argument-map` (по
> историческим причинам), но это **платформа** цифровых инструментов
> для исламских учёных и студентов. Argument-map - первое приложение
> на этом фундаменте, не центральный продукт. См. [`docs/vision.md`](docs/vision.md)
> для целевого видения.

## Что это

Платформа цифровых инструментов для **исламских учёных и студентов**.
Главный принцип - **точная атрибуция**: любая цитата в любом
приложении имеет автора (`Authority`), труд (`Source`/`Book`),
точное место (`location`), степень достоверности.

```
                    ┌─────────────────────────┐
                    │    library (книги +     │
                    │    цитирование)         │
                    └────────┬────────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
       ┌──────▼─────┐  ┌────▼─────┐  ┌─────▼──────┐
       │ argument-  │  │   Q&A    │  │  будущие   │
       │   map      │  │          │  │ приложения │
       └────────────┘  └──────────┘  └────────────┘
```

## Текущее состояние

- **Argument-map**: рабочее приложение с CRUD узлов/связей, drag-create,
  reconnect edges, секцией «Цитаты» с трёхуровневой иерархией
  (Authority + Source + NodeSource), скрытой для QUESTION-узлов
- **Library**: в проектировании. См.
  [`docs/architecture-platform.md`](docs/architecture-platform.md)
- **Q&A**: в roadmap, после library MVP

См. [`docs/roadmap.md`](docs/roadmap.md) для актуального плана.

## Структура

```
argument-map/
├── docs/                    общая документация платформы
│   ├── vision.md            что и зачем строим
│   ├── architecture-platform.md  как технически устроено целевое
│   ├── architecture.md      текущее (legacy) backend описание
│   ├── decisions.md         все принятые ADR-001..018
│   ├── roadmap.md           этапы работ
│   ├── progress.md          журнал сессий
│   ├── gotchas.md           известные ловушки
│   ├── glossary.md          термины проекта (исламский контекст)
│   ├── api-contract.md      REST API контракт
│   └── SESSION_START_PROMPT.md  стартовый промпт для Claude Code
│
├── backend/                 один Spring Boot для всех приложений
│   ├── docs/                бэк-специфичные стандарты
│   └── src/main/java/ru/basnukaev/argumentmap/
│       ├── domain/          сейчас плоско; в целевой архитектуре
│       │                    разойдётся по доменам (argumentmap/,
│       │                    library/, citation/, qa/, shared/)
│       └── ...
│
├── frontend/                сейчас один React-app argument-map.
│                            В целевой архитектуре переедет в
│                            apps/argument-map/, появятся
│                            apps/library/, packages/shared-*/
│
├── scripts/                 утилиты (seed-скрипты для тестовых
│                            данных)
└── docker-compose.yml       Postgres (+ будущее: MinIO для image
                             store)
```

## Технологии

- **Backend**: Java 21, Spring Boot 3.5, JDBC Template (без JPA),
  PostgreSQL 16, Liquibase
- **Frontend**: React 19, TypeScript strict, Vite 6, Tailwind v4,
  React Flow (для argument-map), Zustand 5, openapi-typescript
- **Тесты**: JUnit 5 + Testcontainers на бэке, Vitest + RTL + MSW
  на фронте
- **Расширения для library** (планируются): Apache Tika для PDF,
  Tess4j для OCR, MinIO для object store, react-pdf и
  react-image-crop на фронте, pnpm workspaces для monorepo

См. [`docs/architecture-platform.md`](docs/architecture-platform.md)
для детального описания стэка и обоснований.

## Быстрый старт

```bash
# Поднять Postgres
docker compose up -d

# Backend (порт 9090)
cd backend
./mvnw spring-boot:run

# Frontend (порт 5173)
cd frontend
npm install && npm run dev

# Открыть браузер
# http://localhost:5173/topics
```

Dev-user UUID: `14561248-0bfd-4a62-8395-d40a6972182a` (в
`frontend/.env.local` как `VITE_DEV_USER_ID`).

Тестовая тема «Дозволенность Мавлида ан-Наби»:
`640a7ac7-2827-4b80-9893-dc7142f100e4` (создаётся
`scripts/seed-mawlid.sh`).

## Документация

**Для начала работы:**
- [`docs/vision.md`](docs/vision.md) - **что мы строим и зачем**.
  Прочитать первым
- [`docs/architecture-platform.md`](docs/architecture-platform.md) -
  как технически устроена целевая архитектура
- [`docs/roadmap.md`](docs/roadmap.md) - текущий план работ
- [`docs/progress.md`](docs/progress.md) - журнал сессий

**Принятые архитектурные решения:**
- [`docs/decisions.md`](docs/decisions.md) - все 18 ADR. Самые
  важные:
  - ADR-018: pivot в платформу (это решение)
  - ADR-017: трёхуровневая модель цитирования
  - ADR-010: матрица семантики связей argument-map
  - ADR-005: монорепа (с обновлением под платформу в ADR-018)

**Бэк-специфика:**
- [`backend/docs/coding-standards.md`](backend/docs/coding-standards.md)
- [`backend/docs/api-design.md`](backend/docs/api-design.md)
- [`backend/docs/testing-strategy.md`](backend/docs/testing-strategy.md)

**Фронт-специфика:**
- [`frontend/docs/coding-standards.md`](frontend/docs/coding-standards.md)
- [`frontend/docs/ui-guidelines.md`](frontend/docs/ui-guidelines.md)

## Работа с Claude Code

Claude Code в этой репе работает по всему дереву - бэк, фронт,
корневая документация. Стартовый промпт для новой сессии:
[`docs/SESSION_START_PROMPT.md`](docs/SESSION_START_PROMPT.md).

Memory Claude Code хранится в
`~/.claude/projects/-mnt-c-my-folders-projects-argument-map/memory/`
с фиксированными feedback-памятками (decision authority, WSL-only,
private build cadence, и т.д.).

## История

Проект начался как `argument-map` MVP в апреле 2026. После 18+
сессий и 60+ коммитов (см. `docs/progress.md`) переориентирован
в платформу - **ADR-018**, май 2026. Имя репозитория сохранено
как technical id; платформа называется в `vision.md`.
