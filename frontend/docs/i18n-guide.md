# i18n и RTL guide

Двуязычный интерфейс (RU/AR) с разными направлениями письма
требует трёх правил: разделять локаль/контент, использовать
логические CSS-классы, изолировать mixed-content. Этот гайд -
для будущих сессий Claude и контрибьюторов чтобы делать
i18n-friendly код с первой попытки.

## Три понятия которые НЕЛЬЗЯ путать

1. **Локаль интерфейса** - язык UI (`useLocaleStore.locale`),
   управляет `<html dir>` и переводами кнопок/лейблов
2. **Язык контента** - что в данных API (book.language,
   node.content). Может отличаться от локали
3. **Направление текста** - визуальный RTL/LTR конкретной
   строки. Определяется автоматически через `dir="auto"` или
   эвристикой `hasArabicScript`

Самая частая ошибка - смешивать (1) и (2): «если book.language ==
ar, делать `dir="rtl"` на toolbar'е». Это ломает RU-пользователя
читающего арабскую книгу. **Правило**: UI компоненты (header,
toolbar, sidebar) следуют локали интерфейса. Контент следует
своему dir.

## Добавляешь UI-строку

1. Открой `frontend/src/shared/i18n/dictionary.ts`
2. Добавь ключ в namespace по смыслу (`nav.*`, `reader.*`,
   `book.list.*`, `common.*`, `cite.*`, etc) в **обе** локали
3. В компоненте: `const t = useT();` + `{t('key')}`

**Никогда** не пиши `lang === 'ar' ? 'مرحبا' : 'Привет'` в JSX.
Это работает только для двух языков, ломается для трёх+.

```tsx
// ❌
<button>Создать тему</button>

// ✅
const t = useT();
<button>{t('topic.list.create_button')}</button>
```

## Добавляешь UI с layout (margin / padding / position)

Используй логические Tailwind-классы вместо физических:

| ❌ Физический | ✅ Логический |
|---|---|
| `ml-*` / `mr-*` | `ms-*` / `me-*` |
| `pl-*` / `pr-*` | `ps-*` / `pe-*` |
| `left-*` / `right-*` | `start-*` / `end-*` |
| `text-left` / `text-right` | `text-start` / `text-end` |
| `border-l*` / `border-r*` | `border-s*` / `border-e*` |
| `rounded-l-*` / `rounded-r-*` | `rounded-s-*` / `rounded-e-*` |

Tailwind v4 logical properties автоматически переключаются по
`<html dir>`. Никакого JS-кода не нужно.

**Исключение** - симметричные классы оставь как есть: `inset-x-*`,
`mx-*`, `px-*`, `gap-*`, `bottom-*`, `top-*`.

## Добавляешь иконку в UI

Большинство иконок (плюс, корзина, шестерёнка, лупа, X-закрытие,
расширение) **не** зеркалятся в RTL - они ненаправленные. Просто
вставляй.

Иконки направленные (стрелки навигации `ChevronLeft/Right`,
`ArrowLeft/Right`) - выбирай по локали интерфейса:

```tsx
const locale = useLocaleStore((s) => s.locale);
const prevIcon = locale === 'ar' ? ChevronRight : ChevronLeft;
const nextIcon = locale === 'ar' ? ChevronLeft : ChevronRight;

<Button icon={prevIcon}>{t('reader.prev')}</Button>
```

Альтернатива (`rtl:-scale-x-100` на иконке) - работает, но
семантически менее ясная. Применяй для chevron'ов сложения и
прочих indicator-стрелок, не для primary навигации.

## Добавляешь текст из API (контент)

Контент - название книги, цитата, имя автора, текст узла -
имеет собственное направление, **не привязанное** к локали UI.

```tsx
// ✅ браузер сам определит по первому сильному символу
<p dir="auto">{book.title}</p>

// ✅ font-naskh при арабском контенте через эвристику
// (dir="auto" сам не переключает шрифт)
import { hasArabicScript } from '@/shared/i18n';
<p
  dir="auto"
  className={hasArabicScript(text) ? 'font-naskh leading-[1.8]' : 'leading-snug'}
>{text}</p>
```

**Не** делай так:

```tsx
// ❌ ломается для RU UI с AR книгой
const isArabic = locale === 'ar';
<p dir={isArabic ? 'rtl' : 'ltr'}>{book.title}</p>

// ❌ ломается для русской книги на AR-локали
<p dir={book.language === 'ar' ? 'rtl' : 'ltr'}>{book.title}</p>
```

## Mixed-content (RU + AR + цифры в одной строке)

Без изоляции Unicode Bidi Algorithm перемешивает куски: «8 мая
2026 г.» в RTL контексте превращается в «мая 2026 г. в 8». Решение
- собирать строку из `<bdi>`-обёрнутых фрагментов.

