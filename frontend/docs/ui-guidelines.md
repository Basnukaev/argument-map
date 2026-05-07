# UI Guidelines (frontend)

Дизайн-система и визуальные правила для фронтенда Argument Map. Опора
для всех компонентов и страниц.

> Источник истины для палитр и цветовых классов - `src/utils/designTokens.ts`
> (`STATUS_TOKENS`, `NODE_TYPE_TOKENS`, `EDGE_TYPE_TOKENS`). Этот документ
> описывает принципы; токены - реализация.

## Общий стиль

- **Минималистичный, чистый, профессиональный** - фокус на содержание,
  не на украшения
- **Светлая тема по умолчанию.** Тёмная - после MVP
- **Brand-цвет:** `indigo-600` (#4f46e5) - primary-кнопки, focus-ring,
  selected-состояния, ссылки. Не `blue` - индиго придаёт ощущение
  "редакторского" продукта, blue нейтрален
- **Шрифт:** системный стек (`-apple-system, BlinkMacSystemFont,
  "Segoe UI", Roboto, ...`)
- **Скругления:**
  - `rounded-xl` (12px) для карточек узлов и тем, секций контента
  - `rounded-lg` (8px) для модалок, drawer-ов
  - `rounded-md` (6px) для кнопок, инпутов, бэйджей
  - `rounded` (4px) для маленьких чипов и kbd-клавиш
  - `rounded-full` для аватарок
- **Тени (лестница):**
  - `shadow-[0_1px_2px_rgba(15,23,42,0.04)]` - idle-карточка
  - `shadow-[0_4px_12px_rgba(15,23,42,0.10)]` - hover на карточке
  - `shadow-[0_0_0_3px_rgba(99,102,241,0.18),0_8px_20px_rgba(15,23,42,0.10)]`
    - selected (indigo ring + glow)
  - `shadow-md` - тосты, popover
  - `shadow-2xl` - модалки
- **Отступы:** Tailwind шкала. `gap-4` (16px) - стандарт между
  элементами, `gap-2` (8px) - внутри плотных групп
- **Анимации:** только для важных переходов (открытие модалки, drag,
  hover-приподнятие), длительность `duration-150` или `duration-200`.
  Никаких "красивых" эффектов "потому что можно"

## Цветовые токены

Каждый статус и тип имеет полный набор оттенков (см. `designTokens.ts`):
`bar` для status-bar слева, `bg` для лёгкой подкладки, `text` для
самого текста, `badgeBg`/`badgeText` для бейджа, `chipBg`/`chipText`
для капсулы типа в header карточки, `ring` для focus/selected.

### Статусы узлов

Цвет показывает результат алгоритма пересчёта статуса. Ключевая
визуальная фича: **статус виден как полоса 5px слева у карточки**
(не border вокруг - см. ADR-015). Это освобождает контур карточки
для других сигналов (hover, selected) и не "конкурирует" с цветом
типа узла.

| Статус | Назначение | bar | badgeBg / badgeText |
|---|---|---|---|
| `STANDING` | поддержан, не опровергнут | `bg-emerald-500` | `bg-emerald-100 text-emerald-800` |
| `DISPUTED` | есть и за, и против | `bg-amber-500` | `bg-amber-100 text-amber-900` |
| `REFUTED` | опровергнут | `bg-red-500` | `bg-red-100 text-red-800` |
| `UNVERIFIED` | не оценён | `bg-slate-400` | `bg-slate-100 text-slate-700` |

Иконка статуса (`Check` / `AlertTriangle` / `XCircle` / `Circle`)
дополняет цвет в `StatusBadge` для дальтонической доступности.

### Типы узлов

Тип - категория узла, отличается **набором холодных оттенков** в
"капсуле" (`TypeChip`) в header карточки. Цвет типа не пересекается
с цветом статуса (статус - тёплая шкала emerald/amber/red, тип -
violet/indigo/sky/teal).

| Тип | Семантика | Иконка | chipBg / chipText |
|---|---|---|---|
| `QUESTION` | корневой или уточняющий вопрос | `CircleHelp` | `bg-violet-100 text-violet-700` |
| `CLAIM` | тезис, ответ на вопрос | `Megaphone` | `bg-indigo-100 text-indigo-700` |
| `ARGUMENT` | довод за/против тезиса | `MessageSquareQuote` | `bg-sky-100 text-sky-700` |
| `EVIDENCE` | хадис, цитата, факт | `FileText` | `bg-teal-100 text-teal-700` |

### Типы рёбер

Цвет и стиль линии передают тип связи:

| Тип | Семантика | Цвет stroke | Стиль |
|---|---|---|---|
| `SUPPORTS` | поддерживает | `#10b981` (emerald-500) | сплошной, толщина 2 |
| `REFUTES` | опровергает | `#ef4444` (red-500) | сплошной, толщина 2 |
| `INVALIDATES` | мета-опровержение (kill) | `#b91c1c` (red-700) | **пунктир** (`8 4`), толщина 3 |
| `QUALIFIES` | уточняет применимость | `#3b82f6` (blue-500) | сплошной, толщина 2 |
| `RESPONDS_TO` | организационная связь | `#94a3b8` (slate-400) | сплошной, толщина 1.5, opacity 0.7 |

Подпись на ребре - badge с иконкой типа на середине bezier-кривой.
Текст подписи опционален (toggle "Подписи рёбер" в toolbar). Иконка
видна всегда - служит маркером типа даже при скрытых подписях.

## Кастомный узел React Flow (`NodeCard`)

```
┌─┬──────────────────────────────────┐
│ │ [TypeChip]    [StatusBadge]  ⋯   │  ← header
│ │                                  │
│ │ Заголовок узла (font-semibold)   │  ← title
│ │ Тело: 2 строки с line-clamp-2... │  ← body (опционально)
└─┴──────────────────────────────────┘
 ↑
 Status bar 5px слева (rounded-l-xl)
```

### Header
- **TypeChip** - капсула с иконкой типа + label `(QUESTION/CLAIM/ARGUMENT/EVIDENCE)`,
  uppercase tracking-wider, font-semibold. Цвет из `chipBg`/`chipText` токенов.
  Один и тот же визуал в карточке и в radio-list модалок - пользователь
  узнаёт тип без чтения
- **StatusBadge** - бейдж с иконкой + русский label
  `(Устоявшийся/Спорный/Опровергнут/Не оценён)`. Цвет из
  `badgeBg`/`badgeText` токенов. В карточке размер `sm`,
  в `NodeDetailsPanel` - `lg`
- Кнопка `⋯` (`MoreHorizontal`) - placeholder для будущих node-actions
  (контекстное меню есть и без неё, кнопка декоративная пока)

### Тело
- Заголовок (`title` или первая строка `content`): `text-[13px]`,
  `font-semibold`, `text-pretty`, без обрезки
- Body (`content` если длиннее заголовка): `text-[12px]`,
  `text-slate-600`, `line-clamp-2` (две строки с многоточием).
  CSS `line-clamp` через `-webkit-line-clamp` встроен в Tailwind
- Полный текст показывается в `NodeDetailsPanel` при double-click
  или из контекстного меню "Редактировать"

### Состояния
- **Idle:** мягкая `shadow-[0_1px_2px...]`, `border-slate-200`
- **Hover:** `shadow-[0_4px_12px...]`, `border-slate-300` -
  ощущение "приподнялось"
- **Selected:** `border-indigo-500` + indigo ring
  (`shadow-[0_0_0_3px_rgba(99,102,241,0.18)...]` ) +
  усиленная тень. Сигнал: "узел выбран, можно действовать"
- **Handles:** 4 точки (top/right/bottom/left), `bg-white border-indigo-500`.
  Видны на hover/selected (group-hover/group-data-[selected]).
  Hit-area расширена до 28×28 через `::before inset-[-8px]` -
  попадать мышкой удобно даже в визуально-12×12 точки

## Боковые панели (`NodeDetailsPanel`, `EdgeDetailsPanel`)

Открываются справа при double-click на узле/ребре или из контекстного
меню. Ширина 400px.

### Header
Градиент от цвета типа к белому (`bg-gradient-to-b from-{type}-50/70 to-white`).
Внутри:
- Иконка типа в квадратике 32×32 (`bg-{type}-100`, `text-{type}-700`)
- Метка типа (uppercase tracking-wider) + ID узла моноширинный
- StatusBadge size=lg + опциональные теги (через `Badge`)

### Секции
Collapse-секции с одинаковым header:
- Иконка (lucide) + title (uppercase tracking-wider) + count
  (моноширинный) + chevron справа
- Click переключает open/close. По умолчанию "Содержание" открыта,
  остальные - в зависимости от количества данных

Доступные секции:
- **Содержание** - сам текст узла + кнопка "Редактировать"
- **Метаданные** - `dl` грид: ID / Создан / Обновлён / Автор / Версия
- **Источники** - lazy-load через `GET /nodes/{id}/sources` + параллельный
  `GET /sources` (для матчинга id → название). Карточка источника:
  kind моно-uppercase (хадис/аят/книга/статья/ссылка), title жирным,
  citation моноширинно, опциональный `quote` курсивом с `border-l-2`,
  опциональный `context` светло-серым. Кнопка отвязки появляется на
  group-hover. Кнопка `Plus` "Привязать источник" внизу секции открывает
  `AddSourceModal`
- **Авторитеты** - lazy-load через `GET /nodes/{id}/authorities` +
  `GET /authorities`. Строка с avatar (инициалы), name + era · madhab,
  бэйдж stance (`HOLDS`/`OPPOSES`/`NEUTRAL`) с цветным кодированием.
  Кнопка `Plus` "Привязать авторитета" открывает `AddAuthorityModal`
- **История изменений** - lazy-load, diff-блоки v.X→v.Y
  с red/green подсветкой строк

### Diff-блок истории
Карточка с moderate border, header `v.3 → v.4 · автор · дата`,
тело - моноширинный текст `divide-y`, строки `bg-red-50/40 text-red-800`
и `bg-emerald-50/40 text-emerald-800` для удалённого/добавленного
текста.

## Модалки

Используют `<dialog>` с focus trap, Escape, role=dialog. Width
`max-w-xl` для AddEdgeModal, `max-w-lg` для AddNodeModal. Тени
`shadow-2xl`, скругление `rounded-lg`.

### Выбор типа узла/ребра - не вертикальный radio-list, а grid карточек
- 4 колонки для типов узлов (`QUESTION/CLAIM/ARGUMENT/EVIDENCE`)
- 5 колонок для типов рёбер (`SUPPORTS/REFUTES/INVALIDATES/QUALIFIES/RESPONDS_TO`)
- Каждая карточка: иконка типа в `chipBg`/`chipText` сверху-слева,
  radio-input справа, метка `font-semibold`, описание `line-clamp-2`
- Selected: `border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400`
- Disabled (запрещённый тип для пары): `opacity-40 cursor-not-allowed`
  + tooltip с упоминанием ADR-010

### Footer модалки
Светло-серая полоса (`bg-slate-50 border-t`) с подсказкой хоткея
слева (`<Kbd>⌘</Kbd><Kbd>↵</Kbd> создать`) и кнопками действий
справа.

## Страницы

### `/topics` - Список тем

**Topbar** (h-12, white bg, border-bottom):
- Логотип + название "Argument Map"
- Навигация (`Темы` активная / `Авторитеты` / `Источники` -
  пока неактивны, заглушки на будущее)
- IconButton-ы: поиск, настройки, аватар

**Контент:**
- Заголовок "Темы аргументации" + подзаголовок с количеством
- Кнопка "Создать тему" (top-right)
- Полоса фильтров: поиск + сортировка + переключатель view (сетка/список)
- Сетка карточек тем 1-3 колонки в зависимости от ширины

**TopicCard:**
- Превью-блок 110px высотой с декоративным мини-графом (SVG)
  и бейджем "N узлов / M связей" (`nodeCount`/`edgeCount` из бэка)
- Контент:
  - `title` (font-semibold, line-clamp-2)
  - `description` (line-clamp-2, slate-500)
  - Footer: автор (аватарка + имя) + дата
- При hover - мягко приподнимается (`hover:-translate-y-0.5
  hover:shadow-md hover:border-slate-300`)
- При клике - переход на `/topics/{id}`

### `/topics/new` - Создание темы

Форма (как сейчас, минимальные правки стилизации):
- Название (`title`, max 200)
- Описание (`description`, max 2000, опционально)
- Корневой вопрос (`rootQuestion`, max 10000) - с QUESTION-чипом
- Превью корневого `NodeCard`
- Кнопки "Отмена" / "Создать"

После создания - редирект на `/topics/{id}`.

### `/topics/{id}` - Граф темы

**Главная рабочая поверхность.**

Layout:
```
┌─────────────────────────────────────────────────────────┐
│  [< К списку] · Название темы              [actions]    │
├──┬──────────────────────────────────────────┬───────────┤
│  │                                          │           │
│ ┃│            React Flow Canvas             │  Боковая  │
│ ┃│            (зум, пан, drag)              │  панель   │
│ ┃│                                          │  (open on │
│ ┃│ [hotkeys hint top-right]                 │   detail) │
│ ┃│ [legend bottom-left] [zoom] [minimap]    │           │
│  │                                          │           │
└──┴──────────────────────────────────────────┴───────────┘
 ↑ левый вертикальный toolbar (h-full, w-14)
```

**Topbar:** breadcrumb `[< Темы] · Название темы` + статус-бейдж
("сохранено") + actions (history/share/settings - пока заглушки или
скрыты).

**Левый toolbar (вертикальный, w-14):**
- IconButton-ы: `+ Узел` (`N`), `+ Связь` (`E`),
  `Подписи рёбер` (toggle), `Удалить` (`Del`)
- Tooltip-ы справа с подсказкой хоткея

**Floating элементы поверх canvas:**
- **Легенда статусов** (bottom-left): grid 2×2 с цветными
  квадратиками + лейблом, белый фон с backdrop-blur
- **Zoom controls** (bottom-center): IconButton-ы `-`, `%`, `+`,
  разделитель, `Maximize`, `Crosshair` - вместо нативных RF Controls
- **MiniMap** (bottom-right): уже есть `CompactMiniMap`
- **Hotkeys hint** (top-right): `<Kbd>` для основных хоткеев
  (`N`, `E`, `Del`, `⌘Z`)

**Боковая панель** (открывается при double-click): см. раздел
"Боковые панели".

## Компоненты

Список переиспользуемых компонентов и их назначения:

### UI базовые (`components/ui/`)
- `Button` - 6 вариантов (`primary` indigo / `secondary` /
  `ghost` / `danger` / `danger-ghost` / `link`), 4 размера
  (`xs` / `sm` / `md` / `lg`). Props `icon`/`iconRight` для
  lucide-иконок. Focus-ring `ring-indigo-500`
- `IconButton` - кнопка только с иконкой, `aria-label` обязателен,
  3 размера, варианты `ghost` (active=indigo) / `solid`
- `Badge` - универсальный бэйдж, тоны (slate, indigo, emerald,
  amber, red, blue, violet, sky, teal), 3 размера, опциональная иконка
- `StatusBadge` - специализация Badge для статусов узлов;
  токены из `STATUS_TOKENS`. data-testid="status-badge" сохранён
  для совместимости с тестами
- `TypeChip` - капсула типа узла (`QUESTION`/`CLAIM`/`ARGUMENT`/`EVIDENCE`)
  с иконкой и uppercase меткой
- `Card` - обёртка с `rounded-xl border-slate-200 bg-white shadow-...`
- `Kbd` - моноширинная кнопка-клавиша для хоткеев в подсказках
- `Input` / `Textarea` - текстовые поля с focus-ring indigo,
  опциональная иконка слева, label, hint, error
- `Modal` - на нативном `<dialog>` с focus trap (см. Cross-cutting
  в roadmap)
- `Toaster` + `useToastStore` - тосты (см. Cross-cutting)
- `ContextMenu` - универсальный для правых кликов (см. Cross-cutting)

### Графовые (`components/graph/`)
- `NodeCard` - кастомный узел RF с status-bar слева, TypeChip,
  StatusBadge, body line-clamp-2, 4 handles
- `CustomEdge` - кастомное ребро со стилем по типу (см. EDGE_TYPE_TOKENS)
  + bezier-путь + badge с иконкой и подписью на середине
- `NodeSelect` - кастомный select для выбора узла из списка
  (с цветным dot статуса и иконкой типа)
- `AddNodeModal` / `AddEdgeModal` - модалки создания
- `AddSourceModal` - модалка привязки источника к узлу. Два режима:
  search (фильтрация по title/citation в локальной памяти) и
  create (inline-форма с типом источника, title, citation,
  conditional reliability для HADITH). После create делает
  POST /sources → POST /nodes/{id}/sources одной операцией для
  пользователя
- `AddAuthorityModal` - то же для авторитетов. Stance picker
  (HOLDS/OPPOSES/NEUTRAL) - обязательный элемент при привязке.
  Create-form: name (required), era, madhab, bio
- `NodeDetailsPanel` / `EdgeDetailsPanel` - боковые панели
- `CompactMiniMap` - мини-карта в правом нижнем углу

## Responsive

- **Desktop-first:** граф плохо работает на мобилках (drag-and-drop,
  чтение мелкого текста)
- **Минимальная ширина страницы графа:** `1024px`. На меньшем -
  показывать сообщение "Откройте на десктопе" с read-only-режимом
- **Список тем и форма создания:** адаптивные, работают на любой ширине
- **Брейкпоинты Tailwind:** стандартные (`sm`, `md`, `lg`, `xl`,
  `2xl`)

## Accessibility (a11y)

- Семантический HTML: `<button>`, не `<div onClick>`
- `aria-label` для иконок-кнопок без текста (особенно `IconButton`)
- Keyboard navigation: `Tab`, `Enter`, `Esc` для модалок
- Focus-visible: `focus-visible:ring-2 focus-visible:ring-indigo-500
  focus-visible:ring-offset-2`
- Контрастность текста: минимум `WCAG AA` (4.5:1) - проверять при
  выборе оттенка из палитры (для статуса используем `-700`/`-800`
  на `-50`/`-100` фоне - удовлетворяет)
- Дальтоническая доступность: цвет статуса дублируется иконкой
  (`Check`/`AlertTriangle`/`XCircle`/`Circle`) в `StatusBadge` -
  пользователь различает статусы и без цвета
- React Flow имеет встроенную поддержку клавиатурной навигации -
  не отключать

## Локализация

- MVP - только русский. Тексты UI в коде на русском
- При появлении второй локали - выносить в `i18n` (`react-i18next` -
  отдельный ADR)
- Технические термины (типы узлов, статусы) - русские в UI
  (`Тезис`/`Довод`), английские в коде (`CLAIM`/`ARGUMENT`)

## Иконки

- **Lucide React** (`lucide-react`) - основной набор. Современные,
  лёгкие, тонкая обводка
- Типы узлов и статусы используют конкретный набор lucide-иконок
  (см. `STATUS_TOKENS` / `NODE_TYPE_TOKENS`) - **тот же** в карточке,
  модалках, панелях. Это единственный источник для иконок этих
  концепций
- Эмодзи как тип-индикаторы запрещены - в шрифтах ОС они выглядят
  по-разному и плохо контрастируют (`📢` vs `💬` сложно различить
  на типичном системном шрифте)
