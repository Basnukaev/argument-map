# Handoff: Help Button with Shortcuts Popover

## Overview

A compact icon-button (the "?" in the top-right of a canvas/editor) that reveals a popover listing keyboard shortcuts. Replaces three free-floating `kbd`-style legend pills that previously cluttered the empty canvas area. The popover groups shortcuts by category, supports a "pin open" mode, and works in any corner (auto-flips up/down and left/right).

## About the Design Files

The file in this bundle (`Help Button — Shortcuts.html`) is a **design reference** — a working HTML/React prototype that demonstrates the intended look, behavior, and API. **It is not production code to copy directly.**

Your task is to **recreate this component in the target codebase's existing environment** (likely React, but adapt to Vue, Svelte, SwiftUI, etc. as appropriate) using its established patterns, design tokens, and component libraries. If no UI framework is set up yet, pick the most idiomatic option for the stack.

When the target codebase has its own design tokens (colors, typography, spacing, shadows), prefer those over the literal values below — match the *visual relationships and proportions*, not the exact hex codes. The exact values below are provided for fidelity reference only.

## Fidelity

**High-fidelity (hifi).** Final colors, typography, spacing, shadows, and interaction timing are all specified. The popover should be visually pixel-equivalent to the prototype, modulo the target codebase's design-token substitutions.

## Component: `<HelpShortcuts />`

### Purpose
A trigger button + dismissable popover that displays grouped keyboard shortcuts. Used as an unobtrusive replacement for inline help legends.

### API

```ts
type Shortcut = {
  group?: string;     // Optional category header. Consecutive items sharing a group are clustered under one label.
  label: string;      // Human description of the action.
  keys: string[];     // Key cap strings. Each renders as a separate <kbd>. Use ['⌘', 'A'] for combos.
};

interface HelpShortcutsProps {
  shortcuts: Shortcut[];
  position?: 'down' | 'up';        // Popover direction. Default: 'down'.
  align?: 'right' | 'left';        // Popover horizontal alignment. Default: 'right'.
  title?: string;                  // Popover header label. Default: 'Шорткаты'.
  icon?: ReactNode;                // Override the "?" trigger icon.
  trigger?: 'hover' | 'click';     // Open behavior. Default: 'hover'.
}
```

### Behavior

1. **Trigger button** — 36×36 rounded square (radius 9px). Icon centered. On hover, the icon color shifts from `--text-muted` to `--text` and the border shifts from `--border` to `--border-strong`. On `:active`, scales to 0.96 (150ms ease).

2. **Open mechanism**:
   - `trigger="hover"` (default): popover appears on `mouseenter`, disappears on `mouseleave` from the trigger *or* the popover (the popover itself accepts hover to keep it open while reading).
   - `trigger="click"`: popover toggles on click. Clicking outside should close it (add an outside-click listener for the click variant).

3. **Pinning**: A small pin icon button in the popover header toggles a "pinned open" state. When pinned, the popover stays open regardless of hover/click. Pin icon is invisible by default (opacity 0) and fades in on popover hover. When active, pin color is `--accent-fg`. Clicking the pin must not bubble up and close the popover (`stopPropagation`).

4. **Animation**: Popover transitions `opacity 0→1` and `translateY(-4px → 0)` (or `+4px → 0` if opening upward) over 150ms ease. When closed, `pointer-events: none`.

