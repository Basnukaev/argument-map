# alminasa — narrator-commentary: джарх/таʿдиль о рави (план)

> **Оркестрация:** OMC autopilot (Сессия 61). Клон **Плана 8** (علل/غريب,
> `2026-06-06-alminasa-ilal-gharib.md`) со сдвигом ключа джойна хадис→рави.
> Детальный пофайловый scope-отчёт (агент-исследование, 180k токенов) —
> `/tmp/narrator-commentary-scope.md`; здесь — контракт, решения, декомпозиция.

## Goal

Джарх/таʿдиль-цитаты учёных **о передатчике** (рави) из ES-индекса alminasa
`narrator-commentary-12` → доменная таблица `hd_narrator_commentaries` → секция
«Оценки учёных о передатчике» на карточке рави (`NarratorDetailPage`) с
атрибуцией (критик · книга · стр.).

## Контракт данных (live-проб 2026-06-16 — **32 848 доков** на корпус)

ES `narrator-commentary-12`, джойн `terms:{id:[narrator external_id]}`. `_source`:

| поле | тип | назначение |
|---|---|---|
| `id` | string | narrator id = `hd_narrators.external_id` — **ключ джойна** |
| `name` | string | имя рави (денормализовано — НЕ используем, у нас своё) |
| `commenter` | string | критик (краткое имя) |
| `commenter_dod` | int | год смерти критика (в `_source`, проверено live) |
| `comments` | **string[]** | массив вердиктов (обычно 1, бывает >1) |
| `book` | string | книга-источник (напр. تقريب التهذيب) |
| `author` | string | автор книги |
| `page` / `volume` / `book_order` | int | локация + порядок |

## Design decisions (resolved — фид в ADR-061)

1. **Отдельная таблица `hd_narrator_commentaries`** (НЕ реюз `hd_explanations`:
   там `hadith_id` NOT NULL FK на хадис, цитата о рави не привязана к хадису;
   backlog + План 8 называют таблицу явно дважды). → **ADR-061**.
2. **Staging PK = ES `_id` (text)** — в `_source` нет природного int id; `_id`
   стабилен per-doc → идемпотентный upsert `ON CONFLICT (doc_id)`. Caveat
   re-index alminasa (новые `_id`) → документировать в javadoc; staging
   транзиентен, re-map per-narrator delete-recreate выправляет домен.
3. **`comments` → `jsonb`** (forward-compat, массив строк), join в строку в DTO
   для UI.
4. **`commenter_dod` из `_source`** (live-данные несут его).
5. **Цитаты inline в `NarratorResponse.commentaries`** (как `relations`),
   сортировка `commenter_dod asc, book_order`. Пагинацию НЕ делаем (корпус
   bounded, реально <~50 на рави); отдельный paginated `GET
   /narrators/{id}/commentaries` — fallback в backlog, если payload вырастет.
6. **Backfill отдельным проходом** (keyset по `am_staging_narrator` по id),
   маппинг встроен в `AlminasaNarratorMapper.mapNarrator` (re-map narrators
   подтянет цитаты). НЕ в hadith-mapper (цитаты привязаны к рави, не хадису).
7. **Backfill бьёт по всем staged-рави** (terms возвращает пусто для рави без
   цитат — robust); `hasCommentary`-фильтр (`metadata`) — опц. оптимизация, не
   обязательна для MVP.

## Декомпозиция (пофайловые детали — scope-отчёт §F)

- **X.a — Миграция 76 + staging/domain DAO + Rows.** `hd_narrator_commentaries`
  (`id uuid PK`, `narrator_id uuid NOT NULL REFERENCES hd_narrators(id) ON DELETE
  CASCADE`, `commenter text`, `commenter_death_year int`, `book_name text`,
  `author text`, `page int`, `volume int`, `comments jsonb NOT NULL`, `metadata
  jsonb`, `created_at`; `idx … (narrator_id)`) + `am_staging_narrator_commentary`
  (`doc_id text PK`, `narrator_id int`, `commenter text`, `book text`, `raw jsonb
  NOT NULL`, `imported_at`; `idx … (narrator_id)`) + register master. +
  `AmNarratorCommentaryStagingDao` (зеркало `AmCommentaryStagingDao`:
  `upsertAll` ON CONFLICT, `findByNarratorId(int)`, `count()`) +
  `NarratorCommentaryRepository` (зеркало `NarratorRelationRepository`) +
  `AlminasaRows.fromNarratorCommentaryHit` + records (`NarratorCommentary`
  domain, `AmNarratorCommentaryRow`). + round-trip IT.
- **X.b — ES-клиент + backfill-проход.**
  `AlminasaEsClient.fetchNarratorCommentaries(List<Integer> narratorIds)` (клон
  `fetchNarratorsByIds`, `terms:{id}`, batched) + const
  `NARRATOR_COMMENTARY_INDEX = "narrator-commentary-12"`. Backfill: проход
  `backfillNarratorCommentaries()` в `AlminasaDependentsBackfillService` (keyset
  по `am_staging_narrator` по narrator id, чекпоинт
  `index_name='narrator-commentary-backfill'`, reuse `alminasaBackfillExecutor`).
  REST в `AlminasaAdminController`: `POST /backfill/narrator-commentary/{start,
  pause}` + `GET /backfill/narrator-commentary/status`. IT со стабом
  (паттерн existing backfill IT).
- **X.c — Маппер.** `AlminasaNarratorMapper.recreateNarratorCommentaries(
  narratorUuid, externalId)` после `recreateRelations` (delete-recreate по
  `narrator_id`, лукап `narratorCommentaryStagingDao.findByNarratorId(int)`).
  Unit-тест на фикстуре `narrator-commentary-12.json`.
- **X.d — Web.** `NarratorResponse` + `List<NarratorCommentaryDto> commentaries`
  (nullable, только в `getOne`); `NarratorCommentaryDto` record `{commenter,
  commenterDeathYear, bookName, author, page, volume, comments}`;
  `NarratorController.getOne` грузит, `toResponse` расширить, `list` → null.
  `docs/api-contract.md` (секция GET `/narrators/{id}`).
- **X.e — Frontend.** `apps/hadith/types.ts` + `NarratorCommentaryDto`, расширить
  `NarratorResponseDto`; `NarratorDetailPage.tsx` новая секция (между verbatim-
  джарх `:164` и сетью `:166`); `NarratorCommentaryList.tsx` (клон
  `ExplanationsList`/`BookHeadedItem`); i18n ru+ar
  (`hadith.narrator.commentaries.*`); MSW-тест в `NarratorDetailPage.test.tsx`.
  `generate-api` НЕ нужен (hadith-домен ручной).
- **X.f — Live + review + handoff.** backfill narrator-commentary (фон) → re-map
  narrators → playwright (рави Абу Хурайра, external_id 4396) → independent
  review → docs (ADR-061, roadmap/progress, снять `backlog.md:158`) → handoff.

## Definition of Done

1. **CI-гейт:** IT round-trip БЕЗ живого API — staged narrator-commentary
   фикстура (id 4396) → re-map → строка `hd_narrator_commentaries` у рави
   с `comments`/`commenter`/`book_name`.
2. **Live:** рави Абу Хурайра — секция «Оценки учёных» с цитатами (критик +
   книга + стр.). Пустой результат на рави с `hasCommentary=true` = сигнал бага.
3. Идемпотентность backfill (upsert по `doc_id`) + re-map (delete-recreate per
   narrator); `verify`/`vitest`/`tsc` зелёные.
4. Review 0 Critical/Important; **ADR-061** записан; `backlog.md:158` снят.
