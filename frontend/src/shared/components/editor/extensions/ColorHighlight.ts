/**
 * ColorHighlight - inline mark для подсветки текста одним из
 * предопределённых цветов (Этап 17.0.b, ADR-039).
 *
 * MVP whitelist цветов: `red` / `blue` / `green` / `yellow` / `purple`.
 * Custom hex / picker - отложено (можно расширить attribute parser
 * добавив regex hex когда возникнет реальная нужда). Whitelist даёт
 * predictable CSS palette из tokens, нет «полностью свободного» цвета
 * который ломает design system.
 *
 * **Schema:**
 * - mark, не node - применяется к inline тексту как Bold/Italic
 *
 * **Attributes:**
 * - {@code color} - 'red' | 'blue' | 'green' | 'yellow' | 'purple'.
 *   Default 'red'. Невалидное значение fallback на 'red'
 *
 * **HTML serialization:** {@code <span data-type="color-highlight"
 * class="color-highlight color-highlight-red">текст</span>}
 *
 * **Visual:** см. {@code tiptap.css} - text-color из Tailwind palette
 * 700-уровня для контраста
 */
import { Mark, mergeAttributes } from '@tiptap/core';

export const HIGHLIGHT_COLORS = ['red', 'blue', 'green', 'yellow', 'purple'] as const;
export type HighlightColor = (typeof HIGHLIGHT_COLORS)[number];

export interface ColorHighlightAttributes {
  color: HighlightColor;
}

function normalizeColor(raw: string | null | undefined): HighlightColor {
  if (raw && (HIGHLIGHT_COLORS as readonly string[]).includes(raw)) {
    return raw as HighlightColor;
  }
  return 'red';
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    colorHighlight: {
      /**
       * Применяет ColorHighlight mark с указанным цветом. Если selection
       * уже содержит этот же цвет - снимает mark (toggle behaviour)
       */
      setColorHighlight: (color: HighlightColor) => ReturnType;
      /**
       * Безусловно снимает ColorHighlight mark с selection
       */
      unsetColorHighlight: () => ReturnType;
    };
  }
}

export const ColorHighlight = Mark.create<Record<string, never>>({
  name: 'colorHighlight',

  addAttributes() {
    return {
      color: {
        default: 'red' as HighlightColor,
        parseHTML: (element) => {
          // допускаем чтение из class (color-highlight-red) или
          // data-color на случай разных вариантов сериализации
          const cls = element.getAttribute('class') ?? '';
          const match = cls.match(/color-highlight-(\w+)/);
          if (match && match[1]) return normalizeColor(match[1]);
          return normalizeColor(element.getAttribute('data-color'));
        },
        renderHTML: (attributes) => {
          const color = normalizeColor(attributes.color as string | null);
          return {
            'data-color': color,
            class: `color-highlight color-highlight-${color}`,
          };
        },
      },
    };
  },

  parseHTML() {
    return [{ tag: 'span[data-type="color-highlight"]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-type': 'color-highlight',
      }),
      0,
    ];
  },

  addCommands() {
    return {
      setColorHighlight:
        (color) =>
        ({ chain, editor }) => {
          const normalized = normalizeColor(color);
          // toggle: если selection уже подсвечен этим же цветом - снять
          const current = editor.getAttributes(this.name) as { color?: string };
          if (editor.isActive(this.name) && current.color === normalized) {
            return chain().unsetMark(this.name).run();
          }
          return chain().setMark(this.name, { color: normalized }).run();
        },
      unsetColorHighlight:
        () =>
        ({ commands }) =>
          commands.unsetMark(this.name),
    };
  },
});

export default ColorHighlight;
