# Handoff: Minimap + Zoom Controls

## Overview

Two paired components for a node-editor canvas, designed to sit together in the bottom-right corner:

1. **`<ZoomControls />`** — a horizontal pill with zoom −/+, a clickable percentage that opens a preset dropdown, plus "fit" and "fullscreen" actions.
2. **`<Minimap />`** — a collapsible card showing nodes, optional connection lines, and a draggable viewport rectangle. Click anywhere in the canvas to jump the main viewport there.

They are designed to work together (zoom % displayed in both, fit/fit-selection actions wire to the same handlers) but each is independently usable.

## About the Design Files

The file in this bundle (`Minimap + Zoom Controls.html`) is a **design reference** — a working HTML/React prototype that demonstrates the intended look, behavior, and API. **It is not production code to copy directly.**

Your task is to **recreate these components in the target codebase's existing environment** (React, Vue, Svelte, etc.) using its established patterns, design tokens, and component libraries. If no UI framework is set up yet, pick the most idiomatic option for the stack.

When the target codebase has its own design tokens (colors, typography, spacing, shadows), prefer those over the literal values below — match the *visual relationships and proportions*, not the exact hex codes.

## Fidelity

**High-fidelity (hifi).** Final colors, typography, spacing, shadows, and interaction timing are all specified. The components should be visually pixel-equivalent to the prototype, modulo the target codebase's design-token substitutions.

---

## Component 1: `<ZoomControls />`

### Purpose
A compact horizontal toolbar for controlling canvas zoom. Replaces a barebones `[−] [+] [⛶]` toolbar that lacked a zoom-percentage indicator and any concept of presets or "fit-to-content."

### API

```ts
interface ZoomControlsProps {
  zoom: number;                          // Current zoom factor (1 = 100%).
  min?: number;                          // Default: 0.1
  max?: number;                          // Default: 5
  step?: number;                         // Increment per click. Default: 0.1
  onZoomChange?: (zoom: number) => void; // Fired by ±, preset clicks
  onFit?: () => void;                    // "Fit to content" — frame all nodes
  onFitSelection?: () => void;           // "Fit to selection" — only when hasSelection
  onFullscreen?: () => void;             // Toggle canvas fullscreen mode
  hasSelection?: boolean;                // Controls whether "Fit selection" preset appears
}
```

### Behavior

1. **Minus / Plus buttons** — adjust zoom by `step`. Disabled at limits (opacity 0.35, `cursor: not-allowed`).
2. **Percentage button** — shows current zoom as `NNN%` + chevron. On click, opens a dropdown menu of presets above the button:
   - 25%, 50%, 75%, **100%** (`⌘0`), 125%, 150%, 200%
   - Divider
   - "Вписать всё" (`⌘1`) — fit-to-content
   - "Вписать выделение" (`⌘2`) — fit-to-selection, **shown only when `hasSelection` is true**
   - Current zoom is highlighted with a 6px filled dot before the label and `--accent-fg` colored kbd
3. **Vertical divider** — between zoom controls and view actions
4. **Fit button** — calls `onFit`. Same as "Вписать всё" preset.
5. **Fullscreen button** — calls `onFullscreen`. Toggle canvas to full viewport.

**Dropdown dismissal**:
- Click outside → closes
- Press `Escape` → closes
- Click a preset → applies + closes

**Tooltips** appear above each icon button on hover, showing the action label + keyboard shortcut in a small kbd chip. 150ms fade.

### Layout

| Element                | Value                                            |
|------------------------|--------------------------------------------------|
| Container              | inline-flex, padding 4px, gap 2px                |
| Container background   | `--surface`                                      |
| Container border       | 1px solid `--border`                             |
| Container border-radius| 10px                                             |
| Container shadow       | `--shadow-card`                                  |
| Icon buttons (− / +)   | 30×30px, border-radius 6px                       |
| Icon size              | 14px                                             |
| Percentage button      | min-width 56px, height 30px, padding 0 8px       |
| Percentage font        | 500 / 12.5px / Geist Mono                        |
| Vertical divider       | 1px × 18px, color `--border`, 4px horizontal margin |

### Presets Dropdown Layout

