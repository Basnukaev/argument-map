import { useEffect, useMemo, useRef, useState } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import { AlertCircle, ChevronLeft, ChevronRight, Loader2 } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Select, { type SelectOption } from '@/shared/components/ui/Select';
import { API_BASE_URL, apiGetRaw, ApiError } from '@/shared/api/client';
import { hasArabicScript, useT } from '@/shared/i18n';
import { pixelRectToBbox, type PdfRegion } from './pdfRegion';

import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

// PDF.js worker - тот же setup что в PdfViewer (vite-aware URL).
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString();

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

export type { PdfRegion };

interface Props {
  bookId: string;
  /** Текущий выбранный регион (controlled). null = ещё не нарисован. */
  region: PdfRegion | null;
  /** Callback при изменении региона (draw/redraw/смена тома/страницы). */
  onRegionChange: (region: PdfRegion | null) => void;
}

type LoadState =
  | { kind: 'loading-info' }
  | { kind: 'ready'; info: PdfInfo }
  | { kind: 'unavailable' }
  | { kind: 'error'; message: string };

/** Незакоммиченный прямоугольник во время drag (пиксели относительно <Page>). */
interface DraftRect {
  startX: number;
  startY: number;
  curX: number;
  curY: number;
}

function rectFromDraft(d: DraftRect): { left: number; top: number; width: number; height: number } {
  return {
    left: Math.min(d.startX, d.curX),
    top: Math.min(d.startY, d.curY),
    width: Math.abs(d.curX - d.startX),
    height: Math.abs(d.curY - d.startY),
  };
}

/**
 * Центральная панель CitationPicker для FILE_ONLY (PDF-only) книг:
 * рендерит PDF-страницу и даёт обвести курсором прямоугольную область
 * (REGION-цитата, ADR-067 mode PDF_LINK). Зеркалит PdfViewer'ский
 * loading + multi-volume + `relative inline-block` overlay, добавляя
 * draw-слой поверх <Page>.
 *
 * <p>Координаты bbox нормализуются (0..1) делением пиксельного rect на
 * размеры отрендеренной страницы (см. pixelRectToBbox). Это та же
 * система координат что у DISPLAY-overlay в PdfViewer.
 */
