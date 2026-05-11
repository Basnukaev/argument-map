/** Покрывает блоки Unicode: Arabic, Arabic Supplement, Arabic Extended-A,
 * Arabic Presentation Forms-A/B (huruf, harakat, formed/joined glyphs). */
// eslint-disable-next-line no-irregular-whitespace -- U+FEFF в Arabic Presentation Forms-B
const ARABIC_SCRIPT = /[؀-ۿݐ-ݿࢠ-ࣿﭐ-﷿ﹰ-﻿]/;

/** Содержит ли строка арабские символы - триггер для RTL/naskh-рендера. */
export function hasArabicScript(text?: string): boolean {
  if (!text) return false;
  return ARABIC_SCRIPT.test(text);
}

const DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDate(iso?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return DATE_FORMAT.format(d);
}

export function shortId(id?: string): string {
  if (!id) return '—';
  return id.slice(0, 8);
}
