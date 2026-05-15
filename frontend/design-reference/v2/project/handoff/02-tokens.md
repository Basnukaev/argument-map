# 02 — Tokens

## How to install

1. Copy `tokens.css` to `src/styles/tokens.css`
2. Import it once, at the top of your global stylesheet (before Tailwind's `@tailwind base`)
3. Merge `tailwind.config.ts` with your existing config — the key thing is
   the `theme.extend.colors` block and the `fontFamily` block, both of which
   reference the CSS variables

```ts
// tailwind.config.ts
export default {
  // ...
  theme: {
    extend: {
      colors: {
        // semantic
        bg:        'var(--c-bg)',
        elevated:  'var(--c-bg-elevated)',
        sunken:    'var(--c-bg-sunken)',
        border:    'var(--c-border)',
        ink: {
          50:  'var(--c-ink-50)',
          100: 'var(--c-ink-100)',
          150: 'var(--c-ink-150)',
          200: 'var(--c-ink-200)',
          300: 'var(--c-ink-300)',
          400: 'var(--c-ink-400)',
          500: 'var(--c-ink-500)',
          600: 'var(--c-ink-600)',
          700: 'var(--c-ink-700)',
          800: 'var(--c-ink-800)',
          900: 'var(--c-ink-900)',
        },
        accent: {
          50:  'var(--c-accent-50)',
          100: 'var(--c-accent-100)',
          500: 'var(--c-accent-500)',
          600: 'var(--c-accent-600)',
          700: 'var(--c-accent-700)',
        },
        ok:   { 100: 'var(--c-ok-100)',   500: 'var(--c-ok-500)',   700: 'var(--c-ok-700)' },
        warn: { 100: 'var(--c-warn-100)', 500: 'var(--c-warn-500)', 700: 'var(--c-warn-700)' },
        err:  { 100: 'var(--c-err-100)',  500: 'var(--c-err-500)',  700: 'var(--c-err-700)' },
      },
      fontFamily: {
        ui:     'var(--font-ui)',
        serif:  'var(--font-serif)',
        mono:   'var(--font-mono)',
        arabic: 'var(--font-arabic)',
      },
      fontSize: {
        xs:   ['var(--t-xs)',   '1.4'],
        sm:   ['var(--t-sm)',   '1.45'],
        base: ['var(--t-base)', '1.55'],
        md:   ['var(--t-md)',   '1.55'],
        lg:   ['var(--t-lg)',   '1.3'],
        xl:   ['var(--t-xl)',   '1.2'],
      },
      borderRadius: {
        sm: 'var(--r-sm)',
        md: 'var(--r-md)',
        lg: 'var(--r-lg)',
      },
      boxShadow: {
        sh1: 'var(--sh-1)',
        sh2: 'var(--sh-2)',
        sh3: 'var(--sh-3)',
        sh4: 'var(--sh-4)',
      },
    },
  },
  // ...
};
```

## How theming works

`tokens.css` defines `:root` (light theme) and `[data-theme="dark"]` (dark
theme overrides). The dark block redefines the same variables to dark
values. Every semantic token (`--c-bg`, `--c-border`, etc.) switches
automatically.

To toggle theme:

```ts
document.documentElement.setAttribute('data-theme', 'dark');
// or 'light' — persist in localStorage / honor prefers-color-scheme
```

## Categories

### Ink scale (neutrals)

`--c-ink-0` is the lightest in light theme (white), darkest in dark
theme (deep brown-black). The semantic stays the same:

- `--c-ink-0` → page background, card background
- `--c-ink-50` → sunken surface (search field, code block)
- `--c-ink-100` → subtle fill (chip bg, hover row)
- `--c-ink-150` → hairline border (the default border in this system)
- `--c-ink-200` → firmer border (separators)
- `--c-ink-300` → disabled element border
- `--c-ink-400` → hint text (least important)
- `--c-ink-500` → muted text (meta)
- `--c-ink-600` → secondary text
- `--c-ink-700` → secondary text strong
- `--c-ink-800` → near-primary text (rarely used)
- `--c-ink-900` → primary text

### Semantic aliases

These are wrappers around the ink scale. **Prefer them in components.**

- `--c-bg` — page background
- `--c-bg-elevated` — anything that sits on top of the page (cards, panels, headers)
- `--c-bg-sunken` — search inputs, inset surfaces
- `--c-border` — default border (hairline)
- `--c-border-strong` — firmer border (separators)
- `--c-text` — body text
- `--c-text-muted` — meta text
- `--c-link` — link/accent color

### Accent

`--c-accent-600` is the canonical accent (navy in light, indigo in dark).
Variants `-50`, `-100`, `-500`, `-600`, `-700` go from quietest to
most-pressed. No reason to invent shades in between.

### Status

`--c-ok-*`, `--c-warn-*`, `--c-err-*` each have `-100` (background tint),
`-500` (main), `-700` (text/contrast). Used for:

- StatusBadge components
- Edge labels in TopicGraph
- Toast colors

### Type tokens for graph

The graph has special semantic colors that don't fit elsewhere:

- `--c-type-abstract-bg` / `--c-type-abstract-fg` — for QUESTION / CLAIM / ARGUMENT chips
- `--c-type-empirical-bg` / `--c-type-empirical-fg` — for EVIDENCE chips
- `--c-edge-supports` / `--c-edge-refutes` / `--c-edge-qualifies` / `--c-edge-responds` — for edge labels

These also have `-bg` versions for the soft fill behind edge pills.

## Density

`--density-scale` is a multiplier applied to vertical spacing inside
prose (between paragraphs and headings). Default 1.0; values 0.7–1.15
roughly correspond to dense → loose. Set on `:root` to affect the whole
app, or on a container to affect a region.

## When to add a token vs. inline

**Add a token when:**
- The value is reused 3+ times
- The value changes between light and dark
- The value has semantic meaning (status, type)

**Inline acceptable when:**
- Value is genuinely one-off (e.g., a specific node position in a graph)
- Value is structural (e.g., a `width: 248px` sidebar)

## Don't extend without a reason

If Claude Code is about to add `--c-blue-300`, stop and ask: is this a
new semantic concept, or just a shade that wasn't covered by accent /
status? In 95% of cases the right move is to pick the closest existing
token, not invent a new one.
