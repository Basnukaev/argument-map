/**
 * BlockDir - глобальное расширение, проставляющее `dir="auto"` на все
 * блочные узлы документа (bidi-фикс, ADR-039 follow-up).
 *
 * **Проблема:** контент книг арабский (RTL), но локаль UI может быть
 * русской (LTR). При LTR-локали `<html dir="ltr">`, и блочные узлы
 * (paragraph / heading / blockquote / listItem из StarterKit + кастомные
 * боксы) наследуют LTR base direction. Итог - арабская пунктуация
 * (`.` `:`) и «слабые» символы прилипают к НЕВЕРНОЙ стороне (классический
 * bidi base-direction mismatch).
 *
 * **Решение:** `dir="auto"` заставляет каждый блок определять собственное
 * направление по первому сильному символу (араб → RTL, кириллица/латиница
 * → LTR). Так контент рендерится корректно при любой локали UI - именно
 * это требование: «арабский текст в данных ВСЕГДА отображается правильно».
 *
 * **Почему global attribute, а не per-node renderHTML:** покрывает
 * StarterKit-узлы (paragraph / heading / blockquote / listItem), которые
 * мы не редактируем напрямую, единым местом. Кастомные блочные узлы
 * (AyahBox / HadithBox / Marginalia / DecoratedHeading) добавляют
 * `dir="auto"` сами в своём renderHTML - они не входят в этот список,
 * т.к. требуют ещё и `unicode-bidi: isolate` через CSS.
 *
 * **JSON остаётся чистым:** `parseHTML: () => null` всегда возвращает
 * default (`null`), поэтому Tiptap не сохраняет `dir` как атрибут узла в
 * `getJSON()` - сериализованный ProseMirror JSON не раздувается и не
 * меняет round-trip. Атрибут существует только на выходном HTML/DOM.
 */
import { Extension } from '@tiptap/core';

/** Блочные узлы StarterKit, которым нужен auto-direction. */
const BLOCK_TYPES = ['paragraph', 'heading', 'blockquote', 'listItem'] as const;

export const BlockDir = Extension.create({
  name: 'blockDir',

  addGlobalAttributes() {
    return [
      {
        types: [...BLOCK_TYPES],
        attributes: {
          dir: {
            default: null,
            // Никогда не читаем сохранённый dir обратно в модель - всегда
            // авто-детект. Возврат null = default → не попадает в getJSON()
            parseHTML: () => null,
            renderHTML: () => ({ dir: 'auto' }),
          },
        },
      },
    ];
  },
});

export default BlockDir;
