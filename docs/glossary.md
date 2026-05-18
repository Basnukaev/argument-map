# Глоссарий

Чтобы в коде, API и обсуждениях использовать одни и те же термины.

## Основные сущности

**Topic (тема)** — контейнер для одной дискуссии. Имеет один корневой
вопрос и произвольное количество связанных узлов.

**Node (узел)** — вершина графа. Любое утверждение, вопрос или довод.
Типизирован через `node_type`.

**Edge (ребро, связь)** — направленная связь между двумя узлами.
Типизирована через `edge_type`.

**Source (источник)** — переиспользуемая запись в справочнике источников:
Коран, хадис, книга, статья, URL.

**Authority (авторитет, учёный)** — переиспользуемая запись об учёном
или эксперте, мнение которого цитируется.

**Revision (ревизия)** — запись в истории изменений узла.

## Типы узлов

**QUESTION** — вопрос, обычно корень темы. Пример: *"Мавлид это бид'а?"*

**CLAIM (тезис)** — утверждение-ответ на вопрос. Пример: *"Нет, не является"*

**ARGUMENT (довод)** — обоснование тезиса или возражение против него.
Пример: *"Потому что учёный X сказал..."*

**EVIDENCE (свидетельство)** — фактическое подтверждение, обычно
непосредственно ссылающееся на источник. На практике часто выражается
через связь `ARGUMENT` → `Source`, но оставлено как отдельный тип для
случаев, когда свидетельство — это само по себе утверждение факта.

## Типы связей

**SUPPORTS (поддерживает)** — аргумент поддерживает тезис.

**REFUTES (опровергает)** — аргумент опровергает тезис или другой аргумент.

**QUALIFIES (уточняет)** — ограничивает применимость узла. Пример:
*"Это верно, но только если Y"*

**INVALIDATES (объявляет нерелевантным)** — мета-уровень: объявляет, что
узел не должен учитываться в пересчёте (например, аргумент основан на
недостоверном источнике). Сильнее, чем `REFUTES`.

**RESPONDS_TO (отвечает на)** — прямой диалоговый ответ, без оценочной
окраски (не "за" и не "против"). Используется для сохранения структуры
обсуждения.

## Статусы узлов

**UNVERIFIED** — новый узел, ещё не оценён.

**STANDING** — устоявшийся: есть поддерживающие аргументы, нет
опровергающих.

**DISPUTED** — спорный: есть и поддерживающие, и опровергающие аргументы
в статусе `STANDING`.

**REFUTED** — опровергнут: поддерживающие аргументы нейтрализованы, есть
`STANDING` опровержение (или `INVALIDATES`).

## Прочие понятия

**Reliability (достоверность)** — степень достоверности источника.
Для хадисов: `sahih`, `hasan`, `daif`. Для других типов источников — своё.

**Опора (مُسْتَنَدٌ / دَلِيلٌ)** — UI-термин для секции в `NodeDetailsPanel`,
содержащей привязанные к узлу подкрепления (positional citations
из библиотеки + freeform citations через AddSourceModal). Выбран
вместо «Цитат» / «Источников» как семантически прямой эквивалент
исламского `мустанад` / `далиль` («то, на что опирается тезис») -
покрывает оба типа подкреплений и не конфликтует с domain term `Source`.
Иконка `Anchor` (Сессия 29, после Claude Design рекомендации в
`frontend/design-reference/project/citations.jsx`).

**Orphan node (висящий узел)** — узел без связей с корнем темы. Допустим
на этапе брейншторма, не считается ошибкой.

**EdgeSemantics / матрица допустимых пар** — таблица разрешённых
троек `(fromType, edgeType, toType)`. Источник истины — ADR-010.
Реализована параллельно на беке (`EdgeSemantics.java`, валидация
в `EdgeService.createEdge` → 422 `invalid-edge`) и на фронте
(`edgeRules.ts`, фильтрация типов в `AddEdgeModal` под пару
узлов, блокировка drag-create через toast). Запрещённая пара не
должна попасть в БД ни одним путём.

