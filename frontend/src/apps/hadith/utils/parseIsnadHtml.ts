/**
 * Токенизация `full_text_ar` хадиса из alminasa в сегменты для безопасного
 * рендера (НЕ dangerouslySetInnerHTML). Контракт зеркалит backend-regex
 * AlminasaIsnadParser: атрибуты тегов БЕЗ кавычек.
 *
 * Распознаются ровно два тега:
 *  - `<a class=rawy id=N>ИМЯ</a>` — кликабельный передатчик (externalId=N);
 *  - `<a class=matn>…</a>` — стилевое выделение матна (БЕЗ id, НИКОГДА не
 *    кликабельно, не входит в rawy-карту).
 *
 * Всё прочее (литеральный текст между тегами, включая `"`, `،`, пробелы,
 * литералы `عنه`) — plain-сегменты как есть. Незакрытые/неизвестные теги
 * не ломают парсер — остаток уходит в text.
 */

export type IsnadSegmentKind = 'text' | 'rawy' | 'matn';

export interface IsnadSegment {
  kind: IsnadSegmentKind;
  /** Текстовое содержимое сегмента (для rawy/matn — внутренность тега). */
  text: string;
  /** Внешний id передатчика (alminasa) — только для kind='rawy'. */
  externalId?: string;
}

// Открывающий тег рави: <a class=rawy id=4698>. Атрибуты без кавычек,
// порядок class→id, пробелы гибкие. Захватываем id в группу. Флаг `y`
// (sticky) — сопоставляем строго с текущей позиции, не сканируя вперёд.
const RAWY_OPEN = /<a\s+class=rawy\s+id=([^\s>]+)\s*>/iy;
// Открывающий тег матна: <a class=matn>. Без id.
const MATN_OPEN = /<a\s+class=matn\s*>/iy;
// Закрывающий </a>. Флаг `g` (НЕ sticky) — ищем вперёд от lastIndex до
// первого закрытия (между открытием и закрытием — текст имени/матна).
const A_CLOSE = /<\/a\s*>/ig;

/**
 * Парсит HTML-строку иснада в плоский массив сегментов. Пустая строка →
 * пустой массив. Строка без тегов → один text-сегмент.
 */
export function parseIsnadHtml(html: string | null | undefined): IsnadSegment[] {
  if (!html) return [];

  const segments: IsnadSegment[] = [];
  let pos = 0;
  let textStart = 0;

  /** Сбрасывает накопленный plain-текст [textStart, pos) в сегмент. */
  const flushText = (end: number) => {
    if (end > textStart) {
      segments.push({ kind: 'text', text: html.slice(textStart, end) });
    }
  };

  while (pos < html.length) {
    if (html[pos] !== '<') {
      pos += 1;
      continue;
    }

    // Пробуем сопоставить один из распознаваемых тегов с позиции pos.
    RAWY_OPEN.lastIndex = pos;
    MATN_OPEN.lastIndex = pos;
    const rawyMatch = RAWY_OPEN.exec(html);
    const matnMatch = MATN_OPEN.exec(html);

    if (rawyMatch) {
      flushText(pos);
      const externalId = rawyMatch[1] ?? '';
      const innerStart = pos + rawyMatch[0].length;
      A_CLOSE.lastIndex = innerStart;
      const close = A_CLOSE.exec(html);
      if (!close) {
        // Незакрытый тег рави: остаток строки трактуем как inner-текст рави
        // (не теряем имя), парсинг завершаем.
        segments.push({ kind: 'rawy', text: html.slice(innerStart), externalId });
        return segments;
      }
      segments.push({
        kind: 'rawy',
        text: html.slice(innerStart, close.index),
        externalId,
      });
      pos = A_CLOSE.lastIndex;
      textStart = pos;
      continue;
    }

    if (matnMatch) {
      flushText(pos);
      const innerStart = pos + matnMatch[0].length;
      A_CLOSE.lastIndex = innerStart;
      const close = A_CLOSE.exec(html);
      if (!close) {
        segments.push({ kind: 'matn', text: html.slice(innerStart) });
        return segments;
      }
      segments.push({ kind: 'matn', text: html.slice(innerStart, close.index) });
      pos = A_CLOSE.lastIndex;
      textStart = pos;
      continue;
    }

    // Неизвестный/посторонний `<` — оставляем как plain-текст, двигаемся дальше.
    pos += 1;
  }

  flushText(html.length);
  return segments;
}
