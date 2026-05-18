/**
 * Парсер inline citation маркеров `[N]` в тексте узла.
 *
 * Подход A (implicit ordinal, ADR-pending) - маркер `[1]` соответствует
 * первому source в `node.inlineCitations[]` (1-based). Если ordinal не
 * найден среди citations - segment всё равно создаётся (type='citation',
 * ordinal=N), но компонент <InlineCitationMarker> отрисует его dead-style
 * (grey, без popover).
 *
 * Паттерн `\[(\d+)\]` - целые числа в квадратных скобках. Не пересекается
 * с типичными markdown-конструкциями ([link](url)) - в plain-text body
 * markdown не парсится.
 *
 * Использование:
 * ```ts
 * const segments = parseInlineCitations('Доказательство [1] - см. [2]');
 * // [
 * //   { type: 'text', text: 'Доказательство ' },
 * //   { type: 'citation', text: '[1]', ordinal: 1 },
 * //   { type: 'text', text: ' - см. ' },
 * //   { type: 'citation', text: '[2]', ordinal: 2 }
 * // ]
 * ```
 */

const MARKER_PATTERN = /\[(\d+)\]/g;

export type ParsedSegment =
  | { type: 'text'; text: string }
  | { type: 'citation'; text: string; ordinal: number };

/**
 * Разбивает строку на последовательность текстовых сегментов и citation
 * маркеров. Если в строке нет ни одного `[N]` - возвращает один text-сегмент.
 * Пустая строка - пустой массив.
 */
export function parseInlineCitations(body: string): ParsedSegment[] {
  if (!body) {
    return [];
  }
  const segments: ParsedSegment[] = [];
  let lastIndex = 0;
  // Регексп с флагом g сохраняет lastIndex между вызовами - явный сброс перед циклом
  MARKER_PATTERN.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = MARKER_PATTERN.exec(body)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ type: 'text', text: body.slice(lastIndex, match.index) });
    }
    // ordinal через индекс группы 1 (\d+) - regex гарантирует число
    const ordinalRaw = match[1];
    if (ordinalRaw !== undefined) {
      segments.push({
        type: 'citation',
        text: match[0],
        ordinal: parseInt(ordinalRaw, 10),
      });
    }
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < body.length) {
    segments.push({ type: 'text', text: body.slice(lastIndex) });
  }
  return segments;
}

/**
 * Проверяет содержит ли строка хотя бы один маркер `[N]`. Используется
 * для conditional fast-path - не рендерить wrapper если ничего парсить
 */
export function hasInlineCitations(body: string): boolean {
  if (!body) {
    return false;
  }
  MARKER_PATTERN.lastIndex = 0;
  return MARKER_PATTERN.test(body);
}