**Kill-switch** — семантика ребра `INVALIDATES`: если источник в
статусе `STANDING`, цель безусловно становится `REFUTED`, даже
если у неё есть `STANDING`-аргументы поддержки. Сильнее чем
`REFUTES` (участвующий в обычной арифметике статусов). Используется
для мета-аргументов вида *"сказанное оппонентом нерелевантно
потому что..."*. См. ADR-007.

**Mixed layout** — режим работы фронт-функции `layoutGraph`: если
у всех узлов есть сохранённые `posX`/`posY` — используются as-is;
если ни у одного нет — dagre считает все позиции; если смешанно
(часть сохранена, часть нет) — сохранённые остаются на своих
местах, fresh-узлы расставляются столбцом справа от `max(posX)`.
Решение из ADR-012.

**Toast (тост, уведомление)** — кратковременное всплывающее UI-
сообщение в правом нижнем углу. Четыре типа: `error` / `warning`
/ `info` / `success` с разными default ttl и цветовой схемой.
Реализован через `useToastStore` (Zustand) + `Toaster.tsx`,
вызывается через `toast.warning('...')` из любого callback без
хука. Используется при drag-create запрещённых пар, ошибках
сохранения позиции и других не-блокирующих сообщениях.

**Голосование за аргумент / vote (миграция 38)** — пользовательский
сигнал силы узла (`ARGUMENT` или `EVIDENCE`). 1 user может
проголосовать за 1 узел один раз с весом `+1` (upvote, поддержать)
либо `-1` (downvote, не согласен). Нейтральная позиция не сохраняется
отдельно - вместо неё row удаляется через DELETE. **Голоса
ортогональны `StatusCalculation`** - они не меняют логического
статуса узла (`STANDING`/`DISPUTED`/...), а параллельно отражают
коллективную оценку силы. Permission: vote требует только
read-access к topic'у (это reaction, не write). MVP 3-point scale
`{-1, +1}`; возможное расширение до 5-point `{-2..+2}` - в backlog.
Хранится в `node_votes (id, node_id, user_id, weight, voted_at)` с
UNIQUE на `(node_id, user_id)`. Агрегаты `upvotes`/`downvotes`/
`score = upvotes - downvotes` живут в `NodeResponse.vote*` и
заполняются bulk-load в `GET /api/v1/topics/{id}/graph`. На фронте -
`VoteWidget` в `NodeCard` footer (только для ARGUMENT/EVIDENCE -
QUESTION/CLAIM не голосуются, тезис/вопрос не нуждается в
weight-сигнале).

## Понятия исламской текстологии (для будущих этапов)

Термины из домена работы с хадисами и тафсиром. Сейчас в коде не
используются — добавлены заранее, чтобы при работе над этапами
"источники", "sanad explorer", "multi-grading" использовать их
консистентно. Все термины пришли из дизайн-референса
(`frontend/design-reference/`) и относятся к секциям бэклога
"будущие фичи (исламский контекст)".

**Sanad / Isnad (иснад)** — цепочка передатчиков хадиса от
Пророка ﷺ к составителю сборника (Бухари, Муслим и т.д.). В UI
будет визуализирована как звенья с типизированными связями
между ними (`sama'`, `'an'ana`, `haddathana`, `мункати'`). См.
секцию `extras.jsx`/`SanadExplorer` в дизайне.

**Rawi (передатчик)** — звено в цепочке иснада. Имеет имя,
поколение (`табиин`/`табиут табиин` и т.д.), tier (степень
надёжности от ас-сахабий до условного-надёжного).

**Hadith grade (степень хадиса)** — оценка достоверности хадиса
учёным. Базовые значения (поле `Reliability`):
- `sahih` — достоверный
- `hasan` — хороший
- `daif` — слабый

