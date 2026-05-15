# Reader Redesign — handoff to Claude Code

This folder is the deliverable for migrating the prototype design system
into the real codebase. It's structured so that you can feed each file to
Claude Code in sequence and get predictable output.

## Order of operations

1. **`01-system.md`** — read first. Defines the design philosophy,
   semantic vocabulary (when to use Card vs Panel, etc.), and explicit
   anti-patterns (things the old codebase did that we don't want anymore).

2. **`02-tokens.md` + `tokens.css` + `tailwind.config.ts`** — copy
   `tokens.css` into your `src/styles/` and merge `tailwind.config.ts`
   with your existing config. Every component depends on these tokens —
   nothing else should land before they do.

3. **`03-components.md` + `components/*.tsx`** — the primitives.
   Replace your existing `Button`, `Card`, `Chip`, etc. with these or
   align yours to match the prop shapes. The TSX files are ready to drop
   in (Tailwind classes, lucide-react icons, no extra deps).

4. **`04-pages.md`** — per-page migration notes. For each screen
   (BookReader, TopicList, TopicGraph, etc.) there's a checklist of what
   changes, which old components get retired, and which new ones replace them.

## How to drive Claude Code

Open Claude Code in your repo and paste:

> "Read `handoff/01-system.md` and `handoff/02-tokens.md`. Apply tokens
> to my project: copy `tokens.css` into `src/styles/`, update
> `tailwind.config.ts` to use the semantic colors from there. Don't
> touch components yet."

Then move on:

> "Now read `handoff/03-components.md`. Migrate my `BookCard` component
> to match the new `Card` primitive — keep the existing data shape but
> swap the styling. Show me the diff before applying."

And so on. Doing it one component / one page at a time keeps the diffs
reviewable.

## Files

```
handoff/
├── README.md          ← you are here
├── 01-system.md       ← philosophy, vocabulary, anti-patterns
├── 02-tokens.md       ← what each token means, how to extend
├── 03-components.md   ← primitives reference
├── 04-pages.md        ← per-screen migration notes
├── tokens.css         ← drop into src/styles/tokens.css
├── tailwind.config.ts ← merge into yours
└── components/        ← drop-in TSX (Tailwind + lucide-react)
    ├── Button.tsx
    ├── Card.tsx
    ├── Chip.tsx
    ├── StatusBadge.tsx
    ├── TypeChip.tsx
    ├── Field.tsx
    ├── ChapterTree.tsx
    ├── AppHeader.tsx
    └── EdgePill.tsx
```

## Source of truth

The visual reference lives at the project root in `Reader Redesign.html`.
Whenever a doc says "see the prototype", that's where to look. The
`topic-graph-v3` and `rtl-tafsir` artboards are the canonical states.
