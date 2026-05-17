/**
 * Утилита экспорта графа React Flow в PNG / SVG через `html-to-image`.
 *
 * Контекст: backlog «Экспорт графа в PNG / SVG». Используется кнопкой
 * в `GraphPanels` toolbar - пользователь выбирает формат, граф сохраняется
 * как файл через программный клик по `<a download>`.
 *
 * Поведение:
 * - Захватывается React Flow `.react-flow__viewport` element (содержит
 *   все узлы + рёбра, без overlay-панелей)
 * - Перед export рекомендуется `fitView({ padding: 0.1 })` чтобы все
 *   узлы попали в кадр - иначе экспортируется только текущая видимая
 *   часть viewport. Это делает caller, см. `handleExportGraph` в `GraphPanels`
 * - filter исключает controls / minimap / attribution - они не часть
 *   данных графа, а UI-overlay
 * - pixelRatio управляет качеством PNG (1x стандарт, 2x retina-ready).
 *   SVG векторный - pixelRatio не применим
 *
 * Известные ограничения html-to-image:
 * - Внешние шрифты (Google Fonts) могут не зашиться в экспорт без
 *   `fontEmbedCSS` - тогда текст рендерится system fallback. Если станет
 *   проблемой - подгружать CSS и передавать в опции
 * - CORS-картинки (внешние домены без CORS-headers) не попадают в PNG -
 *   у нас в графе картинок нет, поэтому не критично
 * - `box-shadow` иногда обрезается по краям node - workaround padding
 *   через ReactFlow `fitView({ padding: 0.1 })` перед export
 */
import { toPng, toSvg } from 'html-to-image';

/**
 * Slugify русского / арабского / латинского текста для filename:
 * - оставляет only ASCII letters/digits, пробелы → дефисы
 * - чистит cyrillic / arabic / любую non-ASCII через replace
 * - max 60 символов чтобы filename не разрывал FS limits
 * - fallback 'topic' если результат пустой
 */
export function slugifyForFilename(input: string | null | undefined): string {
  if (!input) return 'topic';
  const cleaned = input
    .toLowerCase()
    .normalize('NFKD')
    // оставляем латиницу / цифры / пробелы / дефисы; всё остальное (cyrillic,
    // arabic, punctuation) - убираем. Без транслитерации - простота важнее
    // полноты, пользователь видит файл в downloads и его узнаёт по timestamp
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
  return cleaned.length > 0 ? cleaned : 'topic';
}

/** Локальная дата в формате YYYY-MM-DD для filename. Не ISO с временем -
 * это файл, пользователь чаще ищет по дню чем по часу */
export function todayDateStamp(now: Date = new Date()): string {
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

export function buildExportFilename(
  topicTitle: string | null | undefined,
  ext: 'png' | 'svg',
  now?: Date,
): string {
  return `topic-${slugifyForFilename(topicTitle)}-${todayDateStamp(now)}.${ext}`;
}

/** True для overlay-элементов, которые НЕ должны попадать в экспорт.
 * Экспортируем только actual граф - узлы + рёбра + фон */
export function isExcludedFromExport(node: Element): boolean {
  if (!node.classList) return false;
  if (node.classList.contains('react-flow__controls')) return true;
  if (node.classList.contains('react-flow__minimap')) return true;
  if (node.classList.contains('react-flow__attribution')) return true;
  if (node.classList.contains('react-flow__panel')) return true;
  return false;
}

interface ExportOptions {
  /** density-multiplier для PNG: 1=стандарт, 2=retina, 4=print. SVG ignore */
  pixelRatio?: number;
  /**
   * фон canvas. Если не передан - читается из CSS variable `--c-bg` на
   * `<html>`, что автоматически адаптируется под текущую тему (light/dark).
   * Раньше был хардкод '#ffffff' - dark theme экспортировался на белом
   * фоне, тёмный текст узлов сливался с background
   */
  backgroundColor?: string;
}

/** Считывает effective background для export из CSS var `--c-bg` на <html>.
 * Fallback на '#ffffff' (light) если var не доступен (SSR / тесты) */
function readThemeBackground(): string {
  if (typeof window === 'undefined') return '#ffffff';
  const styles = window.getComputedStyle(document.documentElement);
  const bg = styles.getPropertyValue('--c-bg').trim();
  return bg || '#ffffff';
}

/** Триггерит браузерный download через программный клик по `<a download>`.
 * Не требует backend - data URL уже содержит контент */
function triggerDownload(dataUrl: string, filename: string): void {
  const link = document.createElement('a');
  link.download = filename;
  link.href = dataUrl;
  // append → click → remove - чтобы Firefox/Safari корректно обработали клик
  // без DOM-attachment они иногда не реагируют
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

export async function exportGraphAsPng(
  graphElement: HTMLElement,
  filename: string,
  options: ExportOptions = {},
): Promise<void> {
  const dataUrl = await toPng(graphElement, {
    backgroundColor: options.backgroundColor ?? readThemeBackground(),
    pixelRatio: options.pixelRatio ?? 2,
    filter: (node) => !isExcludedFromExport(node),
  });
  triggerDownload(dataUrl, filename);
}

export async function exportGraphAsSvg(
  graphElement: HTMLElement,
  filename: string,
  options: ExportOptions = {},
): Promise<void> {
  const dataUrl = await toSvg(graphElement, {
    backgroundColor: options.backgroundColor ?? readThemeBackground(),
    filter: (node) => !isExcludedFromExport(node),
  });
  triggerDownload(dataUrl, filename);
}