Multi-grade означает что один и тот же хадис мог быть оценён
разными учёными (Бухари, Тирмизи, Навави) - в дизайне
`MultiGradingSection` показывает чипы с отдельными оценками
от каждого. Поле `Reliability` сейчас на бэке — single-value.

**Tashkeel (огласовки)** — диакритические знаки в арабском тексте
(`harakat`: фатха, кясра, дамма, шадда, сукун). Toggle "с/без
огласовок" в UI - источник в полной форме, на canvas можно
отключить для краткости.

**Harakat** — конкретные знаки огласовок (часть `tashkeel`).

**Mushaf (мусхаф)** — кодифицированная запись Корана. В UI
важно при показе аятов: в каком mushaf-стиле (Хафс/Варш/Дури) -
влияет на чтение и иногда на смысл. Сейчас не реализовано.

**Riwayah (риваят, передача)** — версия чтения Корана от
конкретного чтеца. Связано с mushaf.

**Madhab (мазхаб, правовая школа)** — у `Authority` есть поле
`madhab`: ханафи, малики, шафии, ханбали и др. Используется
при показе позиции учёного на тезисе.

**Kunya (куния)** — патронимическая часть имени учёного
(например, "Абу Бакр" - "отец Бакра"). Часть полного имени
авторитета в дизайн-карточках. Сейчас не отдельное поле.

## Library (книги и страницы)

Введено в Этапе 14 (ADR-019) как фундамент платформы.

**Book (книга, труд)** - запись в библиотеке. Любой текст с
автором (опц.) и страницами: Коран, сборник хадисов, классический
труд, статья, манускрипт. Хранится в `lib_books`.

**BookType** - дискриминатор типа книги: `QURAN` (Коран),
`HADITH_COLLECTION` (сборник хадисов - Сахих аль-Бухари, Сахих
Муслим и т.д.), `BOOK` (классический труд - Маджму' аль-Фатава,
Хусн аль-максыд), `ARTICLE` (статья / отдельная работа),
`MANUSCRIPT` (рукопись / скан без оцифрованного текста). На MVP
все типы имеют общую таблицу `lib_books` со специфичными полями
в jsonb `metadata`.

**Chapter (глава)** - структурный элемент книги. Может иметь
родительскую главу через self-FK `parent_chapter_id` - так
выражается иерархия (том → книга → глава). Хранится в
`lib_chapters`.

**Page (страница)** - страница книги. Имеет `page_number`
(internal navigation counter, уникальный в пределах книги) и
**либо** `text_content` (извлечённый текст для цитирования),
**либо** `image_url` (ссылка на скан), либо оба. Хранится в
`lib_pages`.

**Printed page (печатная страница)** - маркер страницы в реальном
**бумажном/PDF оригинале**. Введён в миграции 19 (ADR-021) для
source-first нумерации. Хранится в `lib_pages.printed_page` как
TEXT - может быть числом ("47"), арабской буквой ("أ"), римской
цифрой. Это то что показывается пользователю в reader UI и
используется в цитатах вида «см. стр 47 тома 1». Nullable -
legacy книги до миграции 19 и manuscripts без структурированных
метаданных могут не иметь маркера.

