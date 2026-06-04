# 2026-05-14 - ADR-028 Academic citation metadata - design spec

## Контекст

В проекте argument-map реализована трёхуровневая модель цитирования (ADR-017): `Node → NodeSource → Source → Book`. Текущий computed location на бэке (`NodeSourceRepository.findByNodeIdWithLocation`) возвращает строку вида:

```
تفسير ابن كثير - ط ابن الجوزي, Т.المقدمة стр.3
```

Для исламского `бахс` (научное исследование/разбор) такая сноска **дефектна**. Согласно конвенциям академической традиции, библиографическая ссылка должна содержать **минимум 8 полей**:

1. **полное имя автора** с куньей, насабом, нисбой (а не просто короткое `ابن كثير`)
2. **год смерти автора по хиджре** (для первого упоминания в работе)
3. **полное название книги**
4. **мухаккик (تحقيق)** - редактор тахкика. **Критично:** разные тахкики одной и той же книги имеют разные пагинации, поэтому ссылка `Тафсир Ибн Касира, стр.145` без указания тахкика неоднозначна
5. **издательство**
6. **город (место издания)**
7. **номер издания**
8. **год издания по хиджре + григорианскому**

Сейчас `lib_books` имеет только `title`, `authority_id` (basic Authority с short name), `language`, `description`, `metadata` JSONB. `authorities` имеет `name`, `bio`, `era`, `madhab`, `metadata`. Этого недостаточно.

## Решение - короткий обзор

Расширение схемы по принципу **нормализованного middle path**:

- **Нормализованные справочники** для high-reuse полей: `lib_publishers`, `lib_publication_places`, `lib_muhaqqiqs` (одно издательство = десятки книг)
- **Расширение `authorities`** двумя полями: `full_name`, `death_year_hijri` (`authorities` - уже cross-book entity, естественное место для академического имени автора)
- **Per-book скаляры** в `lib_books`: `edition_number`, `published_year_hijri`, `published_year_gregorian` (не reusable, каждая книга имеет свои)
- **Structured citation response** вместо склеенной строки. Backend отдаёт `CitationDetail` record с raw fields. Frontend рендерит каждое поле в своём визуальном блоке - решает проблему слипания арабского текста с латинскими цифрами и кириллическими пометками типа `изд.`
- **No backward compat**: миграция чистая, у проекта пока нет production'а (см. feedback `no_prod_no_backward_compat` в auto memory). Существующие dev-данные либо переимпортируются, либо получают `null` в новых FK

## Альтернативы (rejected)

**Option A - все 8 полей плоско в `lib_books` как TEXT/INTEGER**
- Преимущество: одна простая миграция, никаких JOIN'ов
- Минус: typo-дубли при импорте 1000+ книг (`Дар Тайба` / `Дар-Тайба` / `دار طيبة`)
- Минус: поиск книг по publisher / city / muhaqqiq невозможен без full-text scan
- Минус: одно издательство editing требует обходить все его книги

**Option B - отдельная `lib_book_editions` 1:N**
- Преимущество: clean architecture work vs edition
- Минус: каскад изменений массивный - `lib_pages.book_id` должен стать `edition_id` (пагинации specific к edition), ETL переделывать, REST API менять, frontend перерабатывать
- Для MVP overkill: shamela импорт даёт **одно** издание per book, multi-edition - сценарий не подтверждён реальным use case
- Future migration path сохранён: при появлении multi-edition use case можно retrofit `lib_books` → `lib_book_editions` + создать `lib_works` parent

**Option C - JSONB `academic_metadata` в `lib_books`**
- Преимущество: минимум schema changes
- Минус: нет query-able индексов на отдельные поля
- Минус: теряем type safety в Java (`Map<String, Object>` или нестед record над JSONB)

## Schema (миграция 24)

Файл `backend/src/main/resources/db/changelog/changes/20260514-24-add-academic-citation-metadata.xml`.

