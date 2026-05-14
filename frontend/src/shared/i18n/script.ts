/**
 * Покрывает блоки Unicode: Arabic, Arabic Supplement, Arabic Extended-A,
 * Arabic Presentation Forms-A/B (huruf, harakat, formed/joined glyphs).
 */
// eslint-disable-next-line no-irregular-whitespace -- U+FEFF в Arabic Presentation Forms-B
const ARABIC_SCRIPT = /[؀-ۿݐ-ݿࢠ-ࣿﭐ-﷿ﹰ-﻿]/;

/** Содержит ли строка арабские символы - триггер для font-naskh и аналогичных переключений шрифта. */
export function hasArabicScript(text?: string): boolean {
  if (!text) return false;
  return ARABIC_SCRIPT.test(text);
}

/**
 * Эвристика направления текста по содержимому. Использовать ТОЛЬКО для решений
 * о шрифте или fallback'ах. Для разметки самих узлов и инпутов предпочтительнее
 * `dir="auto"` - браузер сам найдёт первый сильный символ. Локаль интерфейса
 * берётся из useLocaleStore и не должна определяться этой функцией.
 */
export function getTextDirection(text?: string): 'rtl' | 'ltr' {
  return hasArabicScript(text) ? 'rtl' : 'ltr';
}