```tsx
// ✅ дата всегда LTR-неделимая
<dd>
  <bdi dir="ltr">{formatDate(createdAt)}</bdi>
</dd>

// ✅ UUID-кусок
<span className="font-mono">
  <bdi dir="ltr">{shortId(id)}</bdi>
</span>

// ✅ pair с разделителем
<span>
  <bdi>{publisher}</bdi>
  <span aria-hidden> · </span>
  <bdi>{place}</bdi>
</span>
```

Для готовых компонентов используй `@/shared/components/citation/sourceCard`:
- `<Bdi>` - LTR-обёртка для cyrillic/латиничных значений
- `<HijriYear>` - хиджра + григориан с правильной изоляцией чисел
- `<FlexValue>` - значение с переключением font-naskh/Inter по script

## Форматирование чисел и дат

Числа всегда LTR. Если число встроено в RTL-контекст и состоит
из >1 цифры, оборачивай в `<bdi dir="ltr">{number}</bdi>`.

Даты форматируются под локаль интерфейса (не контента):

```tsx
// ❌ всегда ru-RU
const DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', { ... });

// ✅ локаль-aware через утилиту
import { useFormatDate } from '@/shared/i18n';
const formatDate = useFormatDate();
<bdi dir="ltr">{formatDate(node.createdAt)}</bdi>
```

## Карточки метаданных

Используй `RtlRow` из `@/shared/components/citation/sourceCard` -
он рендерит shamela inline формат `Label: value` в обеих локалях,
direction родителя сам зеркалит порядок.

```tsx
<RtlRow label={t('cite.label.author')}>
  <FlexValue text={book.authority.fullName} />
</RtlRow>
```

## Что зеркалится, что нет

**Зеркалится** (используй логические классы):
- поток UI-текста и блоков, выравнивание, отступы
- сайдбары, drawer'ы, панели деталей
- toolbar'ы пагинации
- модалки, формы (input/label flow)

**НЕ зеркалится**:
- логотип/бренд → обернуть в `dir="ltr"` чтобы заблокировать flip
  иконки и текста
- ненаправленные иконки (плюс, шестерёнка, лупа, корзина)
- числа, моноширинные ID, код в backtick'ах
- **граф React Flow** (canvas, позиции узлов, мини-карта) -
  пространственная структура. Меняется только язык текста внутри
  узлов через `dir="auto"` + font-naskh

## Чек-лист перед PR

- [ ] Все новые UI-строки в `dictionary.ts` (ru + ar)
- [ ] Использован `useT()`, не хардкод
- [ ] Tailwind классы логические (`ms-*`, `text-start`, etc)
- [ ] Иконки направленные - по локали интерфейса
- [ ] Контент - `dir="auto"` + `hasArabicScript` для шрифта
- [ ] Mixed-content в `<bdi>` (даты, ID, числа в RTL контексте)
- [ ] Даты через `useFormatDate()` (или утилиту-аналог)
- [ ] grep по src/ - нет inline regex `/[؀-ۿ]/`, только импорт из
  `@/shared/i18n`
- [ ] Visual smoke test в обеих локалях (Playwright или вручную)

## Анти-паттерны

```tsx
// ❌ inline regex
/[؀-ۿ]/.test(text)
// ✅ единый модуль
import { hasArabicScript } from '@/shared/i18n';

// ❌ хардкод одной локали
const DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', ...);
// ✅ локаль-aware

// ❌ хардкод направления по контенту-источнику
<aside dir={book.language === 'ar' ? 'rtl' : 'ltr'}>
// ✅ направление по локали интерфейса
const locale = useLocaleStore((s) => s.locale);

// ❌ физические классы
<div className="ml-4 text-right border-l">
// ✅ логические классы
<div className="ms-4 text-end border-s">

// ❌ строки в JSX
<button>Сохранить</button>
// ✅ через словарь
<button>{t('common.save')}</button>

// ❌ конкатенация в RTL контексте
{`${publisher} · ${place}`}
// ✅ разделитель элементом + bdi
<span>
  <bdi>{publisher}</bdi>
  <span aria-hidden> · </span>
  <bdi>{place}</bdi>
</span>

// ❌ ручная эвристика для UI
const isArabic = lang === 'ar';
<p dir={isArabic ? 'rtl' : 'ltr'}>{content}</p>
// ✅ dir=auto для контента
<p dir="auto">{content}</p>
```

## Где смотреть примеры

- `frontend/src/shared/components/citation/sourceCard/` - готовые
  i18n-friendly примитивы (Bdi, FlexValue, HijriYear, RtlRow)
- `frontend/src/shared/components/reader/PageJump.tsx` - mixed-content
  с bdi-обёртками
- `frontend/src/apps/argument-map/components/graph/NodeCard.tsx` -
  `dir="auto"` для контента + эвристика для шрифта
- `frontend/src/shared/components/layout/Header.tsx` - бренд с
  блокировкой flip через `dir="ltr"`
