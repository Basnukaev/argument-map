# Дизайн Этапа 14 - Library MVP (доменная модель и REST)

**Дата:** 2026-05-08
**Связанные ADR:** ADR-018 (платформенный pivot), ADR-019 (вводится в этом этапе - library как доменный пакет)
**Связанные документы:** `docs/vision.md`, `docs/architecture-platform.md`, `docs/roadmap.md` (Этапы 14-18)

## Цель

Заложить фундамент платформы - доменную модель библиотеки и базовые
REST-эндпоинты. После этапа в системе можно создавать книги вручную
через API (POST), читать содержимое постранично, удалять. Это **не
читалка**, а лишь backend-контракт - frontend-читалка появится на
Этапе 18.

Этап 14 разбивается на 4 подэтапа (14.a-d), каждый закрывается своим
коммитом.

## Принципы

1. **Сразу новая структура пакетов** - код пишется в
   `ru.basnukaev.argumentmap.library.{domain,repository,service,web}`.
   Старый `argumentmap`-код плоско в корне пакета мигрируется
   постепенно (strangler), не в этом этапе
2. **Имена таблиц с префиксом `lib_`** - согласно
   `architecture-platform.md`. Существующие `topics`/`nodes`/`edges`
   переименуем `arg_*` отдельной миграцией не сейчас
3. **Универсальный `Book` с дискриминатором `book_type`** - один
   шаблон для Корана / хадис-сборников / классических трудов /
   статей / манускриптов. Тип-специфичные поля - в jsonb `metadata`.
   Это симметрично тому как устроен существующий `Source` (ADR-002)
4. **MVP без content - только структура** - в подэтапах 14.a-c
   фокус на схему таблиц и CRUD. Парсинг shamela / PDF / OCR -
   следующие этапы (15/16/17). На этапе 14 контент книги добавляется
   только через POST endpoint руками
5. **Изоляция домена** - cross-domain вызов только через service-фасад
   (`argumentmap` → `library.BookService`), не лезть в чужой
   `library.repository.*`

## Доменные сущности

### Иерархия

```
Book
├── Chapter (опционально с parent_chapter_id для иерархии разделов)
└── Page
    └── ImageRegion (для image-сканов)
```

`Page` принадлежит **одной** книге обязательно, но `chapter_id`
опционален (preface, индекс, страницы вне глав).

### Book - книга

| Поле | Тип Java | Тип SQL | Nullable | Комментарий |
|---|---|---|---|---|
| `id` | `UUID` | `UUID PK` | no | `uuid_generate_v4()` |
| `bookType` | `BookType` | `TEXT CHECK` | no | `QURAN/HADITH_COLLECTION/BOOK/ARTICLE/MANUSCRIPT` |
| `title` | `String` | `TEXT` | no | заголовок труда |
| `authorityId` | `UUID` | `UUID FK→authorities` | yes | автор/составитель. `ON DELETE SET NULL` (как в Source ADR-017). NULL для Корана |
| `language` | `String` | `TEXT` | no | свободная строка - `'ar'`, `'ru'`, `'ar+ru'`, BCP-47 без CHECK на MVP |
| `description` | `String` | `TEXT` | yes | короткое описание для UI |
| `metadata` | `String (JSON)` | `JSONB` | yes | тип-специфичные поля. GIN-индекс |
| `createdBy` | `UUID` | `UUID FK→users` | no | кто загрузил/импортировал |
| `createdAt` | `Instant` | `TIMESTAMPTZ` | no | `now()` default |
| `updatedAt` | `Instant` | `TIMESTAMPTZ` | no | обновляется при правке |

Индексы:
- `idx_lib_books_book_type ON lib_books(book_type)` - фильтр по типу
- `idx_lib_books_authority_id ON lib_books(authority_id)` - "все труды учёного X"
- `idx_lib_books_metadata_gin ON lib_books USING GIN(metadata)` - произвольные ключи в jsonb

### Chapter - глава или раздел

Иерархия через `parent_chapter_id` self-FK. Например для shamela:
«том 1» → «книга об омовении» → «глава 3».

