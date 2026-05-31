# Handoff: Argument Map — UI/UX Redesign

## Overview

End-to-end redesign of **Argument Map**, a platform for structured discussions in Islamic sciences (bilingual RU/AR, desktop-first with mobile support, React 19 + Tailwind v4 + React Flow + Radix UI).

The bundle contains:

1. **A complete design token system** (`tokens.css`) — single source of truth for colors, typography, spacing, radii, shadows, motion. Includes light + dark + RTL variants tuned for WCAG AA contrast.
2. **A component primitives layer** (`app.css`) — `.btn`, `.input`, `.card`, `.pill`, `.header`, `.segmented`, `.icon-btn`, `.sk` (skeleton), etc. All values reference tokens — no magic numbers.
3. **A multi-artboard prototype** (`Argument Map - Redesign.html`) — every screen redesigned across light/dark/RTL/mobile, mounted side-by-side on a pan/zoom design canvas. Open it to see all screens at once.
4. **A visual handoff reference** (`Design Handoff - Tokens & States.html`) — interactive token gallery + live demos of every loading / empty / error / feedback pattern.

## About the Design Files

The two HTML files are **design references** — working React/HTML prototypes that demonstrate the intended look, behavior, and component API. **They are not production code to copy directly.**

Your task is to **recreate these designs in the target codebase** (React 19 + Tailwind v4 + Radix UI) using its established patterns. The CSS in `tokens.css` and `app.css` is **portable as-is** — you can drop both files into the project and start using the classes immediately, or bridge them into Tailwind's `@theme` (see "Tailwind v4 integration" below).

When the target codebase has its own pre-existing tokens, prefer those over the literal values below — match the *visual relationships and proportions*, not the exact hex codes.

## Fidelity

**High-fidelity (hifi).** Final colors, typography, spacing, shadows, and interaction timing are all specified. Screens should be visually pixel-equivalent to the prototype, modulo your design-token substitutions.

The prototype was built after a full design review of 104 screens covering:
- Navigation & topics list
- Argument graph (the main feature) with node detail panel
- Source-citation flow
- Library & reader (text + PDF modes)
- Q&A list & detail
- Hadith catalog & detail (with isnād chain)
- Collections
- Settings & admin (Shamela import)
- Authentication
- All of the above in **dark mode**, **Arabic RTL**, and **mobile**

A summary of the problems found in the original and the fixes applied is at the end of this document.

---

## Bundle Contents

| File | Purpose | Use directly? |
|---|---|---|
| `tokens.css` | All design tokens (CSS variables). Light/dark/RTL. | ✅ Yes — copy into project |
| `app.css` | Component primitives built on tokens. | ✅ Yes — copy into project |
| `Argument Map - Redesign.html` | All redesigned screens on a design canvas. | 🔍 Reference |
| `Design Handoff - Tokens & States.html` | Token gallery + state-pattern demos. | 🔍 Reference |
| `design-canvas.jsx` | Pan/zoom canvas wrapper used by the prototype. | 🚫 Prototype only |
| `artboards.jsx` | Mounts artboards into canvas sections. | 🚫 Prototype only |
| `artboards-part1.jsx` | Foundations + Topics list components. | 🔍 Reference (logic + styling) |
| `artboards-part2.jsx` | Login + Argument graph components. | 🔍 Reference |
| `artboards-part3.jsx` | Library / Reader / Q&A / Hadith / Collections. | 🔍 Reference |
| `artboards-part4.jsx` | Settings / Admin / States / Mobile. | 🔍 Reference |

Open both HTML files in a browser — they work standalone.

---

## Design Tokens (`tokens.css`)

All values live in CSS custom properties on `:root` (light) and `[data-theme="dark"]` (dark). RTL adjustments via `[dir="rtl"]`.

### Color philosophy

