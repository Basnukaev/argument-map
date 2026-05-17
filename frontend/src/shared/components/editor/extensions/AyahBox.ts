/**
 * AyahBox - блочный node для аята Корана (Этап 17.0.b, ADR-039).
 *
 * Mirror паттерна {@link HadithBox} с другими атрибутами и визуальным
 * стилем. Используется для обёртывания цитаты аята (или диапазона аятов)
 * с метаданными surah / ayah / опциональным русским переводом.
 *
 * **Schema:**
 * - {@code group: 'block'} - ведёт себя как параграф
 * - {@code content: 'block+'} - внутри один или больше блоков (обычно
 *   paragraph с арабским текстом, опционально - paragraph с переводом)
 * - {@code defining: true} - backspace на пустой первой линии удаляет
 *   node целиком, не схлопывает в parent
 *
 * **Attributes:**
 * - {@code surah} - номер суры (1..114), по умолчанию 1
 * - {@code ayah} - номер аята (1..N), по умолчанию 1. Хранится как
 *   number; UI может позже расширить до строки `'1-5'` для диапазона
 * - {@code translation} - опциональный русский перевод. Пустая строка
 *   означает «без перевода» - в renderHTML атрибут не добавляется
 *
 * **HTML serialization:** {@code <div data-type="ayah-box"
 * data-surah="2" data-ayah="255" data-translation="...">}
 *
 * **Visual:** см. {@code tiptap.css} `.ayah-box` - gold accent border
 * (`amber-400`), subtle gold background (`amber-50`), орнаментальные
 * скобки `﴿ ﴾` в углах (классический Quran-стиль).
 */
import { Node, mergeAttributes } from '@tiptap/core';

export interface AyahBoxAttributes {
  surah: number;
  ayah: number;
  translation: string;
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    ayahBox: {
      /**
       * Оборачивает текущее selection в AyahBox node с переданными
       * attributes. Если selection пустой - создаёт пустой AyahBox с
       * одним параграфом внутри
       */
      setAyahBox: (attrs?: Partial<AyahBoxAttributes>) => ReturnType;
      /**
       * Снимает AyahBox - вытаскивает content наружу как обычные
       * параграфы. Используется для «убрать аят-бокс» в toolbar
       */
      unsetAyahBox: () => ReturnType;
    };
  }
}

export const AyahBox = Node.create<Record<string, never>>({
  name: 'ayahBox',
  group: 'block',
  content: 'block+',
  defining: true,

  addAttributes() {
    return {
      surah: {
        default: 1,
        parseHTML: (element) => {
          const raw = element.getAttribute('data-surah');
          const n = raw ? Number.parseInt(raw, 10) : NaN;
          return Number.isFinite(n) && n >= 1 && n <= 114 ? n : 1;
        },
        renderHTML: (attributes) => ({
          'data-surah': String(attributes.surah ?? 1),
        }),
      },
      ayah: {
        default: 1,
        parseHTML: (element) => {
          const raw = element.getAttribute('data-ayah');
          const n = raw ? Number.parseInt(raw, 10) : NaN;
          return Number.isFinite(n) && n >= 1 ? n : 1;
        },
        renderHTML: (attributes) => ({
          'data-ayah': String(attributes.ayah ?? 1),
        }),
      },
      translation: {
        default: '',
        parseHTML: (element) => element.getAttribute('data-translation') ?? '',
        renderHTML: (attributes) => {
          if (!attributes.translation) return {};
          return { 'data-translation': attributes.translation };
        },
      },
    };
  },

  parseHTML() {
    return [{ tag: 'div[data-type="ayah-box"]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-type': 'ayah-box',
        class: 'ayah-box',
      }),
      0,
    ];
  },

  addCommands() {
    return {
      setAyahBox:
        (attrs) =>
        ({ commands }) =>
          commands.wrapIn(this.name, attrs),
      unsetAyahBox:
        () =>
        ({ commands }) =>
          commands.lift(this.name),
    };
  },
});

export default AyahBox;
