# alminasa Hadith Ingestion — Plan 4: выпил legacy (sunnah ETL + AI-иснад)

> **Оркестрация:** OMC. База — read-only разведка зависимостей (полная карта
> в этом доке). После исполнения — независимый review.

**Goal:** удалить sunnah.com-ETL и AI-извлечение иснада (ADR-059) как legacy
по ADR-060 (alminasa = единственный источник). `SanadGraphService`/`SanadGraph`
(фронт) ОСТАЮТСЯ — визуализация переиспользуется на alminasa-данных;
удаляется только `buildGraphFromExtracted` (превью AI-иснада).

**Спека:** `docs/specs/2026-06-03-alminasa-hadith-source-design.md` §D.

## Факты разведки (проверено)

- `docker-compose.yml` — sunnah-mysql СЕРВИСА НЕТ (никогда не добавлялся;
  MySQL-дамп конфигурился только properties `sunnah.dump.*`). Удалять нечего.
- `buildGraphFromExtracted` используется ТОЛЬКО SunnahAdminController +
  его тесты в SanadGraphServiceTest.
- `IsnadPersistenceService` вызывается только из SunnahImportService;
  `IsnadExtractionService` — только из SunnahAdminController.
- Narrator 16-арг compat-конструктор используется ещё и DevHadithSeeder —
  остаётся (только javadoc-правка: упоминание IsnadPersistenceService).
- `sn_staging_*` создала миграция 59 (`20260601-59-create-sunnah-staging-tables`).
- backlog: sunnah-пункты на строках ~68 (SourcePickerHadith), ~225 (Admin
  REST-триггер + prod MySQL), ~249-253 (превью каталога, SunnahApiClient).

## Tasks

- [ ] **T1 backend (один атомарный коммит — билд зелёный на каждом коммите):**
  - удалить пакеты `hadith/sunnah/**` (26 main + 7 тестов) и `hadith/isnad/**`
    (4 main + 3 теста), `test/resources/sunnah/`;
  - `GlobalExceptionHandler`: убрать хендлеры Sunnah*-исключений + импорты;
  - `SanadGraphService`: убрать `buildGraphFromExtracted` + helpers
    (`sparseNarratorData`/`collectorData`) + javadoc ADR-059; в
    `SanadGraphServiceTest` убрать соответствующие тесты (buildGraph остаётся);
  - `application.yml`: убрать блок `sunnah.dump.*` с комментарием;
  - Narrator javadoc: упоминание IsnadPersistenceService → DevHadithSeeder;
  - **миграция 74** `20260604-74-drop-sunnah-staging-tables` (skill-чеклист:
    author Abdula Basnukaev, master-include последним, rollback пропущен с
    обоснованием — DROP необратим, legacy-данные не восстанавливаются) —
    `DROP TABLE IF EXISTS sn_staging_hadith/chapter/book/collection`;
  - точечный прогон: компиляция + SanadGraphServiceTest + быстрый smoke IT.
- [ ] **T2 frontend (коммит 2):** AdminSunnahPage.tsx + .test.tsx; App.tsx
  (lazy-импорт + роут /admin/sunnah); AdminDashboardPage (карточка sunnah);
  dictionary.ts (ключи admin.sunnah.* / admin.dashboard.sunnah.*);
  vitest + tsc.
- [ ] **T3 regen types.ts (коммит 3):** поднять backend (JDWP, CLAUDE.md) →
  `npm run generate-api` → diff ТОЛЬКО удаления Sunnah*/Isnad*-типов → tsc.
- [ ] **T4 docs (коммит 4):** ADR-059 (строка ~6372 decisions.md) — шапка
  «⟵ SUPERSEDED ADR-060 (План 4)» + индекс (строка 89); api-contract.md —
  удалить секцию `/api/v1/admin/sunnah/*`; architecture.md — sunnah-ETL
  упоминания; roadmap 49.C — **отложенный split**: alminasa-трек остаётся,
  legacy sunnah-трек сжать в строку «выпилен Планом 4»; backlog — снять
  sunnah-защиту: удалить мёртвые sunnah-пункты (~68, ~225, ~249-253).
- [ ] **Граница плана:** полный `./mvnw verify` + `npm run test:run` + tsc.
- [ ] Независимый review (lite — деletion-этап): verify-агент проверяет
  отсутствие осиротевших ссылок (grep sunnah/isnad по main-коду), чистоту
  diff types.ts, полноту doc-правок.

## Definition of Done

1. `grep -ri "sunnah" backend/src/main/java` → 0 (кроме исторических
   комментариев, если осознанно оставлены); `hadith/isnad/` не существует.
2. verify + vitest + tsc зелёные; types.ts — только удаления.
3. ADR-059 superseded, api-contract без /admin/sunnah, roadmap split, backlog
   чист.