function CitationPickerPdfRegion({ bookId, region, onRegionChange }: Props) {
  const t = useT();
  const [state, setState] = useState<LoadState>({ kind: 'loading-info' });
  const [fileIndex, setFileIndex] = useState<number>(0);
  const [pageNumber, setPageNumber] = useState(1);
  const [pageInput, setPageInput] = useState('1');
  const [numPages, setNumPages] = useState<number | null>(null);
  const scale = 1.2;
  const [draft, setDraft] = useState<DraftRect | null>(null);
  const pageWrapRef = useRef<HTMLDivElement | null>(null);

  // Загрузка PDF метаданных (список томов). Дефолтный fileIndex - первый
  // не-cover файл (cover пропускаем как в PdfViewer).
  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<PdfInfo>(`/api/v1/library/books/${bookId}/pdf/info`, {
      signal: controller.signal,
    })
      .then((info) => {
        setState({ kind: 'ready', info });
        const files = info.files ?? [];
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
            ? (e.problem.detail ?? e.problem.title)
            : e instanceof Error
              ? e.message
              : t('reader.pdf_load_failed');
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [bookId, t]);

  // Multi-volume - те же derived labels что в PdfViewer (المقدمة как есть,
  // filename-like 01_113015 → "Том N"). Hooks до early-returns.
  const contentFiles = useMemo(
    () => (state.kind === 'ready' ? (state.info.files ?? []).filter((f) => !f.isCover) : []),
    [state],
  );
  const fileLabels = useMemo(() => {
    return contentFiles.map((f, i) => {
      const raw = f.label ?? '';
      const hasArabic = hasArabicScript(raw);
      const looksLikeFilename = /^\d{2}_\d+/.test(raw);
      if (hasArabic) return { index: f.index ?? 0, display: raw };
      if (looksLikeFilename) {
        const volumeNumber = contentFiles
          .slice(0, i + 1)
          .filter((prev) => /^\d{2}_\d+/.test((prev.label ?? '').trim())).length;
        return { index: f.index ?? 0, display: `${t('reader.volume')} ${volumeNumber}` };
      }
      return { index: f.index ?? 0, display: raw };
    });
  }, [contentFiles, t]);
  const showVolumeSelector = fileLabels.length > 1;
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
      <Card className="flex flex-1 items-center justify-center p-12 text-center">
        <div>
          <Loader2 size={20} className="mx-auto animate-spin text-ink-400" aria-hidden="true" />
          <p className="mt-2 text-xs text-ink-500">{t('reader.pdf_metadata_loading')}</p>
        </div>
      </Card>
    );
  }
  if (state.kind === 'unavailable') {
    return (
      <Card className="flex flex-1 items-center justify-center p-12 text-center">
        <div>
          <AlertCircle size={20} className="mx-auto text-ink-400" aria-hidden="true" />
          <p className="mt-2 text-sm text-ink-600">{t('reader.pdf_unavailable')}</p>
        </div>
      </Card>
    );
  }
  if (state.kind === 'error') {
    return (
      <Card className="flex flex-1 items-center justify-center border-err-500/40 bg-err-100 p-5">
        <p className="text-sm text-err-700">{state.message}</p>
      </Card>
    );
  }

  // Absolute URL обязателен - vite dev не проксирует /api/* (см. PdfViewer).
  const fileUrl = `${API_BASE_URL}/api/v1/library/books/${bookId}/pdf?fileIndex=${fileIndex}`;

  const changePage = (next: number) => {
    setPageNumber(next);
    setPageInput(String(next));
    // Смена страницы аннулирует регион (он привязан к конкретной странице).
    onRegionChange(null);
  };
  const goPrev = () => {
    if (pageNumber > 1) changePage(pageNumber - 1);
  };
  const goNext = () => {
    if (numPages && pageNumber < numPages) changePage(pageNumber + 1);
  };
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
    if (newIndex === fileIndex) return;
    setFileIndex(newIndex);
    setPageNumber(1);
    setPageInput('1');
    setNumPages(null);
    onRegionChange(null);
  };

  // --- Draw layer (pointer drag → нормализованный bbox) ---
  const pointerToLocal = (e: React.PointerEvent): { x: number; y: number } | null => {
    const el = pageWrapRef.current;
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  };
  const handlePointerDown = (e: React.PointerEvent) => {
    if (e.button !== 0) return;
    const p = pointerToLocal(e);
    if (!p) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    setDraft({ startX: p.x, startY: p.y, curX: p.x, curY: p.y });
  };
  const handlePointerMove = (e: React.PointerEvent) => {
    if (!draft) return;
    const p = pointerToLocal(e);
    if (!p) return;
    setDraft({ ...draft, curX: p.x, curY: p.y });
  };
  const handlePointerUp = () => {
    if (!draft) return;
    const el = pageWrapRef.current;
    const pixelRect = rectFromDraft(draft);
    setDraft(null);
    if (!el) return;
    // Слишком маленький прямоугольник (случайный клик) — игнорируем.
    if (pixelRect.width < 4 || pixelRect.height < 4) return;
    const bbox = pixelRectToBbox(pixelRect, {
      width: el.offsetWidth,
      height: el.offsetHeight,
    });
    if (!bbox || bbox.width <= 0 || bbox.height <= 0) return;
    onRegionChange({ fileIndex, pageNumber, bbox });
  };

  // Текущий регион показываем как persistent box (только если он на этой
  // странице/томе). Во время draft рисуем draft-rect.
  const showSavedBox =
    region != null && region.fileIndex === fileIndex && region.pageNumber === pageNumber;

  return (
    <Card className="flex flex-1 flex-col overflow-hidden">
      {showVolumeSelector && (
        <div className="flex items-center gap-2 border-b border-border bg-ink-50/60 px-4 py-2">
          <label className="text-xs uppercase tracking-wide text-ink-500" htmlFor="region-volume">
            {t('reader.volume')}
          </label>
          <Select
            value={String(fileIndex)}
            onChange={(v) => handleVolumeChange(Number(v))}
            options={volumeOptions}
            size="sm"
            ariaLabel={t('reader.volume_aria')}
            menuMinWidth={140}
            className="w-[140px]"
          />
        </div>
      )}

      {/* Pagination toolbar */}
      <div className="flex items-center justify-between gap-3 border-b border-border bg-elevated px-3 py-2">
        <Button variant="ghost" size="sm" icon={ChevronLeft} onClick={goPrev} disabled={pageNumber <= 1}>
          {t('reader.prev')}
        </Button>
        <div className="flex items-center gap-2 text-sm text-ink-700">
          <span className="text-ink-500">{t('reader.page_short')}</span>
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
            className="h-7 w-14 rounded border border-border-strong px-2 text-center font-mono text-sm outline-none transition-colors focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20"
            aria-label={t('reader.page')}
          />
          <span className="font-mono text-ink-400">
            <bdi dir="ltr">/ {numPages ?? '…'}</bdi>
          </span>
        </div>
        <Button
          variant="ghost"
          size="sm"
          iconRight={ChevronRight}
          onClick={goNext}
          disabled={!numPages || pageNumber >= numPages}
        >
          {t('reader.next')}
        </Button>
      </div>

      {/* Hint */}
      <p className="border-b border-border bg-accent-50/50 px-3 py-1.5 text-center text-xs text-ink-600">
        {t('citation_picker.region_draw_hint')}
      </p>

      {/* PDF viewport */}
      <div className="flex-1 overflow-auto bg-ink-100 p-4" style={{ minHeight: '400px' }}>
        <div className="mx-auto flex justify-center">
          <Document
            key={fileUrl}
            file={fileUrl}
            onLoadSuccess={({ numPages: loaded }) => {
              setNumPages(loaded);
              if (pageNumber > loaded) changePage(loaded);
            }}
            loading={
              <div className="py-20 text-center">
                <Loader2 size={20} className="mx-auto animate-spin text-ink-400" />
                <p className="mt-2 text-xs text-ink-500">{t('reader.pdf_preview_load_long')}</p>
              </div>
            }
            error={
              <Card className="border-err-500/40 bg-err-100 p-5">
                <p className="text-sm text-err-700">{t('reader.pdf_load_failed')}</p>
              </Card>
            }
          >
            {/* `relative inline-block` плотно облегает <Page> — overlay в %
                якорится к странице и масштабируется вместе с zoom (как в
                PdfViewer DISPLAY-overlay). Draw-слой ловит pointer-события. */}
            <div
              ref={pageWrapRef}
              data-testid="region-draw-layer"
              className="relative inline-block cursor-crosshair touch-none select-none"
              onPointerDown={handlePointerDown}
              onPointerMove={handlePointerMove}
              onPointerUp={handlePointerUp}
            >
              <Page
                pageNumber={pageNumber}
                scale={scale}
                className="shadow-lg"
                loading={null}
                renderTextLayer={false}
                renderAnnotationLayer={false}
              />
              {draft && (
                <div
                  aria-hidden="true"
                  className="pointer-events-none absolute rounded-sm bg-accent-300/25 ring-2 ring-accent-500"
                  style={(() => {
                    const r = rectFromDraft(draft);
                    return {
                      left: `${r.left}px`,
                      top: `${r.top}px`,
                      width: `${r.width}px`,
                      height: `${r.height}px`,
                    };
                  })()}
                />
              )}
              {!draft && showSavedBox && (
                <div
                  data-testid="region-saved-box"
                  aria-hidden="true"
                  className="pointer-events-none absolute rounded-sm bg-amber-300/20 ring-2 ring-amber-400/80"
                  style={{
                    left: `${region.bbox.x * 100}%`,
                    top: `${region.bbox.y * 100}%`,
                    width: `${region.bbox.width * 100}%`,
                    height: `${region.bbox.height * 100}%`,
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

export default CitationPickerPdfRegion;