```sql
-- Расширяем authorities полями для академического первого упоминания.
-- full_name - полное имя с куньей/насабом/нисбой; name остаётся для short display.
-- death_year_hijri - для footnote "(т.XXX هـ)" при первом упоминании в работе.
ALTER TABLE authorities
  ADD COLUMN full_name              TEXT,
  ADD COLUMN death_year_hijri       INTEGER;
ALTER TABLE authorities
  ADD CONSTRAINT authorities_death_year_sane
    CHECK (death_year_hijri IS NULL
           OR (death_year_hijri > 0 AND death_year_hijri < 2000));

-- Справочник издательств. UNIQUE name - один publisher = один row,
-- ETL делает findOrCreate по нормализованному имени.
CREATE TABLE lib_publishers (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lib_publishers_name ON lib_publishers(name);

-- Справочник городов публикации. Бейрут / Каир / Эр-Рияд / Дамаск ...
CREATE TABLE lib_publication_places (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lib_publication_places_name ON lib_publication_places(name);

-- Справочник редакторов тахкика. Отдельная таблица а не reuse authorities -
-- мухаккики часто modern editors, не классические учёные. Если придёт case
-- "редактор и автор - одна персона" - retrofit FK на authorities позже.
-- full_name отдельно для short vs full display (как в authorities).
CREATE TABLE lib_muhaqqiqs (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        TEXT NOT NULL UNIQUE,
    full_name   TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lib_muhaqqiqs_name ON lib_muhaqqiqs(name);

-- lib_books расширяется FK на 3 справочника + 3 per-book скаляра.
-- ON DELETE SET NULL по аналогии с ADR-017 (удаление справочника
-- не сносит книгу каскадно).
ALTER TABLE lib_books
  ADD COLUMN muhaqqiq_id              UUID REFERENCES lib_muhaqqiqs(id) ON DELETE SET NULL,
  ADD COLUMN publisher_id             UUID REFERENCES lib_publishers(id) ON DELETE SET NULL,
  ADD COLUMN publication_place_id     UUID REFERENCES lib_publication_places(id) ON DELETE SET NULL,
  ADD COLUMN edition_number           INTEGER,
  ADD COLUMN published_year_hijri     INTEGER,
  ADD COLUMN published_year_gregorian INTEGER;

ALTER TABLE lib_books
  ADD CONSTRAINT lib_books_edition_positive
    CHECK (edition_number IS NULL OR edition_number > 0),
  ADD CONSTRAINT lib_books_hijri_sane
    CHECK (published_year_hijri IS NULL
           OR (published_year_hijri > 0 AND published_year_hijri < 2000)),
  ADD CONSTRAINT lib_books_gregorian_sane
    CHECK (published_year_gregorian IS NULL
           OR (published_year_gregorian > 0 AND published_year_gregorian < 2200));

CREATE INDEX idx_lib_books_muhaqqiq_id          ON lib_books(muhaqqiq_id);
CREATE INDEX idx_lib_books_publisher_id         ON lib_books(publisher_id);
CREATE INDEX idx_lib_books_publication_place_id ON lib_books(publication_place_id);
```

**Rollback**: `DROP TABLE lib_muhaqqiqs / lib_publishers / lib_publication_places` + `ALTER lib_books DROP COLUMN ...` + `ALTER authorities DROP COLUMN ...`.

## Domain (Java)

### Новые records

`backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Publisher.java`
```java
public record Publisher(UUID id, String name, Instant createdAt) { }
```

`backend/src/main/java/ru/basnukaev/argumentmap/library/domain/PublicationPlace.java`
```java
public record PublicationPlace(UUID id, String name, Instant createdAt) { }
```

`backend/src/main/java/ru/basnukaev/argumentmap/library/domain/Muhaqqiq.java`
```java
public record Muhaqqiq(UUID id, String name, String fullName, Instant createdAt) { }
```

### Расширение existing records

`Authority` получает `fullName` + `deathYearHijri`:
```java
public record Authority(
    UUID id, String name, String bio, String era, String madhab,
    String metadata, Instant createdAt,
    String fullName,           // ADR-028
    Integer deathYearHijri     // ADR-028
) { }
```

`Book` получает 3 FK + 3 скаляра:
```java
public record Book(
    UUID id, BookType bookType, String title, UUID authorityId,
    String language, String description, String metadata,
    UUID createdBy, Instant createdAt, Instant updatedAt,
    // ADR-028
    UUID muhaqqiqId,
    UUID publisherId,
    UUID publicationPlaceId,
    Integer editionNumber,
    Integer publishedYearHijri,
    Integer publishedYearGregorian
) { }
```

### Новые repositories

`PublisherRepository`, `PublicationPlaceRepository`, `MuhaqqiqRepository` - симметричный паттерн `BookRepository`:

```java
public interface SimpleNamedRepository<T> {
    T save(T entity);
    Optional<T> findById(UUID id);
    Optional<T> findByName(String name);
    UUID findOrCreate(String name);   // helper для ETL upsert
    List<T> findAll();
}
```