- **Brand**: a purple-violet scale (`--brand-50` → `--brand-900`) tuned in oklch so steps are perceptually uniform. `--brand-500` is the canonical primary CTA. In dark mode, `--brand-500` is brightened to `oklch(70% 0.17 270)` to maintain accessible contrast on dark surfaces — **the primary button must never look desaturated in dark.**
- **Surfaces**: 5 levels — `--bg-app` (page), `--bg-canvas` (graph/reader), `--bg-card` (modal, popover, card), `--bg-subtle` (input, inactive), `--bg-hover` / `--bg-active` (interactive states).
- **Text**: 5 tiers — `--text-strong` (headings), `--text-base` (body), `--text-muted` (secondary), `--text-meta` (metadata, **≥13px only**), `--text-faint` (placeholders only). Every tier ≥4.5:1 contrast against `--bg-app` in both themes.
- **Status**: `--status-ok` / `--status-warn` / `--status-err` / `--status-info`. Used semantically across pills, banners, toasts, edges.
- **Node types** (graph): four pairs — `--node-{type}` (background), `--node-{type}-ink` (text), `--node-{type}-bd` (border) for `question` (violet), `thesis` (purple), `argument` (amber), `evidence` (green). Re-tuned independently for dark mode — **never share oklch values across themes for these.**
- **Edges**: `--edge-supports` (green), `--edge-attacks` (red), `--edge-rebuts` (red dashed), `--edge-clarifies` (blue), `--edge-neutral` (gray). Pair every edge color with a glyph icon (`✓ ✗ ⊘ »`) for colorblind safety. In dark, all edges are boosted in lightness + chroma so they don't burn out.

### Typography

| Token | Value | Use |
|---|---|---|
| `--font-ui` | Manrope, system-ui | All UI |
| `--font-serif` | Source Serif 4 | Book titles, long-form |
| `--font-mono` | JetBrains Mono | Hashes, kbd, code, numerics |
| `--font-ar` | Scheherazade New, Amiri, Noto Naskh | Arabic |

Type scale: `--text-xs 11px`, `--text-sm 13px`, `--text-base 15px`, `--text-md 16px`, `--text-lg 18px`, `--text-xl 22px`, `--text-2xl 28px`, `--text-3xl 36px`, `--text-4xl 48px`.

For RTL: `[dir="rtl"]` automatically swaps `--font-ui` to Scheherazade so Arabic UI renders natively. No further work needed in components.

### Spacing & radii

- **Spacing**: 8px grid, 10 values — `--space-1 4px` through `--space-16 64px`. No other margin/padding values allowed in components.
- **Radii**: `--radius-xs 4px` (pill icon) · `--radius-sm 6px` (kbd) · `--radius-md 8px` (button, input) · `--radius-lg 12px` (card) · `--radius-xl 16px` (modal) · `--radius-full 9999px` (avatar).

### Shadows

Three elevation levels — `--shadow-sm`, `--shadow-md`, `--shadow-lg` — plus `--shadow-focus` (3px ring at `--brand-500 / 30% alpha`). Never combine. Dark-mode shadows are deeper (heavier alphas) since they need to read against dark surfaces.

### Hit targets

- Desktop: `--hit-target-desktop: 36px` (default for `.btn`, `.icon-btn`).
- Mobile: `--hit-target: 44px`. `@media (max-width: 640px)` automatically grows all `.btn` and `.icon-btn` to 44px.

---

## Component Primitives (`app.css`)

All classes are tiny — `.btn` is ~6 lines. Each consumes only token variables.

### Buttons

```html
<button class="btn btn--primary">Создать тему</button>
<button class="btn btn--primary" disabled>Disabled</button>
<button class="btn btn--secondary">Вторичная</button>
<button class="btn btn--ghost">Ghost</button>
<button class="btn btn--danger">Удалить</button>

<!-- Size variants -->
<button class="btn btn--primary btn--lg">44px height</button>
<button class="btn btn--icon"><Icon/></button>            <!-- 36×36 -->
<button class="btn btn--icon-lg"><Icon/></button>         <!-- 44×44 -->
```

