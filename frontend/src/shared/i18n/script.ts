/**
 * Покрывает блоки Unicode: Arabic, Arabic Supplement, Arabic Extended-A,
 * Arabic Presentation Forms-A/B (huruf, harakat, formed/joined glyphs).
 */
// eslint-disable-next-line no-irregular-whitespace -- U+FEFF в Arabic Presentation Forms-B
const ARABIC_SCRIPT = /[؀-ۿݐ-ݿࢠ-ࣿﭐ-﷿ﹰ-﻿]/;

/**
 * Религиозные лигатуры которые пишутся как один codepoint в Arabic Presentation
 * Forms-A, но в русском/английском тексте функционируют как символ-«орнамент»
 * (после имён, после Аллаха, в bismillah). Их наличие не делает текст
 * арабским — «Празднование любви к Пророку ﷺ» это русский текст с одной
 * лигатурой. Убираем их перед детектом, чтобы не включать font-naskh на
 * mixed-content.
 *
 *   ﷺ U+FDFA  ṣallā 'llāhu ʿalayhi wa-sallam
 *   ﷻ U+FDFB  jalla jalāluhu
 *   ﷲ U+FDF2  allāh
 *   ﷽ U+FDFD  bismillāhi r-raḥmāni r-raḥīm
 *   ﷽-ﷺ типовые «benediction marks» в популярных текстах
 */
const ARABIC_NEUTRAL_LIGATURES = /[ﷺﷻﷲ﷽]/g;

/** Содержит ли строка арабские символы - триггер для font-naskh и аналогичных переключений шрифта. */
export function hasArabicScript(text?: string): boolean {
  if (!text) return false;
  const stripped = text.replace(ARABIC_NEUTRAL_LIGATURES, '');
  return ARABIC_SCRIPT.test(stripped);
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
