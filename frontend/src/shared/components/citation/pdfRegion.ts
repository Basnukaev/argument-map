/**
 * Чистые хелперы для REGION-цитат по PDF (ADR-067, mode PDF_LINK).
 *
 * Вынесены из компонентов чтобы быть юнит-тестируемыми без layout
 * (jsdom не считает реальные размеры элементов, поэтому pointer-drag
 * сам по себе не тестируется — тестируем нормализацию координат).
 */

/** Нормализованный прямоугольник bbox, каждое значение 0..1 (доля от
 *  размера PDF-страницы; x,y = верхний левый угол). Совпадает с
 *  серверным `PdfBbox` (components['schemas']['PdfBbox']). */
export interface NormalizedBbox {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Прямоугольник в пикселях относительно левого-верхнего угла страницы. */
export interface PixelRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

/** Размеры отрендеренной PDF-страницы в пикселях. */
export interface PageDims {
  width: number;
  height: number;
}

/**
 * Регион REGION-цитаты, который выбрал пользователь: том (0-based
 * fileIndex), страница (1-based) и нормализованный bbox. Живёт здесь
 * (а не в компоненте) чтобы CitationPicker импортировал тип, не таща
 * react-pdf в module graph (см. lazy-load в CitationPicker).
 */
export interface PdfRegion {
  fileIndex: number;
  pageNumber: number;
  bbox: NormalizedBbox;
}

const clamp01 = (v: number): number => Math.max(0, Math.min(1, v));

/**
 * Переводит пиксельный прямоугольник (относительно отрендеренной
 * `<Page>`) в нормализованный bbox 0..1, деля на ширину/высоту страницы.
 * Каждая координата клампится в [0,1] — drag, начатый/законченный за
 * пределами страницы, не даёт значений вне диапазона.
 *
 * Возвращает null если размеры страницы невалидны (0 или отрицательны).
 */
export function pixelRectToBbox(rect: PixelRect, page: PageDims): NormalizedBbox | null {
  if (page.width <= 0 || page.height <= 0) return null;
  // Клампим края (left/top и right/bottom) в пределах страницы, затем
  // выводим x/y/width/height — так width/height не уходят за границу.
  const x1 = clamp01(rect.left / page.width);
  const y1 = clamp01(rect.top / page.height);
  const x2 = clamp01((rect.left + rect.width) / page.width);
  const y2 = clamp01((rect.top + rect.height) / page.height);
  return {
    x: Math.min(x1, x2),
    y: Math.min(y1, y2),
    width: Math.abs(x2 - x1),
    height: Math.abs(y2 - y1),
  };
}

/**
 * Строит query-suffix для deep-link на reader с PDF-цитатой:
 * `?pdf=1&pdfPageNumber=P[&fileIndex=N][&bbox=x,y,w,h]`.
 *
 * `fileIndex` добавляется только если != null (одно-томные книги
 * дефолтятся в 0 на стороне reader'а и параметр опускается). `bbox`
 * добавляется только если задан (x обязателен как маркер наличия).
 *
 * Вынесено для unit-тестирования логики latent multi-volume фикса —
 * сами `*CitationsSection` зовут его inline.
 */
export function buildPdfDeepLinkQuery(opts: {
  pageNumber: number;
  fileIndex?: number | null;
  bbox?: { x?: number; y?: number; width?: number; height?: number } | null;
}): string {
  const { pageNumber, fileIndex, bbox } = opts;
  const fileIndexStr = fileIndex != null ? `&fileIndex=${fileIndex}` : '';
  const bboxStr =
    bbox && bbox.x != null ? `&bbox=${bbox.x},${bbox.y},${bbox.width},${bbox.height}` : '';
  return `?pdf=1&pdfPageNumber=${pageNumber}${fileIndexStr}${bboxStr}`;
}