**Critical**: Disabled state uses `opacity: 0.45` on the regular primary color — **never desaturate**. In dark mode the original implementation rendered primary buttons as muted gray that looked broken; this is the canonical fix. Apply the same pattern (`opacity: 0.45` over original color) to any new disabled state across the app.

### Inputs

```html
<input class="input" placeholder="..."/>
<input class="input input--lg"/>           <!-- 44px height -->
<textarea class="textarea"></textarea>
```

Focus state uses `--shadow-focus` ring + `--brand-500` border. Never use native `outline`.

### Dropdowns (replaces all `<select>`)

```html
<div class="dropdown">
  <button class="dropdown__trigger">
    <span>Сначала новые</span>
    <span class="dropdown__chevron"><ChevronDown/></span>
  </button>
  <!-- menu rendered via Radix DropdownMenu -->
</div>
```

**Critical**: The original app uses `<select>` in 5+ places (sort, volume picker, status filter, layout, bilingual mode). All native selects must be replaced with this pattern + Radix `DropdownMenu` — they break the visual language. For 2–3 short options, use `.segmented` instead (see below).

### Segmented control

```html
<div class="segmented">
  <button class="segmented__opt" aria-pressed="true">Открытые</button>
  <button class="segmented__opt">Закрытые</button>
  <button class="segmented__opt">Все</button>
</div>
```

Use for: language switcher (RU/AR), theme picker, bilingual mode, status filter, text/PDF toggle in reader. Maximum ~3 options with ≤10 chars each.

### Pills

```html
<span class="pill pill--question">ВОПРОС</span>
<span class="pill pill--thesis">ТЕЗИС</span>
<span class="pill pill--argument">ДОВОД</span>
<span class="pill pill--evidence">СВИДЕТЕЛЬСТВО</span>
<span class="pill pill--ok">SAHIH</span>
<span class="pill pill--warn">WARN</span>
<span class="pill pill--err">ERR</span>
<span class="pill pill--neutral">v1261</span>
```

### Cards

```html
<div class="card">Static</div>
<div class="card card--interactive">Hover-able</div>
```

### Skeleton (loading)

```html
<div class="sk-card">
  <div class="sk" style="height: 90px"></div>
  <div class="sk sk-line" style="width: 70%"></div>
</div>
```

Animates a shimmer gradient (1.5s ease-in-out). See "States" section below.

### Header

```html
<div class="header">
  <Brand/>
  <nav class="header__nav">
    <a aria-current="page">Темы</a>
    <a>Библиотека</a>
    ...
  </nav>
  <div class="header__utility">
    <div class="header__utility-group">...</div>
    <div class="vdivider"></div>
    <div class="header__utility-group">...</div>
  </div>
</div>
```

Active nav uses **underline 2px** (not filled pill). Utility groups are separated by `.vdivider` (1px × stretch). The original crammed 13 elements without separators — this is the fix.

### Hash IDs

```html
<span class="hash">2a65afd7</span>            <!-- hidden by default, shown on card hover -->
<span class="hash hash--always">2a65afd7</span> <!-- always 60% opacity -->
```

**Critical**: The original showed 8-char hex IDs everywhere — on every card, every breadcrumb, every isnād row. They distract from real content. Reveal only on hover, or on detail pages where they are actually useful (metadata sections).

---

## Screens — what's included in `Argument Map - Redesign.html`

The prototype mounts every screen on a [DesignCanvas](./design-canvas.jsx) — pan with click-drag, zoom with mouse wheel, click "Focus" on any artboard to open fullscreen.

