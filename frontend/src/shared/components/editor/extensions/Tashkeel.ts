/**
 * Tashkeel - inline mark для семантической маркировки текста с
 * арабскими диакритическими знаками (Этап 17.0.c, ADR-039).
 *
 * Tashkeel (огласовки / harakat) - короткие гласные знаки над/под
 * арабской буквой: fatha `َ`, kasra `ِ`, damma `ُ`, sukun `ْ`, shadda `ّ`
 * и др. (Unicode range `U+064B`-`U+0652` + superscript alef `U+0670`).
 * В classical тахкике один и тот же абзац может быть с tashkeel
 * (для Корана / хадиса / места требующего точного чтения) или без
 * (для удобства быстрого reading). Mark даёт admin'у возможность
 * **семантически пометить** participle с огласовками, чтобы reader
 * мог toggle их видимость.
 *
 * **MVP (этот PR):** Mark существует и сериализуется. В reader root
 * элемент при `hideTashkeel=true` получает класс `.hide-tashkeel` -
 * см. {@code tiptap.css}. Полноценное **удаление диакритики из
 * глифов** (regex по text nodes для замены `[ً-ْٰ]+`
 * на `""`) - не делается в MVP, требует DOM-walk через React-managed
 * subtree. Записан в backlog «True tashkeel removal через runtime
 * regex DOM walk» и в `gotchas.md`
 *
 * **Schema:**
 * - Mark, не Node - применяется inline к выделенному фрагменту с
 *   огласовками
 * - Не имеет атрибутов - mark сам по себе несёт семантику
 *
 * **HTML serialization:** {@code <span data-type="tashkeel"
 * class="tashkeel">...</span>}
 *
 * **Visual:** см. {@code tiptap.css} - editor показывает текст
 * с лёгким underline-маркером (чтобы admin видел что mark применён).
 * Reader пока визуально не отличает (placeholder под full removal)
 */
import { Mark, mergeAttributes } from '@tiptap/core';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    tashkeel: {
      /**
       * Применяет tashkeel mark к выделенному тексту. Если selection
       * уже имеет mark - снимает (toggle). Пустой selection - no-op
       */
      setTashkeel: () => ReturnType;
      /**
       * Безусловно снимает tashkeel mark с selection
       */
      unsetTashkeel: () => ReturnType;
      /**
       * Toggle: добавляет если нет, снимает если есть
       */
      toggleTashkeel: () => ReturnType;
    };
  }
}

export const Tashkeel = Mark.create<Record<string, never>>({
  name: 'tashkeel',

  parseHTML() {
    return [{ tag: 'span[data-type="tashkeel"]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-type': 'tashkeel',
        class: 'tashkeel',
      }),
      0,
    ];
  },

  addCommands() {
    return {
      setTashkeel:
        () =>
        ({ commands }) =>
          commands.setMark(this.name),
      unsetTashkeel:
        () =>
        ({ commands }) =>
          commands.unsetMark(this.name),
      toggleTashkeel:
        () =>
        ({ commands }) =>
          commands.toggleMark(this.name),
    };
  },
});

export default Tashkeel;
