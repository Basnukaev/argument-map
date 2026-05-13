/**
 * Утилиты для работы с char-offset диапазонами текста внутри HTML-контейнера.
 *
 * Char-offsets считаются по plain text (сумма length всех text nodes по
 * порядку через TreeWalker, HTML теги не считаются). Это стабильнее чем
 * DOM-Range serialization при mutation структуры (например при добавлении
 * <mark>): offsets зависят только от текстового содержимого.
 */

export interface TextRange {
  start: number;
  end: number;
  quote: string;
}

/**
 * Вычисляет char offsets от начала plain text container'а к DOM Range.
 * Возвращает null если range пуст / endpoints не внутри container.
 */
export function computeRangeOffsets(
  container: HTMLElement,
  range: Range,
): TextRange | null {
  if (range.collapsed) {
    return null;
  }
  if (
    !container.contains(range.startContainer) ||
    !container.contains(range.endContainer)
  ) {
    return null;
  }

  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  let offset = 0;
  let start: number | null = null;
  let end: number | null = null;
  let node: Node | null = walker.nextNode();

  while (node) {
    const len = node.textContent?.length ?? 0;
    if (node === range.startContainer) {
      start = offset + range.startOffset;
    }
    if (node === range.endContainer) {
      end = offset + range.endOffset;
    }
    offset += len;
    node = walker.nextNode();
  }

  if (start === null || end === null || end <= start) {
    return null;
  }

  return { start, end, quote: range.toString() };
}

/**
 * Оборачивает text в container'е в <mark class="citation-highlight">
 * по char offsets. Если range охватывает несколько text nodes - создаёт
 * несколько <mark> элементов соответствующих частям (one per node).
 *
 * Идемпотентный: повторный вызов не удваивает highlights, но и не убирает
 * предыдущие - caller должен сам очистить через removeHighlights если надо.
 */
export function applyHighlight(
  container: HTMLElement,
  startOffset: number,
  endOffset: number,
): void {
  if (endOffset <= startOffset) {
    return;
  }
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  let offset = 0;
  const toWrap: Array<{ node: Text; localStart: number; localEnd: number }> = [];

  let node: Node | null = walker.nextNode();
  while (node && node instanceof Text) {
    const len = node.textContent?.length ?? 0;
    const nodeStart = offset;
    const nodeEnd = offset + len;

    if (nodeEnd > startOffset && nodeStart < endOffset) {
      const localStart = Math.max(0, startOffset - nodeStart);
      const localEnd = Math.min(len, endOffset - nodeStart);
      toWrap.push({ node, localStart, localEnd });
    }

    offset += len;
    node = walker.nextNode();
  }

  // Reverse - чтобы не сбить offsets при mutation
  for (const w of toWrap.reverse()) {
    const text = w.node.textContent ?? '';
    const before = text.substring(0, w.localStart);
    const mark = text.substring(w.localStart, w.localEnd);
    const after = text.substring(w.localEnd);
    const parent = w.node.parentNode;
    if (!parent) continue;
    const markElem = document.createElement('mark');
    markElem.className = 'citation-highlight';
    markElem.textContent = mark;
    if (before) {
      parent.insertBefore(document.createTextNode(before), w.node);
    }
    parent.insertBefore(markElem, w.node);
    if (after) {
      parent.insertBefore(document.createTextNode(after), w.node);
    }
    parent.removeChild(w.node);
  }
}

/**
 * Удаляет все <mark class="citation-highlight"> в container'е, склеивая
 * соседние text nodes. Используется перед re-apply highlight (например
 * при смене selection).
 */
export function removeHighlights(container: HTMLElement): void {
  const marks = container.querySelectorAll('mark.citation-highlight');
  for (const mark of Array.from(marks)) {
    const parent = mark.parentNode;
    if (!parent) continue;
    const text = mark.textContent ?? '';
    parent.replaceChild(document.createTextNode(text), mark);
  }
  // Normalize чтобы склеить соседние text nodes
  container.normalize();
}
