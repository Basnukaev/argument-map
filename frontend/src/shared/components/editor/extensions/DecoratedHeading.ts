/**
 * DecoratedHeading - блочный node для заголовков с орнаментом
 * (Этап 17.0.c, ADR-039).
 *
 * Классический арабский тахкик использует декоративные заголовки
 * с symmetric ornament glyph по обе стороны: `◆ Имя главы ◆` или
 * `❀ Подраздел ❀`. Это не просто h1/h2 - семантически другая роль,
 * другой ornament + не должен попадать в StarterKit Heading-toggle
 * (чтобы не путать с обычными заголовками).
 *
 * **Schema:**
 * - {@code group: 'block'} - ведёт себя как блочный заголовок
 * - {@code content: 'inline*'} - inline текст (как у обычных headings)
 * - {@code defining: true} - paragraph внутри не схлопывается при backspace
 *
 * **Attributes:**
 * - {@code level} - 1..4. Влияет на тэг (`h1`/`h2`/`h3`/`h4`) и
 *   font-size. Default 2
 * - {@code ornament} - 'diamond' | 'flower' | 'star' | 'crescent'.
 *   Glyph через CSS `::before`/`::after`. Default 'diamond'
 *
 * **HTML serialization:** {@code <h2 data-type="decorated-heading"
 * data-level="2" data-ornament="flower" class="decorated-heading">...</h2>}
 *
 * **Visual:** см. {@code tiptap.css} `.decorated-heading[data-ornament=...]`
 * - subtle gray glyphs flanking heading text, центрированный layout
 */
import { Node, mergeAttributes } from '@tiptap/core';

export const HEADING_LEVELS = [1, 2, 3, 4] as const;
export type DecoratedHeadingLevel = (typeof HEADING_LEVELS)[number];

export const HEADING_ORNAMENTS = ['diamond', 'flower', 'star', 'crescent'] as const;
export type DecoratedHeadingOrnament = (typeof HEADING_ORNAMENTS)[number];

export interface DecoratedHeadingAttributes {
  level: DecoratedHeadingLevel;
  ornament: DecoratedHeadingOrnament;
}

function normalizeLevel(raw: string | number | null | undefined): DecoratedHeadingLevel {
  const n = typeof raw === 'number' ? raw : raw ? Number.parseInt(raw, 10) : NaN;
  if (n === 1 || n === 2 || n === 3 || n === 4) return n;
  return 2;
}

function normalizeOrnament(
  raw: string | null | undefined,
): DecoratedHeadingOrnament {
  if (raw && (HEADING_ORNAMENTS as readonly string[]).includes(raw)) {
    return raw as DecoratedHeadingOrnament;
  }
  return 'diamond';
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    decoratedHeading: {
      /**
       * Преобразует текущий блок в DecoratedHeading с указанным
       * level и ornament. Использует setNode (как StarterKit Heading)
       */
      setDecoratedHeading: (attrs: Partial<DecoratedHeadingAttributes>) => ReturnType;
      /**
       * Превращает DecoratedHeading обратно в paragraph
       */
      unsetDecoratedHeading: () => ReturnType;
    };
  }
}

export const DecoratedHeading = Node.create<Record<string, never>>({
  name: 'decoratedHeading',
  group: 'block',
  content: 'inline*',
  defining: true,

  addAttributes() {
    return {
      level: {
        default: 2 as DecoratedHeadingLevel,
        parseHTML: (element) => {
          const fromData = element.getAttribute('data-level');
          if (fromData) return normalizeLevel(fromData);
          // fallback - tag name h1..h4
          const tag = element.tagName.toLowerCase();
          if (tag === 'h1') return 1;
          if (tag === 'h2') return 2;
          if (tag === 'h3') return 3;
          if (tag === 'h4') return 4;
          return 2;
        },
        renderHTML: (attributes) => ({
          'data-level': String(normalizeLevel(attributes.level as number | null)),
        }),
      },
      ornament: {
        default: 'diamond' as DecoratedHeadingOrnament,
        parseHTML: (element) => normalizeOrnament(element.getAttribute('data-ornament')),
        renderHTML: (attributes) => ({
          'data-ornament': normalizeOrnament(attributes.ornament as string | null),
        }),
      },
    };
  },

  parseHTML() {
    // принимаем любой h1..h4 с data-type="decorated-heading".
    // priority выше дефолтного 50 - чтобы StarterKit Heading
    // не перехватил h2[data-type="decorated-heading"] (он матчит
    // просто `h1`..`h6` без attr check, и при равном priority
    // выигрывает по порядку регистрации)
    return [
      { tag: 'h1[data-type="decorated-heading"]', priority: 60 },
      { tag: 'h2[data-type="decorated-heading"]', priority: 60 },
      { tag: 'h3[data-type="decorated-heading"]', priority: 60 },
      { tag: 'h4[data-type="decorated-heading"]', priority: 60 },
    ];
  },

  renderHTML({ node, HTMLAttributes }) {
    const level = normalizeLevel(node.attrs.level as number | null);
    const tag = `h${level}`;
    return [
      tag,
      mergeAttributes(HTMLAttributes, {
        'data-type': 'decorated-heading',
        class: 'decorated-heading',
      }),
      0,
    ];
  },

  addCommands() {
    return {
      setDecoratedHeading:
        (attrs) =>
        ({ commands }) =>
          commands.setNode(this.name, {
            level: normalizeLevel(attrs.level),
            ornament: normalizeOrnament(attrs.ornament),
          }),
      unsetDecoratedHeading:
        () =>
        ({ commands }) =>
          commands.setNode('paragraph'),
    };
  },
});

export default DecoratedHeading;