| Element                | Value                                            |
|------------------------|--------------------------------------------------|
| Position               | Absolute, anchored above the % button, centered  |
| Offset from button     | 6px                                              |
| Min-width              | 180px                                            |
| Padding                | 5px                                              |
| Border-radius          | 10px                                             |
| Border                 | 1px solid `--border-strong`                      |
| Background             | `--surface`                                      |
| Shadow                 | `--shadow-float`                                 |
| Row                    | flex, gap 10px, padding 8px 10px, radius 6px     |
| Row hover bg           | `--surface-2`                                    |
| Kbd chip               | 500 / 10.5px Geist Mono, padding 1px 5px, radius 3px |
| Divider                | 1px × 100%, color `--border`, 4px vertical margin |

### Icons

All from a Lucide-style set (16px, stroke 1.7, rounded caps). **Fit and Fullscreen must be visually distinct** — they sit next to each other and are easy to confuse if both are just "corner brackets":

- **Minus** (`−`) and **Plus** (`+`)
- **Fit** — outer corner brackets **plus an inner content rectangle** (signifies "fit content into frame"). Distinct from fullscreen by having the inner rect.
- **Fullscreen** — outer corner brackets **only**, no inner content (signifies "make canvas fill viewport").
- **Chevron-down** — preset dropdown indicator

---

## Component 2: `<Minimap />`

### Purpose
A bird's-eye view of the entire canvas, with a viewport rectangle the user can drag to pan the main canvas. Replaces a static minimap that showed nodes as a single dash and had no interaction.

### API

```ts
type NodeType = 'q' | 'a' | 'h' | 't' | string;
// q = Question (purple), a = Answer (green), h = Hadith (amber), t = Topic (blue)

interface MinimapNode {
  id: string;
  x: number; y: number;     // Top-left in canvas coords
  w: number; h: number;     // Size in canvas coords
  type?: NodeType;
  selected?: boolean;
}

interface MinimapEdge {
  from: string;             // Node id
  to: string;               // Node id
}

interface Viewport {
  x: number; y: number;     // Top-left of visible area in canvas coords
  w: number; h: number;     // Width/height of visible area in canvas coords
}

interface MinimapProps {
  nodes: MinimapNode[];
  edges?: MinimapEdge[];
  viewport: Viewport;
  canvasBounds?: { w: number; h: number };  // If omitted, derived from node extents × 1.2
  zoom?: number;                            // Used to display % badge; default 1
  onViewportChange?: (v: Viewport) => void;
  onCenterOnSelection?: () => void;         // Header button, shown only when nodes have selected:true
  collapsed?: boolean;                      // Controlled mode
  defaultCollapsed?: boolean;               // Uncontrolled initial state
  onCollapsedChange?: (collapsed: boolean) => void;
  showEdges?: boolean;                      // Controlled; toggles edge rendering
}
```

### Structure

Three vertical sections:

```
┌─────────────────────────────────────┐
│ ОБЗОР              [center] [eye] [×]│   ← Header (32px tall)
├─────────────────────────────────────┤
│  ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░    │
│  ░░ ▢   ▢░░░░░░░░░░░░░░░░░░░░░    │   ← Canvas area (150px tall)
│  ░░ ▢───▢───▢░░░░░░░░░░░░░░░░    │
│  ░░     ▢░░░░░░░░░░░░░░░░░░░░░    │
│  ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░    │
├─────────────────────────────────────┤
│ ● 4  ● 2  ● 1              [100%]   │   ← Footer (28px tall)
└─────────────────────────────────────┘
```

### Behavior

1. **Header**:
   - Title "Обзор" — uppercase, letter-spacing 0.08em, color `--text-dim`, font 500/11px
   - Action buttons (24×24, radius 5px) right-aligned:
     - **Center-on-selection** — only rendered when at least one node has `selected: true`. Calls `onCenterOnSelection`. Icon: target/crosshair.
     - **Edges toggle** — calls `setShowEdges(v => !v)` if uncontrolled. Eye / eye-off icon.
     - **Collapse** — collapses the card. Icon: minimize-2 (diagonal arrows in).

