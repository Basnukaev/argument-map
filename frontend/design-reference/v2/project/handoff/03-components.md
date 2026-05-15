# 03 — Components reference

This doc covers the primitives. For each, you get:

1. The visual reference (which artboard in the prototype it lives on)
2. Props it accepts
3. The full TSX file in `handoff/components/`

All components use Tailwind classes that resolve to the tokens defined
in `02-tokens.md`. They use `lucide-react` for icons.

## Button

**Reference:** UI Kit artboard, "Кнопки" section.

Three variants × two sizes. **One primary per surface.** If you need
multiple actions, the others are `secondary` or `ghost`.

```tsx
<Button variant="primary" size="md" leftIcon={<Sparkles />}>
  Создать тему
</Button>
<Button variant="secondary" size="sm">Импорт</Button>
<Button variant="ghost" iconOnly aria-label="More">
  <Menu />
</Button>
```

| Prop | Type | Default | Notes |
|---|---|---|---|
| `variant` | `'primary' \| 'secondary' \| 'ghost'` | `'secondary'` | |
| `size` | `'sm' \| 'md'` | `'md'` | sm is for dense toolbars / tables |
| `iconOnly` | `boolean` | false | square 28×28 (sm) or 32×32 (md) |
| `leftIcon` / `rightIcon` | `ReactNode` | — | lucide-react icon |
| `disabled` | `boolean` | false | |

**Never reach for `bg-blue-600`.** If you need a primary button, the
variant gives you the right semantic color in both themes.

## Card

**Reference:** UI Kit "Карточки", BookListBoard, TopicListBoard.

A discrete content unit. Always interactive (hover state, cursor pointer).

```tsx
<Card onClick={() => navigate(`/books/${book.id}`)}>
  <Card.Cover color={book.accent}>{book.title[0]}</Card.Cover>
  <Card.Body>
    <Card.Eyebrow><Chip>BOOK</Chip> AR</Card.Eyebrow>
    <Card.Title arabic={book.lang === 'ar'}>{book.title}</Card.Title>
    <Card.Meta>{book.author}</Card.Meta>
  </Card.Body>
</Card>
```

The Card namespace is for the most common pattern (cover/body/title).
For custom internal layouts (like TopicCard with a mini-graph), just
use the root `<Card>` and lay out the content yourself.

## Chip & TypeChip & StatusBadge

**Reference:** UI Kit "Чипы · Бейджи".

Three different things that look similar — make sure to use the right
one:

- **Chip** — generic short label. `<Chip>BOOK</Chip>`, `<Chip accent>...</Chip>`
- **TypeChip** — semantic for graph node types. `<TypeChip type="CLAIM" />`
- **StatusBadge** — semantic for argument status. `<StatusBadge status="DISPUTED" />`

Don't roll your own chip with `<span className="bg-blue-100 px-2 py-1 rounded text-xs">`.
There's a primitive for this.

## Field

**Reference:** UI Kit "Поля ввода", CreateTopicBoard.

A form input + label + hint + error message. Use this even if you only
need a plain input — the consistent label/hint/error stack is what makes
forms feel like one product.

```tsx
<Field label="Название" hint="Краткая формулировка" required>
  <Field.Input value={name} onChange={setName} />
  <Field.Meta left="60 / 500" />
</Field>

<Field label="Описание">
  <Field.Textarea rows={3} value={desc} onChange={setDesc} />
</Field>

<Field label="С ошибкой" error="Слишком короткое">
  <Field.Input value={n} onChange={setN} />
</Field>
```

## ChapterTree

**Reference:** BookReader balanced variant, left sidebar.

Recursive collapsible chapter list. Pass a tree of
`{id, title, page, current?, children?}` nodes.

```tsx
<ChapterTree
  chapters={book.chapters}
  arabicFont={book.language === 'ar'}
  onClick={(c) => navigate(`/books/${book.id}?page=${c.page}`)}
/>
```

Highlights the current chapter and draws the active-row left-border
accent. Indentation per depth level is handled internally.

## AppHeader

**Reference:** Any non-reader page (TopicList, BookList, etc.).

The single top bar across the product. Don't fork it per page.

```tsx
<AppHeader
  currentPath="/topics"
  user={{ initials: 'МА' }}
  locale="ru"
  onSearchClick={() => openCommandPalette()}
/>
```

## EdgePill (graph-only)

**Reference:** TopicGraph v3.

SVG-rendered pill for edge labels. Used as a `<g>` element inside the
graph's edge layer.

```tsx
<EdgePill x={midX} y={midY} kind="SUPPORTS" />
```

Kind controls color, mark glyph (✓ ✗ » etc), and label text. Stays
readable in both themes via tokens.

## What's intentionally NOT a component

These exist in the prototype but as page-level patterns, not
extractable primitives:

- **NodeCard** in the graph — specific to TopicGraph, doesn't generalize
- **SidePanel** in the reader — basically a styled `<section>`, do it inline
- **Minimap** — too coupled to its parent canvas

If you find yourself wanting these as components later, extract them
*after* you've used the pattern twice, not before.