| Section | Artboards | Notes |
|---|---|---|
| **Foundations** | Light + dark token gallery | Color/type/component reference |
| **Auth** | Login (light + dark) | Fixed: primary button no longer looks disabled in dark |
| **Topics list** | Light + dark + AR | RTL audit: gaps, dividers, header layout corrected |
| **Argument graph** ⭐ | 4 variants: ±dark × ±selection | Toolbar tooltips, edge contrast, mini-map, detail panel |
| **Library** | List (light + dark) | Replaced native select, floating card actions |
| **Reader** | Text mode (light + dark) | TOC + page controls + bilingual toggle |
| **Q&A** | List + detail (light + dark) | Source-attached question pattern |
| **Hadith** | Detail with isnād chain | Tooltips on chain links explain each hash |
| **Collections** | Empty + filled | Empty state with CTA back to library |
| **Settings** | Light + dark | Already the strongest page — preserved + minor polish |
| **Admin** | Shamela import dashboard | Cards now visibly clickable |
| **States** ✨ | Loading × 2 + Empty + 3× Error | Skeleton, 404, network, permission — **new** |
| **Mobile** | Topics, graph (vertical), reader | 44pt hit targets, FAB, thumb-zone action bar |

---

## State Patterns: Loading / Empty / Error / Feedback

These were **missing from the original app** — the most important addition in this redesign. Full visual demos in `Design Handoff - Tokens & States.html`.

### Loading: Skeleton

Use whenever first paint > 200ms. The base class is `.sk` with a shimmer animation; helper classes give common shapes.

```css
.sk {
  background: linear-gradient(90deg,
    var(--bg-subtle) 0%,
    var(--bg-hover) 50%,
    var(--bg-subtle) 100%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}
@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
```

**Patterns by view type:**

| View | Skeleton |
|---|---|
| Topics / Library grid | `.sk-card` × 6 — thumbnail block + 3 lines + meta row |
| Q&A / Hadith / Comments list | `.sk-row` × 4 — `.sk-circle` + 2 lines + status pill placeholder |
| Argument graph | **Don't skeleton** — spinner in canvas center + "Загружаем граф…"; nodes animate in atomically once data arrives |
| Reader / page content | Block of 4–5 `.sk-line` of varied widths inside `.sk-card` |
| Detail right-rail | Title 200×24, meta grid, content block 4 lines |

**React pattern:**

```jsx
{isLoading
  ? Array.from({length: 4}).map((_, i) => <SkeletonRow key={i} />)
  : items.map(item => <ItemRow {...item} />)}
```

### Empty states

Every empty surface needs: illustration → title → 1-2 sentence body → primary CTA (+ optional secondary). The illustration should be **schematic and on-brand** (use the graph node/edge motifs), not stock art.

```jsx
<div className="state-block">
  <div className="state-block__illus">
    <GraphSketchIcon/>
  </div>
  <h3 className="state-block__title">Начните свой первый аргумент</h3>
  <p className="state-block__body">
    Темы — это структурированные дискуссии, где аргументы становятся узлами,
    а связи — отношениями.
  </p>
  <div className="state-block__actions">
    <button className="btn btn--primary">+ Создать первую тему</button>
    <button className="btn btn--secondary">Шаблоны</button>
  </div>
</div>
```

**Empty-state catalog:**

| Where | Title | Primary CTA |
|---|---|---|
| Topics (no topics yet) | Начните свой первый аргумент | + Создать первую тему |
| Q&A (no questions) | Здесь будут вопросы | + Задать вопрос |
| Hadith (no search results) | По запросу «…» ничего не найдено | Сбросить фильтры |
| Collections (empty favourites) | Коллекция «Избранное» пуста | Перейти в библиотеку |
| Notifications popup | Пока нет новых уведомлений | Настроить → |

### Error states

Three variants: full-page (404, network), full-page-recoverable (permission, network retry), and inline (form validation).

**Full-page** — same `.state-block` chrome as empty states, different illustration + tone:

