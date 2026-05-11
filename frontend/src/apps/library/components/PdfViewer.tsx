import { useEffect, useMemo, useState } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import {
  AlertCircle,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Download,
  Loader2,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import { API_BASE_URL, apiGetRaw, ApiError } from '@/shared/api/client';

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
  isArabic: boolean;
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
function PdfViewer({ bookId, isArabic }: PdfViewerProps) {
  const [state, setState] = useState<LoadState>({ kind: 'loading-info' });
  const [fileIndex, setFileIndex] = useState<number | null>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [pageInput, setPageInput] = useState<string>('1');
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
        // Cover - всегда обложка по convention shamela/archive.org.
        // По умолчанию открываем первый не-cover файл (юзер хочет
        // читать книгу, а не любоваться обложкой)
        const firstContentFile = (info.files ?? []).find((f) => f.isCover === false);
        const fallback = info.files?.[0];
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
              : 'Не удалось загрузить PDF';
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [bookId]);

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
  const fileLabels = useMemo(
    () =>
      contentFiles.map((f, i) => {
        const raw = f.label ?? '';
        const hasArabic = /[؀-ۿ]/.test(raw);
        const looksLikeFilename = /^\d{2}_\d+/.test(raw);
        const display = hasArabic ? raw : looksLikeFilename ? `Том ${i + 1}` : raw;
        return { index: f.index ?? 0, display };
      }),
    [contentFiles],
  );
  const showVolumeSelector = fileLabels.length > 1;

  if (state.kind === 'loading-info') {
    return (
      <Card className="p-12 text-center">
        <Loader2 size={20} className="mx-auto animate-spin text-slate-400" aria-hidden="true" />
        <p className="mt-2 text-[12px] text-slate-500">Загрузка PDF метаданных</p>
      </Card>
    );
  }

  if (state.kind === 'unavailable') {
    return (
      <Card className="p-12 text-center">
        <AlertCircle size={20} className="mx-auto text-slate-400" aria-hidden="true" />
        <p className="mt-2 text-[13px] text-slate-600">
          У этой книги нет привязанного PDF-источника
        </p>
        <p className="mt-1 text-[12px] text-slate-400">
          Источник {isArabic ? '· المصدر الأصلي ' : ''}появится при импорте книги с PDF
          (shamela / archive.org / upload)
        </p>
      </Card>
    );
  }

  if (state.kind === 'error') {
    return (
      <Card className="border-red-200 bg-red-50 p-5">
        <div className="flex items-start gap-3">
          <AlertCircle size={20} className="mt-0.5 shrink-0 text-red-600" aria-hidden="true" />
          <p className="text-[13px] text-red-800">{state.message}</p>
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
      {/* Volume selector - только для multi-volume книг. Stylish native select:
          design-reference не покрывает <select> элемент, делаем минимальный
          стиль в духе Button outline + Card patterns (rounded-md, slate-200,
          indigo focus, ChevronDown indicator справа). Custom dropdown с
          listbox - отдельный refactor если ROI оправдает */}
      {showVolumeSelector && (
        <div
          className="flex items-center gap-2 border-b border-slate-200 bg-slate-50/60 px-4 py-2"
          dir={isArabic ? 'rtl' : 'ltr'}
        >
          <label
            className="text-[11px] uppercase tracking-wide text-slate-500"
            htmlFor="pdf-volume"
          >
            Том
          </label>
          <div className="relative">
            <select
              id="pdf-volume"
              className="h-7 appearance-none rounded-md border border-slate-300 bg-white pe-7 ps-3 text-[13px] font-medium text-slate-700 outline-none transition-colors hover:border-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              value={activeFileIndex}
              onChange={(e) => handleVolumeChange(Number(e.target.value))}
              dir={isArabic ? 'rtl' : 'ltr'}
            >
              {fileLabels.map((f) => (
                <option key={f.index} value={f.index}>
                  {f.display}
                </option>
              ))}
            </select>
            <ChevronDown
              size={14}
              className="pointer-events-none absolute end-2 top-1/2 -translate-y-1/2 text-slate-400"
              aria-hidden="true"
            />
          </div>
        </div>
      )}

      {/* Pagination toolbar */}
      <div
        className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 py-2.5"
        dir={isArabic ? 'rtl' : 'ltr'}
      >
        <Button
          variant="ghost"
          size="sm"
          icon={isArabic ? ChevronRight : ChevronLeft}
          onClick={goPrev}
          disabled={pageNumber <= 1}
        >
          Предыдущая
        </Button>

        {/* Page jump input - как в text mode PageJump, но без source-first markers
            (в PDF одна страница = одно полотно, нет printedPage/part) */}
        <div className="flex items-center gap-2 text-[13px] text-slate-700">
          <span className="text-slate-500">Стр</span>
          <input
            type="number"
            min={1}
            max={numPages ?? undefined}
            value={pageInput}
            onChange={(e) => setPageInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                submitPageJump();
              }
            }}
            onBlur={submitPageJump}
            className="h-7 w-16 rounded border border-slate-300 px-2 text-center font-mono text-[13px] outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
            aria-label="Номер PDF страницы"
          />
          <span className="font-mono text-slate-400">/ {numPages ?? '…'}</span>
        </div>

        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={zoomOut}
            disabled={scale <= 0.5}
            className="grid h-7 w-7 place-items-center rounded text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30"
            aria-label="Уменьшить"
          >
            <ZoomOut size={14} />
          </button>
          <span className="w-12 text-center font-mono text-[11px] text-slate-500 tabular-nums">
            {Math.round(scale * 100)}%
          </span>
          <button
            type="button"
            onClick={zoomIn}
            disabled={scale >= 3}
            className="grid h-7 w-7 place-items-center rounded text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30"
            aria-label="Увеличить"
          >
            <ZoomIn size={14} />
          </button>

          {/* Download кнопка - прямая ссылка на streaming endpoint, browser
              сохранит как файл благодаря attribute `download` */}
          <a
            href={fileUrl}
            download={downloadFilename}
            className="ms-1 inline-flex h-7 items-center gap-1 rounded px-2 text-[12px] font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-indigo-700"
            title="Скачать PDF целиком"
          >
            <Download size={14} aria-hidden="true" />
            <span className="hidden sm:inline">PDF</span>
          </a>
        </div>

        <Button
          variant="ghost"
          size="sm"
          iconRight={isArabic ? ChevronLeft : ChevronRight}
          onClick={goNext}
          disabled={!numPages || pageNumber >= numPages}
        >
          Следующая
        </Button>
      </div>

      {/* PDF viewport */}
      <div className="overflow-auto bg-slate-100 p-4" style={{ minHeight: '600px' }}>
        <div className="mx-auto flex justify-center">
          <Document
            key={fileUrl}
            file={fileUrl}
            onLoadSuccess={({ numPages }) => setNumPages(numPages)}
            onLoadError={(err) => {
              setState({ kind: 'error', message: err.message });
            }}
            loading={
              <div className="py-20 text-center">
                <Loader2 size={20} className="mx-auto animate-spin text-slate-400" />
                <p className="mt-2 text-[12px] text-slate-500">
                  Загрузка PDF... первая загрузка может занять время (~50MB качается через прокси на нашем сервере)
                </p>
              </div>
            }
            error={
              <Card className="border-red-200 bg-red-50 p-5">
                <p className="text-[13px] text-red-800">Не удалось загрузить PDF файл</p>
              </Card>
            }
          >
            {/* loading={null} - не показываем спиннер на каждый prev/next,
                react-pdf оставляет предыдущую страницу видимой пока новая
                рендерится. Сильно убирает flicker на быстрых клик-паттернах */}
            <Page
              pageNumber={pageNumber}
              scale={scale}
              className="shadow-lg"
              loading={null}
              renderTextLayer
              renderAnnotationLayer={false}
            />
          </Document>
        </div>
      </div>
    </Card>
  );
}

export default PdfViewer;