2. **Canvas area**:
   - Background: `--canvas-bg` with a finer dot grid (`background-size: 14px 14px`) than the main canvas
   - **Projection**: `s = min(innerW/bounds.w, innerH/bounds.h)`. Nodes are positioned at `(offsetX + n.x*s, offsetY + n.y*s)` with size `(n.w*s, n.h*s)`. Offsets center the projection.
   - **Nodes** rendered as rounded 2px rectangles, colored by `type`:
     - `q` → `--node-q` (purple)
     - `a` → `--node-a` (green)
     - `h` → `--node-h` (amber)
     - `t` → `--node-t` (blue)
     - Selected nodes get a 1.5px `--accent` outline + 6px glow (`box-shadow: 0 0 0 1.5px var(--accent), 0 0 6px 0 var(--accent)`)
   - **Edges** (if `showEdges`): drawn as thin 1px lines (`background: var(--text-dim); opacity: 0.35`) between node centers via CSS transform-rotate. Hidden when toggled off.
   - **Viewport rectangle**: see below.
   - **Click on empty area**: pans the viewport so the click point becomes the new viewport center. Calls `onViewportChange` with clamped coords.

3. **Viewport rectangle**:
   - Border: 1.5px solid `--accent`
   - Background: `color-mix(in oklch, var(--accent) 12%, transparent)`
   - Border-radius: 3px
   - `cursor: grab`; on drag, `cursor: grabbing` and background opacity bumps to 22%
   - Hover state: background opacity 18% + 1px outline glow
   - **Drag**: uses pointer events. On `pointerdown`, capture pointer + record start mouse coords + start viewport coords. On `pointermove`, compute `dx/dy` in canvas coords (`/s`) and call `onViewportChange` with clamped position. On `pointerup`, release.
   - Clamping: `0 ≤ x ≤ bounds.w - viewport.w` and same for y.
   - Click on viewport itself does NOT trigger the canvas click-to-jump (stopPropagation).

4. **Footer**:
   - Three "type stat" pills on the left: a colored dot + count (e.g. `● 4` for 4 questions). Only render types with count > 0.
   - When `hasSelection`, the type stats are replaced by a single pill: `"Выбрано N"` with `--accent-bg` background and `--accent-fg` text.
   - Right-aligned: zoom badge showing `pct%` in a `--surface-2` chip (500/11px Geist Mono).

5. **Collapsed state**:
   - Width animates from 240px → **168px** over 250ms `cubic-bezier(.2,.7,.3,1)`
   - Canvas and footer hide; header height bumps to 40px to host a richer pill
   - The collapsed body is a clickable region (whole pill is the trigger; `role="button"`, keyboard `Enter`/`Space` activate) containing:
     1. **Mini preview** — a 44×28px thumbnail with `--canvas-bg` background, 1px border, 4px radius. Renders all nodes as 1px-min colored rectangles using the same type→color mapping as the full minimap, plus a single 1px `--accent`-bordered viewport rectangle (18% accent-color fill). No edges, no labels — pure spatial sketch.
     2. **Meta column** — two stacked lines:
        - Line 1: `NNN%` in 500/11.5px Geist Mono, color `--text`
        - Line 2: `N узлов` (Russian-pluralized: 1=`узел`, 2–4=`узла`, 5+=`узлов`) in 400/10px Geist, color `--text-muted`
   - A dedicated **expand button** (32×32, icon-only) sits to the right, separated from the clickable body. Clicking it (or the body) restores the full minimap. Icon: `expand` (4 diagonal arrows pointing outward to corners).
   - Hover on the clickable body adds `--surface-2` background to signal interactivity.
   - **Do not** use a tiny cramped 24×24 button as the only expand affordance — it disappears next to other UI. The whole collapsed pill should look like an inviting, recognizable mini-map preview.

### Layout & Sizing

| Element                | Value                                           |
|------------------------|-------------------------------------------------|
| Container width        | 240px (88px collapsed)                          |
| Container border-radius| 12px                                            |
| Container border       | 1px solid `--border`                            |
| Container shadow       | `--shadow-card`                                 |
| Header height          | 32px                                            |
| Header padding         | 6px 6px 6px 12px                                |
| Canvas height          | 150px                                           |
| Canvas dot-grid        | radial-gradient at 14px spacing                 |
| Footer height          | 28px                                            |
| Footer padding         | 6px 10px                                        |
| Header/footer divider  | 1px solid `--border`                            |
| Collapse animation     | 250ms `cubic-bezier(0.2, 0.7, 0.3, 1)` on width |
| Stat dot               | 6px circle                                      |
| Action button          | 24×24px, radius 5px, icon 13px                  |