| Kind | Illustration | Body | Actions |
|---|---|---|---|
| 404 | Ghost / empty graph node | Возможно, тема удалена или вы зашли по старой ссылке. | Вернуться · На главную |
| Network | Wi-Fi with slash | Проверьте подключение. Повтор через 5с… (3/5) | Повторить сейчас |
| Permission | Lock icon | Запросите доступ у автора или вернитесь к публичным. | Запросить · Назад |

**Inline** — for form validation errors:

```html
<input class="input" style="border-color: var(--status-err)" value="not-an-email"/>
<div class="inline-error">
  <ErrorIcon/>
  <span><strong>Некорректный email.</strong> Проверьте, что строка содержит @ и домен.</span>
</div>
```

### Toast & banner feedback

Use `Radix Toast.Provider` at app root. Toasts appear bottom-right (LTR) / bottom-left (RTL) automatically based on `[dir]`. Auto-dismiss in 3s.

```jsx
<Toast.Root className="toast toast--ok" duration={3000}>
  <div className="toast__icon"><CheckIcon/></div>
  <div className="toast__body">
    <Toast.Title className="toast__title">Тема создана</Toast.Title>
    <Toast.Description className="toast__msg">
      «Дозволенность Мавлида» добавлена в ваши темы.
    </Toast.Description>
  </div>
  <Toast.Close asChild><IconButton/></Toast.Close>
</Toast.Root>
```

Variants: `.toast--ok` / `.toast--warn` / `.toast--err` / `.toast--info`. Errors should include a "Повторить" button when retry is possible.

**Banner** — sticky top-of-page for persistent notices ("В этой теме новые ответы — обновить?"). Manual dismiss. Use `.banner` class.

---

## Mobile Adaptations

The original mobile pass was incomplete. The redesign assumes these rules apply everywhere:

1. **Hit targets ≥44pt** — enforced via `@media (max-width: 640px)` on `.btn` and `.icon-btn`. Both grow to 44px automatically.
2. **Header** — never put 13 controls in one row. Mobile = hamburger left + brand center + 1 action right. Move everything else into the drawer.
3. **FAB for primary action** — bottom-right floating 56px circle for "+ Создать тему" / "+ Задать вопрос". Don't try to squeeze the button into the header.
4. **Graph: vertical-only layout** below 600px viewport — top-down tree with bigger gaps. The horizontal flow used on desktop becomes unreadable on mobile. Implement via a layout switcher in your React Flow integration.
5. **Reader: thumb-zone bottom bar** — page navigation goes to a sticky bottom bar (prev / page indicator / next), not inline above the text.
6. **PDF mode on mobile** — pinch zoom + swipe pages instead of buttons. Hide zoom controls below 640px.
7. **Action bars** — sticky bottom-bar selection patterns (the "Выбрано N · Удалить · …" desktop bar) should stay visually identical on mobile but reserve `env(safe-area-inset-bottom)` padding to avoid the iOS home indicator.

---

## RTL Adaptations

Most layouts mirror via flex/grid + logical properties. The RTL audit caught a few specific issues:

- **Header**: vertical dividers between utility groups must use `--space-3` minimum gap; the original lost spacing in RTL.
- **Reader**: TOC should sit on the **right** in RTL, not the left. Verify the column order in your grid swaps via `direction: rtl`.
- **Hash IDs in isnād rows**: text-align right in LTR, left in RTL (mirrored to stay visually at the row end).
- **Charts/graphs**: edges, viewport indicators, and the mini-map do *not* mirror — graph spatial semantics are direction-agnostic. Only the surrounding chrome mirrors.
- **Native form chrome**: don't use native `<select>` — its dropdown alignment in RTL is unreliable across browsers. The `.dropdown` pattern handles direction correctly.

`[dir="rtl"]` in `tokens.css` swaps `--font-ui` to Scheherazade automatically — no per-component code needed.

---

## Tailwind v4 Integration

`tokens.css` is portable. Bridge to Tailwind's `@theme` so utilities consume the CSS variables (themes still work):