**Part (том/juz')** - сегмент многотомной книги. Введён в
миграции 19 для source-first нумерации. Хранится в
`lib_pages.part` как TEXT - может быть цифрой ("1"), арабским
словом ("المقدمة" - предисловие), любым многотомным маркером.
Nullable - однотомные книги имеют все страницы с part=NULL.
Indexed (book_id, part) для быстрого SELECT DISTINCT part при
построении dropdown селектора томов на frontend.

**PDF page number (страница в PDF)** - физическая страница в
PDF-скане оригинала. Введён в миграции 19 (ADR-021) для будущей
PDF integration. Хранится в `lib_pages.pdf_page_number` как
INTEGER. На MVP всегда NULL - ETL pipeline не скачивает PDF.
Заполняется в этапе PDF integration (после Этапа 17). Отличается
от `printed_page` тем что это **physical** позиция в PDF файле,
а `printed_page` - **logical** маркер на странице (вводное "أ"
может быть на физической странице 5 PDF).

**Source-first (принцип «электронная как production оригинала»)** -
ADR-021. Электронная версия книги должна *ссылаться* на
оригинальное бумажное/PDF издание, не подменять его. Реализуется
через хранение `printed_page` + `part` + `pdf_page_number` -
пользователь видит «стр 47 тома 1» в reader, может найти ту же
страницу в своей бумажной копии того же издания. Применяется к
библиотеке shamela ETL и будущим источникам (PDF upload, image-
сканы рукописей).

**ImageRegion (область скана)** - выделенный прямоугольник на
image-странице. Координаты нормализованные `(x, y, width, height)`
в диапазоне 0..1 (не пиксельные - чтобы не зависеть от dpi скана).
Содержит `extracted_text` из OCR или ручного ввода. Используется
для точного цитирования рукописей.

## Object storage (бинарные файлы)

Введено в ADR-024 (Этап 25.b). Слой хранения blobs - PDF/EPUB книг,
image-сканов страниц, derived артефактов (thumbnails, AI summaries,
exports).

**S3-compatible storage** - формат API совместимый с AWS S3. На dev
используется MinIO (self-hosted в `docker-compose.yml`), в проде
любой S3-сервис (AWS S3, Cloudflare R2, Backblaze B2, Yandex Object
Storage, Hetzner Object Storage). Доступ через `AWS SDK for Java v2`
(`software.amazon.awssdk:s3`), миграция между провайдерами = смена
endpoint+credentials в `application.yml`.

**Bucket (бакет)** - именованный контейнер для объектов в object
storage. В нашей платформе четыре bucket'а:
- `library-imported-books` - PDF/EPUB из внешних источников
  (shamela, archive.org). Re-derivable
- `library-user-uploads` - book uploads от пользователей (Этап 16),
  Q&A attachments (Этап 19). **Critical** - единственный источник
- `library-page-images` - image-сканы страниц рукописей (Этап 17).
  **Critical** для редких манускриптов
- `derived-artifacts` - PDF previews, AI summaries (blob),
  graph exports. Re-derivable, TTL допустим

Bucket выбирается **по операционной семантике** (backup priority +
retention), не по контент-типу. Shamela PDF и user-uploaded PDF -
оба PDF, но разная criticality → разные bucket'ы.

**Storage key** - идентификатор объекта внутри bucket'а. Convention
проекта: `{book_id}/{file_index}.pdf` для books, `{page_id}/scan.jpg`
для page-images. URL объекта: `s3://{bucket}/{storage_key}`.

**Versioning (bucket versioning)** - режим bucket'а где `PUT` на
существующий ключ создаёт **новую версию**, старая остаётся
доступна по version-id. Защита от accidental overwrite и data loss.
В нашей платформе включено по умолчанию для всех bucket'ов кроме
`derived-artifacts`. Старые версии хранятся forever (lifecycle delete
policy не настраиваем).

**Catalog (library_files)** - таблица Postgres которая ведёт реестр
всех файлов в storage. **Source of truth** для платформы: хранит
SHA-256 hash, source URL, bucket, storage_key, метаданные.
Позволяет детектировать orphans (ключ в bucket'е, в БД нет), missing
(в БД есть, в bucket'е нет), integrity mismatch (hash в БД ≠ hash
файла). Catalog имеет собственный backup через `pg_dump` - даже при
полной потере bucket'а мы знаем что нужно перекачать.

**Content hash** - SHA-256 байтов файла. Сохраняется в
`library_files.content_hash`, проверяется на каждый `put` и
опционально на `get`. Mismatch = data corruption alarm. Не путать с
S3 ETag (это MD5 от upload, не для security).

**Soft-delete / hard-delete** - двухфазное удаление. Default: запись
помечается `library_files.deleted_at = NOW()`, объект в bucket'е
остаётся (доступен для recovery в течение admin period). Hard-delete -
отдельная admin action с двумя подтверждениями (юзерским и админским),
физически удаляет все версии объекта в bucket'е и оставляет tombstone
в `library_files`. Логика: для научной библиотеки accidental delete
больше вреда чем slight delay; двухфазное защищает от ошибок.

## Академическая citation (ADR-028)

**Мухаккик (تحقيق)** — редактор/исследователь тахкика. Готовит критическое
издание классического текста: сверяет рукописи, расставляет диакритику,
даёт сноски. **Критично**: разные тахкики одной книги имеют разные
пагинации, поэтому citation `Тафсир Ибн Касира, стр.145` без указания
мухаккика неоднозначна. В коде: `Muhaqqiq` record + `lib_muhaqqiqs`
таблица.

**Тахкик** — процесс подготовки критического издания классического
текста. Работа мухаккика. Конечный продукт - book edition с конкретной
пагинацией, привязанной к этому тахкику.

**Edition (издание, طبعة)** — конкретное опубликованное издание книги.
Книга «Тафсир Ибн Касира» может иметь edition 2 от издательства
«Дар Тайба» и edition 1 от «Дар аль-Фикр» - это разные пагинации с
разными мухаккиками. В schema: `lib_books.edition_number INTEGER`.

**Хиджра (هـ)** — исламский лунный календарь. Эра начинается с 622 г.
н.э. (переселение пророка из Мекки в Медину). Современные мусульманские
книги обычно указывают год публикации на обоих календарях
(`1420 هـ / 1999 м.`). В schema: `published_year_hijri` /
`published_year_gregorian` / `authorities.death_year_hijri`.

**Кунья (كنية)** — первая часть полного арабского имени, форма
«Абу/Умм Х» (отец/мать Х). Пример: `Абу Абдуллах`.

**Насаб (نسب)** — родословная часть имени: `Х ибн Y ибн Z`
(Х сын Y сына Z). Пример: `Мухаммад ибн Ахмад ибн Усман`.

**Нисба (نسبة)** — последняя часть имени, привязка к месту/племени/
мазхабу. Пример: `аль-Багдади` (из Багдада), `аш-Шафии` (приверженец
мазхаба имама Шафии).

**Полное имя автора** — в академической citation должно включать кунью
+ насаб + нисбу + год смерти при первом упоминании. Пример: `أبو الفداء
إسماعيل بن عمر بن كثير الدمشقي (т.774 هـ)`. В schema:
`authorities.full_name TEXT`.

**Бахс (بحث)** — научное исследование/разбор. Жанр исламской науки -
разбор вопроса с привлечением цитат из Корана, хадиса, мнений учёных.
Бахс-grade citation требует minimum 8 полей сноски (см. ADR-028).

## Rich text editor (ADR-039, Этап 17.0)

Введено в ADR-039 как фундамент для красивого рендера арабских
тахкиков и для AI editing pass поверх OCR pipeline (Этап 17).

**Tiptap** — headless rich text editor framework на ProseMirror.
Выбран как стандарт платформы для редактирования содержимого
страниц книг (`lib_pages.formatted_content`). React 19 совместим
через `@tiptap/react`. MIT license. Альтернативы (Lexical / Slate /
CKEditor / TinyMCE) и обоснование выбора — в ADR-039.

**ProseMirror** — underlying schema/transaction model на котором
построен Tiptap. Документ - строго типизированное дерево узлов
(`type: 'doc' → paragraph → text` и custom nodes). Сохраняется в
БД как jsonb сериализация. Schema валидируется на application
level (не через PG CHECK).

**ProseMirror JSON** — формат хранения формата страницы. Хранится в
`lib_pages.formatted_content jsonb NULL`. Nullable — если null,
frontend оборачивает `text_content` в минимальный
paragraph-документ прозрачно (backward compat для Shamela ETL и
PDFBox импортов).

**Tahqiq edition (тахкик-издание)** — academic-grade publication
исламского классического текста (см. также «Тахкик» в секции
«Академическая citation»). Цель rich text editor — добиться
визуального соответствия классическим тахкикам типа изданий
`Дар аль-Кутуб аль-Ильмия` / `Дар Тайба` / `Дар Ибн Хазм`: рамки
для хадисов/аятов, marginalia на полях, footnotes с decorative
separator, decorated headings с орнаментом, vocalized text toggle,
color highlights для key terms.

**Hadith Box (хадис-бокс)** — custom Tiptap node `HadithBox`.
Blockquote-like блок с розовым/peach background, рамкой и мета-
строкой снизу (источник + grade `sahih/hasan/daif`). Используется
для цитирования хадиса внутри страницы. Attributes: `source`,
`grade`, `narrator` (опционально). Аналогичный `AyahBox` — для
аятов Корана с attributes `surah` + `ayah` + опционально `reciter`.

**Marginalia (маргиналии)** — комментарии мухаккика на полях
страницы, мелким кеглем, смещённые вправо/влево от основного
flow. В editor реализованы как Tiptap node `Marginalia` с
attribute `side` (`left` / `right`) и опциональным `anchor`
(ref на word/phrase в main flow). Рендер — absolute-positioned
float или CSS Grid column.

**Footnote (сноска)** — двухпарный Tiptap extension: inline
marker `(¹)` `(²)` в тексте + footer block в конце страницы с
auto-numbering через ProseMirror plugin. Attributes на marker:
`noteId` соединяет с footer entry. Сноска в тахкике обычно
содержит дополнительный комментарий, альтернативный иснад,
ссылку на другой источник.

**DecoratedHeading (декорированный заголовок)** — heading h1-h4 с
attributes `ornament` (`diamond` / `flower` / `bracket` / `none`)
и `decorationColor`. Рендер — heading с prefix/suffix glyph через
CSS pseudo-element. Имитирует орнаментальные разделители разделов
в классических тахкиках.

## Audit log (ADR-043 Amendment 3, Этап 22.d)

**Audit log** - таблица `audit_log` (миграция 39) - event-sourcing lite
аудит-трейл мутаций. Каждый create/update/delete + visibility/member-
changes пишет 1 row synchronous в той же транзакции что и main flow.
Не reconstruct'ит state из event log - source of truth только для
compliance/debugging/observability.

**AuditEntityType** - константы (string literals, не enum) для
`entity_type` колонки: `TOPIC` / `NODE` / `EDGE` / `BOOK` / `QUESTION` /
`ANSWER` / `TOPIC_MEMBER` / `BOOK_MEMBER` / `NODE_SOURCE` /
`QUESTION_SOURCE` / `ANSWER_SOURCE`. Не enum чтобы добавление нового
типа не требовало миграции - валидация через `isValid()`.

**AuditAction** - параллельные константы для `action` колонки: `CREATE`
/ `UPDATE` / `DELETE` / `VISIBILITY_CHANGE` / `MEMBER_ADD` /
`MEMBER_REMOVE` / `MEMBER_ROLE_CHANGE`.

**parentEntityType / parentEntityId** - линковка child entity к parent
для одно-запросного fetch'а всей темы (node/edge → TOPIC, chapter/page →
BOOK, answer → QUESTION). Через partial index `(parent_entity_type,
parent_entity_id, created_at DESC) WHERE parent_entity_id NOT NULL`.

**changes** - jsonb колонка с описанием изменения:
- CREATE: `{"created": {...key fields snapshot}}`
- UPDATE: `{"field": {"old": X, "new": Y}}`
- DELETE: `{"deleted": {...snapshot}}`
- VISIBILITY_CHANGE: `{"visibility": {"old", "new"}}`
- MEMBER_*: `{"userId", "role": scalar | {"old", "new"}}`

Frontend парсит по action - schema не валидируется backend'ом.

## Dung's argumentation framework (ADR-044, Этап 6)

**Argumentation Framework (AF)** - формальная модель из работы Phan Minh
Dung'а (1995) для абстрактного аргументирования. Пара `(A, R)`: `A` -
множество аргументов (у нас `Node` записи), `R ⊆ A × A` - **attack
relation** (у нас `Edge` типа `REFUTES` либо `INVALIDATES`).
Аргумент `(a, b) ∈ R` читается как «a атакует b»

**Attack relation** - бинарное отношение нападения между аргументами.
В нашей схеме отображается через edges типа `REFUTES` / `INVALIDATES`.
SUPPORTS/QUALIFIES/RESPONDS_TO в Dung's framework не входят (фреймворк
**attack-based** по дизайну)