---

## Design Tokens

The prototype uses CSS custom properties. Map these to your codebase's existing tokens — these are reference values, not prescriptions.

### Dark theme

| Token              | Value                              | Used for                                |
|--------------------|------------------------------------|------------------------------------------|
| `--bg`             | `#0d0d0a`                          | Page background                         |
| `--surface`        | `#1a1a16`                          | Component background                    |
| `--surface-2`      | `#22221d`                          | Row hover, kbd chip, zoom badge         |
| `--surface-3`      | `#2c2c25`                          | Tooltip background                      |
| `--border`         | `#2a2a23`                          | Component borders, internal dividers    |
| `--border-strong`  | `#3a3a31`                          | Dropdown / tooltip borders              |
| `--text`           | `#ece8df`                          | Primary text                            |
| `--text-muted`     | `#8a8678`                          | Icons (default), stat counts            |
| `--text-dim`       | `#5a5750`                          | Title labels, chevron, edge lines       |
| `--accent`         | `oklch(0.72 0.16 295)`             | Viewport border, current-preset dot     |
| `--accent-bg`      | `oklch(0.32 0.08 295 / 0.4)`       | Selection banner background             |
| `--accent-fg`      | `oklch(0.85 0.14 295)`             | Selection banner text, current-preset kbd |
| `--node-q`         | `oklch(0.7 0.16 295)`              | Question nodes                          |
| `--node-a`         | `oklch(0.7 0.16 160)`              | Answer nodes                            |
| `--node-h`         | `oklch(0.72 0.14 60)`              | Hadith nodes                            |
| `--node-t`         | `oklch(0.7 0.14 230)`              | Topic nodes                             |
| `--canvas-bg`      | `#131310`                          | Minimap canvas background               |
| `--grid-dot`       | `rgba(255,255,255,0.045)`          | Dot grid color                          |

### Light theme

| Token              | Value                              |
|--------------------|------------------------------------|
| `--bg`             | `#f6f4ee`                          |
| `--surface`        | `#ffffff`                          |
| `--surface-2`      | `#f1efe7`                          |
| `--surface-3`      | `#e9e7dd`                          |
| `--border`         | `#e4e1d6`                          |
| `--border-strong`  | `#cfcbbb`                          |
| `--text`           | `#1c1b16`                          |
| `--text-muted`     | `#6f6c5e`                          |
| `--text-dim`       | `#a09c8c`                          |
| `--accent`         | `oklch(0.55 0.18 295)`             |
| `--accent-bg`      | `oklch(0.93 0.04 295)`             |
| `--accent-fg`      | `oklch(0.45 0.2 295)`              |
| `--node-q`         | `oklch(0.55 0.16 295)`             |
| `--node-a`         | `oklch(0.5 0.14 160)`              |
| `--node-h`         | `oklch(0.6 0.12 60)`               |
| `--node-t`         | `oklch(0.5 0.14 230)`              |
| `--canvas-bg`      | `#f1efe7`                          |
| `--grid-dot`       | `rgba(0,0,0,0.06)`                 |

### Shadows

| Token           | Dark                                                                  | Light                                                            |
|-----------------|-----------------------------------------------------------------------|------------------------------------------------------------------|
| `--shadow-card` | `0 1px 0 rgba(255,255,255,.04), 0 8px 24px rgba(0,0,0,.4)`            | `0 1px 2px rgba(20,18,10,.06), 0 8px 24px rgba(20,18,10,.08)`    |
| `--shadow-float`| `0 2px 0 rgba(255,255,255,.03), 0 16px 40px rgba(0,0,0,.55)`          | `0 2px 4px rgba(20,18,10,.06), 0 16px 40px rgba(20,18,10,.16)`   |

### Typography

- **UI font**: Geist (Google Fonts). Weights used: 400, 500, 600. Fallback: `system-ui, -apple-system, sans-serif`.
- **Mono font**: Geist Mono. Weights used: 400, 500. Fallback: `ui-monospace, monospace`.