```css
@import "tailwindcss";
@import "./tokens.css";
@import "./app.css";

@theme {
  --color-brand-50: var(--brand-50);
  --color-brand-500: var(--brand-500);
  --color-brand-700: var(--brand-700);
  --color-text-strong: var(--text-strong);
  --color-text-base: var(--text-base);
  --color-text-muted: var(--text-muted);
  --color-bg-app: var(--bg-app);
  --color-bg-card: var(--bg-card);
  --color-bg-subtle: var(--bg-subtle);
  --color-border: var(--border-base);
  --radius-md: var(--radius-md);
  --radius-lg: var(--radius-lg);
  --font-sans: var(--font-ui);
  --font-serif: var(--font-serif);
  --font-mono: var(--font-mono);
}
```

Then use Tailwind classes normally:

```jsx
<button className="bg-brand-500 text-white rounded-md px-4 h-9 hover:bg-brand-700">
  Создать
</button>
```

The component primitives in `app.css` (`.btn`, `.card`, etc.) and Tailwind utilities can coexist — use primitives for repeated patterns, Tailwind for one-offs.

---

## Theme switching (light / dark / system)

```js
// On app boot:
const stored = localStorage.getItem("theme");          // "light" | "dark" | null
const prefersDark = matchMedia("(prefers-color-scheme: dark)").matches;
const theme = stored ?? (prefersDark ? "dark" : "light");
document.documentElement.dataset.theme = theme;

// On toggle:
function setTheme(theme) {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem("theme", theme);
}
```

Tokens automatically swap via `[data-theme="dark"]` in `tokens.css`. No component code needs to be aware of the theme.

---

## Problems Fixed (summary of original review)

This redesign addressed these systemic issues found across 104 screens:

### 🔴 Critical
1. **Primary button looked disabled in dark theme** — original used desaturated color, now uses `opacity: 0.45` over the saturated brand color.
2. **Secondary text contrast <4.5:1** across metadata, captions, breadcrumbs — all text tiers retuned to AA in both themes.
3. **Mobile hit targets 32px or smaller** — `.btn`/`.icon-btn` auto-grow to 44px on `<640px`.
4. **Argument graph: unlabeled "0" counters under every node** — now an explicit ↑/↓ vote control with tooltips.
5. **Argument graph: low-contrast edges in dark mode** — re-tuned oklch independently from light, edges paired with glyph icons (✓ ✗ ⊘ ») for colorblind safety.
6. **Mobile graph unusable** — added vertical-only layout below 600px viewport.

### 🟡 Medium
7. **Native `<select>` mixed with custom UI** — `.dropdown` (long options) + `.segmented` (≤3 short options) replace all natives.
8. **Hash IDs visible everywhere** — hidden by default (`opacity: 0`), shown on card hover only.
9. **Header utility cluster of 13 elements** — grouped via `.vdivider` separators.
10. **Active nav tab used filled pill** — replaced with 2px underline (less visual weight, more Linear-like).
11. **Empty state with no CTA** — every empty surface now has illustration + body + primary action.
12. **Destructive actions not isolated** — `.btn--danger` is outlined-red; the "Опасная зона" section is wrapped in a tinted panel.

### 🆕 Added (was missing entirely)
13. **Loading skeletons** — `.sk` system with grid/row/canvas patterns.
14. **Empty states** — 5 catalog entries with reusable component.
15. **Error pages** — 404, network with retry, permission-denied.
16. **Toast feedback** — 4 variants + banner pattern, RTL-aware.
17. **Inline form errors** — `.inline-error` pattern with status-err tinted panel.

---

## Implementation Checklist

### Foundation
- [ ] Drop `tokens.css` into project; ensure it loads first (before component CSS)
- [ ] Drop `app.css` into project; load after `tokens.css`
- [ ] Bridge tokens into Tailwind via `@theme` (see above)
- [ ] Add theme switcher logic with `localStorage` persistence + system preference fallback
- [ ] Verify Google Fonts loaded: Manrope · Source Serif 4 · JetBrains Mono · Scheherazade New

