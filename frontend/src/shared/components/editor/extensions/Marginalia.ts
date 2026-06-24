/**
 * Marginalia - inline-content block для комментария на полях
 * (Этап 17.0.b, ADR-039).
 *
 * Используется для коротких заметок мухаккика (комментатора) которые в
 * классических арабских тахкиках выносятся сбоку от main flow мелким
 * кеглем. На desktop рендерится как float сбоку (left/right логически
 * через RTL-aware `data-side`); на mobile (`max-width: 768px`) -
 * сворачивается в inline-блок после параграфа.
 *
 * **Schema:**
 * - {@code group: 'block'} - сам node блочный (располагается между
 *   параграфами в порядке)
 * - {@code content: 'block+'} - принимает один или больше блоков
 *   (обычно paragraph). Это позволяет `wrapIn` работать с
 *   выделенным параграфом (ProseMirror требует совместимость
 *   parent.content при wrap)
 *
 * **Attributes:**
 * - {@code side} - 'start' | 'end'. Логические направления (RTL-aware):
 *   в LTR start = left, в RTL start = right. Default 'start'
 *
 * **HTML serialization:** {@code <aside data-type="marginalia"
 * data-side="start">текст</aside>}
 *
 * **Visual:** см. {@code tiptap.css} `.marginalia` - small italic font,
 * border-s + bg subtle, absolute float сбоку на desktop. На mobile
 * (через {@code @media}) - inline block с тонкой рамкой
 */
import { Node, mergeAttributes } from '@tiptap/core';

export type MarginaliaSide = 'start' | 'end';

export interface MarginaliaAttributes {
  side: MarginaliaSide;
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    marginalia: {
      /**
       * Оборачивает выделенный текст в Marginalia-блок. Если selection
       * пуст - создаёт пустой aside после текущего параграфа
       */
      setMarginalia: (attrs?: Partial<MarginaliaAttributes>) => ReturnType;
      /**
       * Удаляет Marginalia-обёртку, оставляя текст inline в parent
       * параграфе
       */
      unsetMarginalia: () => ReturnType;
    };
  }
}

export const Marginalia = Node.create<Record<string, never>>({
  name: 'marginalia',
  group: 'block',
  content: 'block+',
  defining: true,

  addAttributes() {
    return {
      side: {
        default: 'start' as MarginaliaSide,
        parseHTML: (element) => {
          const v = element.getAttribute('data-side');
          return v === 'end' || v === 'start' ? v : 'start';
        },
        renderHTML: (attributes) => ({
          'data-side': attributes.side ?? 'start',
        }),
      },
    };
  },

  parseHTML() {
    return [{ tag: 'aside[data-type="marginalia"]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'aside',
      mergeAttributes(HTMLAttributes, {
        'data-type': 'marginalia',
        class: 'marginalia',
        // bidi-фикс: заметка сама определяет направление по тексту внутри,
        // не наследуя LTR-локаль UI
        dir: 'auto',
      }),
      0,
    ];
  },

  addCommands() {
    return {
      setMarginalia:
        (attrs) =>
        ({ commands }) =>
          commands.wrapIn(this.name, attrs),
      unsetMarginalia:
        () =>
        ({ commands }) =>
          commands.lift(this.name),
    };
  },
});

export default Marginalia;
