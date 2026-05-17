/**
 * Footnote - inline mark для сносок с auto-numbering (Этап 17.0.b,
 * ADR-039 variant B).
 *
 * Простая реализация (Mark, не сложная Mark+Node coordination): mark
 * оборачивает выделенный текст в `<sup data-type="footnote">`. Содержимое
 * сноски хранится в attribute {@code content} - browser показывает его
 * как нативный tooltip через атрибут {@code title} при hover.
 *
 * **Auto-numbering** делаем чисто CSS через counter в `tiptap.css`:
 * ```css
 * .ProseMirror { counter-reset: footnote; }
 * .footnote-ref::before {
 *   counter-increment: footnote;
 *   content: '[' counter(footnote) ']';
 * }
 * ```
 * Так номер `[1]`, `[2]`, `[3]` появляется автоматически в порядке
 * следования mark'ов в документе. Никакого JS бухгалтерства,
 * пересчитывается на любом DOM update
 *
 * **Schema:**
 * - {@code inline: true} - mark может применяться только к inline
 *   content (текст внутри параграфа)
 * - {@code excludes: ''} - НЕ исключает другие marks (можно
 *   bold+footnote одновременно)
 *
 * **Attributes:**
 * - {@code content} - текст сноски, по умолчанию пустая строка.
 *   Не валидируется по длине - admin сам отвечает за разумность
 *
 * **HTML serialization:** {@code <sup data-type="footnote"
 * class="footnote-ref" title="комментарий мухаккика">текст-якорь</sup>}
 *
 * **Visual:** см. {@code tiptap.css} `.footnote-ref` - small superscript,
 * synий, cursor-help. Browser сам показывает tooltip с content
 */
import { Mark, mergeAttributes } from '@tiptap/core';

export interface FootnoteAttributes {
  content: string;
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    footnote: {
      /**
       * Применяет footnote mark к текущему selection. Если selection
       * пустой - команда no-op (Tiptap не применяет mark на zero-width
       * range). Если уже есть footnote mark - обновляет content
       */
      setFootnote: (content: string) => ReturnType;
      /**
       * Снимает footnote mark с текущего selection
       */
      unsetFootnote: () => ReturnType;
    };
  }
}

export const Footnote = Mark.create<Record<string, never>>({
  name: 'footnote',

  addAttributes() {
    return {
      content: {
        default: '',
        parseHTML: (element) =>
          element.getAttribute('title') ?? element.getAttribute('data-content') ?? '',
        renderHTML: (attributes) => {
          if (!attributes.content) return {};
          // дублируем content в title (native tooltip) и data-content
          // (надёжный round-trip - title может быть стрипнут sanitizer'ом)
          return {
            title: attributes.content,
            'data-content': attributes.content,
          };
        },
      },
    };
  },

  parseHTML() {
    return [{ tag: 'sup[data-type="footnote"]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'sup',
      mergeAttributes(HTMLAttributes, {
        'data-type': 'footnote',
        class: 'footnote-ref',
      }),
      0,
    ];
  },

  addCommands() {
    return {
      setFootnote:
        (content) =>
        ({ commands }) =>
          commands.setMark(this.name, { content }),
      unsetFootnote:
        () =>
        ({ commands }) =>
          commands.unsetMark(this.name),
    };
  },
});

export default Footnote;
