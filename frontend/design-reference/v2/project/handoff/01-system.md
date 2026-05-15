# 01 — Design system philosophy

This is what was wrong, what we changed, and the rules that follow.

## What was wrong

The original frontend had three concrete problems that produced the
"хаос, неединообразие, разные шрифты, сливающиеся кнопки" feeling:

1. **No type scale.** Sizes appeared as `text-[28px]`, `text-[10.5px]`,
   `text-2xl`, `text-sm` interchangeably. The same hierarchy was
   expressed at four different sizes across screens.
2. **No spacing scale.** `p-2`, `p-2.5`, `p-3`, `p-4`, `gap-1.5`, `gap-2`,
   `gap-3` were all in use — Tailwind's full menu, not a curated subset.
3. **No semantic colors.** `slate-50`, `slate-100`, `gray-50`, `zinc-100`
   appeared as backgrounds for the same conceptual surfaces. Borders
   were sometimes `slate-200`, sometimes `gray-300`.

The fix is not "use more design tokens" — that's just more variables.
The fix is **fewer choices** with stricter meanings.

## The rules

### 1. Six text sizes. That's all.

| Token | Pixels | Used for |
|---|---|---|
| `--t-xs`  | 12px | meta, id, mono labels, eyebrow |
| `--t-sm`  | 14px | UI body — buttons, nav, table rows |
| `--t-base`| 16px | default page text |
| `--t-md`  | 18px | reader body (serif) |
| `--t-lg`  | 22px | chapter heads, section heads |
| `--t-xl`  | 28px | book titles, page titles |

Anything outside this range needs a comment explaining why. **Don't ever
write a literal pixel size in JSX.**

### 2. Six spacing stops.

| Token | Pixels | Used for |
|---|---|---|
| `--s-1` | 4  | icon-to-text, dense chips |
| `--s-2` | 8  | button internal, list rows |
| `--s-3` | 16 | card padding, between siblings |
| `--s-4` | 24 | section padding, between sections |
| `--s-5` | 40 | between major page regions |
| `--s-6` | 64 | hero margins, page top margin |

Tailwind shorthand: `p-1` (4) `p-2` (8) `p-4` (16) `p-6` (24) `p-10` (40) `p-16` (64).

### 3. Three border radii. Semantic.

| Token | Pixels | Used for |
|---|---|---|
| `--r-sm` | 4  | buttons, inputs, chips, kbds |
| `--r-md` | 8  | cards, alerts, popovers |
| `--r-lg` | 12 | panels, modals, large container blocks |

**No `rounded-full` on rectangles.** Use only on dots, avatars, pill
status-indicators where the shape is meant to read as round.

### 4. One accent. Plus status colors.

- `--c-accent-*` (navy in light, indigo in dark) — the **only** primary
  brand color. Used for: primary buttons, current-nav highlight,
  selected-node ring, links.
- `--c-ok-*`, `--c-warn-*`, `--c-err-*` — semantic status. Used for:
  status badges, edge labels in graph, toasts.

Never invent a new tinted blue for "this thing is sort of important".
If it's important, it's the accent. If it's not, it's neutral.

### 5. Tone is set by background, not gradient.

The prototype uses two surface tones:

- **Cool** (`--c-surface-cool` ≈ #f8f8fa) — productivity contexts:
  graph canvas, topic list, admin pages.
- **Paper** (`--c-paper` ≈ #fbfaf5) — editorial contexts:
  reader article body, source cards, citation blocks.

This is the only place "warm vs cool" lives. No gradient backgrounds on
cards. No tinted slate-100 hovers.

## The vocabulary

These words have specific meanings in this codebase. Don't reach for a
synonym.

| Word | Definition | Example |
|---|---|---|
| **Card** | A discrete content unit with hover/click affordance. Always interactive. `padding: 14`, `radius: var(--r-md)`. | BookCard, TopicCard |
| **Panel** | A persistent UI surface. Never lifts. No hover state. `padding: 16`, `radius: var(--r-lg)`. | Right detail panel in graph |
| **Article** | The reader's text content container. White background, serif body, generous padding. | Reader main column |
| **Chip** | Inline-flex label, short text, no border (except outline-variant). `padding: 2px 7px`. | Type chip, status chip |
| **Badge** | Status indicator with semantic color + dot. `padding: 2px 6px`, uppercase 10px. | StatusBadge |
| **Pill** | Rounded-full chip-like element, used **only** for edges in graph, kbd hints, status dots with label. | Edge label, kbd cluster |
| **Field** | Form input + label + hint + error. Never just `<input />`. | Form fields |

## Anti-patterns (do NOT do these)

- ❌ `text-[10.5px]` — every text size must come from the scale
- ❌ `p-2.5` — every spacing must come from the scale
- ❌ `bg-gradient-to-br from-...` — no decorative gradients
- ❌ Two primary buttons in one view — there is **one** primary action per surface
- ❌ Mixing `border-slate-200` and `border-gray-200` — use `border-[color:var(--c-border)]`
- ❌ Hover effects with `hover:scale-105 hover:shadow-2xl` — keep hover subtle (border darken, bg shift)
- ❌ `rounded-full` on buttons — use `rounded` (4px). Reserved for dots/avatars
- ❌ Inline `style={{ color: '#1e3a8a' }}` — use tokens
- ❌ Dark theme done via `dark:bg-slate-900 dark:text-slate-100` on every component — use semantic tokens that switch theme-wide

## RTL & i18n

Layout uses CSS logical properties everywhere:

- `padding-inline-start` / `padding-inline-end` (not left/right)
- `border-inline-start` (not border-left)
- `margin-inline-end` (not margin-right)
- Tailwind has `ps-`, `pe-`, `ms-`, `me-`, `border-s`, `border-e`

When you see `dir="auto"` on a text node, it's because the content might
be Arabic. Use the `font-arabic` token for Arabic text (Amiri), not the
default `font-ui`.

The `--font-arabic` variable is set in `tokens.css`. To apply it
conditionally in React: `<span className="font-arabic">{text}</span>` if
`/[\u0600-\u06FF]/.test(text)`.

## Dark theme

Theme is set on `[data-theme="dark"]` on `<html>`. All token variables
swap automatically; components shouldn't need any `dark:` Tailwind
classes if they use semantic tokens. If you find yourself writing
`dark:bg-...` it usually means a token is missing — add one rather than
working around it.