Не вводим abstract base class (Java records не наследуются от abstract'ов чисто); просто 3 параллельных JDBC repository с тем же набором методов. `findOrCreate(name)`:
1. `findByName(name)` → если found, return id
2. иначе `save(new T(UUID.randomUUID(), name, null, now()))` → return new id

### Расширение existing repositories

- `BookRepository.COLUMNS` расширяется до 16 полей (старые 10 + новые 6)
- `BookRepository.ROW_MAPPER` маппит новые поля
- `BookRepository.save` параметрический список расширяется
- `AuthorityRepository.COLUMNS` + `ROW_MAPPER` + `save` - аналогично, +2 поля

### Новый CitationDetail record

`backend/src/main/java/ru/basnukaev/argumentmap/domain/CitationDetail.java`:

```java
public record CitationDetail(
    // автор
    UUID authorityId,
    String authorityName,
    String authorFullName,
    Integer authorDeathYearHijri,

    // книга
    UUID bookId,
    String bookTitle,
    String bookLanguage,

    // тахкик
    UUID muhaqqiqId,
    String muhaqqiqName,
    String muhaqqiqFullName,

    // издание
    UUID publisherId,
    String publisherName,
    UUID publicationPlaceId,
    String publicationPlaceName,
    Integer editionNumber,
    Integer publishedYearHijri,
    Integer publishedYearGregorian,

    // локация в книге (TEXT mode)
    UUID pageId,
    String part,
    String printedPage,
    Integer pageNumber,
    Integer rangeStart,
    Integer rangeEnd,

    // pdf альтернатива
    UUID pdfFileId,
    Integer pdfPageNumber,
    String pdfBbox,

    // region (скан) альтернатива
    UUID imageRegionId
) { }
```

Любое из вложенных полей может быть `null` - frontend проверяет каждое и пропускает соответствующий блок при рендере.

### Обновление NodeSourceWithLocation

```java
// БЫЛО
public record NodeSourceWithLocation(NodeSource ns, String computedLocation, UUID bookId) { }

// СТАЛО
public record NodeSourceWithLocation(NodeSource ns, CitationDetail citation) { }
```

`citation` содержит и `bookId`, и (вместо `computedLocation`) все raw fields. **No CASE / concat_ws в SQL** - просто LEFT JOIN на 8 таблиц, маппинг row → CitationDetail в Java.

## SQL новый JOIN

`NodeSourceRepository.findByNodeIdWithLocation`:

```sql
SELECT
  ns.node_id, ns.source_id, ns.quote, ns.context, ns.location,
  ns.page_id, ns.range_start, ns.range_end,
  ns.pdf_file_id, ns.pdf_page_number, ns.pdf_bbox,
  ns.image_region_id, ns.created_at,
  -- source.book_id
  s.book_id AS src_book_id,
  -- authority (автор)
  a.id AS authority_id,
  a.name AS authority_name,
  a.full_name AS author_full_name,
  a.death_year_hijri AS author_death_year_hijri,
  -- book
  b.title AS book_title,
  b.language AS book_language,
  b.edition_number,
  b.published_year_hijri,
  b.published_year_gregorian,
  -- muhaqqiq
  mh.id AS muhaqqiq_id,
  mh.name AS muhaqqiq_name,
  mh.full_name AS muhaqqiq_full_name,
  -- publisher + place
  pub.id AS publisher_id,
  pub.name AS publisher_name,
  pl.id AS publication_place_id,
  pl.name AS publication_place_name,
  -- page (TEXT mode)
  p.part,
  p.printed_page,
  p.page_number,
  -- region page (REGION mode - подтягиваем page координат скана)
  p2.printed_page AS region_printed_page,
  p2.page_number AS region_page_number
FROM node_sources ns
LEFT JOIN sources s                   ON s.id = ns.source_id
LEFT JOIN lib_books b                 ON b.id = s.book_id
LEFT JOIN authorities a               ON a.id = b.authority_id
LEFT JOIN lib_muhaqqiqs mh            ON mh.id = b.muhaqqiq_id
LEFT JOIN lib_publishers pub          ON pub.id = b.publisher_id
LEFT JOIN lib_publication_places pl   ON pl.id = b.publication_place_id
LEFT JOIN lib_pages p                 ON p.id = ns.page_id
LEFT JOIN lib_image_regions ir        ON ir.id = ns.image_region_id
LEFT JOIN lib_pages p2                ON p2.id = ir.page_id
WHERE ns.node_id = ?
ORDER BY ns.created_at
```

9 LEFT JOIN (sources → lib_books → authorities → lib_muhaqqiqs → lib_publishers → lib_publication_places, плюс lib_pages для TEXT, lib_image_regions + второй lib_pages для REGION). Все на indexed FK. Performance ожидание: 1-50 citations per node, query latency < 5 ms.

Маппинг row → `CitationDetail` в Java: один `RowMapper`, выбирает все колонки, заворачивает в record. Для REGION mode маппинг `printedPage` берёт `region_printed_page` поверх `printed_page` (которая будет NULL).

## DTO (REST API)

### Изменение NodeSourceResponse (или новый CitationResponse)

Текущий ответ `GET /api/v1/nodes/{id}/sources` отдаёт массив объектов с computed location string. Меняется формат на:

```json
{
  "nodeId": "...",
  "sourceId": "...",
  "quote": "...",
  "context": "...",
  "citation": {
    "authority": {
      "id": "...",
      "name": "ابن كثير",
      "fullName": "إسماعيل بن عمر بن كثير الدمشقي",
      "deathYearHijri": 774
    },
    "book": {
      "id": "...",
      "title": "تفسير القرآن العظيم",
      "language": "ar",
      "editionNumber": 2,
      "publishedYearHijri": 1420,
      "publishedYearGregorian": 1999
    },
    "muhaqqiq": {
      "id": "...",
      "name": "السلامة",
      "fullName": "سامي بن محمد السلامة"
    },
    "publisher": {
      "id": "...",
      "name": "Дар Тайба"
    },
    "publicationPlace": {
      "id": "...",
      "name": "Эр-Рияд"
    },
    "location": {
      "pageId": "...",
      "part": "1",
      "printedPage": "145",
      "pageNumber": 145,
      "rangeStart": 234,
      "rangeEnd": 287
    },
    "pdf": null,
    "region": null
  }
}
```

Each nested object - либо filled, либо `null` целиком (если соответствующего FK нет). Это даёт фронту простую проверку `citation.muhaqqiq != null` для рендеринга блока «тахкик».

DTO records в `backend/src/main/java/ru/basnukaev/argumentmap/web/dto/`:

```java
public record CitationResponse(
    AuthorityCitationRef authority,
    BookCitationRef book,
    MuhaqqiqRef muhaqqiq,
    PublisherRef publisher,
    PublicationPlaceRef publicationPlace,
    LocationRef location,
    PdfRef pdf,
    RegionRef region
) { }

public record AuthorityCitationRef(UUID id, String name, String fullName, Integer deathYearHijri) { }
public record BookCitationRef(UUID id, String title, String language, Integer editionNumber, Integer publishedYearHijri, Integer publishedYearGregorian) { }
public record MuhaqqiqRef(UUID id, String name, String fullName) { }
public record PublisherRef(UUID id, String name) { }
public record PublicationPlaceRef(UUID id, String name) { }
public record LocationRef(UUID pageId, String part, String printedPage, Integer pageNumber, Integer rangeStart, Integer rangeEnd) { }
public record PdfRef(UUID fileId, Integer pageNumber, String bbox) { }
public record RegionRef(UUID id, Integer printedPage, Integer pageNumber) { }
```

DTO mapper `CitationDetail → CitationResponse`: создаёт nested ref если соответствующий ID не null, иначе nested = null. **No fallback'и на короткий текст**.

## ETL импорт (Этап 20.c в отдельной сессии)

В текущей сессии `ShamelaToLibraryMapper.mapBook` пишет **NULL** в новые FK - existing flow продолжает работать, new academic fields пустые. Парсер заполнения - **отдельная задача 20.c** (regex для извлечения из raw `bibliography` shamela-страницы).

## Testing strategy

### Уровень repository (3 новых repository IT)

- `PublisherRepositoryIT`:
  - save → findById round-trip
  - findByName когда есть match
  - findByName когда нет match → `Optional.empty()`
  - findOrCreate существующего → возвращает existing id (no duplicate)
  - findOrCreate нового → создаёт row + возвращает new id
  - UNIQUE constraint violation на duplicate save

(симметрично для `PublicationPlaceRepositoryIT` и `MuhaqqiqRepositoryIT`)

### Уровень repository (расширение existing)

- `AuthorityRepositoryIT`:
  - save с `fullName` + `deathYearHijri` → findById round-trip
  - CHECK violation: `death_year_hijri = 0` → DataIntegrityViolationException
  - save с NULL в новых полях → no errors

- `BookRepositoryIT`:
  - save с full academic data (3 FK + 3 scalars) → findById round-trip
  - save с partial (только publisher_id, остальное NULL)
  - save с all NULL (минимальная книга)
  - CHECK violations: edition_number = 0, year_hijri = 2500, year_gregorian = 2500

### NodeSourceRepository computed citation

- `NodeSourceRepositoryIT`:
  - `findByNodeIdWithLocation` для citation с full academic data → CitationDetail все поля заполнены
  - `findByNodeIdWithLocation` для citation с partial data (book без muhaqqiq) → muhaqqiq = null, остальное заполнено
  - `findByNodeIdWithLocation` для citation без book связи (Source без bookId) → book = null, authority = null, location = null
  - `findByNodeIdWithLocation` для PDF citation → pdf filled, page = null, region = null
  - `findByNodeIdWithLocation` для REGION citation → region filled, page = null

Объём: ~15-20 новых IT через Testcontainers (Postgres).

## Документация

Обновляется в same commit:

- `docs/decisions.md` - **ADR-028** новый раздел с контекстом, решением, альтернативами (А/Б/В), последствиями
- `docs/architecture.md` - раздел Library обновлён: Book + Authority описание расширено, 3 новых справочника, CitationDetail упомянут
- `docs/api-contract.md` - `GET /api/v1/nodes/{id}/sources` - response format меняется на nested citation, добавить пример + строку в "История изменений"
- `docs/glossary.md` - новые термины: **мухаккик (تحقيق)**, **тахкик**, **edition** (издание), **хиджра** (исламский календарь), **полное имя автора** (кунья/насаб/нисба)
- `docs/roadmap.md` - проставить `[x]` на подэтапах 20.a (ADR + миграция + domain + computed location)

## Out of scope текущей сессии

- **20.c shamela bibliography parser** - regex для извлечения muhaqqiq/publisher/place из raw `bibliography` text shamela-страницы. Регламент: regex pull → upsert через `*Repository.findOrCreate(name)` → set FK на book. Объём: ~0.5 сессии
- **20.d Admin BookEditModal** - frontend UI для ручного дозаполнения academic fields после импорта (когда parser не справился). Объём: ~1 сессия
- **20.e AddSourceModal расширенная форма** - при manual entry для `sourceType=BOOK` запросить полные поля. Объём: ~0.5 сессии
- **20.f Frontend `<LibraryCite>` блочный рендер** - после regenerate-api переписать рендеринг citation на блоки. **Frontend сломается в этой сессии** при regenerate-api (поле `computedLocation` исчезнет в favor `citation`) - починим в 20.f следующей сессией

## Последствия

**Положительные:**
- Citation для бахс качества: 8-полевая сноска по конвенции исламской академической традиции
- Data quality через справочники: нет typo-дублей publisher/city/muhaqqiq
- Поиск книг по publisher / city / muhaqqiq возможен (`WHERE publisher_id = ?`)
- Frontend получает structured data - визуально читаемые блоки вместо склеенной строки
- Authority enrichment бенефит cross-book (расширенные поля видны в любой книге автора)

**Отрицательные:**
- 8 JOIN в caption query (приемлемо для 1-50 citations per node, all on indexed FK)
- Migration требует переимпорта существующих shamela-книг для заполнения новых полей (acceptable - dev only, no prod)
- Frontend `<LibraryCite>` ломается в текущей сессии при regenerate-api - чинится в 20.f

**Future migration path к Option B (lib_book_editions):**
Если придёт реальный multi-edition use case - retrofit:
1. Rename `lib_books` → `lib_book_editions`
2. Создать `lib_works` parent (id, title, authority_id, language, description)
3. `lib_book_editions` получает `work_id UUID NOT NULL`, `title` уходит в `lib_works`, academic fields остаются
4. `lib_pages.book_id` → `lib_pages.edition_id` (FK rename)
5. ETL и REST API обновляются один раз

Все academic fields сохраняются - переезжают вместе с rename'ом.

## Estimated effort

- migration 24: ~100 строк XML (5 ALTER/CREATE с indexes + constraints)
- 3 простых record + 3 repository: ~250 строк Java
- Authority/Book records expansion + ROW_MAPPER: ~80 строк
- CitationDetail + 8 DTO refs: ~120 строк
- NodeSourceRepository SQL переписан: ~50 строк
- DTO mappers: ~100 строк
- ServiceLayer mapping CitationDetail → CitationResponse: ~80 строк
- IT: ~600-800 строк (15-20 тестов)
- Docs: ADR-028 (~300 строк) + 4 updates
- 3-4 atomic коммита: миграция / domain + repositories / SQL+DTO+маппер / docs

**~1 сессия** на 20.a + spec-described scope.

## Открытые вопросы (для следующих сессий)

- **20.c parser**: regex или LLM extraction? Bibliography в shamela часто свободно-форматный текст
- **Authority deduplication**: при импорте shamela у нас 1 author = 1 row, но same author разные spelling в разных книгах. Future ADR-029 если станет проблемой
- **Authority.full_name backfill**: можно подтянуть из shamela master `author.name` (там часто полное имя) → backfill в 20.c вместе с parser
