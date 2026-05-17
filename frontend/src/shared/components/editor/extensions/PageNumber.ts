/**
 * PageNumber - inline decorative element для номера страницы
 * (Этап 17.0.c, ADR-039).
 *
 * Inline-atom (self-contained, без content) который рендерит номер
 * страницы в декоративных скобках `⟦N⟧`. Используется внутри
 * абзаца чтобы пометить границу между логическими страницами
 * оригинальной книги (полезно для citations и cross-referencing).
 *
 * **Schema:**
 * - {@code group: 'inline'} - может быть внутри параграфа
 * - {@code inline: true} - inline-уровневый
 * - {@code atom: true} - self-contained, нет редактируемого content
 *   внутри (как Image)
 *
 * **Attributes:**
 * - {@code number} - номер страницы (1..∞), default 1. Хранится как
 *   number; UI может позже расширить до строки (`'12א'`, `'iv'`) для
 *   non-arabic numerations
 *
 * **HTML serialization:** {@code <span data-type="page-number"
 * data-number="42" class="page-number"></span>}
 *
 * **Visual:** см. {@code tiptap.css} `.page-number::before` -
 * `content: '⟦' attr(data-number) '⟧'` decorative bracketing, small
 * font, gray color, не-выделяемый pointer-events
 */
import { Node, mergeAttributes } from '@tiptap/core';

export interface PageNumberAttributes {
  number: number;
}

function normalizeNumber(raw: string | number | null | undefined): number {
  const n = typeof raw === 'number' ? raw : raw ? Number.parseInt(raw, 10) : NaN;
  return Number.isFinite(n) && n >= 1 ? n : 1;
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    pageNumber: {
      /**
       * Вставляет PageNumber atom в текущую позицию курсора с
       * указанным номером. Если selection не пустой - сначала схлопнет
       * в позицию start, потом вставит
       */
      setPageNumber: (number: number) => ReturnType;
    };
  }
}

export const PageNumber = Node.create<Record<string, never>>({
  name: 'pageNumber',
  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  draggable: false,

  addAttributes() {
    return {
      number: {
        default: 1,
        parseHTML: (element) => normalizeNumber(element.getAttribute('data-number')),
        renderHTML: (attributes) => ({
          'data-number': String(normalizeNumber(attributes.number as number | null)),
        }),
      },
    };
  },

  parseHTML() {
    return [{ tag: 'span[data-type="page-number"]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-type': 'page-number',
        class: 'page-number',
      }),
    ];
  },

  addCommands() {
    return {
      setPageNumber:
        (number) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: { number: normalizeNumber(number) },
          }),
    };
  },
});

export default PageNumber;