5. **Keyboard accessibility**:
   - Trigger is a `div` with `role="button"`, `tabIndex={0}`, and an `aria-label`. (It's a `div` because the popover header contains its own button — nested `<button>` is invalid HTML.)
   - `Enter` and `Space` activate the trigger.
   - When `trigger="click"`, `Escape` should close the popover (worth adding in production even though the prototype omits it).

### Layout

**Trigger button:**
- Size: 36×36px
- Border-radius: 9px
- Border: 1px solid `--border`
- Background: `--surface`
- Box-shadow: `--shadow-card` (see tokens below)
- Display: inline-flex, centered children

**Popover:**
- Position: absolute, anchored to the trigger
- Offset from trigger: 10px gap (top, bottom, etc. depending on `position`/`align`)
- Min-width: 260px
- Padding: 14px
- Background: `--surface`
- Border: 1px solid `--border-strong`
- Border-radius: 12px
- Box-shadow: `--shadow-float`
- z-index: 50

**Popover header (`pop-title`):**
- Font: 500 / 11px / Geist
- Text-transform: uppercase
- Letter-spacing: 0.08em
- Color: `--text-dim`
- Margin-bottom: 10px
- Flex row, space-between (title left, pin button right)

**Group label:**
- Font: 500 / 10.5px / Geist Mono
- Text-transform: uppercase
- Letter-spacing: 0.05em
- Color: `--text-dim`
- Padding: 8px 6px 4px (top reset to 0 for the first group label)

**Row (one shortcut):**
- Flex row, gap 12px, items center
- Padding: 7px 6px
- Border-radius: 6px (for hover background)
- Font: 400 / 13px / Geist
- Color: `--text`
- Label takes `flex: 1`; keys hug the right
- Hover background: `--surface-2`

**Key cap (`.kbd`):**
- Font: 500 / 11px / Geist Mono
- Padding: 3px 6px
- Border-radius: 4px
- Border: 1px solid `--border`
- Background: `--surface-2`
- Color: `--text`
- Min-width: 18px, text-align center
- When multiple keys (a combo), display inline-flex with `gap: 3px` between them

### Pin Icon Button

A small icon button (Lucide-style pin), 14×14 icon.
- Default: opacity 0, color `--text-dim`
- Popover hover: opacity 1
- Hover on pin itself: color `--accent-fg`
- When pinned: opacity 1 and color `--accent-fg` persisted
- No background, no border, padding 2px
- On click: `stopPropagation()` so the popover doesn't toggle closed

### Positioning Variants

The popover supports 4 corner-friendly positions via the `position` and `align` props:

| `position` | `align` | Use case             | CSS effect                              |
|------------|---------|----------------------|------------------------------------------|
| `down`     | `right` | Top-right of canvas  | `top: 100%+10px; right: 0`               |
| `down`     | `left`  | Top-left of canvas   | `top: 100%+10px; left: 0`                |
| `up`       | `right` | Bottom-right of canvas | `bottom: 100%+10px; right: 0`          |
| `up`       | `left`  | Bottom-left of canvas | `bottom: 100%+10px; left: 0`            |

For production, consider a floating-element library (Floating UI, Radix Popover, Headless UI Popover) to handle viewport-edge collisions automatically rather than relying on a manually-set `position`/`align`.

## Design Tokens

The prototype uses CSS custom properties for theming. Map these to your codebase's existing tokens where possible — these are reference values, not prescriptions.

### Dark theme (canonical)

| Token            | Value                                  | Used for                          |
|------------------|----------------------------------------|-----------------------------------|
| `--bg`           | `#0d0d0a`                              | Canvas / page background          |
| `--surface`      | `#1a1a16`                              | Button & popover background       |
| `--surface-2`    | `#22221d`                              | Key caps, row hover               |
| `--border`       | `#2a2a23`                              | Button border, key cap border     |
| `--border-strong`| `#3a3a31`                              | Popover border, hover border      |
| `--text`         | `#ece8df`                              | Primary text, key cap text        |
| `--text-muted`   | `#8a8678`                              | Default icon color                |
| `--text-dim`     | `#5a5750`                              | Header label, group label, pin    |
| `--accent-fg`    | `oklch(0.82 0.14 295)` ≈ `#cdb1ff`     | Pinned state                      |

### Light theme

| Token            | Value                                  |
|------------------|----------------------------------------|
| `--bg`           | `#f6f4ee`                              |
| `--surface`      | `#ffffff`                              |
| `--surface-2`    | `#f1efe7`                              |
| `--border`       | `#e4e1d6`                              |
| `--border-strong`| `#cfcbbb`                              |
| `--text`         | `#1c1b16`                              |
| `--text-muted`   | `#6f6c5e`                              |
| `--text-dim`     | `#a09c8c`                              |
| `--accent-fg`    | `oklch(0.45 0.2 295)` ≈ `#6536d6`      |

### Shadows

| Token             | Value (dark theme)                                                                  |
|-------------------|--------------------------------------------------------------------------------------|
| `--shadow-card`   | `0 1px 0 rgba(255,255,255,.04), 0 8px 24px rgba(0,0,0,.4)`                          |
| `--shadow-float`  | `0 2px 0 rgba(255,255,255,.03), 0 16px 40px rgba(0,0,0,.55)`                        |

| Token             | Value (light theme)                                                                 |
|-------------------|--------------------------------------------------------------------------------------|
| `--shadow-card`   | `0 1px 2px rgba(20,18,10,.06), 0 8px 24px rgba(20,18,10,.08)`                       |
| `--shadow-float`  | `0 2px 4px rgba(20,18,10,.06), 0 16px 40px rgba(20,18,10,.16)`                      |

### Typography

- **UI font**: Geist (Google Fonts). Weights used: 400, 500, 600. Fallback: `system-ui, -apple-system, sans-serif`.
- **Mono font** (key caps, group labels): Geist Mono. Weights used: 400, 500. Fallback: `ui-monospace, monospace`.

If your codebase uses a different sans / mono pair, swap them — the design depends on having a clear contrast between the two, not on Geist specifically.

### Spacing & sizing

| Element                  | Value                  |
|--------------------------|------------------------|
| Trigger button           | 36×36px                |
| Trigger border-radius    | 9px                    |
| Popover padding          | 14px                   |
| Popover border-radius    | 12px                   |
| Popover min-width        | 260px                  |
| Gap between trigger & popover | 10px              |
| Row padding              | 7px 6px                |
| Key cap padding          | 3px 6px                |
| Key cap border-radius    | 4px                    |
| Key cap min-width        | 18px                   |
| Key cap inline gap (combos) | 3px                 |
| Pin icon size            | 12–14px                |

### Animation timing

- Trigger color/border hover: 150ms ease
- Trigger active scale: 150ms ease (to 0.96)
- Popover open/close: 150ms ease (opacity + translateY 4px)

## Icons

Two icons are used. In production, use whatever icon library the codebase already has (Lucide, Radix, Heroicons, etc.) — these are the closest equivalents:

| Slot       | Lucide name | Approximate paths (for reference)                                                                                  |
|------------|-------------|--------------------------------------------------------------------------------------------------------------------|
| Trigger    | `circle-help` / `help-circle` | Circle + question-mark glyph                                                                       |
| Pin toggle | `pin`       | Standard map-pin                                                                                                    |

Both icons render at 16px (trigger) and 12–14px (pin), stroke-width 1.7, stroke-linecap and -linejoin `round`, `fill="none"` with `currentColor` stroke.

## Sample Data

The prototype uses these shortcuts for the demo — keep the same shape, swap actual values to match the host app:

```ts
const SHORTCUTS: Shortcut[] = [
  { group: 'Навигация',  label: 'Детали узла',       keys: ['2×']   },
  { group: 'Навигация',  label: 'Контекстное меню',  keys: ['RMB']  },
  { group: 'Навигация',  label: 'Сбросить вид',      keys: ['0']    },

  { group: 'Действия',   label: 'Добавить узел',     keys: ['N']    },
  { group: 'Действия',   label: 'Создать связь',     keys: ['L']    },
  { group: 'Действия',   label: 'Удалить',           keys: ['Del']  },

  { group: 'Выделение',  label: 'Выделить всё',      keys: ['⌘','A']},
  { group: 'Выделение',  label: 'Снять выделение',   keys: ['Esc']  },
];
```

## State Management

The component owns its own local state — no external state required:

- `open: boolean` — whether the popover is currently visible (driven by hover or click)
- `pinned: boolean` — sticky open mode toggled by the pin button

`isOpen = pinned || open`. No data fetching, no global state, no side effects beyond the DOM listeners on the trigger.

## Files in this Handoff

- `Help Button — Shortcuts.html` — Standalone working prototype. Open in a browser to see the component in 5 positions (centered + 4 corners) with controls for theme, trigger mode (hover/click), and direction (up/down). Use it as the visual ground truth while implementing.

## Implementation Checklist

- [ ] Component accepts the props in the API table above
- [ ] Trigger is a 36×36 rounded square with the `?` icon
- [ ] Hover on trigger reveals popover (default mode)
- [ ] `trigger="click"` toggles the popover and closes on outside click + Escape
- [ ] Popover renders grouped shortcuts with category labels
- [ ] Multiple keys in `keys` render as separate inline kbd caps with 3px gap
- [ ] Row hover background highlight
- [ ] Pin button in header, hidden until popover hover, locks open when active
- [ ] Pin click does not bubble to close the popover
- [ ] 4 positioning variants work (down-right, down-left, up-right, up-left) — or replace with Floating UI / Radix Popover for auto-flip
- [ ] Both light and dark themes map to the host codebase's tokens
- [ ] Geist (or host equivalent) sans + mono pairing for label vs. kbd contrast
- [ ] `role="button"`, `tabIndex={0}`, `aria-label`, and Enter/Space activation on the trigger (since it's a `div`, not a `button`)