| Поле | Тип Java | Тип SQL | Nullable | Комментарий |
|---|---|---|---|---|
| `id` | `UUID` | `UUID PK` | no | |
| `bookId` | `UUID` | `UUID FK→lib_books` | no | `ON DELETE CASCADE` |
| `parentChapterId` | `UUID` | `UUID FK→lib_chapters` | yes | self-FK, `ON DELETE CASCADE` |
| `title` | `String` | `TEXT` | no | |
| `orderIndex` | `int` | `INTEGER` | no | порядок внутри родителя |
| `createdAt` | `Instant` | `TIMESTAMPTZ` | no | |

Индексы:
- `idx_lib_chapters_book_id ON lib_chapters(book_id)`
- `idx_lib_chapters_parent ON lib_chapters(parent_chapter_id)` - выбрать
  поддерево

### Page - страница

| Поле | Тип Java | Тип SQL | Nullable | Комментарий |
|---|---|---|---|---|
| `id` | `UUID` | `UUID PK` | no | |
| `bookId` | `UUID` | `UUID FK→lib_books` | no | `ON DELETE CASCADE` |
| `chapterId` | `UUID` | `UUID FK→lib_chapters` | yes | `ON DELETE SET NULL` - страница вне главы возможна |
| `pageNumber` | `int` | `INTEGER` | no | физический номер. `> 0` |
| `textContent` | `String` | `TEXT` | yes | извлечённый текст. NULL если только image без OCR |
| `imageUrl` | `String` | `TEXT` | yes | URL/path к скану. NULL если только текстовая |
| `createdAt` | `Instant` | `TIMESTAMPTZ` | no | |
| `updatedAt` | `Instant` | `TIMESTAMPTZ` | no | |

CHECK constraint - `text_content IS NOT NULL OR image_url IS NOT NULL`
(страница не может быть пустой)

Индексы:
- `idx_lib_pages_book_page UNIQUE(book_id, page_number)` - "одна
  страница номер 5 в книге"
- `idx_lib_pages_chapter ON lib_pages(chapter_id)` - все страницы
  главы

### ImageRegion - область на скане

Координаты **нормализованные** (0..1), не пиксельные. Это убирает
зависимость от `image_dpi` и позволяет один и тот же регион
рендерить на разных разрешениях.

| Поле | Тип Java | Тип SQL | Nullable | Комментарий |
|---|---|---|---|---|
| `id` | `UUID` | `UUID PK` | no | |
| `pageId` | `UUID` | `UUID FK→lib_pages` | no | `ON DELETE CASCADE` |
| `x` | `double` | `DOUBLE PRECISION` | no | левый-верхний угол, 0..1 |
| `y` | `double` | `DOUBLE PRECISION` | no | 0..1 |
| `width` | `double` | `DOUBLE PRECISION` | no | 0..1, > 0 |
| `height` | `double` | `DOUBLE PRECISION` | no | 0..1, > 0 |
| `extractedText` | `String` | `TEXT` | yes | текст под регионом из OCR или ручного ввода |
| `createdAt` | `Instant` | `TIMESTAMPTZ` | no | |

CHECK constraint `width > 0 AND height > 0 AND x >= 0 AND y >= 0
AND x + width <= 1 AND y + height <= 1` - корректность нормализованного
прямоугольника.

Индекс `idx_lib_image_regions_page_id ON lib_image_regions(page_id)`.

## Liquibase миграция 16

