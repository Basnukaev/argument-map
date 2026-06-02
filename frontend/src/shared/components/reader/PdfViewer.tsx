import { useEffect, useMemo, useState } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Download,
  Loader2,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Select, { type SelectOption } from '@/shared/components/ui/Select';
import { API_BASE_URL, apiGetRaw, ApiError } from '@/shared/api/client';
import { hasArabicScript, useLocaleStore, useT } from '@/shared/i18n';

import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

// Локальный тип. isCover пришёл в backend response, types.ts регенерируется
// при следующем `npm run generate-api` после рестарта бэка. До регенерации
// поле читается через optional.
type PdfFileInfoEntry = {
  index?: number;
  label?: string;
  isCover?: boolean;
  sizeBytes?: number | null;
  pageCount?: number | null;
};
type PdfInfo = {
  hasCover?: boolean;
  totalSizeBytes?: number | null;
  files?: PdfFileInfoEntry[];
};

// PDF.js worker - vite-aware URL. Import.meta.url разрешается в
// абсолютный URL внутри bundled dist, worker файл копируется
// автоматически Vite-плагином (через optimizeDeps include в
// vite.config.ts если потребуется)
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString();

interface PdfViewerProps {
  bookId: string;
  /**
   * Том/часть из shamela mapping (`lib_pages.part`). Используется для
   * выбора правильного fileIndex - "Том 3" в shamela соответствует
   * PDF-файлу 03_*.pdf. Numeric → matching через `Том N` label;
   * arabic (المقدمة) → exact match arabic label.
   *
   * <p>Если null или не находится - fallback на первый не-cover файл.
   */
  initialPart?: string | null;
  /**
   * Печатная страница из shamela mapping (`lib_pages.printed_page`).
   * Используется как pageNumber внутри выбранного PDF-файла. TEXT поле
   * в БД (может быть "39", "أ", roman) - принимаем number здесь (parsed
   * родителем). Null → page 1.
   */
  initialPrintedPage?: number | null;
  /**
   * Прилипает ли pagination toolbar к низу глобального Header'а при
   * скролле. По умолчанию true — для use-case full-page reader. В
   * embedded-overlay (bottom-sheet preview) у нас свой header, sticky
   * пересекается с overlay-структурой — передавать false.
   */
  stickyToolbar?: boolean;
  /**
   * Нормализованный прямоугольник bbox-цитаты `[x, y, width, height]`,
   * каждое значение 0..1 (доля от размера PDF-страницы; x,y = верхний
   * левый угол). При переходе по deep-link citation подсвечиваем эту
   * область поверх PDF-страницы. Подсветка показывается ТОЛЬКО на той
   * странице, на которую вёл deep-link (initialPrintedPage / startPage);
   * при навигации prev/next она скрывается. null → подсветки нет.
   *
   * <p>DISPLAY-only: рисование/выбор bbox (CREATION) — отдельный этап
   * (roadmap 25.f).
   */
  initialBbox?: [number, number, number, number] | null;
}

type LoadState =
  | { kind: 'loading-info' }
  | { kind: 'ready'; info: PdfInfo }
  | { kind: 'unavailable' }
  | { kind: 'error'; message: string };

/**
 * PDF Viewer для книги через source-agnostic backend endpoint
 * `/api/v1/library/books/{id}/pdf`. Использует react-pdf (обёртка
 * над PDF.js), worker-thread обрабатывает PDF parsing/rendering -
 * main thread не блокируется на больших файлах.
 *
 * <p>Оптимизация как shamela - одна страница за раз, prev/next
 * через `setCurrentPdfPage`. react-pdf автоматически делает Range-
 * запросы через PDF.js worker - backend chunk'ит до 1MB
 * (DEFAULT_CHUNK_SIZE в PdfController).
 *
 * <p>Multi-volume через dropdown селектор. Cover (isCover=true)
 * автоматически пропускается - default fileIndex = первый не-cover
 * файл из info.files.
 *
 * <p>RTL для арабских книг - PDF.js рендерит контент как есть (он
 * embedded в PDF), но controls (prev/next) меняем направление
 * через CSS dir.
 *
 * <p>Loading flicker mitigation - вместо setNumPages(null) при page
 * change используем placeholder `loading={null}` на Page, и держим
 * previousPageRef для отображения старой страницы пока новая грузится.
 */
/**
 * Ищет fileIndex соответствующий shamela `part`. Logic:
 * - arabic part (المقدمة) - exact match label
 * - numeric part ("3") - match "Том 3" в derived labels (после исключения
 *   cover, по порядку non-arabic-label файлов начиная с index 1)
 *
 * Returns null если part не передан или не нашлось match.
 */
