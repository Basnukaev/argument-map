import { useEffect, useState } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import { AlertCircle, ChevronLeft, ChevronRight, Loader2, ZoomIn, ZoomOut } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import { API_BASE_URL, apiGetRaw, ApiError } from '@/shared/api/client';

import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

// Локальный тип до regen-api (Pre-flight Сессии 25 - перезапуск
// бэка → npm run generate-api). После regen заменим на
// components['schemas']['PdfInfoResponse']
type PdfInfo = {
  hasCover?: boolean;
  totalSizeBytes?: number | null;
  files?: Array<{
    index?: number;
    label?: string;
    sizeBytes?: number | null;
    pageCount?: number | null;
  }>;
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
 * <p>Multi-file books (multi-volume) - на MVP показываем только
 * первый file (fileIndex=0). Dropdown селектор томов добавится
 * в 25.d вместе с page sync.
 *
 * <p>RTL для арабских книг - PDF.js рендерит контент как есть (он
 * embedded в PDF), но controls (prev/next) меняем направление
 * через CSS dir.
 */
function PdfViewer({ bookId, isArabic }: PdfViewerProps) {
  const [state, setState] = useState<LoadState>({ kind: 'loading-info' });
  const [pageNumber, setPageNumber] = useState(1);
  const [numPages, setNumPages] = useState<number | null>(null);
  const [scale, setScale] = useState(1.2);

  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<PdfInfo>(`/api/v1/library/books/${bookId}/pdf/info`, {
      signal: controller.signal,
    })
      .then((info) => setState({ kind: 'ready', info }))
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
  const fileUrl = `${API_BASE_URL}/api/v1/library/books/${bookId}/pdf?fileIndex=0`;
  const goPrev = () => pageNumber > 1 && setPageNumber(pageNumber - 1);
  const goNext = () => numPages && pageNumber < numPages && setPageNumber(pageNumber + 1);
  const zoomIn = () => setScale((s) => Math.min(s + 0.2, 3));
  const zoomOut = () => setScale((s) => Math.max(s - 0.2, 0.5));

  return (
    <Card className="overflow-hidden">
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
        <span className="font-mono text-[13px] text-slate-700">
          {pageNumber} / {numPages ?? '…'}
        </span>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={zoomOut}
            disabled={scale <= 0.5}
            className="h-7 w-7 grid place-items-center rounded text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30"
            aria-label="Уменьшить"
          >
            <ZoomOut size={14} />
          </button>
          <span className="font-mono text-[11px] text-slate-500 tabular-nums w-12 text-center">
            {Math.round(scale * 100)}%
          </span>
          <button
            type="button"
            onClick={zoomIn}
            disabled={scale >= 3}
            className="h-7 w-7 grid place-items-center rounded text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-30"
            aria-label="Увеличить"
          >
            <ZoomIn size={14} />
          </button>
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
      <div className="bg-slate-100 p-4 overflow-auto" style={{ minHeight: '600px' }}>
        <div className="mx-auto flex justify-center">
          <Document
            file={fileUrl}
            onLoadSuccess={({ numPages }) => setNumPages(numPages)}
            onLoadError={(err) => {
              setState({ kind: 'error', message: err.message });
            }}
            loading={
              <div className="py-20 text-center">
                <Loader2 size={20} className="mx-auto animate-spin text-slate-400" />
                <p className="mt-2 text-[12px] text-slate-500">
                  Загрузка PDF... первая загрузка может занять время
                  (~50MB качается через прокси на нашем сервере)
                </p>
              </div>
            }
            error={
              <Card className="border-red-200 bg-red-50 p-5">
                <p className="text-[13px] text-red-800">Не удалось загрузить PDF файл</p>
              </Card>
            }
          >
            <Page
              pageNumber={pageNumber}
              scale={scale}
              className="shadow-lg"
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
