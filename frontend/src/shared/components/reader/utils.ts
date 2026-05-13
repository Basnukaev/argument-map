import DOMPurify from 'dompurify';

export type ReaderMode = 'text' | 'pdf';

/**
 * Эвристика: если контент содержит арабские символы (Unicode-блок
 * 0x0600-0x06FF), это арабский текст - рендерим RTL + naskh-шрифт.
 */
export function isArabicText(text: string | undefined): boolean {
  if (!text) return false;
  return /[؀-ۿ]/.test(text);
}

/**
 * Двухступенчатая санитизация HTML контента страницы:
 * 1. Убираются shamela-specific артефакты - PUA glyphs (U+820C `舄`,
 *    U+E000..U+F8FF Private Use Area). Shamela использует фирменный шрифт
 *    MUSHAF который их рендерит как иконки; Noto Naskh Arabic показал бы
 *    их как мусор. ﷺ и bismillah-лигатура (U+FDFA, U+FDFD) сохраняются
 * 2. DOMPurify убирает потенциально опасные элементы (script tags,
 *    event handlers, javascript: URIs). Сохраняем data-attributes для
 *    .book-content стилизации
 */
export function sanitizePageHtml(html: string): string {
  const stripped = html.replace(/\u{820C}/gu, '').replace(/[\u{E000}-\u{F8FF}]/gu, '');
  return DOMPurify.sanitize(stripped, { ADD_ATTR: ['data-type'] });
}

/**
 * shamela-bibliography приходит одной плоской строкой с ключами через
 * пробел: "الكتاب: ... المؤلف: ... تحقيق: ... الطبعة: ...". Вставляем
 * \n перед каждым ключом (кроме первого) - вместе с white-space: pre-line
 * в CSS это даёт многострочное отображение.
 *
 * Список ключей расширяется по мере обнаружения новых форматов в книгах
 * shamela.
 */
const SHAMELA_BIBLIOGRAPHY_KEYS = [
  'الكتاب',
  'المؤلف',
  'المحقق',
  'تحقيق',
  'الناشر',
  'الطبعة',
  'سنة النشر',
  'تاريخ النشر',
  'عدد الأجزاء',
  'الجزء',
  'الصفحة',
  'عدد الصفحات',
  'حجم الكتاب',
  'مصدر الكتاب',
];

export function formatShamelaBibliography(raw: string | undefined): string {
  if (!raw) return '';
  let result = raw.trim();
  for (const key of SHAMELA_BIBLIOGRAPHY_KEYS) {
    const re = new RegExp(`\\s+(${key}\\s*:)`, 'g');
    result = result.replace(re, '\n$1');
  }
  return result;
}
