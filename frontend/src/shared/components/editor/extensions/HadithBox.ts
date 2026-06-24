/**
 * HadithBox - первый custom Tiptap extension платформы (Этап 17.0, ADR-039).
 *
 * Блочный node для обёртывания цитаты хадиса с метаданными source / grade.
 * Это reference-extension для следующих (AyahBox / Marginalia / Footnote
 * и т.д.) - подробные комментарии чтобы команда могла копировать паттерн.
 *
 * **Schema:**
 * - {@code group: 'block'} - node ведёт себя как параграф (не inline)
 * - {@code content: 'block+'} - внутри один или больше блоков (paragraph,
 *   list, blockquote и т.д.) - даёт user'у возможность класть многострочные
 *   хадисы с разной markup внутри
 * - {@code defining: true} - при backspace на пустой первой линии node
 *   не схлопывается в parent, а удаляется целиком (UX expectation)
 *
 * **Attributes:**
 * - {@code source} - текст ссылки на источник, например "Бухари 1",
 *   "Муслим 32, кит. аль-Иман" - произвольная строка, отображается в
 *   мета-строке под текстом хадиса
 * - {@code grade} - оценка хадиса: 'sahih' (достоверный), 'hasan' (хороший),
 *   'daif' (слабый). Frontend toolbar предлагает 3 варианта в dropdown.
 *   Хранится как строка (не enum - см. frontend/CLAUDE.md «без enum»)
 *
 * **HTML serialization:**
 * - parseHTML / renderHTML дают тэг {@code <div data-type="hadith-box">}.
 *   Атрибуты source / grade прокидываются как data-source / data-grade.
 *   Это даёт SSR-friendly output для {@code generateHTML(json, extensions)}
 *   путь (PDF export / Open Graph preview - см. ADR-039 Consequences)
 *
 * **Visual:** см. {@code tiptap.css} - розовый peach background, dashed
 * border, "«" ornament в углу. Реализует classical tahqiq стиль
 * хадис-бокса (см. ADR-039 «Контекст»).
 */
import { Node, mergeAttributes } from '@tiptap/core';

export type HadithGrade = 'sahih' | 'hasan' | 'daif';

export interface HadithBoxAttributes {
  source: string;
  grade: HadithGrade;
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    hadithBox: {
      /**
       * Оборачивает текущее selection в HadithBox node с переданными
       * attributes. Если selection пустой - создаёт пустой HadithBox с
       * одним параграфом внутри
       */
      setHadithBox: (attrs?: Partial<HadithBoxAttributes>) => ReturnType;
      /**
       * Снимает HadithBox - вытаскивает content наружу как обычные
       * параграфы. Используется для "отменить хадис-бокс" в toolbar
       */
      unsetHadithBox: () => ReturnType;
    };
  }
}

export const HadithBox = Node.create<Record<string, never>>({
  name: 'hadithBox',
  group: 'block',
  content: 'block+',
  defining: true,

  addAttributes() {
    return {
      source: {
        default: '',
        parseHTML: (element) => element.getAttribute('data-source') ?? '',
        renderHTML: (attributes) => {
          if (!attributes.source) return {};
          return { 'data-source': attributes.source };
        },
      },
      grade: {
        default: 'sahih' as HadithGrade,
        parseHTML: (element) => {
          const v = element.getAttribute('data-grade');
          return v === 'hasan' || v === 'daif' || v === 'sahih' ? v : 'sahih';
        },
        renderHTML: (attributes) => ({
          'data-grade': attributes.grade ?? 'sahih',
        }),
      },
    };
  },

  parseHTML() {
    return [{ tag: 'div[data-type="hadith-box"]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-type': 'hadith-box',
        class: 'hadith-box',
        // bidi-фикс: бокс сам определяет направление по тексту внутри,
        // не наследуя LTR-локаль UI (unicode-bidi: isolate в CSS)
        dir: 'auto',
      }),
      0, // content hole - Tiptap кладёт сюда вложенные блоки
    ];
  },

  addCommands() {
    return {
      setHadithBox:
        (attrs) =>
        ({ commands }) =>
          commands.wrapIn(this.name, attrs),
      unsetHadithBox:
        () =>
        ({ commands }) =>
          commands.lift(this.name),
    };
  },
});

export default HadithBox;