### Buttons & inputs
- [ ] All `.btn` variants implemented (`primary`, `secondary`, `ghost`, `danger`)
- [ ] Disabled state uses `opacity: 0.45`, not desaturate
- [ ] All native `<select>` elements replaced with Radix `DropdownMenu` + `.dropdown` styling
- [ ] Short-list selects (≤3 options) use `.segmented` instead

### Layout
- [ ] Header active tab uses underline, not pill fill
- [ ] Header utility groups separated by `.vdivider`
- [ ] Mobile breakpoint enforces 44px hit targets

### Graph (main feature)
- [ ] Node types use `--node-{type}` tokens (background) + `--node-{type}-ink` (text) + `--node-{type}-bd` (border)
- [ ] Edges use `--edge-{kind}` colors + glyph icons (✓ ✗ ⊘ »)
- [ ] Edge colors re-tuned for dark — verify they don't burn out
- [ ] Vote counter under nodes has explicit ↑/↓ buttons + aria labels
- [ ] Toolbar buttons have tooltips with keyboard shortcuts
- [ ] Mini-map viewport indicator uses 2px dashed `--brand-500` border
- [ ] Selection detail panel right-rail uses `--bg-card` + `--shadow-lg`
- [ ] Mobile: vertical-only graph layout below 600px

### States
- [ ] `.sk` skeleton class implemented with shimmer animation
- [ ] `.sk-card` used for grid views (topics, library)
- [ ] `.sk-row` used for list views (Q&A, hadith, log)
- [ ] `EmptyState` component built from `.state-block` primitives
- [ ] 404, network, permission-denied pages exist as routes
- [ ] Network error supports auto-retry with countdown + manual override
- [ ] Toast provider mounted at app root with viewport bottom-right (LTR) / bottom-left (RTL)
- [ ] Inline form-error pattern (`.inline-error`) used for all validation feedback

### RTL & a11y
- [ ] `[dir="rtl"]` mirrors layouts via logical properties
- [ ] Reader TOC swaps to the right side in RTL
- [ ] All icon buttons have `aria-label` or visible tooltip
- [ ] Focus rings visible via `--shadow-focus` (never `outline: none` without replacement)
- [ ] Dropdowns have `aria-haspopup="menu"` + `aria-expanded`
- [ ] Vote buttons + segmented controls have proper `aria-pressed`/`aria-current`

### Mobile
- [ ] Hamburger drawer for nav on mobile
- [ ] FAB for primary creation action
- [ ] Sticky bottom action bars use `padding-bottom: env(safe-area-inset-bottom)`
- [ ] Reader page-nav moved to bottom thumb-zone bar
- [ ] PDF mode uses pinch+swipe instead of buttons

---

## Open questions for the dev team

- **Tailwind v4** — please confirm you've upgraded to v4 with `@theme`. If still on v3, the bridge needs to use `theme.extend.colors` in `tailwind.config.js` instead.
- **React Flow** — for the mobile vertical-only layout, please confirm whether React Flow's `dagre` layout or your own elk/dagre setup is used. The constraint is: below 600px, force `rankdir: "TB"` and bump `nodesep` / `ranksep` for tap-friendly spacing.
- **Speaker notes / future features** — the design system has slots for `.pill--info` etc; let me know if there are other semantic statuses (e.g. "needs review", "verified") that need their own tokens.
- **Brand mark** — the prototype uses ﷽ (Bismillah glyph) inside a violet square. If a real wordmark/logo exists, please drop it in `/public/brand/` and update the `<Brand>` component.

---

## Contact

Iterate on this design — questions, edge cases, missing screens — by opening the prototype HTML files and pointing at specific artboards. The bundle is meant as a living reference, not a one-shot spec.
