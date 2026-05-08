# Architecture - платформа

> Дополняет `vision.md` (что мы строим) и `architecture.md` (как было до
> платформенного pivot). Этот документ описывает целевую архитектуру
> платформы. Не вся она реализована - см. `roadmap.md` для текущего
> состояния.

## Содержание

1. [Высокоуровневая структура](#высокоуровневая-структура)
2. [Backend - доменные пакеты в одном Spring приложении](#backend)
3. [Frontend - monorepo с workspaces](#frontend)
4. [Library - центральный домен](#library)
5. [Цитирование - связь приложений с library](#цитирование)
6. [Стэк - текущий и расширения](#стэк)
7. [Альтернативы рассмотрены и отклонены](#альтернативы)

---

## Высокоуровневая структура

```
argument-map/                     # репозиторий (имя оставляем для git history)
├── apps/                         # пользовательские приложения, каждое
│   │                             # - отдельный фронт-package
│   ├── library/                  # читалка книг + citation picker
│   ├── argument-map/             # текущий argument-map (после миграции
│   │                             # из frontend/)
│   └── qa/                       # Q&A с источниками (будущее)
├── packages/                     # переиспользуемые между apps
│   ├── shared-ui/                # Button, Modal, IconButton, Toast,
│   │                             # ContextMenu, designTokens
│   ├── shared-api/               # apiClient, ApiError, openapi-types
│   ├── shared-types/             # доменные типы (NodeStatus, EdgeType)
│   └── shared-citation/          # CitationPicker компонент - общий
│                                 # для всех apps когда нужен «выбрать
│                                 # цитату из library»
├── backend/                      # один Spring Boot, доменные пакеты
│   └── src/main/java/ru/basnukaev/argumentmap/
│       ├── argumentmap/          # текущие topics/nodes/edges
│       ├── library/              # books/chapters/pages/regions
│       ├── citation/             # межприложенческие привязки
│       ├── qa/                   # Q&A (будущее)
│       └── shared/               # users, authorities
├── docs/                         # общая документация
├── scripts/                      # seed-скрипты, утилиты
├── docker-compose.yml            # postgres + (будущее: minio для image
│                                 # store + tesseract worker)
└── README.md                     # quick start, ссылки на vision/arch
```

**Ключевая идея**: один git-репозиторий, один postgres, один
Spring Boot, **несколько фронт-приложений** объединённых через общий
бэкенд и shared-packages.

Никаких микросервисов на этом этапе. Когда (если) что-то начнёт
расти - выделим. Раннее разделение усложняет deployment без
осязаемой пользы.

## Backend

### Один Spring Boot, пакеты по доменам

Текущая структура `ru.basnukaev.argumentmap.{config,domain,
repository,service,web,exception}` - это **техническая** разбивка
(по слоям). При росте проекта она плохо масштабируется: всё в
куче, поиск «что относится к argument-map vs что к library» -
сложен.

Переход на **доменную** разбивку:

```
ru.basnukaev.argumentmap/
├── argumentmap/                  # домен argument-map
│   ├── domain/    Topic, Node, Edge, Revision
│   ├── repository/
│   ├── service/   TopicService, NodeService, ...
│   └── web/       TopicController, NodeController, ...
├── library/                      # домен library
│   ├── domain/    Book, Chapter, Page, ImageRegion, BookSource
│   ├── repository/
│   ├── service/   BookService, ChapterService, PageService,
│   │              ImportService (parsing), OcrService
│   └── web/       BookController, ImportController, ...
├── citation/                     # межприложенческие привязки
│   ├── domain/    Citation (универсальная), CitationContext
│   ├── repository/
│   └── service/   CitationService
├── qa/                           # будущее
├── shared/                       # transversal
│   ├── domain/    User, Authority, Source (legacy ADR-017)
│   ├── repository/
│   ├── service/
│   └── web/
└── config/                       # spring config, OpenApi customization
```

Внутри каждого домена - стандартная слоёная разбивка
(domain/repository/service/web), но **домены изолированы**: чтобы
обратиться к чужому домену, идём через его service-фасад
(например `argumentmap` зовёт `library.BookService`, не лезет
напрямую в `library.repository`).

### Миграция с текущей структуры

Сейчас всё в `domain/`, `repository/`, `service/`, `web/`. Это
НЕ требует миграции прямо сейчас - можно жить с миксом старая
структура + новая для library. Постепенно (когда будет повод
дотрагиваться до файлов) переносить argument-map-специфичные
классы в `argumentmap/`.

ADR-018 фиксирует переход; конкретный refactor - отдельные коммиты
по мере необходимости.

### База данных - один postgres, разные namespaces по таблицам

Все таблицы в одной БД, но имена с префиксом домена для ясности:
- `arg_topics`, `arg_nodes`, `arg_edges` (от argument-map)
- `lib_books`, `lib_chapters`, `lib_pages`, `lib_image_regions`
  (от library)
- `cit_citations` (universal citation table)
- `qa_questions`, `qa_answers` (будущее)
- `shared_users`, `shared_authorities`, `shared_sources` (transversal)

Текущие `topics`, `nodes`, `edges` со временем переименуем
`arg_topics` etc - **не сейчас**, отдельной миграцией когда
накопятся другие изменения.

## Frontend

### Monorepo с workspaces

Сейчас один `frontend/` пакет. Целевая структура - несколько
пакетов в `apps/*` и `packages/*`, связанных через workspaces.

**Выбор инструмента: pnpm workspaces** (см. секцию [альтернатив](#альтернативы) ниже).

```
package.json (корневой - workspace root)
├── apps/
│   ├── library/
│   │   ├── package.json (name: @platform/library)
│   │   ├── vite.config.ts
│   │   └── src/
│   ├── argument-map/
│   │   ├── package.json (name: @platform/argument-map)
│   │   └── src/  (текущий frontend/src перенесённый сюда)
│   └── qa/  (когда дойдём)
├── packages/
│   ├── shared-ui/
│   │   ├── package.json (name: @platform/shared-ui)
│   │   └── src/  (Button, Modal, IconButton, etc)
│   ├── shared-api/
│   │   ├── package.json (name: @platform/shared-api)
│   │   └── src/  (apiClient, types.ts из openapi)
│   ├── shared-citation/
│   └── shared-tokens/
└── pnpm-workspace.yaml
```

В `package.json` приложения зависимости на shared-packages
указываются через `workspace:*` protocol:
```json
"dependencies": {
  "@platform/shared-ui": "workspace:*",
  "@platform/shared-api": "workspace:*"
}
```

При `pnpm install` создаются symlinks - изменение в
`packages/shared-ui` мгновенно отражается во всех `apps/*`.

### Деплой

В первой итерации - **один build-output на одно приложение**.
Каждый `apps/X` собирается в `dist/`, deploy раздельный
(например, разные nginx vhost).

Будущее: возможно объединение через **Module Federation** или
shell-app с микро-фронтендами в runtime. Не сейчас.

### Роутинг между приложениями

Пользователь работает в одном приложении в каждый момент времени.
Переход между library и argument-map - либо через **shared
top-bar** с иконками приложений (deploy: тогда они на одном
domain), либо через явные ссылки.

В MVP - простые ссылки, top-bar с переключением приложений
добавляется когда есть >1 deployed приложение.

### Текущее состояние - что мигрируем

Текущий `frontend/` остаётся как есть на время. Когда дойдём до
Этапа миграции (см. roadmap):

1. Создаётся корневой `package.json` с workspaces
2. Текущий `frontend/` физически переезжает в `apps/argument-map/`
3. Из него выделяются shared-компоненты в `packages/shared-ui`,
   `packages/shared-api`, etc - **постепенно** (не один большой
   refactor, по мере необходимости)
4. Создаётся `apps/library/` как новый пакет

## Library

Центральный домен. Главная сущность - `Book`. Иерархия:

```
Book (книга)
├── id, title, author (FK Authority), year, language, sourceType
├── visibility (public / private), uploadedBy
└── [Chapter / Page]

Chapter (глава) - опциональная промежуточная сущность
├── bookId, title, ordinal
└── [Page]

Page (страница)
├── id, bookId, chapterId?, pageNumber
├── textContent (full text - результат OCR или ручного ввода)
├── imageUrl? (если страница как изображение)
├── language (для multi-language книг)
└── [ImageRegion?]

ImageRegion (выделенная область на image-странице)
├── id, pageId
├── x, y, width, height (relative 0-1)
├── extractedText (что под этим регионом)
└── createdBy
```

Дополнительно:
- **Цитирование** - не в `library`, а в кросс-доменном `citation`-
  модуле. Library только хранит контент, не «кто цитирует»
- **Coran-специфичная структура** - суры (`Surah`) с аятами
  (`Ayah`), классическая нумерация. Но представлена через `Book`
  с `chapterIds → суры`, `pageIds → аяты`, плюс metadata
  `quranEdition`, `riwayah`, `mushafType`. Это позволяет писать
  единые компоненты-читалки

### Workflow добавления книги

Три способа (см. `vision.md`):

#### A. Парсинг внешнего источника (shamela)

```
[пользователь] → POST /api/v1/library/imports/shamela
                 body: { bookUrl: "https://shamela.ws/book/12345" }
                       │
[бэк] ImportService → парсит HTML страниц, извлекает structure
                    → создаёт Book + Chapter[] + Page[]
                    → сохраняет в БД
                    → возвращает bookId
```

Парсер - отдельный сервис (`ShamelaImportService`), который умеет
конкретный сайт. Расширяемо: `QuranComImportService`,
`SunnahComImportService` etc.

#### B. Загрузка PDF/EPUB

```
[пользователь] → POST /api/v1/library/imports/file (multipart)
                                    │
[бэк] FileImportService → распознаёт формат (PDF/EPUB)
                        → извлекает текст постранично (Apache Tika
                          или PDFBox для PDF, epub4j для EPUB)
                        → создаёт Book + Page[]
                        → возвращает bookId
```

#### C. Загрузка изображений-сканов

```
[пользователь] → POST /api/v1/library/books (создать пустую книгу)
              → POST /api/v1/library/books/{id}/pages (multipart, image-per-page)
                                    │
[бэк] PageImageService → сохраняет файлы в object store (или FS)
                       → создаёт Page с imageUrl и ПУСТЫМ textContent
                       → опционально: запускает OCR через
                         OcrService (асинхронно, через @Async или
                         job queue)
                                    │
[бэк] OcrService → Tesseract (Tess4j) с поддержкой Arabic
                 → извлекает текст
                 → апдейтит Page.textContent
                 → пользователь может потом редактировать
                   распознанный текст вручную
```

OCR арабского несовершенен. Поэтому:
- В UI всегда показываем оригинальное image
- OCR-текст под ним помечен как «автоматический, проверьте»
- При выделении пользователь может либо использовать OCR-текст,
  либо вручную ввести текст для региона
- Image-Region citation - fallback когда текст невозможен

## Цитирование

### Универсальная модель `Citation`

Хочется, чтобы и `argument-map`, и `Q&A`, и любое будущее
приложение имели **единый способ** цитировать book-content.

Текущий `NodeSource` (ADR-017) - это **частный случай** citation
для argument-map. Универсализируем:

```
Citation (универсальная)
├── id
├── bookId (FK lib_books)
├── pageId (FK lib_pages, optional если цитата распределена)
├── textRange (start/end character offset, для text-cite)
├── imageRegion (x/y/w/h, для image-cite)
├── quote (фактически процитированный текст - для копии в случае
│         если page.textContent изменится)
├── location (свободная строка: «стр. 41, изд. Дар аль-кутуб»)
├── createdBy, createdAt

NodeCitation (привязка citation → node argument-map)
├── citationId (FK)
├── nodeId (FK arg_nodes)
├── context (как цитата подкрепляет узел)
└── createdAt

AnswerCitation (когда появится Q&A)
├── citationId
├── answerId
└── role (basis / objection / qualification)
```

Преимущество: одна и та же Citation может быть привязана к
многим контекстам в разных приложениях. **Granular reuse:**
если перевод аята обновился - все привязки автоматически
показывают новый текст.

### CitationPicker - общий UI-компонент

Расположение: `packages/shared-citation/CitationPicker.tsx`.

Использование (псевдокод):
```tsx
<CitationPicker
  onPick={(citation) => attachToNode(nodeId, citation)}
  initialFilter={{ bookType: 'HADITH' }}
/>
```

Внутри:
- Tabs: Коран / Хадисы / Книги / Произвольный текст
- В каждой вкладке - читалка/poiск
- Пользователь выделяет фрагмент → создаётся Citation на лету
  через `POST /api/v1/citations`
- Возвращается citation в `onPick`

### Migration с текущего состояния

Сейчас в `argument-map`:
- `Source` (лежит в `shared/domain/`)
- `NodeSource` (привязка node → source с quote/context/location)
- `Authority`

Эти сущности **остаются**. После introducing `Book/Page/Citation`:
- `Source` становится «обобщённой ссылкой» - может ссылаться на
  Book (тогда `Source.bookId` появится) или быть свободным
  (как сейчас). Backward compatible
- `NodeSource` становится `NodeCitation`-like - переименование +
  добавление полей `bookPageId`, `textRange`, `imageRegion`.
  Существующие привязки с ручным `quote` остаются валидны
- `Authority` без изменений (это master data)

Конкретный план миграции прорабатывается перед Этапом 17 (когда
argument-map переключается на library citation).

## Стэк

### Текущий стэк (сохраняем)

- **Java 21**, Spring Boot 3.5
- **PostgreSQL 16** + Liquibase
- **JDBC Template** (без JPA)
- **React 19**, Vite 6
- **Tailwind v4**
- **Zustand 5**, **React Router 7**
- **openapi-typescript** для типов

Не меняем то что работает. Текущий стэк проверен на argument-map.

### Расширения для library

#### Backend

- **Apache PDFBox** или **Apache Tika** - извлечение текста из
  PDF. Tika гибче (поддерживает кучу форматов), PDFBox быстрее
  для PDF. **Предпочитаем Tika** - универсальнее
- **epub4j** - извлечение EPUB
- **Tess4j** - Java-обёртка вокруг Tesseract OCR. Поддерживает
  Arabic с тренированными моделями (`ara`)
- **org.jsoup** - парсинг HTML для shamela-импорта
- **MinIO Java SDK** или **AWS SDK для S3** - object store для
  image-сканов и uploaded PDF. В docker-compose добавим minio как
  локальный S3-compatible store. Можно начать с FS-based
  storage и мигрировать позже

#### Frontend

- **react-pdf** или **pdf.js** - просмотр PDF в браузере
- **react-arabic-reshaper** или работа через CSS+font - корректный
  рендер арабского с tashkeel
- **fabric.js** или **react-image-crop** - выделение региона на
  image-странице (rectangular region selection)
- **TanStack Virtual** или **react-window** - виртуализация для
  больших книг (1000+ страниц)
- **highlight.js**-подобный custom хайлайтер для отображения
  выделенных цитат в тексте

#### Возможные tools для monorepo

- **pnpm 9+** - workspace tool
- **Turborepo** - opt-in для caching builds. Не обязателен в MVP

## Альтернативы

### Frontend monorepo tool

Рассмотрено:

1. **npm workspaces** - встроено в npm 7+, нулевая зависимость.
   Минусы: медленный install (большие node_modules), плохо
   решает hoisting на больших монорепо
2. **yarn workspaces** - проверенная классика. Минусы: yarn в
   проекте сейчас нет (только npm), переключаться - extra
   стэк-зависимость
3. **pnpm workspaces** (выбрано) - быстрый install, эффективный
   disk-usage через hard-links, отличная workspace-поддержка через
   `workspace:*` protocol. Лучший выбор для нашего размера
   проекта. Стандарт в большой части индустрии (Vue, Vite, Astro
   используют pnpm workspaces)
4. **Nx / Turborepo** - сложнее, дают caching и task graph. Для
   платформы с 5+ приложениями - оправдано. Сейчас 2-3
   приложения - overkill. Можно добавить Turborepo поверх pnpm
   позже без миграции

### Backend - один сервис vs микросервисы

Рассмотрено:

1. **Один Spring Boot** (выбрано) - простой deploy, одна БД,
   общая транзакционность, общий пакет user/auth. Минусы:
   при росте до десятков приложений - монолит распухает
2. **Микросервисы по доменам** - library service, argument-map
   service, qa service, общий auth-service. Минусы: distributed
   transactions, eventual consistency, gateway, service-discovery
   - всё это _до_ того как у нас есть пользователи
3. **Modular monolith** (то что выбрано фактически) - один деплой,
   но строгие границы между доменами. Спокойно эволюционирует в
   микросервисы когда будет нужно

### Хранение image-сканов

Рассмотрено:

1. **Локальная FS на бэке** - просто, но не масштабируется,
   проблемы с deploy на разные машины
2. **Postgres BLOB** - хранение больших binary в БД.
   Антипаттерн в большинстве случаев - тяжёлые backup'ы, BD
   как WORM-storage
3. **MinIO** локально + **S3** в проде - стандартный путь,
   docker-compose даёт MinIO бесплатно, на проде - swap на
   реальный S3
4. **Внешние ссылки на archive.org** - использовать существующие
   сканы. Зависимость от стороннего сервиса, не control над
   доступностью

**Выбрано: MinIO локально, S3-API совместимый**. Можно начать с
FS на dev и переключить в проде - оба совместимы с одним
интерфейсом

### OCR для арабского

Рассмотрено:

1. **Tesseract** (выбрано) - open source, поддерживает Arabic
   с моделью `ara`. Качество приемлемое для печатного текста,
   плохо с tashkeel. Tess4j (Java binding) или CLI invocation
2. **Google Cloud Vision API** - выше качество, но платно и
   зависимость от внешнего сервиса
3. **EasyOCR** (Python) - sometimes лучше Tesseract для
   арабского, но требует Python-стэк
4. **Ручной OCR** - пользователь сам набирает текст под
   изображением. Trial-and-error, fallback когда auto-OCR
   подвёл

**Выбрано: Tesseract как primary (Tess4j на бэке), ручной ввод
как fallback** + UI всегда показывает оригинал-image, OCR помечен
как «автоматический»

### Структура данных Корана

Рассмотрено:

1. **Хранить как обычный Book** с chapters=суры, pages=аяты
   - универсально, но теряем classical numbering, не знает про
   уникальные требования (taghyim al-bayan, juz divisions)
2. **Отдельная сущность Quran** с Sura и Ayah - тяжелее domain,
   но точнее
3. **Гибрид** (выбрано) - Quran это Book со специальной
   `bookType=QURAN` + дополнительные таблицы `quran_metadata`
   (juz, hizb, manzil, sajda и т.д.). Reader для Корана знает
   про эти metadata; обычный book reader работает на любом
   `bookType`

Этот вопрос перерабатывается перед Этапом 14 - сейчас фиксируем
направление, конкретная схема в ADR при кодировании.