function findFileIndexForPart(
  part: string | null | undefined,
  files: PdfFileInfoEntry[],
): number | null {
  if (!part) return null;
  const trimmed = part.trim();
  if (!trimmed) return null;

  const contentFiles = files.filter((f) => !f.isCover);

  // Arabic part - exact label match
  if (hasArabicScript(trimmed)) {
    const match = contentFiles.find((f) => (f.label ?? '').trim() === trimmed);
    return match?.index ?? null;
  }

  // Numeric part - matching по позиции среди filename-like томов
  const numericPart = parseInt(trimmed, 10);
  if (!Number.isFinite(numericPart)) return null;

  // contentFiles = [المقدمة, 01_..., 02_..., ...]. Numeric тома идут после
  // arabic-label предисловий. "Том 1" = первый filename-like = contentFiles[1]
  // (index 0 в shamela structure = المقдмة, index 1 = том 1)
  let volumeCounter = 0;
  for (const f of contentFiles) {
    const raw = (f.label ?? '').trim();
    const isFilenameLike = /^\d{2}_\d+/.test(raw);
    if (isFilenameLike) {
      volumeCounter += 1;
      if (volumeCounter === numericPart) return f.index ?? null;
    }
  }
  return null;
}

function PdfViewer({
  bookId,
  initialPart,
  initialPrintedPage,
  stickyToolbar = true,
  initialBbox = null,
}: PdfViewerProps) {
  const locale = useLocaleStore((s) => s.locale);
  const t = useT();
  // Toolbar (стрелки, направление flex) - по локали интерфейса
  const isRtlUi = locale === 'ar';
  const [state, setState] = useState<LoadState>({ kind: 'loading-info' });
  const [fileIndex, setFileIndex] = useState<number | null>(null);
  const startPage = initialPrintedPage && initialPrintedPage > 0 ? initialPrintedPage : 1;
  const [pageNumber, setPageNumber] = useState(startPage);
  const [pageInput, setPageInput] = useState<string>(String(startPage));
  const [numPages, setNumPages] = useState<number | null>(null);
  const [scale, setScale] = useState(1.2);

  /** Меняем pageNumber + sync input одной парой. Используется во всех
   * местах где меняется страница не через input (prev/next/volume/submit) */
  const changePage = (next: number) => {
    setPageNumber(next);
    setPageInput(String(next));
  };

  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<PdfInfo>(`/api/v1/library/books/${bookId}/pdf/info`, {
      signal: controller.signal,
    })
      .then((info) => {
        setState({ kind: 'ready', info });
        const files = info.files ?? [];
        // 1. Если родитель передал shamela part - matching на правильный том
        const partMatch = findFileIndexForPart(initialPart, files);
        if (partMatch != null) {
          setFileIndex(partMatch);
          return;
        }
        // 2. Иначе - первый не-cover файл по дефолту
        const firstContentFile = files.find((f) => f.isCover === false);
        const fallback = files[0];
        setFileIndex(firstContentFile?.index ?? fallback?.index ?? 0);
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        if (e instanceof ApiError && e.problem.type?.includes('pdf-not-available')) {
          setState({ kind: 'unavailable' });
          return;
        }
        const message =
          e instanceof ApiError
            ? e.problem.detail ?? e.problem.title
            : e instanceof Error
              ? e.message
              : t('reader.pdf_load_failed');
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [bookId, initialPart, t]);

  // Multi-volume - dropdown показываем когда есть >1 не-cover файла.
  // Labels: арабские шамеловские (المقدمة) показываем как есть, а
  // безсмысленные filename-like (01_113015) переименовываем в "Том N"
  // по порядковому номеру (исключая cover). Cover скрываем из dropdown.
  //
  // Hooks обязаны быть до early returns - правило react-hooks/rules-of-hooks.
  const contentFiles = useMemo(
    () => (state.kind === 'ready' ? (state.info.files ?? []).filter((f) => !f.isCover) : []),
    [state],
  );
  const fileLabels = useMemo(() => {
    // "Том N" - N-й filename-like файл (исключая المقدمة и прочие arabic
    // labels). Соответствует shamela `part` numbering и согласуется с
    // findFileIndexForPart. Counter вычисляется через prefix-slice вместо
    // mutable let (правило react-hooks/immutability в React 19)
    return contentFiles.map((f, i) => {
      const raw = f.label ?? '';
      const hasArabic = hasArabicScript(raw);
      const looksLikeFilename = /^\d{2}_\d+/.test(raw);
      if (hasArabic) {
        return { index: f.index ?? 0, display: raw };
      }
      if (looksLikeFilename) {
        const volumeNumber = contentFiles
          .slice(0, i + 1)
          .filter((prev) => /^\d{2}_\d+/.test((prev.label ?? '').trim()))
          .length;
        return { index: f.index ?? 0, display: `${t('reader.volume')} ${volumeNumber}` };
      }
      return { index: f.index ?? 0, display: raw };
    });
  }, [contentFiles, t]);
  const showVolumeSelector = fileLabels.length > 1;

  // Опции для кастомного Select: value=stringified fileIndex, label=display
  const volumeOptions: SelectOption[] = fileLabels.map((f) => {
    const arabic = hasArabicScript(f.display);
    return {
      value: String(f.index),
      label: f.display,
      labelClassName: arabic ? 'font-naskh' : '',
      dir: arabic ? 'rtl' : 'ltr',
    };
  });

  if (state.kind === 'loading-info') {
    return (
      <Card className="p-12 text-center">
        <Loader2 size={20} className="mx-auto animate-spin text-ink-400" aria-hidden="true" />
        <p className="mt-2 text-xs text-ink-500">{t('reader.pdf_metadata_loading')}</p>
      </Card>
    );
  }

  if (state.kind === 'unavailable') {
    return (
      <Card className="p-12 text-center">
        <AlertCircle size={20} className="mx-auto text-ink-400" aria-hidden="true" />
        <p className="mt-2 text-sm text-ink-600">
          {t('reader.pdf_unavailable')}
        </p>
        <p className="mt-1 text-xs text-ink-400">
          {t('reader.pdf_will_appear')}
        </p>
      </Card>
    );
  }

  if (state.kind === 'error') {
    return (
      <Card className="border-err-500/40 bg-err-100 p-5">
        <div className="flex items-start gap-3">
          <AlertCircle size={20} className="mt-0.5 shrink-0 text-err-700" aria-hidden="true" />
          <p className="text-sm text-err-700">{state.message}</p>
        </div>
      </Card>
    );
  }

  // Absolute URL обязателен - vite dev-server не проксирует /api/* и
  // вернул бы SPA index.html на относительный путь, PDF.js получил
  // бы HTML и упал с InvalidPDFException. Production-сборка идёт через
  // тот же origin, API_BASE_URL может быть пустым - тогда работает
  // как относительный
  const activeFileIndex = fileIndex ?? 0;
  const fileUrl = `${API_BASE_URL}/api/v1/library/books/${bookId}/pdf?fileIndex=${activeFileIndex}`;
  const currentLabel = fileLabels.find((f) => f.index === activeFileIndex)?.display;
  // sanitize label для download filename: пропускаем латиницу, кириллицу,
  // арабский (U+0600-U+06FF), цифры. Всё остальное (точки, пробелы, пунктуация) → _
  const sanitizedLabel = currentLabel?.replace(/[^A-Za-zА-Яа-яёЁ0-9؀-ۿ]+/g, '_').replace(/^_+|_+$/g, '');
  const downloadFilename = `${bookId}-${activeFileIndex}${sanitizedLabel ? `-${sanitizedLabel}` : ''}.pdf`;

  // Bbox-подсветка цитаты. Показываем только на той странице, на которую
  // вёл deep-link (startPage), и только пока юзер с неё не ушёл prev/next.
  // startPage clamp'ится в onLoadSuccess к numPages (deep-link мог указывать
  // printed_page > числа PDF-страниц) — поэтому сверяем с уже clamp'нутым
  // pageNumber через min(startPage, numPages). Overlay позиционируется в %
  // от обёртки <Page> → масштабируется вместе с zoom (scale меняет размер
  // обёртки, проценты остаются те же).
  const deepLinkPage = numPages ? Math.min(startPage, numPages) : startPage;
  const showBbox = initialBbox != null && pageNumber === deepLinkPage;

  const goPrev = () => {
    if (pageNumber > 1) changePage(pageNumber - 1);
  };
  const goNext = () => {
    if (numPages && pageNumber < numPages) changePage(pageNumber + 1);
  };
  const zoomIn = () => setScale((s) => Math.min(s + 0.2, 3));
  const zoomOut = () => setScale((s) => Math.max(s - 0.2, 0.5));

  const submitPageJump = () => {
    const parsed = parseInt(pageInput, 10);
    if (!Number.isFinite(parsed) || parsed < 1) {
      setPageInput(String(pageNumber));
      return;
    }
    const clamped = numPages ? Math.min(parsed, numPages) : parsed;
    if (clamped !== pageNumber) changePage(clamped);
    else setPageInput(String(pageNumber));
  };

  const handleVolumeChange = (newIndex: number) => {
    if (newIndex === activeFileIndex) return;
    setFileIndex(newIndex);
    changePage(1);
    // numPages намеренно НЕ сбрасываем - чтобы counter `X / Y` не показывал
    // "1 / …" пока новый PDF грузится. Старое numPages корректно обновится
    // в onLoadSuccess через ~1-2 сек, а до того юзер видит привычное
    // значение - меньше визуального шума
  };

  return (
    <Card className="overflow-hidden">
      {/* Volume selector - только для multi-volume книг. Используем кастомный
          Select из shared (порт design-reference dropdown.jsx) - centered
          options + indigo styling + Check icon на selected. Native <select>
          option'ы не центрируются standard HTML */}
      {showVolumeSelector && (
        <div
          className="flex items-center gap-2 border-b border-border bg-ink-50/60 px-4 py-2"
          dir={isRtlUi ? 'rtl' : 'ltr'}
        >
          <label
            className="text-xs uppercase tracking-wide text-ink-500"
            htmlFor="pdf-volume"
          >
            {t('reader.volume')}
          </label>
          <Select
            value={String(activeFileIndex)}
            onChange={(v) => handleVolumeChange(Number(v))}
            options={volumeOptions}
            size="sm"
            ariaLabel={t('reader.volume_aria')}
            dir={isRtlUi ? 'rtl' : 'ltr'}
            menuMinWidth={140}
            className="w-[140px]"
          />
        </div>
      )}

      {/* Pagination toolbar.
          Mobile (<sm): два ряда - prev/page/next сверху, zoom+download снизу.
          Desktop: всё в одну строку через justify-between. Flex-wrap +
          явная разбивка через order на mobile через CSS не делает - проще
          stack groups через `flex-col sm:flex-row`

          stickyToolbar=true: прилипает sm:top-12 под глобальным Header
          (h-12=48px), z-30 ниже Header z-40, тот же elevation pack что
          у Header (shadow-sh1 + border-strong) - чтобы читалось как
          continuation navigation chrome. На mobile НЕ sticky -
          browser address-bar collapsing глючит. В overlay (embedded
          bottom-sheet) sticky выключен через prop. */}
      <div
        className={`flex flex-col gap-2 bg-elevated px-3 py-2 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between sm:gap-3 sm:px-4 sm:py-2.5 ${
          stickyToolbar
            ? 'border-b border-border-strong shadow-sh1 sm:sticky sm:top-12 sm:z-30'
            : 'border-b border-border'
        }`}
        dir={isRtlUi ? 'rtl' : 'ltr'}
      >
        {/* Row 1 (mobile) / inline (desktop): navigation + page jump */}
        <div className="flex items-center justify-between gap-2 sm:contents">
          <Button
            variant="ghost"
            size="sm"
            icon={isRtlUi ? ChevronRight : ChevronLeft}
            onClick={goPrev}
            disabled={pageNumber <= 1}
          >
            {t('reader.prev')}
          </Button>

          {/* Page jump input - как в text mode PageJump, но без source-first markers
              (в PDF одна страница = одно полотно, нет printedPage/part) */}
          <div className="flex items-center gap-2 text-sm text-ink-700">
            <span className="text-ink-500">{t('reader.page_short')}</span>
            <input
              type="number"
              min={1}
              max={numPages ?? undefined}
              value={pageInput}
              onChange={(e) => setPageInput(e.target.value)}
              // inline form-bound Enter handler на одном input - локальная
              // form-семантика, не global hotkey. См. frontend/CLAUDE.md
              // «Hotkeys»
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  submitPageJump();
                }
              }}
              onBlur={submitPageJump}
              className="h-7 w-14 rounded border border-border-strong px-2 text-center font-mono text-sm outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20 sm:w-16"
              aria-label={t('reader.page')}
            />
            <span className="font-mono text-ink-400">
              <bdi dir="ltr">/ {numPages ?? '…'}</bdi>
            </span>
          </div>

          <Button
            variant="ghost"
            size="sm"
            iconRight={isRtlUi ? ChevronLeft : ChevronRight}
            onClick={goNext}
            disabled={!numPages || pageNumber >= numPages}
          >
            {t('reader.next')}
          </Button>
        </div>

        {/* Row 2 (mobile) / inline (desktop): zoom + download */}
        <div className="flex items-center justify-center gap-1 sm:contents">
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={zoomOut}
              disabled={scale <= 0.5}
              className="grid h-7 w-7 place-items-center rounded text-ink-500 transition-colors hover:bg-ink-100 hover:text-ink-700 disabled:opacity-30"
              aria-label={t('reader.zoom_out')}
            >
              <ZoomOut size={14} />
            </button>
            <span className="w-12 text-center font-mono text-xs text-ink-500 tabular-nums">
              {Math.round(scale * 100)}%
            </span>
            <button
              type="button"
              onClick={zoomIn}
              disabled={scale >= 3}
              className="grid h-7 w-7 place-items-center rounded text-ink-500 transition-colors hover:bg-ink-100 hover:text-ink-700 disabled:opacity-30"
              aria-label={t('reader.zoom_in')}
            >
              <ZoomIn size={14} />
            </button>

            {/* Download кнопка - прямая ссылка на streaming endpoint, browser
                сохранит как файл благодаря attribute `download` */}
            <a
              href={fileUrl}
              download={downloadFilename}
              className="ms-1 inline-flex h-7 items-center gap-1 rounded px-2 text-xs font-medium text-ink-600 transition-colors hover:bg-ink-100 hover:text-accent-700"
              title={t('reader.download_pdf')}
            >
              <Download size={14} aria-hidden="true" />
              <span className="hidden sm:inline">PDF</span>
            </a>
          </div>
        </div>
      </div>

      {/* PDF viewport */}
      <div className="overflow-auto bg-ink-100 p-4" style={{ minHeight: '600px' }}>
        <div className="mx-auto flex justify-center">
          <Document
            key={fileUrl}
            file={fileUrl}
            onLoadSuccess={({ numPages: loaded }) => {
              setNumPages(loaded);
              // Clamp текущую страницу к реальному размеру PDF. initialPrintedPage
              // приходит из shamela printed_page (номер на физической странице
              // книги) и может превышать число страниц в PDF - без clamp
              // открылись бы на несуществующей странице → пустой viewport
              // (Page loading={null} ничего не рисует). Опускаем до последней.
              if (pageNumber > loaded) {
                changePage(loaded);
              }
            }}
            // НЕ эскалируем per-file load failure в component-wide error
            // state: это unmount'ит volume selector + toolbar (см. ранний
            // return на kind==='error') и убивает весь reader из-за сбоя
            // одного тома. Scoped error UI ниже (error=...) держит toolbar
            // смонтированным, юзер может переключить том. Глобальный error
            // оставлен только за /pdf/info fetch (loadInfo).
            loading={
              <div className="py-20 text-center">
                <Loader2 size={20} className="mx-auto animate-spin text-ink-400" />
                <p className="mt-2 text-xs text-ink-500">
                  {t('reader.pdf_preview_load_long')}
                </p>
              </div>
            }
            error={
              <Card className="border-err-500/40 bg-err-100 p-5">
                <p className="text-sm text-err-700">{t('reader.pdf_load_failed')}</p>
              </Card>
            }
          >
            {/* loading={null} - не показываем спиннер на каждый prev/next,
                react-pdf оставляет предыдущую страницу видимой пока новая
                рендерится. Сильно убирает flicker на быстрых клик-паттернах.

                Обёртка `relative inline-block` плотно облегает canvas <Page>
                (inline-block ужимается до его размера), поэтому absolute-
                overlay с процентными координатами якорится точно к странице
                и масштабируется вместе с zoom (scale → меняется размер
                canvas → меняется размер обёртки → проценты те же). */}
            <div className="relative inline-block">
              <Page
                pageNumber={pageNumber}
                scale={scale}
                className="shadow-lg"
                loading={null}
                renderTextLayer
                renderAnnotationLayer={false}
              />
              {showBbox && initialBbox && (
                <div
                  data-testid="pdf-bbox-highlight"
                  aria-hidden="true"
                  className="pointer-events-none absolute rounded-sm bg-amber-300/20 ring-2 ring-amber-400/80"
                  style={{
                    left: `${initialBbox[0] * 100}%`,
                    top: `${initialBbox[1] * 100}%`,
                    width: `${initialBbox[2] * 100}%`,
                    height: `${initialBbox[3] * 100}%`,
                  }}
                />
              )}
            </div>
          </Document>
        </div>
      </div>
    </Card>
  );
}

export default PdfViewer;