If your codebase uses a different sans / mono pair, swap them — the design depends on having a clear sans-vs-mono contrast (mono used for numbers / kbd / zoom %), not on Geist specifically.

---

## Keyboard Shortcuts (recommended)

Wire these at the app level — the components display them in tooltips but don't register listeners themselves:

| Action                    | Shortcut          |
|---------------------------|-------------------|
| Zoom in                   | `⌘+` / `Ctrl++`   |
| Zoom out                  | `⌘−` / `Ctrl+−`   |
| Reset to 100%             | `⌘0` / `Ctrl+0`   |
| Fit to content            | `⌘1` / `Ctrl+1`   |
| Fit to selection          | `⌘2` / `Ctrl+2`   |
| Toggle fullscreen canvas  | `F`               |

---

## State Management

Both components are pure UI — no internal data fetching, no global state required. Owner state:

| State              | Owner             | Notes                                                      |
|--------------------|-------------------|-------------------------------------------------------------|
| `zoom`             | Parent (canvas)   | Drives both `<ZoomControls>` and the badge in `<Minimap>`. |
| `viewport`         | Parent (canvas)   | Two-way bound with `<Minimap>` (drag/click → `onViewportChange`). |
| `nodes`, `edges`   | Parent (canvas)   | Passed to `<Minimap>` for rendering.                       |
| `presetsOpen`      | `<ZoomControls>`  | Local. Closed on outside-click or `Escape`.                |
| `dragging`         | `<Minimap>`       | Local. Tracks viewport rect drag.                          |
| `collapsed`        | Optional          | Controlled or uncontrolled.                                |
| `showEdges`        | Optional          | Controlled or uncontrolled.                                |

---

## Accessibility

- Both components use standard `<button>` elements with discoverable focus rings (let the host codebase apply its global focus style).
- Icon buttons must include `aria-label` or visible tooltip text.
- Percentage button has `aria-haspopup="menu"` and `aria-expanded` reflecting `presetsOpen`.
- Preset dropdown should trap focus and support `Up/Down/Enter` keyboard navigation in production (the prototype omits this for brevity).
- Minimap drag is mouse/pointer-only in the prototype; for full keyboard support, expose arrow-key panning when the viewport rect is focused.

---

## Files in this Handoff

- `Minimap + Zoom Controls.html` — Standalone working prototype. Open in a browser to see both components fully interactive (drag the viewport, click to jump, open the presets dropdown, toggle selection, collapse the minimap). Light/dark theme switcher in the demo bar. Use it as the visual ground truth while implementing.

---

## Implementation Checklist

### ZoomControls
- [ ] −/+ buttons respect `min`/`max` and disable at limits
- [ ] Percentage button renders current zoom as integer % with chevron
- [ ] Click on % opens preset dropdown above
- [ ] Outside-click and `Escape` close the dropdown
- [ ] Current zoom preset is highlighted with a filled dot
- [ ] "Fit to selection" preset hidden when `!hasSelection`
- [ ] Tooltips on every icon button with keyboard shortcut hints
- [ ] Vertical divider between zoom and view actions
- [ ] Both light and dark themes work via design tokens

### Minimap
- [ ] Header with title, collapse button, edge toggle, optional center-on-selection
- [ ] Nodes positioned via uniform scale projection, colored by `type`
- [ ] Selected nodes have accent outline + glow
- [ ] Edges (when enabled) drawn as thin lines between node centers via CSS rotate
- [ ] Viewport rectangle draggable with pointer events, cursor grab → grabbing
- [ ] Viewport position clamped to `bounds`
- [ ] Click on empty canvas jumps viewport center to click point
- [ ] Click on viewport itself does NOT jump (stopPropagation)
- [ ] Footer shows type-stat pills OR "Выбрано N" banner when selection exists
- [ ] Zoom badge in footer reflects `zoom` prop
- [ ] Collapse animates width over 250ms cubic-bezier(.2,.7,.3,1) — 240→168px
- [ ] Collapsed state shows a 44×28 mini preview thumbnail (nodes + viewport rect), zoom %, and pluralized node count
- [ ] Whole collapsed pill is clickable (`role="button"`, keyboard accessible); separate expand button on the right also works
- [ ] Both light and dark themes work via design tokens
