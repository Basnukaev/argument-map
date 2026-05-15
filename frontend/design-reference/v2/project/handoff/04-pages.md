# 04 — Pages

Per-screen migration notes. Each section says:
1. Which artboard in the prototype is the canonical reference
2. What old patterns get retired
3. What new components fill the gap

## BookReaderPage

**Reference:** Section "Три направления", artboard **B · Workspace** (and
its RTL twin **rtl-tafsir** with real backend data).

### Layout

Three-column workspace, fixed top bar:

```
┌─────────────────────────────────────────────────────────────────┐
│  AppHeader                                                      │
├──────────┬───────────────────────────────────────────┬──────────┤
│ Chapter  │                                           │ Side     │
│ tree     │  Article (paper background, serif body)   │ panels   │
│ 248px    │                                           │ 288px    │
│          │                                           │          │
└──────────┴───────────────────────────────────────────┴──────────┘
```

### Components used

- `<AppHeader currentPath="/books/:id" />`
- `<ChapterTree chapters={book.chapters} arabicFont={book.language === 'ar'} />`
- The Article is page-specific markup; no extracted component. See
  prototype source `variant-balanced.jsx`.
- Side panels: plain `<section>` with the `bg-ink-0` and `p-4` pattern,
  small uppercase headings.

### What to retire from old code

- The old `BookReader` page's flat 2-column layout
- The "page numbers as nav at top" pattern (replaced by toolbar group with prev/page-input/next)
- Whatever was rendering raw `<span data-type='title'>` markers in the
  prose — write a small `parseBackendHtml(textContent)` helper that
  converts those into proper `<h2>` / `<p>` nodes

### Real-data wiring

The prototype uses `data-tafsir.js` adapted from a real backend payload.
Notes for the real version:

- `pageNumber` (1, 2, 3…) is the sequential page id used for navigation
- `printedPage` ("3", "ج 1 ص 5") is what was printed on the original page
  — show this in the chip row, smaller and monospace
- `part` ("المقدمة") is the volume/section label, also in the chip row
- `chapterId` may be null for pages that are between chapters — fall back
  to the "outer" chapter from the tree

## TopicListPage

**Reference:** artboard **topic-list**.

### Layout

```
┌─────────────────────────────────────────────────┐
│ AppHeader                                       │
├─────────────────────────────────────────────────┤
│ H1 "Темы дискуссии"           [Экспорт] [+ Тема]│
│ tabs + search                                   │
├─────────────────────────────────────────────────┤
│ ┌──────┐ ┌──────┐ ┌──────┐  (grid auto-fill)    │
│ │ Card │ │ Card │ │ Card │                      │
│ └──────┘ └──────┘ └──────┘                      │
└─────────────────────────────────────────────────┘
```

### Components used

- `<AppHeader currentPath="/topics" />`
- `<Card>` per topic, with custom internal layout (mini-graph + body)
- `<StatusBadge status={...} />` for pinning current activity
- A small `<MiniGraph>` helper — extract this when you have it

### What to retire

- The old admin-panel-looking square cards with hover-lift shadow
- The "all topics in one big sidebar list" pattern

## TopicGraphPage ★ (the main feature)

**Reference:** artboard **topic-graph-v3** (NOT v1 or v2 — v3 is final).

### Layout

```
┌─────────────────────────────────────────────────┐
│ AppHeader                                       │
├─────────────────────────────────────────────────┤
│ Topic crumb                                     │
├──────────────────────────────────────┬──────────┤
│                                      │          │
│  Graph canvas with floating chrome   │ Detail   │
│   ┌─┐ toolbar          [kbd hints]   │ panel    │
│   ├─┤                                │ 380px    │
│   └─┘   …nodes & edges…              │          │
│                                      │          │
│                       [○ ◐ ◓] zoom   │          │
│                                      │ ┌──────┐ │
│                                      │ │ src  │ │
│                                      │ │ card │ │
│                                      │ └──────┘ │
│              [□] minimap            │          │
└──────────────────────────────────────┴──────────┘
```

### Components used

- `<AppHeader currentPath="/topics" />`
- For graph rendering: an SVG layer with `<EdgeMarkerDefs />` in `<defs>`,
  `<EdgePill />` for labels, plus absolute-positioned `<div>` node cards
  (NOT inside the SVG — the dimorphism is intentional, gives you HTML
  hit targets for the cards)
- NodeCard is page-specific; see prototype `page-topic-graph-v3.jsx`
  → `V3NodeCard`
- Detail panel is page-specific; see `V3DetailPanel`. Its sub-pieces:
  - `<CollapseSectionV3>` for collapsible sections
  - The source card pattern — TypeChip + Reader-style citation block

### Layout / position model

Node positions are stored as `(x, y)` on the node record in the backend.
The prototype uses arbitrary values; real positions come from your
auto-layout (looks like you have something graph-positioning-related
already). The redesign doesn't change layout semantics — just the visual
treatment.

### What to retire

- Edge labels rendered as separate React components positioned on top
  of the SVG (the prototype renders them as SVG `<g>` for perfect
  alignment with the curve)
- The old detail panel that listed everything at once — use collapsible
  sections so users can hide what they don't need

## CreateTopicPage

**Reference:** artboard **create-topic**.

### Layout

Two-column form: form on the left, explanation on the right (sticky).

### Components used

- `<AppHeader />`
- `<Field label hint required>` for every input
- `<Button variant="primary">` for submit, `secondary` for save-draft,
  `ghost` for cancel
- Custom radio cards for "visibility" — pattern is in the prototype,
  extract as `<RadioCard>` if you'll reuse for other settings

## BookListPage

**Reference:** artboard **book-list**.

### Layout

Grid of book cards (auto-fill 220px+).

### Components used

- `<AppHeader />`
- `<Card>` with `<Card.Cover>` `<Card.Body>` namespace
- A small "Импорт из Shamela" secondary button in the header

### What to retire

- The hover-lift treatment (`hover:scale-105`) — keep hover subtle
- The cover gradient — solid color (from book's accent) reads cleaner

## AdminShamelaPage

**Reference:** artboard **admin-shamela**.

### Layout

Top "dashboard" with sync stats + status pill, search input, results
table, activity log block at the bottom.

### Components used

- `<AppHeader currentPath="/admin" />`
- Custom `<Stat label value hint accent />` — extract if reused
- Plain `<table>` styled with the system's tokens (uppercase header,
  hairline row separators)
- The "import progress" cell with the spinner — use the `animate-spin`
  utility on a small border-shape

### What to retire

- The admin pages that looked completely different from the user-facing
  ones. Same `<AppHeader>` now, same token palette, same density.

## Cross-page

- All pages live under `<AppHeader />`. The only screen WITHOUT it is
  BookReaderPage (it has its own crumb-style header for context).
- All pages use `bg-bg` (page background = cool surface).
- Reader is the only page that uses `bg-paper` for the article body.
- Density slider in user settings (later) affects `--density-scale` on
  `:root` — that variable is consumed by prose styles for vertical
  rhythm.