**Conflict-free** - множество аргументов `S ⊆ A` где нет attack
внутри: если `a, b ∈ S`, то `(a, b) ∉ R`. Базовое свойство любого
extension'а

**Admissible** - conflict-free множество `S` которое **защищает себя**:
для любого `c` атакующего какой-то `a ∈ S` существует `b ∈ S`
атакующий `c`. Защита от внешних attacks через counter-attacks

**Extension** - множество аргументов удовлетворяющее некоторой
semantics. Главные:
- **Grounded extension** - минимальный complete extension. **Skeptical**
  reasoning - что accepted во всех complete extensions. Ровно одно
  решение для любого AF, всегда существует. Используется у нас
- **Preferred extension** - максимальный admissible. Может быть
  несколько на один AF (**credulous** reasoning). Не реализован
- **Stable extension** - extension которое attacks все не-входящие
  аргументы. Может не существовать (граф с odd-length attack cycle).
  Не реализован

**Grounded labelling** - функция `L: A → {IN, OUT, UNDEC}` которая
характеризует grounded extension:
- `IN` (accepted) - аргумент defended ото всех своих attackers (либо
  не имеет attackers)
- `OUT` (rejected) - есть attacker с label IN
- `UNDEC` (undecided) - не получил определённого label через iterative
  derivation (typically attack-cycle без defender'а)

У нас IN → `STANDING`, OUT → `REFUTED`, UNDEC → `DISPUTED`.
`UNVERIFIED` в Dung'е не используется - каждый node получает
определённый label при complete labelling

**MVP algorithm** - наш дефолтный fixpoint-итератор для пересчёта
статусов (см. ADR-007), реализован в `StatusCalculationService.
recalculateUsingMvp`. Учитывает все edge типы кроме QUALIFIES/
RESPONDS_TO

**DUNG_GROUNDED algorithm** - opt-in (ADR-044) grounded labelling
через `DungFrameworkService.computeGroundedLabelling`. Переключается
через `PATCH /api/v1/topics/{id}/status-algorithm` (owner only).
Хранится в `topics.status_algorithm` (CHECK MVP|DUNG_GROUNDED)

## Удалённые понятия

**Weight (вес)** — было поле `int 1-10`, "субъективная сила
аргумента". Удалено в миграции 12 (см. ADR-011). Не использовалось
в `StatusCalculation`, на UI вводило в заблуждение. Будущая замена —
категориальная разметка для `ARGUMENT`/`EVIDENCE` (факт/мнение/
цитата) и/или voting от сообщества — после Stage 6 (auth).

**NodeAuthority (связь узел↔авторитет)** — было M:N с полем `stance`.
Удалено в Сессии 19, миграция 15 (ADR-017). Функциональность объединена
с `NodeSource` через трёхуровневую модель `Authority→Source→NodeSource`:
авторитет крепится через truд (source), а не напрямую к узлу.

**Stance** — enum `HOLDS/OPPOSES/NEUTRAL`, отношение учёного к узлу.
Удалено в миграции 15 (ADR-017). Семантика теперь передаётся через
направление рёбер графа (`SUPPORTS`/`REFUTES`).
