# Roadmap

## Этап 0. Инициализация проекта

- [ ] Сгенерировать Spring Boot проект (Spring Initializr): Java 21,
      Spring Boot 3.3+, зависимости: Web, JDBC, Liquibase, PostgreSQL Driver,
      Testcontainers, Validation
- [ ] Настроить `application.yml` (datasource, Liquibase, профили `local`/`test`)
- [ ] Проверить, что приложение поднимается и Liquibase подключается к БД

## Этап 1. Схема БД (Liquibase)

Каждая миграция — отдельный changeset, автор `Abdula Basnukaev`.

- [ ] `20260413-01-create-extensions` — `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"`
- [ ] `20260413-02-create-users-table` — пока минимальная (id, username, email)
- [ ] `20260413-03-create-topics-table`
- [ ] `20260413-04-create-nodes-table` + индексы по `topic_id`, `status`
- [ ] `20260413-05-add-root-node-fk-to-topics` (циркулярный FK добавляем отдельно)
- [ ] `20260413-06-create-edges-table` + индексы по `from_node_id`, `to_node_id`, `edge_type`
- [ ] `20260413-07-create-sources-table` + GIN-индекс на `metadata`
- [ ] `20260413-08-create-authorities-table`
- [ ] `20260413-09-create-node-sources-table`
- [ ] `20260413-10-create-node-authorities-table`
- [ ] `20260413-11-create-revisions-table`
- [ ] Интеграционный smoke-тест: Testcontainers поднимает Postgres, Liquibase
      прогоняет все миграции без ошибок

## Этап 2. Доменная модель и репозитории

- [ ] Java records для всех сущностей (`Topic`, `Node`, `Edge`, `Source`,
      `Authority`, `NodeSource`, `NodeAuthority`, `Revision`)
- [ ] Enum'ы: `NodeType`, `EdgeType`, `NodeStatus`, `SourceType`, `Stance`
- [ ] Репозитории на JDBC Template + RowMapper:
  - [ ] `TopicRepository`
  - [ ] `NodeRepository`
  - [ ] `EdgeRepository`
  - [ ] `SourceRepository`
  - [ ] `AuthorityRepository`
  - [ ] `NodeSourceRepository`
  - [ ] `NodeAuthorityRepository`
  - [ ] `RevisionRepository`
- [ ] Интеграционные тесты на каждый репозиторий (CRUD)

## Этап 3. Бизнес-логика

- [ ] `TopicService` — создание темы сразу с корневым вопросом (транзакционно)
- [ ] `NodeService` — создание, редактирование (с записью в `revisions`), удаление
- [ ] `EdgeService` — создание/удаление рёбер
- [ ] `GraphService` — загрузка полного графа темы одним запросом
- [ ] `StatusCalculationService` — MVP-алгоритм пересчёта статусов
      (см. `architecture.md`)
- [ ] Тесты на каждый сервис, особенно на алгоритм пересчёта статусов
      (сценарии: простая поддержка, простое опровержение, цепочка,
      `INVALIDATES`, циклы)

## Этап 4. REST API

- [ ] DTO + мапперы (ручные, без MapStruct — слишком мало маппинга)
- [ ] Контроллеры (см. эскиз в `architecture.md`)
- [ ] Глобальный `@ControllerAdvice` с обработкой исключений
- [ ] Валидация входных DTO (`@Valid`, аннотации)
- [ ] OpenAPI-спецификация через `springdoc-openapi`
- [ ] Интеграционные тесты контроллеров через `MockMvc` + Testcontainers

## Этап 5. Справочники и поиск

- [ ] `SourceService` + REST: CRUD, поиск по названию/типу
- [ ] `AuthorityService` + REST: CRUD, поиск по имени/эпохе/мазхабу
- [ ] Привязка источников и авторитетов к узлам

## Этап 6. Улучшения (после MVP)

- [ ] Полнотекстовый поиск по содержимому узлов (Postgres `tsvector`)
- [ ] Реализация Dung's argumentation framework для продвинутого пересчёта
- [ ] Импорт/экспорт темы в JSON (для бэкапа и обмена)
- [ ] Аутентификация и авторизация (Spring Security, JWT)
- [ ] Голосование за вес аргументов

## Этап 7. Фронтенд

Появится как отдельная папка `frontend/` в корне репы (см. ADR-005).
Запускается после стабилизации бэкенд-API (Этапы 4-5 завершены).

### Подготовка
- [ ] Выбрать фреймворк (React / Vue / Svelte) — оформить как ADR
- [ ] Выбрать библиотеку визуализации графа (React Flow / Cytoscape.js /
      vis.js / D3) — оформить как ADR
- [ ] Создать `frontend/CLAUDE.md`, `frontend/docs/coding-standards.md`,
      `frontend/docs/ui-guidelines.md`
- [ ] Настроить сборку (Vite / другое), TypeScript, линтер

### MVP фронта
- [ ] Генерация TS-клиента из OpenAPI бэка (`openapi-typescript` или
      аналог) — важно для синхронизации типов с `docs/api-contract.md`
- [ ] Экран списка тем
- [ ] Экран создания темы
- [ ] Экран графа темы — визуализация узлов и рёбер
- [ ] Добавление узлов и связей через UI
- [ ] Редактирование содержимого узлов
- [ ] Отображение статусов узлов цветом/иконкой

### Позже
- [ ] Привязка источников и авторитетов к узлам через UI
- [ ] Полнотекстовый поиск
- [ ] Экспорт темы в PNG/SVG
- [ ] Аутентификация (когда появится на беке)
