# Design spec: Hadith Chains Explorer (alminasa.ai style)

**Дата:** 2026-05-20
**Автор:** Абдула + brainstorming
**Статус:** approved, ожидает implementation plan
**Триггер:** vision-expansion-49d Section 2.6
**Связанные ADR (existing):**
- ADR-017 (Source+Authority unification),
- ADR-018 (platform pivot - **именно этим spec'ом окупается**),
- ADR-022 (frontend reorg `src/apps/*`),
- ADR-028 (academic citation metadata),
- ADR-039 (Tiptap hadithBox/ayahBox extensions),
- ADR-043 (per-entity permissions),
- ADR-047 (roles system: SCHOLAR может ставить hadith grade'ы)
**Связанные ADR (создадутся при implementation):**
- ADR-051 (hadith domain: новые сущности `Hadith`/`Narrator`/`Sanad`/`Matn` vs reuse Source/Authority),
- ADR-052 (sanad graph visualization stack - React Flow или dagre-only),
- ADR-053 (hadith ETL: sunnah.com / islamhouse / shamela source matrix),
- ADR-054 (matn variation diff representation - normalized vs raw)

---

## Контекст

Абдула просит «отдельную страницу с полной историей хадисов и
мухаддисов, с иснадами, матнами и т.п., аналог
[alminasa.ai](https://alminasa.ai/), но чтоб было наглядно видно,
кто кому что рассказывал в каких вариациях, вплоть до Пророка ﷺ»
(vision-expansion-49d.md §2.6).

Это **новое приложение** под `frontend/src/apps/hadith/`. ADR-018
platform pivot принимался ровно с расчётом на такие use-case'ы:
library + один Spring Boot + apps/ structure уже готовы принять
новый app без рефакторинга. Реализация sanad explorer'а в архитектуре
single-app argument-map потребовала бы существенной переработки -
здесь же это один из трёх sibling-приложений.

### Прецеденты (что есть в системе уже)

- `Authority` (SCHOLAR type, миграция 47) - персоны с academic
  metadata (`bio`, `era`, `madhab`, `fullName`, `deathYearHijri`).
  Это **подмножество** того, что нужно для Narrator: для Narrator
  нужны kunya, location of teaching, reliability_grade, **список
  учителей и учеников** (ровно для построения isnad'ов).
- `Source` (HADITH type) - сам хадис, но **без сегментации**
  matn/isnad/grades в отдельные сущности. Multi-grading через
  `HadithGrade` уже добавлен (миграция 43).
- `HadithGrade` - оценка хадиса SCHOLAR'ом. Multi-grade поддержан:
  один Source может иметь grade'ы от Бухари, Тирмизи, аль-Албани.
- `Reliability` (legacy enum на Source) - оставлен для backward
  compat, multi-grading в `HadithGrade` это его эволюция.
- Tiptap `hadithBox` extension (ADR-039) - в книгах хадис уже
  размечается как block с metadata. Это **точка входа** для
  cross-link «открыть hadith explorer для этого хадиса».
- Shamela ETL pattern (`library/shamela/etl/`, 6 staging DAO,
  reader/extractor/mapper) - готовый шаблон для нового
  `hadith/sunnah/etl/`.

### Принципиальное решение: новый domain vs reuse existing

Hadith Chains Explorer **создаёт новый namespace** `hadith.*` /
`hd_*` поверх существующих Authority+Source, **не подменяет их**.
Reuse vs new для каждой сущности - в §1 ниже. ADR-051 зафиксирует.

---

## Цель

1. Учёный/студент открывает hadith и **визуально видит**: цепочка
   передачи от Пророка ﷺ через muhaddith'ов до сборника, имена
   narrator'ов с биографией at-a-glance, текст хадиса с
   вариациями в других источниках, grade'ы от классических
   muhaddith'ов.
2. **Поиск** - по тексту matn'а (arabic + russian translit), по
   имени narrator'а (с disambiguation), по сборнику (Бухари,
   Муслим, Тирмизи, ...), по grade'у (только SAHIH через Бухари
   и Муслима, например).
3. **Cross-references** - из argument-map узла со ссылкой на хадис
   - переход в hadith explorer; из библиотечной книги с `hadithBox`
   - тоже переход в hadith explorer.

## Не входит в этот spec

- **Sanad authenticity scoring** (auto-grade на основе reliability
  narrator'ов) - YAGNI до Phase 4+. Сначала надо отрисовать то,
  что вручную assess'нули классики.
- **Cross-collection match algorithm** (автоматически находить
  variations matn'ов через NLP similarity) - Phase 2 в Roadmap,
  rule-based детекция через `primaryHadithId` link.
- **Narrator genealogy** (генеалогические древа) - выйдет
  естественно из sanad graph при достаточном datasource, но не
  отдельная фича.
- **Audio recitation** хадисов - вне scope (можно добавить через
  `library_files` если попросят).
- **Hadith editing UI** - read-only приложение. Curation - через
  ETL + admin tooling, не end-user mutation.

---

## 1. Domain modeling

### 1.1 Решение по reuse vs new (ADR-051 draft)

| Концепт | Решение | Обоснование |
|---|---|---|
| **Hadith** (сам хадис) | **NEW** `hd_hadiths` | Source.HADITH остаётся для legacy и интеграции c node_sources; hadith-domain нуждается в primary_collection, primary_number, normalized_matn, status (canonical/variant). Cross-link `hd_hadiths.source_id` → `sources.id` для backward compat |
| **Narrator** | **NEW** `hd_narrators` | Authority.SCHOLAR имеет `bio/era/madhab` но не `kunya/location/teacher_count/student_count`. Narrator живёт в hadith-domain. Cross-link `hd_narrators.authority_id` → `authorities.id` nullable - для известных учёных мостимся на existing Authority record |
| **Sanad** (цепь narrators) | **NEW** `hd_sanads` + `hd_sanad_narrators` | M:M chain - конкретный хадис имеет конкретную цепь. Ordered list через `hd_sanad_narrators.position INT` (0-indexed). Один хадис может иметь несколько sanad'ов (multiple chains к одному matn) |
| **Matn** (текст) | **NEW** `hd_matns` | Один Hadith может иметь несколько matn вариаций (разные сборники могут цитировать тот же хадис с минимальными differences). Каждый matn привязан к источнику публикации (`hd_matns.source_ref_book_id`, `page_no`, `printed_number`) |
| **Collection** | **REUSE** `lib_books` | Бухари/Муслим/Тирмизи/Абу Дауд/Насаи/Ибн Маджа - это книги. Уже импортируются через Shamela ETL. Hadith explorer reuse'ит lib_books через `hd_hadiths.primary_book_id` + `hd_matns.source_ref_book_id` |
| **HadithGrade** | **REUSE** | Уже работает мульти-grade. `hd_hadiths.source_id` → `sources` → существующий `hadith_grades` flow работает unchanged. Никакой дубликации |
| **Variation** | **EMBEDDED** в `hd_matns` | M:N не нужна - каждый matn ссылается на один source_ref_book. Variation = просто другой matn у того же `hd_hadiths` |

### 1.2 ER diagram (ASCII)

```
+-----------------+         +-----------------+         +------------------+
|   hd_hadiths    |         |    hd_matns     |         |    lib_books     |
+-----------------+  1:N    +-----------------+   N:1   +------------------+
| id (PK)         |◄────────| id (PK)         |────────►| id (PK)          |
| primary_book_id |────┐    | hadith_id (FK)  |         | title            |
| primary_number  |    │    | text_ar         |         | shamela_id?      |
| normalized_matn |    │    | text_ru?        |         | category_id?     |
| status          |    │    | source_book_id  |         | ...              |
| source_id (FK→  |    │    | printed_number  |         +------------------+
|   sources)?     |    │    | page_no?        |
| created_at      |    │    | volume?         |
+-------+---------+    │    | is_primary BOOL |
        │              │    +--------+--------+
        │ 1:N          │
        ▼              │             ▲ matn could be in different book
+-----------------+    │             │
|    hd_sanads    |    │             │
+-----------------+    │             │
| id (PK)         |    │             │
| hadith_id (FK)  |    │             │
| chain_grade     |    │             │
| compiled_by_id  |    │             │
|  (FK→narrators) |    │             │
| compiled_in_    |    │       (primary_book_id is the «canonical» book
|  book_id (FK)   |    └──────  the hadith is cited as primary in -
| created_at      |             usually Bukhari №X)
+-------+---------+
        │ 1:N (ordered)
        ▼
+-----------------------+
| hd_sanad_narrators    |
+-----------------------+
| sanad_id (FK)  PK     |
| position INT   PK     |  -- 0 = closest to Prophet ﷺ
| narrator_id (FK)      |
| transmission_phrase   |  -- «хаддасана», «ахбарана», «ан»
+-----------+-----------+
            │ N:1
            ▼
+-------------------------+
|     hd_narrators        |
+-------------------------+
| id (PK)                 |
| authority_id (FK→        |   <-- nullable, mostly NULL for
|   authorities)?         |       non-famous narrators
| name_ar (full)          |
| kunya                   |
| laqab                   |
| year_birth_hijri?       |
| year_death_hijri?       |
| birthplace              |
| death_place             |
| primary_residence       |
| reliability_grade       |   <-- THIQA / SADUQ / DAIF / MATRUK
| reliability_comment     |
| metadata jsonb          |
+-------------------------+
        ▲                       ▲
        │ N:M                    │ N:M
        │                        │
+-------+------------------------+----+
|       hd_narrator_relations          |
+--------------------------------------+
| id (PK)                              |
| narrator_id (FK)                     |
| related_narrator_id (FK)             |
| relation_type                        |  -- TEACHER / STUDENT / FATHER / SON
| confidence                           |  -- HIGH / MEDIUM / LOW
+--------------------------------------+

Reuse:

+-----------------+        +-----------------+
|   authorities   |        |     sources     |
+-----------------+        +-----------------+
| id              |  refed | id              |  refed
| name            |◄──── | sourceType=HADITH| ◄────
| type=SCHOLAR    |        | title           |
| ...             |        | book_id (=Бухари|
+-----------------+        |   book_id)      |
                           +--------+--------+
                                    │
                                    ▼
                          +-------------------+
                          |   hadith_grades   |
                          +-------------------+
                          | source_id (FK)    |
                          | scholar_id (FK→   |
                          |   authorities)    |
                          | grade SAHIH/...   |
                          +-------------------+
```

### 1.3 Detailed table schemas

#### `hd_hadiths` - сам хадис

| Поле | Тип | Note |
|---|---|---|
| `id` | uuid PK | |
| `primary_book_id` | uuid FK→`lib_books.id` | каноническая публикация - часто Бухари |
| `primary_number` | int | номер в primary_book (e.g. 6018) |
| `normalized_matn` | text | normalized arabic for search (без taшkīl) |
| `normalized_matn_tsv` | tsvector GENERATED | PG FTS index |
| `status` | varchar(20) CHECK | `CANONICAL` / `VARIANT` / `WEAK` / `FABRICATED` |
| `source_id` | uuid FK→`sources.id` nullable | мост в existing citation domain |
| `metadata` | jsonb | extensible: subject, narrators_count cached, etc |
| `created_at` | timestamptz default now() | |

Unique constraint: `(primary_book_id, primary_number)` - один хадис в книге раз.

Index: `GIN (normalized_matn_tsv)` для FTS.

#### `hd_narrators` - narrator (rāwī)

| Поле | Тип | Note |
|---|---|---|
| `id` | uuid PK | |
| `authority_id` | uuid FK→`authorities.id` nullable | если narrator также SCHOLAR/AUTHOR в системе |
| `name_ar` | text NOT NULL | основное имя (full) |
| `name_ar_normalized` | text NOT NULL | normalized для search/disambiguation |
| `name_ar_tsv` | tsvector GENERATED | PG FTS |
| `kunya` | varchar(120) | «أبو هريرة» |
| `laqab` | varchar(120) | прозвище |
| `year_birth_hijri` | int | nullable |
| `year_death_hijri` | int | nullable |
| `birthplace` | varchar(120) | |
| `death_place` | varchar(120) | |
| `primary_residence` | varchar(120) | где жил/учил большую часть жизни |
| `reliability_grade` | varchar(20) CHECK | `THIQA` / `SADUQ` / `MAQBUL` / `DAIF` / `MATRUK` / `UNKNOWN` |
| `reliability_comment` | text | |
| `transmitted_count_cached` | int default 0 | denormalized для UX |
| `metadata` | jsonb | |
| `created_at` | timestamptz default now() | |

Index: `GIN (name_ar_tsv)`, `btree (year_death_hijri)`.

#### `hd_sanads` + `hd_sanad_narrators` - цепь

```sql
hd_sanads:
  id           uuid PK
  hadith_id    uuid FK→hd_hadiths.id NOT NULL
  chain_grade  varchar(20) CHECK (SAHIH/HASAN/DAIF/MAUDU/UNKNOWN)
  compiled_by_id  uuid FK→hd_narrators.id  -- кто составитель сборника
  compiled_in_book_id  uuid FK→lib_books.id  -- в какой книге найдено
  primary_chain BOOL DEFAULT false  -- основная цепь у хадиса
  metadata     jsonb
  created_at   timestamptz

  INDEX (hadith_id)
  INDEX (compiled_in_book_id)

hd_sanad_narrators:
  sanad_id     uuid FK→hd_sanads.id  PK part
  position     int  PK part   -- 0 = Пророк ﷺ или ближайший к нему
  narrator_id  uuid FK→hd_narrators.id NOT NULL
  transmission_phrase  varchar(40)  -- «هَدَّثَنَا», «أَخْبَرَنَا», «عَنْ», «سَمِعْتُ»
                                    -- семантика тех самых отличает saheeh/daif

  UNIQUE (sanad_id, position)
  INDEX (narrator_id)  -- для «кто передавал что» query
```

Договорённость по `position`: **0 = Пророк ﷺ (или ближайший к нему,
sahabi)**, далее по возрастанию вниз цепи (1=tabi'i, 2=tabi-tabi'in,
...). Конечный narrator (составитель сборника) - не входит в
`hd_sanad_narrators`, он в `hd_sanads.compiled_by_id`. Это даёт
естественную сортировку «снизу вверх» в UI.

#### `hd_matns` - тексты variations

```sql
hd_matns:
  id                    uuid PK
  hadith_id             uuid FK→hd_hadiths.id NOT NULL
  text_ar               text NOT NULL
  text_ar_normalized    text NOT NULL  -- для diff vs other matns
  text_ru               text           -- переводы добавляются позже
  text_en               text
  source_book_id        uuid FK→lib_books.id  -- где опубликован этот вариант
  printed_number        int             -- номер в этой книге
  page_no               int
  volume                int
  is_primary            bool DEFAULT false  -- основной matn хадиса
  divergence_summary    text             -- generated при ETL: «opener differs»
  metadata              jsonb
  created_at            timestamptz

  INDEX (hadith_id)
  INDEX (source_book_id)
  GIN INDEX on tsvector(text_ar_normalized)
```

«Является ли два matn'а variations одного хадиса» - **решается при
ETL** (rule-based: same isnad core, normalized text similarity > 0.7,
один primary_number в Бухари etc.) Алгоритм - в ETL service, не в БД.

#### `hd_narrator_relations` - сеть учитель/ученик

```sql
hd_narrator_relations:
  id                    uuid PK
  narrator_id           uuid FK→hd_narrators.id NOT NULL
  related_narrator_id   uuid FK→hd_narrators.id NOT NULL
  relation_type         varchar(20)  -- TEACHER / STUDENT / FATHER / SON / COLLEAGUE
  confidence            varchar(10)  -- HIGH / MEDIUM / LOW
  source_book_id        uuid FK→lib_books.id  -- из какой rijāl-литературы взято
  metadata              jsonb

  UNIQUE (narrator_id, related_narrator_id, relation_type)
  INDEX (narrator_id, relation_type)
  CHECK (narrator_id <> related_narrator_id)
```

Phase 4+. Используется для **suggest** «возможные пропущенные звенья
isnad'а» и для построения «школ narrator'ов» (teaching lineage
graph).

### 1.4 Staging tables (mirror shamela pattern)

ETL не пишет в `hd_*` напрямую - идёт через staging для idempotency
и rollback. Pattern - 1:1 с `lib_shamela_*` (см. shamela ETL).

```
hd_stage_hadiths        (raw rows, source-specific shape)
hd_stage_narrators
hd_stage_sanads
hd_stage_matns
hd_stage_import_runs    (run_id, source, started_at, finished_at, status, error_log)
```

Mapper'ы конвертируют stage → domain в одной транзакции на batch.
Stage rows сохраняются для debug/replay (TTL 30 дней через janitor).

### 1.5 Domain records (Java)

```java
public record Hadith(
    UUID id, UUID primaryBookId, Integer primaryNumber,
    String normalizedMatn, HadithStatus status, UUID sourceId,
    String metadata, Instant createdAt) {}

public record Narrator(
    UUID id, UUID authorityId, String nameAr, String nameArNormalized,
    String kunya, String laqab,
    Integer yearBirthHijri, Integer yearDeathHijri,
    String birthplace, String deathPlace, String primaryResidence,
    NarratorReliabilityGrade reliabilityGrade, String reliabilityComment,
    Integer transmittedCountCached, String metadata, Instant createdAt) {}

public record Sanad(
    UUID id, UUID hadithId, HadithGradeValue chainGrade,
    UUID compiledById, UUID compiledInBookId, boolean primaryChain,
    String metadata, Instant createdAt) {}

public record SanadNarrator(
    UUID sanadId, int position, UUID narratorId,
    TransmissionPhrase transmissionPhrase) {}

public record Matn(
    UUID id, UUID hadithId, String textAr, String textArNormalized,
    String textRu, String textEn,
    UUID sourceBookId, Integer printedNumber, Integer pageNo, Integer volume,
    boolean isPrimary, String divergenceSummary,
    String metadata, Instant createdAt) {}
```

Enum-like через `final class XConstants` (mirror AuthorityType
convention из миграции 47):

- `HadithStatus`: CANONICAL / VARIANT / WEAK / FABRICATED
- `NarratorReliabilityGrade`: THIQA / SADUQ / MAQBUL / DAIF /
  MATRUK / UNKNOWN
- `TransmissionPhrase`: HADDATHANA / AKHBARANA / AN / SAMITU /
  UNKNOWN
- `HadithGradeValue` - **reuse existing** (миграция 43).

---

## 2. ETL (data ingestion)

### 2.1 Sources matrix

| Source | License | Format | Coverage | Phase |
|---|---|---|---|---|
| **sunnah.com** API | Public, attribution required | JSON REST, hadith + narrator endpoints | 6 канонических сборников (Кутуб ас-Ситта) + Муватта + Муснад Ахмада | 1 (manual sample), 5 (auto) |
| **islamhouse.com** corpora | Public | Downloadable XML/SQLite | Comprehensive Sunni hadith DB ~1M records | 5 |
| **Shamela** hadith books | Public via shamela-static | Уже умеем парсить (lib_shamela_*) | All hadith books в Shamela | 5 |
| **Hand-curated seed** | own | JSON в `/scripts/hadith-seed.json` | ~10 famous hadiths из Бухари+Муслима | 1 |

**Лицензирование (open question):** sunnah.com позволяет
non-commercial reuse через CC-BY-NC-SA. Для продакшн платформы
**нужно явное письмо** в их team либо переход на islamhouse
(Saudi-licensed permissive). Помечено в Risks §10.

### 2.2 Service layer (mirror shamela)

```
backend/src/main/java/ru/basnukaev/argumentmap/hadith/
  domain/                    Hadith.java, Narrator.java, ...
  repository/                HadithRepository, NarratorRepository, ...
  service/
    HadithService.java       (read API)
    NarratorService.java     (read API)
    SanadService.java        (граф API)
    HadithSearchService.java (FTS)
    import/
      HadithImportService.java       (orchestrator)
      SunnahDotComImportService.java (sunnah.com REST → staging)
      IslamHouseImportService.java   (XML → staging)
      HadithFromShamelaService.java  (lib_shamela_* → staging)
      mapper/
        HadithMapper.java
        NarratorMapper.java     (включая disambiguation logic)
        SanadMapper.java
  web/
    controller/              REST controllers
    dto/                     Request/Response DTOs
    mapper/                  Domain→DTO mappers
  api/                       (Phase 5) внешний REST клиент к sunnah.com
    SunnahApiClient.java
    dto/                     external API DTOs
```

### 2.3 Narrator disambiguation

Главная нетривиальная проблема ETL. Разные narrator'ы могут иметь
одно имя («Мухаммад ибн Исхак» - не один). Подход:

1. **Hash key** = `normalize(name_ar) + year_death_hijri ?:
   primary_residence`. Если в staging уже есть row с тем же hash -
   merge, иначе - new.
2. **Manual review queue** для ambiguity warnings (когда два
   потенциальных matches с разным `year_death_hijri`). Admin UI
   позволяет resolve через выбор.
3. **Authority bridge** - если найден SCHOLAR с тем же name + era
   в `authorities` (миграция 47), `hd_narrators.authority_id`
   автоматически связывается. Иначе NULL (narrator без bio в нашей
   authority системе).

Алгоритм disambiguation - **в `NarratorMapper`** (часть ETL), не в
БД constraint. Это критично потому что Бухари упоминает одного
«Хасан аль-Басри», а Тирмизи может upper-case'ить имя в другом
варианте - merge только по `name_ar_normalized + era`.

### 2.4 Idempotency

Каждый ETL run = `hd_stage_import_runs` row с `run_id` UUID. Все
staging inserts помечаются `run_id`. На re-run того же source -
старые staging cleared transactionally, mapper упрощается. Mirror
shamela `ShamelaImportRun` pattern.

### 2.5 Phase 1 seed scope

Для Phase 1 - **только hand-curated JSON** с 10 хадисами:

- Hadith #1 (Бухари №1, «Поистине дела по намерениям») - chain через
  Умар ибн аль-Хаттаб → Алкама → Мухаммад ат-Тайми → Яхья → Сабит
  → ... → Бухари. **Эталонный пример с пятью narrator'ами и
  matn вариациями в Муслиме/Тирмизи**.
- Hadith #2 (Муслим №6018) - parallel chain.
- Hadith #3-10 - выборка известных с разной длиной isnad'а (от 3
  до 8 narrator'ов).

Seed загружается через `scripts/seed-hadith.sh` mirror
`scripts/seed-mawlid.sh`. Phase 5 заменяет ручной seed на
sunnah.com sync.

---

## 3. Schema migrations

### 3.1 Migration order (Phase 1)

| # | Файл | Content |
|---|---|---|
| 49 | `20260521-49-create-hd-narrators.xml` | hd_narrators + indices |
| 50 | `20260521-50-create-hd-hadiths.xml` | hd_hadiths + FK to lib_books, sources |
| 51 | `20260521-51-create-hd-sanads.xml` | hd_sanads + hd_sanad_narrators |
| 52 | `20260521-52-create-hd-matns.xml` | hd_matns + FTS index |
| 53 | `20260521-53-create-hd-staging.xml` | 5 staging tables |
| 54 | `20260521-54-create-hd-narrator-relations.xml` | Phase 4, опционально early-create |

Все следуют `liquibase-migration` skill conventions:
- Author `Abdula Basnukaev`
- ID format `YYYYMMDD-NN-short-description`
- Регистрируются в `db.changelog-master.xml`
- `<rollback>` секции
- `& → &amp;` escaping в SQL comments

### 3.2 Namespace decision

**Prefix `hd_*` (не отдельная PG schema).** Mirror `lib_*` pattern.
Reasoning:
- Один PG schema → один search_path, не плодим конфигурацию
- Cross-domain joins (с `lib_books`, `authorities`, `sources`)
  естественны без schema-qualified names
- Migration tooling (`liquibase`) не должен переключать schema на
  лету

Альтернатива «отдельный schema `hadith`» отвергнута - mirror
shamela `lib_shamela_*` тоже сидит в `public`.

---

## 4. REST API

### 4.1 Endpoint list

| Method | Path | Phase | Description |
|---|---|---|---|
| GET | `/api/v1/hadith/hadiths` | 1 | search + filter list, paginated |
| GET | `/api/v1/hadith/hadiths/{id}` | 1 | full hadith с sanads + matns + grades |
| GET | `/api/v1/hadith/hadiths/{id}/sanad-graph` | 3 | graph data ready для React Flow |
| GET | `/api/v1/hadith/hadiths/{id}/matns` | 1 | список вариаций |
| GET | `/api/v1/hadith/narrators` | 2 | каталог narrator'ов |
| GET | `/api/v1/hadith/narrators/{id}` | 2 | biography + statistics |
| GET | `/api/v1/hadith/narrators/{id}/transmitted` | 2 | хадисы, передаваемые этим narrator'ом |
| GET | `/api/v1/hadith/narrators/{id}/relations` | 4 | учители/ученики |
| GET | `/api/v1/hadith/collections` | 2 | список сборников (Кутуб ас-Ситта etc) - filter view над lib_books |
| GET | `/api/v1/hadith/collections/{id}/hadiths` | 2 | хадисы в сборнике |
| POST | `/api/v1/hadith/admin/import-runs` | 5 | trigger ETL (ADMIN only) |
| GET | `/api/v1/hadith/admin/import-runs` | 5 | список запусков ETL |
| GET | `/api/v1/hadith/admin/import-runs/{runId}` | 5 | status + error log |

### 4.2 DTO shapes (key endpoints)

#### `GET /api/v1/hadith/hadiths/{id}`

```jsonc
{
  "id": "uuid",
  "primaryBook": {
    "id": "uuid",
    "title": "صحيح البخاري",
    "titleRu": "Сахих аль-Бухари"
  },
  "primaryNumber": 6018,
  "status": "CANONICAL",
  "matns": [
    {
      "id": "uuid",
      "textAr": "...",
      "textRu": null,
      "sourceBook": { "id": "...", "title": "..." },
      "printedNumber": 6018,
      "isPrimary": true,
      "divergenceSummary": null
    },
    {
      "id": "uuid",
      "textAr": "...",  // variation in Муслим
      "sourceBook": { "id": "...", "title": "صحيح مسلم" },
      "printedNumber": 75,
      "isPrimary": false,
      "divergenceSummary": "opener variant + dropped 'إنما'"
    }
  ],
  "sanads": [
    {
      "id": "uuid",
      "chainGrade": "SAHIH",
      "primaryChain": true,
      "compiledBy": { "id": "...", "nameAr": "البخاري", "nameRu": "аль-Бухари" },
      "compiledInBook": { "id": "...", "title": "..." },
      "narrators": [
        { "position": 0, "narrator": { "id": "...", "nameAr": "عمر بن الخطاب", "yearDeathHijri": 23 }, "transmissionPhrase": "AN" },
        { "position": 1, "narrator": { "id": "...", "nameAr": "علقمة", "yearDeathHijri": 62 }, "transmissionPhrase": "HADDATHANA" }
        // ...
      ]
    }
  ],
  "grades": [   // existing hadith_grades joined через sourceId
    { "scholarName": "البخاري", "grade": "SAHIH", "comment": null },
    { "scholarName": "الألباني", "grade": "SAHIH", "comment": null }
  ]
}
```

#### `GET /api/v1/hadith/hadiths/{id}/sanad-graph`

Pre-shaped под React Flow:

```jsonc
{
  "nodes": [
    {
      "id": "narrator-{uuid}",
      "type": "narrator",
      "data": {
        "nameAr": "عمر بن الخطاب",
        "nameRu": "Умар ибн аль-Хаттаб",
        "kunya": "أبو حفص",
        "reliabilityGrade": "THIQA",
        "yearDeathHijri": 23
      }
    },
    // ... все narrator-узлы (один на narrator, не дублировать если разные
    // sanad'ы имеют общего narrator'а)
    {
      "id": "compiler-{uuid}",
      "type": "compiler",
      "data": {
        "nameAr": "البخاري",
        "bookTitle": "صحيح البخاري"
      }
    }
  ],
  "edges": [
    {
      "id": "uuid",
      "source": "narrator-A",
      "target": "narrator-B",
      "data": {
        "sanadId": "uuid",
        "chainGrade": "SAHIH",
        "transmissionPhrase": "HADDATHANA"
      }
    }
    // ...
  ]
}
```

Key insight: если два sanad'а имеют общий начальный narrator (Пророк
ﷺ → Абу Хурайра), это **тот же node**. Backend дедуплицирует. UI
рисует «развилку» одного источника на несколько compiler'ов
автоматически.

#### `GET /api/v1/hadith/hadiths` - search

```jsonc
{
  "q": "إنما الأعمال",          // FTS by text_ar
  "narratorId": "uuid",         // who transmitted
  "collectionId": "uuid",       // primary_book_id filter
  "grade": "SAHIH",             // chain_grade filter (any sanad with this grade)
  "status": "CANONICAL",
  "page": 0, "size": 20
}
```

Возврат - `PagedResponse<HadithSummary>` (mirror PagedResponse
convention из `api-contract.md`).

### 4.3 Permissions

- **All read endpoints** - permitAll в prod profile (guest mode -
  vision §2.5). Hadith data - публичная.
- **POST `/admin/import-runs`** - `assertRole(ADMIN)`.
- **No write endpoints для regular users** - curation only через
  ETL/admin.
- **Audit** - import-runs логируются через AuditLogService
  (`AUDIT_ENTITY_TYPE = HADITH_IMPORT_RUN`).

### 4.4 Связь с `api-contract.md`

Все эндпоинты документируются в `docs/api-contract.md` секции
«Hadith» **сразу при implementation** (per backend CLAUDE.md
rule). `frontend/src/shared/api/types.ts` регенерируется через
`npm run generate-api` после каждой fазы.

---

## 5. Frontend (`src/apps/hadith/`)

### 5.1 Структура

```
frontend/src/apps/hadith/
  pages/
    HadithListPage.tsx          (search + filter)
    HadithDetailPage.tsx        (sanad viz + matns + grades)
    NarratorListPage.tsx        (Phase 2)
    NarratorDetailPage.tsx      (Phase 2)
    CollectionListPage.tsx      (Phase 2 - Кутуб ас-Ситта etc)
    CollectionDetailPage.tsx    (Phase 2 - хадисы в книге)
  components/
    SanadGraph.tsx              (React Flow граф) - Phase 3
    SanadGraphNode.tsx          (custom node renderer)
    SanadGraphEdge.tsx          (custom edge с tooltip)
    NarratorInfoPanel.tsx       (side panel при click на narrator)
    MatnVariationsTable.tsx     (Phase 1 в простой форме, Phase 2 с diff)
    MatnDiff.tsx                (text diff highlight)
    HadithSearchBar.tsx
    HadithFilters.tsx
    NarratorCard.tsx
  hooks/
    useSanadGraph.ts            (fetch + layout)
    useHadithSearch.ts          (debounced search + zustand)
  stores/
    useHadithFiltersStore.ts    (zustand)
  utils/
    sanadLayout.ts              (dagre / ELK preprocessor)
    matnDiff.ts                 (text diff algorithm)
```

### 5.2 Routes

В `App.tsx` router:

```tsx
<Route path="/hadith" element={<HadithListPage />} />
<Route path="/hadith/:hadithId" element={<HadithDetailPage />} />
<Route path="/hadith/narrators" element={<NarratorListPage />} />
<Route path="/hadith/narrators/:narratorId" element={<NarratorDetailPage />} />
<Route path="/hadith/collections" element={<CollectionListPage />} />
<Route path="/hadith/collections/:collectionId" element={<CollectionDetailPage />} />
```

Меню верхнего уровня в `TopNav.tsx` (existing) добавляет
«Хадисы» → `/hadith`. Mirror «Библиотека» link.

### 5.3 Key UI компоненты

#### SanadGraph (Phase 3) - центральная фича

Reuse React Flow stack (already in argument-map app). Layout
algorithm - ELK layered (mirror argument-map's `useElkAutoLayout`).
**Однако** для sanad'а direction = `DOWN` (Пророк ﷺ сверху,
compiler внизу) - не `RIGHT` как в graph аргументации. Это
семантическое отличие, не technical.

Visual style:
- Node = narrator card: name_ar (Scheherazade font), reliability
  badge (THIQA green, DAIF red, ...), year_death_hijri внизу.
- Edge = transmission phrase label («сама'тух», «хаддасана»). Color
  по `chainGrade`: SAHIH green, HASAN yellow, DAIF red, MAUDU dark
  red.
- Hover на node → tooltip с full biography.
- Click на node → side panel `NarratorInfoPanel.tsx`.
- Click на edge → highlight всех sanad'ов содержащих этот шаг.

Performance constraint: sanad'ы до 10 narrator'ов, до 5 параллельных
chains - ~50 nodes max. React Flow + ELK handles easily.

#### MatnVariationsTable + MatnDiff (Phase 2)

Таблица всех matn'ов. При expand row - inline diff между primary
matn и этой variation. Diff algorithm - `diff-match-patch` library
(industry standard, RTL-safe для arabic).

#### NarratorInfoPanel (Phase 2)

Side panel (right-side, mirror citation picker pattern). Показывает:
- Name + kunya + laqab
- Years (birth - death)
- Birthplace → primary_residence → death_place arrow
- Reliability grade + comment
- Statistics: how many hadiths transmitted
- Link to Wikipedia/Shamela bio if `authority_id` exists

### 5.4 RTL и arabic typography

Все arabic text - в Scheherazade New font (existing). Container =
`dir="rtl"` для arabic text blocks, остальное LTR для UI. React Flow
node содержит mixed RTL/LTR - оборачиваем content в `<bdi>` для
safety.

---

## 6. AI assist (Phase 6, optional)

Использует Anthropic Claude API (existing infra из ADR-042 ai-edit).

### 6.1 Use cases

| Запрос | Что делает Claude |
|---|---|
| «Расскажи об этом narrator'е» | Summary из metadata `hd_narrators` + Wikipedia (если есть), output Markdown в side panel |
| «Анализ цепи» | LLM проверяет: есть ли в sanad'е известный mudallis (deceptive narrator)? тахаммул consistency? |
| «Найди похожие matn в других сборниках» | Embedding-based similarity (Phase 6 extension) |

### 6.2 Implementation

- `service/HadithAiAnalyzer.java` - reuse `AnthropicClient`
  (existing).
- Endpoint `POST /api/v1/hadith/narrators/{id}/analyze` →
  Claude prompt template → response cached в `hd_narrators.metadata`.
- Same async pattern как `AiEditService` (state machine
  pending/processing/done/failed).

### 6.3 Graceful degradation

Без `ANTHROPIC_API_KEY` (per `application.yml` config) - feature
просто hidden из UI. Mirror ai-edit (ADR-042) behavior.

---

## 7. Phasing (5-10 сессий estimate)

### Phase 1 - Foundation + barebones REST (1-2 сессии)

- Liquibase migrations 49-53 (hd_hadiths, hd_narrators, hd_sanads,
  hd_matns, staging)
- Domain records + repositories (без Spring Data, чистый JDBC mirror
  existing pattern)
- HadithService.findHadithDetail(id) + NarratorService.findById
- `GET /hadiths/{id}`, `GET /hadiths` (search by text only),
  `GET /narrators/{id}`
- Hand-curated seed - 10 хадисов через `scripts/seed-hadith.sh`
- IT тесты основных endpoints через Testcontainers
- ADR-051 (hadith domain model) committed
- **Acceptance:** curl `/api/v1/hadith/hadiths/{id}` возвращает
  full nested response с sanad + matns + grades. Все 10 seed хадисов
  findable.

### Phase 2 - Frontend list/detail без graph (1-2 сессии)

- `src/apps/hadith/pages/HadithListPage.tsx` - search + filter + pagination
- `HadithDetailPage.tsx` - **табличный** view sanad'ов (не граф),
  matn variations table
- NarratorListPage, NarratorDetailPage
- CollectionListPage, CollectionDetailPage
- Vitest для key UI logic
- npm run generate-api → types.ts updated
- Playwright headless smoke test после commit
- **Acceptance:** Абдула может через UI на `/hadith` найти хадис
  №6018 Бухари, открыть detail, увидеть sanad как ordered list +
  matns в таблице.

### Phase 3 - Sanad graph viz (1-2 сессии)

- `GET /hadiths/{id}/sanad-graph` endpoint - deduplication
  narrator'ов + React Flow shape
- `SanadGraph.tsx` через React Flow + ELK layout (DOWN direction)
- Custom node/edge renderers
- NarratorInfoPanel сбоку
- **Acceptance:** sanad из 10 narrator'ов рендерится менее чем за
  500ms, click на narrator открывает panel, hover показывает
  tooltip. Все 10 seed хадисов имеют рендерящийся граф.

### Phase 4 - Search + filter UX (1 сессия)

- FTS на `normalized_matn_tsv` + `name_ar_tsv`
- Multi-field фильтры на frontend: collection, grade, reliability,
  narrator-includes, year-range
- Поисковая строка с RTL + suggest для narrator'ов
- ADR-054 (matn variation diff) committed если выбрали MatnDiff
- **Acceptance:** запрос «إنما الأعمال» возвращает Бухари №1 и
  Муслим вариацию. Filter «только SAHIH через Бухари» работает.

### Phase 5 - ETL automation (2-3 сессии - **самая объёмная**)

- `SunnahDotComImportService` - REST клиент + staging mapping
- `IslamHouseImportService` - XML парсинг
- `HadithFromShamelaService` - bridge с lib_shamela_*
- `NarratorMapper` с disambiguation (hash + manual review queue)
- Admin UI `/admin/hadith-import` (mirror AdminShamelaPage)
- Idempotency через `hd_stage_import_runs`
- ADR-053 (ETL source matrix) committed
- **Acceptance:** import первой 100 хадисов из sunnah.com bukhari
  endpoint работает, idempotent, narrator disambiguation runs cleanly
  на manually crafted edge cases (3 «Мухаммад ибн Исхак» с разными
  death years).

### Phase 6 - AI assist (1 сессия, optional)

- HadithAiAnalyzer + endpoint
- Side panel в `NarratorInfoPanel` добавляет «AI summary» секцию
- Cached в `hd_narrators.metadata.ai_summary`
- Graceful без `ANTHROPIC_API_KEY`
- **Acceptance:** клик на «AI analysis» в narrator panel возвращает
  meaningful summary в течение 5s.

### Code review checkpoints

- После Phase 1 - mandatory `/superpowers:requesting-code-review`
  (backend CLAUDE.md rule «крупный этап»)
- После Phase 3 - review React Flow code (perf hazards особенно)
- После Phase 5 - review ETL + disambiguation (subtle bugs возможны)

---

## 8. Acceptance criteria - Phase 1 detailed

Phase 1 closes if **ВСЕ** следующие:

1. `./mvnw verify` зелёный с новыми migrations 49-53 +
   IT тестами `HadithControllerIT`, `NarratorControllerIT`.
2. `scripts/seed-hadith.sh` идёт чисто на свежей БД, создаёт 10
   хадисов с sanad'ами и matn'ами.
3. `GET /api/v1/hadith/hadiths/{id}` возвращает example response
   из §4.2 для seed Бухари №1 (5+ narrator'ов в основном sanad'е,
   2+ matn variations).
4. `GET /api/v1/hadith/hadiths?q=إنما` находит хадис №1 (FTS не
   обязательно, можно `ILIKE` Phase 1).
5. ADR-051 (hadith domain) committed в `docs/decisions.md` с
   полными rejected alternatives.
6. `docs/api-contract.md` обновлён со всеми Phase 1 endpoints.
7. `docs/architecture.md` обновлён с разделом «Hadith domain».
8. `docs/glossary.md` обновлён с терминами: rāwī, isnād, matn,
   sanad, mudallis, tahammul, kunya, laqab, thiqa, saduq, matruk.
9. Code review report имеет 0 Critical и 0 Important issues
   unaddressed.

---

## 9. Risks / open questions

### 9.1 Лицензирование hadith corpora

**Risk:** sunnah.com CC-BY-NC-SA не позволяет commercial reuse.
Если платформа когда-либо станет коммерческой - нужен альтернативный
source.

**Митigation:**
- Phase 1 - hand-curated seed (own copyright).
- Phase 5 - использовать islamhouse (более permissive) или
  Shamela (public domain books, мы их уже парсим).
- Attribution прямо в `hd_hadiths.metadata.source_attribution`.
- ADR-053 явно фиксирует лицензию каждого source.

### 9.2 RTL UI complexity

**Risk:** Mixed RTL/LTR в одном UI (русский интерфейс + arabic
text + английские UI controls). React Flow node positioning -
LTR-first.

**Mitigation:**
- Все arabic content в `<bdi dir="rtl">` blocks.
- React Flow node = fixed-size card, RTL text внутри clamped.
- Reuse FontPreference из existing settings - Абдула может
  переключить.

### 9.3 Большие графы sanad

**Risk:** Хадис из Муснад Ахмада может иметь 10+ narrator'ов и
5+ параллельных chains - граф становится visually overwhelming.

**Mitigation:**
- Default - показать **только primary chain** (`primaryChain=true`).
- Toggle «показать все цепи» - lazy expand.
- ELK layered с aggressive node spacing - читаемо до 50 nodes.
- Phase 4 - filter «show только цепи через scholar X».

### 9.4 Narrator disambiguation accuracy

**Risk:** Auto-merge может слить двух разных «Мухаммад ибн Исхак»
в один record - corrupting downstream queries.

**Mitigation:**
- Manual review queue для всех auto-merge confidence < HIGH.
- `hd_narrator_alternatives` table (Phase 5 add) - tracks
  potential split points для post-hoc split.
- Admin UI для split/merge operations.
- IT тесты с deliberately ambiguous fixtures.

### 9.5 Когда reuse vs new для Narrator/Authority?

**Open question резолвится в ADR-051:** мы создаём `hd_narrators` как
separate, **но** мостимся к `authorities.id` через nullable FK для
narrator'ов, которые также SCHOLAR/AUTHOR в системе (например,
Малик ибн Анас - он narrator в isnad И author Муватты И SCHOLAR
оценивающий хадисы).

**Альтернатива** «всё в Authority» (отвергается):
- Засоряет Authority bio полями reliability_grade /
  primary_residence релевантными только narrator-роли.
- Mass narrator import (~10K rows) - не хочется в shared
  authorities table.
- Cross-app coupling: argument-map's CitationPicker не должен
  показывать narrator'ов которые не SCHOLAR.

### 9.6 Когда показывать «вплоть до Пророка ﷺ»?

**Design clarification:** в seed Phase 1 - всегда показываем
полную цепь до Пророка ﷺ. Mursal хадисы (без sahabi) - помечать
visually в graph (gap между position 0 и compiler).

### 9.7 Backward compatibility с existing Source.HADITH

**Solution:** `hd_hadiths.source_id` nullable FK на `sources.id`. При
импорте, если для хадиса уже есть `sources` row с `sourceType=HADITH`
и `bookId + citation` matches - линкуем. HadithGrade продолжает
работать через source_id.

Существующий argument-map citation flow → когда node ссылается
на hadith source, в UI добавляется ссылка «открыть в Hadith
Explorer» (Phase 3+).

### 9.8 Performance: hot endpoints

**Hot endpoint:** `/sanad-graph/{id}` - jSON может быть до 50KB
(50 nodes + edges).

**Mitigation:**
- ETag + If-None-Match для browser cache (sanad не меняется после
  import).
- Backend response cache (Caffeine, 1h TTL).
- Phase 4 - move graph computation to materialized view if
  necessary.

---

## 10. Сводка ADR'ов которые создадутся

| ADR | Когда | О чём |
|---|---|---|
| ADR-051 | Phase 1 close | hadith domain: новые `hd_*` сущности vs reuse Source/Authority. Связь через FK |
| ADR-052 | Phase 3 close | sanad viz stack - React Flow с ELK layered DOWN, custom node/edge renderers |
| ADR-053 | Phase 5 close | ETL source matrix - sunnah.com vs islamhouse vs shamela; лицензирование |
| ADR-054 | Phase 4 close (если matn diff делаем) | matn variation diff - `diff-match-patch` library, normalized vs raw |

Каждый ADR пишется в `docs/decisions.md` **сразу при closing
фазы** (per backend CLAUDE.md rule).

---

## 11. Validation ADR-018 platform pivot

Этот spec **прямо доказывает** rationale из ADR-018:

1. **Library как фундамент** - hadith использует existing `lib_books`
   для collections, `lib_shamela_*` ETL для bulk import. Без library
   нельзя было бы построить hadith explorer.
2. **`authorities` shared across apps** - narrator'ы мостятся к
   `authorities.id` для cross-cutting use case (SCHOLAR оценивает
   хадис И narrator в isnad'е), reuse без дубликации.
3. **One Spring Boot, домашние пакеты** - hadith живёт в
   `ru.basnukaev.argumentmap.hadith.*` без gateway / service-mesh /
   distributed transaction боли. Modular monolith эволюционировал
   красиво.
4. **`apps/*` frontend structure** окупается - hadith app
   добавляется без рефакторинга argument-map и library. Shared
   `src/shared/api/types.ts` automatically включает все endpoints
   из generate-api.
5. **`design-reference/` имел sanad explorer mockup**
   изначально (ADR-018 §11 «изначально в дизайн-референсе уже были
   источники для будущих фич: source-pickers Quran/Hadith/Books,
   sanad explorer, bilingual cards»). **Этот spec implementирует
   именно тот design intent**.

Если бы ADR-018 не приняли - hadith explorer был бы либо отдельной
репой (потеря shared library/authorities/types), либо боltолтой
для single-app argument-map (полный refactor). Pivot окупается.

---

## 12. Workflow notes

- **Phasing implementation - отдельные сессии**, не одна. Phase 1 = одна
  сессия. После Phase 1 - code review + Абдула asynchronous review.
- **Subagents для context conservation** - Phase 5 ETL особенно
  большой, делегировать NarratorMapper disambiguation в subagent.
- **`/frontend-design` skill** перед каждым UI этапом (Phase 2,
  3, 4) - design quality + RTL details.
- **Playwright** после каждого UI commit phase 2/3/4 (per CLAUDE.md
  rule). Headless WSL2. Screenshots в `/tmp/`.
- **`/superpowers:requesting-code-review`** обязательно после
  Phase 1, 3, 5 (см. §7 «Code review checkpoints»).

---

## Acceptance criteria для целого spec (closing condition)

Spec считается successfully closed if:

- Все 6 фаз пройдены и closed.
- ADR-051..ADR-054 committed.
- `docs/api-contract.md` секция «Hadith» полная.
- `docs/architecture.md` секция «Hadith domain» добавлена.
- `docs/glossary.md` пополнен hadith-терминами.
- Roadmap в `docs/roadmap.md` показывает «Hadith Chains Explorer
  [x] завершено» с reference на этот spec.
- В `docs/progress.md` есть session entry для каждой фазы.
- Абдула может через UI открыть `/hadith`, найти хадис, увидеть
  его sanad graph, открыть narrator panel, увидеть matn variations.
  «Like alminasa.ai но интегрировано в платформу» - тест прохожден.