Файл `20260508-16-create-library-tables.xml`. Один changeset, в
котором создаются все 4 таблицы. Это допустимо для логически
связанной группы (как миграция 11 делала revisions одним changeset'ом).

В `db.changelog-master.xml` добавляется `<include>` нового файла.

```sql
-- (1) lib_books
CREATE TABLE lib_books (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    book_type     TEXT NOT NULL
                  CHECK (book_type IN ('QURAN','HADITH_COLLECTION','BOOK','ARTICLE','MANUSCRIPT')),
    title         TEXT NOT NULL,
    authority_id  UUID REFERENCES authorities(id) ON DELETE SET NULL,
    language      TEXT NOT NULL,
    description   TEXT,
    metadata      JSONB,
    created_by    UUID NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lib_books_book_type     ON lib_books(book_type);
CREATE INDEX idx_lib_books_authority_id  ON lib_books(authority_id);
CREATE INDEX idx_lib_books_metadata_gin  ON lib_books USING GIN (metadata);

-- (2) lib_chapters
CREATE TABLE lib_chapters (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    book_id             UUID NOT NULL REFERENCES lib_books(id) ON DELETE CASCADE,
    parent_chapter_id   UUID REFERENCES lib_chapters(id) ON DELETE CASCADE,
    title               TEXT NOT NULL,
    order_index         INTEGER NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lib_chapters_book_id  ON lib_chapters(book_id);
CREATE INDEX idx_lib_chapters_parent   ON lib_chapters(parent_chapter_id);

-- (3) lib_pages
CREATE TABLE lib_pages (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    book_id       UUID NOT NULL REFERENCES lib_books(id) ON DELETE CASCADE,
    chapter_id    UUID REFERENCES lib_chapters(id) ON DELETE SET NULL,
    page_number   INTEGER NOT NULL CHECK (page_number > 0),
    text_content  TEXT,
    image_url     TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT lib_pages_content_present CHECK (text_content IS NOT NULL OR image_url IS NOT NULL)
);
CREATE UNIQUE INDEX idx_lib_pages_book_page  ON lib_pages(book_id, page_number);
CREATE INDEX        idx_lib_pages_chapter    ON lib_pages(chapter_id);

-- (4) lib_image_regions
CREATE TABLE lib_image_regions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    page_id         UUID NOT NULL REFERENCES lib_pages(id) ON DELETE CASCADE,
    x               DOUBLE PRECISION NOT NULL,
    y               DOUBLE PRECISION NOT NULL,
    width           DOUBLE PRECISION NOT NULL,
    height          DOUBLE PRECISION NOT NULL,
    extracted_text  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT lib_image_regions_bounds CHECK (
        width > 0 AND height > 0
        AND x >= 0 AND y >= 0
        AND x + width <= 1 AND y + height <= 1
    )
);
CREATE INDEX idx_lib_image_regions_page_id  ON lib_image_regions(page_id);
```

Rollback - `DROP TABLE lib_image_regions; DROP TABLE lib_pages;
DROP TABLE lib_chapters; DROP TABLE lib_books;` (обратный порядок
из-за FK).

## Структура пакетов

```
ru.basnukaev.argumentmap/
├── library/                              # новый доменный пакет
│   ├── domain/
│   │   ├── Book.java                    # record
│   │   ├── BookType.java                # enum
│   │   ├── Chapter.java                 # record
│   │   ├── Page.java                    # record
│   │   └── ImageRegion.java             # record
│   ├── repository/
│   │   ├── BookRepository.java
│   │   ├── ChapterRepository.java
│   │   ├── PageRepository.java
│   │   └── ImageRegionRepository.java
│   ├── service/
│   │   └── BookService.java             # 14.c, объединяет все CRUD
│   └── web/
│       ├── controller/
│       │   └── BookController.java
│       ├── dto/
│       │   ├── CreateBookRequest.java
│       │   ├── BookResponse.java
│       │   ├── BookSummary.java
│       │   ├── BookDetailResponse.java
│       │   ├── ChapterResponse.java
│       │   ├── PageSummary.java
│       │   ├── PageResponse.java
│       │   └── ImageRegionResponse.java
│       └── mapper/
│           └── LibraryMapper.java        # все мапперы в одном
└── exception/                            # дополняем существующий пакет
    └── BookNotFoundException.java
```

`exception/BookNotFoundException` лежит **в общем пакете**
`exception/`, не в library-локальном - это симметрично существующим
`TopicNotFoundException`/`NodeNotFoundException`. Глобальный
`@ControllerAdvice` обрабатывает их единообразно.

## REST API

Базовый префикс - `/api/v1/library`. Заголовок `X-User-Id` (как
везде, через `@CurrentUser`).

### Books

#### `POST /api/v1/library/books` - создать книгу

Request:
```json
{
  "bookType": "BOOK",
  "title": "Маджму' аль-Фатава",
  "authorityId": "uuid-of-ibn-taymiyya",
  "language": "ar",
  "description": "37-томный сборник фетв и трактатов",
  "metadata": {
    "volumes": 37,
    "shamelaUrl": "https://shamela.ws/book/123"
  }
}
```

Validation:
- `bookType` required, ∈ enum
- `title` required, non-blank, max 500 char
- `authorityId` optional, если задан - должна существовать запись в
  `authorities` (валидация в `BookService` → 422 `invalid-book` если не найдена)
- `language` required, non-blank, max 32 char
- `description` optional, max 5000 char
- `metadata` optional, валидный JSON (Jackson отклонит невалидный
  JSON автоматически в DTO)

Response 201 + body `BookResponse` со всеми полями + Location
header.

#### `GET /api/v1/library/books?q={search}&type={bookType}` - список

Query params:
- `q` optional - поиск по title (ILIKE, как в SourceRepository)
- `type` optional - фильтр по `bookType`

Response 200 - array `BookSummary[]` (id, bookType, title,
authorityId, language, createdAt). Без полного description и
metadata, чтобы list был компактным.

Pagination не делаем на MVP (`Этап 5` бэклог - то же что для
`/sources`).

#### `GET /api/v1/library/books/{id}` - книга со списком глав

Response 200 - `BookDetailResponse`:
```json
{
  "id": "...",
  "bookType": "BOOK",
  "title": "...",
  "authorityId": "...",
  "language": "ar",
  "description": "...",
  "metadata": { ... },
  "createdBy": "...",
  "createdAt": "...",
  "updatedAt": "...",
  "chapters": [
    {
      "id": "...",
      "title": "Том 1",
      "orderIndex": 0,
      "parentChapterId": null,
      "children": [
        { "id": "...", "title": "Книга об омовении", "orderIndex": 0,
          "parentChapterId": "...", "children": [] }
      ]
    }
  ]
}
```

Дерево собирается на сервисе из плоского `chapterRepository.findByBookId`
+ группировки по `parentChapterId`. Для MVP это OK (chapters в типичной
книге - десятки-сотни узлов).

404 → `book-not-found`.

#### `DELETE /api/v1/library/books/{id}` - удалить книгу

Каскадное удаление через FK (chapters → pages → image_regions).
Response 204. 404 если не найдена.

### Pages

#### `GET /api/v1/library/books/{bookId}/pages?from={N}&to={M}` - постраничный список

Query params:
- `from` optional, default 1
- `to` optional, default `from + 49` (50 страниц за раз)

Response 200 - `PageSummary[]` (id, pageNumber, chapterId, has-text/
has-image флаги). **Без** `textContent` и `imageUrl` - они тяжёлые,
запрашиваются по одной странице через GET pages/{id}.

404 если книга не найдена.

#### `GET /api/v1/library/pages/{id}` - конкретная страница со всем контентом

Не вложенный путь, потому что page уже однозначен по id. Симметрично
тому как `/api/v1/nodes/{id}` (без topic в пути) сделано в текущем
API.

Response 200 - `PageResponse`:
```json
{
  "id": "...",
  "bookId": "...",
  "chapterId": "...",
  "pageNumber": 12,
  "textContent": "...",
  "imageUrl": "...",
  "imageRegions": [
    { "id": "...", "x": 0.1, "y": 0.2, "width": 0.5, "height": 0.3,
      "extractedText": "..." }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

404 → `page-not-found`.

### Что **не** делаем в Этапе 14 (откладываем)

- POST /pages, POST /chapters - страницы и главы создаются только в
  составе книги (через will-be-implemented import endpoints из
  Этапов 15-17). На MVP можно вручную через SQL-fixture в тестах
- Update endpoints (PUT/PATCH) для books/chapters/pages - вернёмся
  когда понадобится. CRUD без update - валидно для MVP
- Image upload - приходит на Этапе 17 (multipart/form-data)
- ImageRegion API - приходит вместе с image upload на Этапе 17

## Сервис

### `BookService`

Один сервис на все операции library-домена в Этапе 14. Если разрастётся
- разделим (PageService, ChapterService).

```java
@Service
public class BookService {
    Book createBook(CreateBookCommand cmd, UUID currentUserId);
    List<Book> listBooks(String query, BookType filter);
    BookDetail getBookWithChapters(UUID bookId);
    void deleteBook(UUID bookId);
    List<Page> listPages(UUID bookId, int from, int to);
    PageDetail getPage(UUID pageId);
}
```

Записи `*Detail` - доменные DTO (record-композиции `Book + chapters`,
`Page + regions`), не web-DTO. Они переводятся в web-DTO мапперами.

`@Transactional` на методах изменяющих данные. `@Transactional(readOnly
= true)` на чтениях.

Валидация при `createBook`:
- если `authorityId` задан - вызвать `authorityRepository.findById`,
  если пусто - бросить `InvalidBookException` (новый, в `exception/`)
  → 422 `invalid-book`

### Cross-domain зависимости

`BookService` зависит от `AuthorityRepository` (валидация
`authorityId`) - это **уже существующий** транс-доменный
репозиторий из `shared/`-будущего пакета. Для MVP это норма, при
выделении `shared/` он переедет.

## Тесты

### Repository IT (4 файла)

`BookRepositoryIT`, `ChapterRepositoryIT`, `PageRepositoryIT`,
`ImageRegionRepositoryIT`. Паттерн как в `SourceRepositoryIT`:
- `@SpringBootTest` + Testcontainers (через `@ServiceConnection` /
  существующая `TestcontainersConfiguration`)
- save / findById / findByXxx / deleteById
- проверка cascade-delete: удалить Book → проверить что Chapter/Page
  удалились
- проверка `ON DELETE SET NULL` для `authority_id` и `chapter_id`

### Service tests

`BookServiceIT` (через Testcontainers, потому что валидация
`authorityId` требует реальной таблицы):
- create с valid authority → ОК
- create с invalid authority → `InvalidBookException`
- create без authority (Коран-сценарий) → ОК
- listBooks с фильтрами
- getBookWithChapters с двухуровневой иерархией → дерево корректно
- deleteBook каскадирует

### Controller IT (`BookControllerIT`)

Через `MockMvc` + Testcontainers:
- POST /books happy path → 201, body, Location
- POST /books invalid bookType → 400 (Bean Validation)
- POST /books invalid authorityId → 422 invalid-book
- GET /books?q=tay → фильтр по title
- GET /books?type=QURAN → фильтр по типу
- GET /books/{id} happy path → дерево chapters
- GET /books/{id} 404
- DELETE /books/{id} → 204 + проверка что getBook → 404
- GET /books/{bookId}/pages with from/to
- GET /pages/{id} happy path

## Подэтапы и коммиты

| Под | Что | Коммит |
|---|---|---|
| 14.a | миграция 16 + smoke IT (`./mvnw verify` зелёный) | `feat(backend): library liquibase migration 16 (lib_books/chapters/pages/image_regions)` |
| 14.b | domain records (5) + 4 repositories + 4 IT | `feat(backend): library domain records and jdbc repositories` |
| 14.c | BookService + DTO + LibraryMapper + BookController + BookServiceIT + BookControllerIT | `feat(backend): library REST api - books and pages CRUD` |
| 14.d | ADR-019 + обновление architecture.md / api-contract.md / glossary | `docs: ADR-019 library как доменный пакет + обновление архитектуры` |

ADR-019 формализуется в 14.d, **но** отдельная заметка ADR-019
добавляется ещё в 14.a-коммит (просто стуб с заголовком и status:
"принято в этом коммите, формализация в 14.d") - чтобы коммит
ссылался на действующий ADR.

Между подэтапами - `./mvnw verify` зелёный, smoke через `curl` для
14.c (POST + GET books).

## Открытый вопрос

Нет полностью открытых для пользователя - все design-решения
приняты в этом spec'е. Если по ходу 14.c обнаружится паттерн
доступа который **очень неудобно** выражать через jsonb metadata
(например, поиск аята по сура+аят), - сделаю отдельную миграцию
17 или рефакторинг через ADR. Решаю ad-hoc по факту.

## Формальные критерии готовности

- [ ] миграция 16 применяется через `./mvnw verify`
- [ ] 4 IT репозитория зелёные
- [ ] BookServiceIT + BookControllerIT зелёные
- [ ] curl smoke: POST /library/books, GET /library/books, GET
      /library/books/{id}, DELETE /library/books/{id} работают с
      корректными статусами и body
- [ ] ADR-019 в `decisions.md`
- [ ] `architecture.md` дополнен разделом «Library» (на высоком
      уровне со ссылкой на ADR-019)
- [ ] `api-contract.md` содержит все 5 новых эндпоинтов
- [ ] `glossary.md` дополнен Book/Chapter/Page/ImageRegion/BookType
- [ ] `roadmap.md` Этап 14 - все подэтапы [x]
- [ ] запись в `progress.md` под Сессию 20
- [ ] `SESSION_START_PROMPT.md` обновлён (текущее состояние, новый
      приоритет = Этап 15)
