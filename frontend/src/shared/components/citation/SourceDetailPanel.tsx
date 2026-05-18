import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { BookOpen, ExternalLink, X } from 'lucide-react';
import { apiGetRaw, formatApiError } from '@/shared/api/client';
import { hasArabicScript, useT } from '@/shared/i18n';
import { useIsMobile } from '@/shared/hooks/useViewport';
import {
  useSourceDetailPanelStore,
  type SourceDetailCitation,
} from '@/shared/stores/sourceDetailPanelStore';
import type { components } from '@/shared/api/types';

type SourceDto = components['schemas']['SourceResponse'];
type AuthorityDto = components['schemas']['AuthorityResponse'];

type FetchState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'loaded'; source: SourceDto; authority: AuthorityDto | null }
  | { kind: 'error'; message: string };

/**
 * Параллельная боковая панель (800px desktop, fullscreen mobile) с полным
 * содержанием цитируемого источника. Открывается из любого места через
 * `useSourceDetailPanelStore.openWith({sourceId, quote, context})` -
 * mount'ится один раз в App.tsx, controlled через store
 *
 * Структура:
 * - Header: title + close (X)
 * - Section Metadata: sourceType + authority + book + edition + year
 * - Section Quote: выделенный quote в розовой `<blockquote>` (если есть)
 * - Section Context: контекст вокруг quote (если есть)
 * - Section Full Reading: кнопка «Открыть полностью» → /books/{bookId}
 *
 * Анимация slide-in справа через `translate-x-*` + CSS transition.
 * На mobile занимает весь viewport (fullscreen drawer)
 *
 * Эскейп / клик по backdrop - close (паттерн как у Modal, но без <dialog>
 * - используем absolute positioning + own backdrop потому что нужен
 *  slide-in справа а не центр)
 */
function SourceDetailPanel() {
  const t = useT();
  const navigate = useNavigate();
  const isMobile = useIsMobile();
  const isOpen = useSourceDetailPanelStore((s) => s.isOpen);
  const current = useSourceDetailPanelStore((s) => s.current);
  const close = useSourceDetailPanelStore((s) => s.close);

  const [state, setState] = useState<FetchState>({ kind: 'idle' });

  // Fetch source + (опционально) authority при открытии. AbortController -
  // если user быстро переключает источники, отменяем in-flight запрос
  useEffect(() => {
    if (!isOpen || !current) {
      setState({ kind: 'idle' });
      return;
    }
    setState({ kind: 'loading' });
    const controller = new AbortController();
    apiGetRaw<SourceDto>(`/api/v1/sources/${current.sourceId}`, {
      signal: controller.signal,
    })
      .then(async (source) => {
        if (controller.signal.aborted) return;
        // Если есть authorityId - подгружаем биографию автора
        if (source.authorityId) {
          try {
            const authority = await apiGetRaw<AuthorityDto>(
              `/api/v1/authorities/${source.authorityId}`,
              { signal: controller.signal },
            );
            if (controller.signal.aborted) return;
            setState({ kind: 'loaded', source, authority });
          } catch {
            // Authority fetch fail - не блокирующая ошибка, показываем без неё
            if (controller.signal.aborted) return;
            setState({ kind: 'loaded', source, authority: null });
          }
        } else {
          setState({ kind: 'loaded', source, authority: null });
        }
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'error', message: formatApiError(e, t('source_detail.error_load')) });
      });
    return () => controller.abort();
  }, [isOpen, current, t]);

  // Escape - close. Listener только когда panel открыт чтобы не висеть
  // постоянно во всех страницах
  useEffect(() => {
    if (!isOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [isOpen, close]);

  if (!isOpen) return null;

  // Panel ширина: desktop 800px, mobile - full viewport. Tailwind
  // logical end-0 чтобы корректно работать в RTL (панель справа в LTR,
  // слева в RTL). Анимация через `data-state` атрибут + transition
  const panelWidthClass = isMobile ? 'w-screen' : 'w-[800px] max-w-[90vw]';

  return (
    <>
      {/* Backdrop - dimmed overlay, click → close. На mobile прозрачный
          т.к. panel занимает весь screen */}
      <div
        className="fixed inset-0 z-40 bg-black/40 backdrop-blur-[1px]"
        onClick={close}
        data-testid="source-detail-backdrop"
        aria-hidden="true"
      />

      {/* Panel - absolute, slides in from end (right in LTR, left in RTL) */}
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="source-detail-title"
        data-testid="source-detail-panel"
        className={`fixed inset-y-0 end-0 z-50 flex ${panelWidthClass} flex-col border-s border-border bg-bg shadow-sh4`}
      >
        <PanelHeader onClose={close} />
        <div className="flex-1 overflow-y-auto px-6 py-5">
          <PanelBody state={state} citation={current!} onNavigate={navigate} />
        </div>
      </aside>
    </>
  );
}

interface HeaderProps {
  onClose: () => void;
}

function PanelHeader({ onClose }: HeaderProps) {
  const t = useT();
  return (
    <header className="flex flex-none items-center justify-between gap-3 border-b border-border px-6 py-4">
      <h2
        id="source-detail-title"
        className="min-w-0 flex-1 truncate text-base font-semibold text-ink-900"
      >
        {t('source_detail.title')}
      </h2>
      <button
        type="button"
        onClick={onClose}
        aria-label={t('source_detail.close')}
        className="rounded p-1.5 text-ink-500 transition-colors hover:bg-ink-100 hover:text-ink-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
      >
        <X size={18} aria-hidden="true" />
      </button>
    </header>
  );
}

interface BodyProps {
  state: FetchState;
  citation: SourceDetailCitation;
  onNavigate: (to: string) => void;
}

function PanelBody({ state, citation, onNavigate }: BodyProps) {
  const t = useT();

  if (state.kind === 'loading' || state.kind === 'idle') {
    return <p className="text-sm text-ink-500">{t('source_detail.loading')}</p>;
  }
  if (state.kind === 'error') {
    return <p className="text-sm text-err-700">{state.message}</p>;
  }

  const { source, authority } = state;
  const sourceTitle = source.title ?? '—';
  const bookId = source.bookId;
  const sourceType = source.sourceType;

  return (
    <div className="space-y-6">
      <MetadataSection source={source} authority={authority} />

      {citation.quote && <QuoteSection quote={citation.quote} title={sourceTitle} />}

      {citation.context && <ContextSection context={citation.context} />}

      {bookId && (sourceType === 'BOOK' || sourceType === 'QURAN' || sourceType === 'HADITH') && (
        <FullReadingSection bookId={bookId} onNavigate={onNavigate} />
      )}
    </div>
  );
}

interface MetadataSectionProps {
  source: SourceDto;
  authority: AuthorityDto | null;
}

function MetadataSection({ source, authority }: MetadataSectionProps) {
  const t = useT();
  const sourceType = source.sourceType;
  const titleAr = source.title && hasArabicScript(source.title);

  return (
    <section aria-labelledby="md-heading">
      <h3
        id="md-heading"
        className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500"
      >
        {t('source_detail.section_metadata')}
      </h3>
      <dl className="space-y-2 text-sm">
        {sourceType && (
          <Row label={t('source_detail.field_type')}>
            <span className="font-mono text-xs uppercase tracking-wide text-ink-700">
              {sourceType}
            </span>
          </Row>
        )}
        {source.title && (
          <Row label={t('source_detail.field_title')}>
            <span
              dir="auto"
              lang={titleAr ? 'ar' : undefined}
              className={titleAr ? 'font-naskh text-base font-semibold' : 'font-semibold'}
            >
              {source.title}
            </span>
          </Row>
        )}
        {authority && (
          <Row label={t('source_detail.field_authority')}>
            <span
              dir="auto"
              className={hasArabicScript(authority.name ?? '') ? 'font-naskh' : ''}
            >
              {authority.fullName ?? authority.name}
              {authority.deathYearHijri != null && (
                <span className="ms-2 text-xs text-ink-500">
                  (<bdi>{authority.deathYearHijri} هـ</bdi>)
                </span>
              )}
            </span>
          </Row>
        )}
        {source.citation && (
          <Row label={t('source_detail.field_citation')}>
            <span dir="auto" className="font-mono text-xs">
              {source.citation}
            </span>
          </Row>
        )}
        {source.reliability && sourceType === 'HADITH' && (
          <Row label={t('source_detail.field_reliability')}>
            <span className="rounded bg-ink-100 px-1.5 py-0.5 font-mono text-xs font-medium uppercase text-ink-700">
              {source.reliability}
            </span>
          </Row>
        )}
      </dl>
    </section>
  );
}

interface RowProps {
  label: string;
  children: React.ReactNode;
}

function Row({ label, children }: RowProps) {
  return (
    <div className="flex flex-col gap-0.5 sm:flex-row sm:items-baseline sm:gap-3">
      <dt className="text-xs uppercase tracking-wide text-ink-500 sm:w-32 sm:flex-none">
        {label}
      </dt>
      <dd className="min-w-0 flex-1 text-ink-900">{children}</dd>
    </div>
  );
}

interface QuoteSectionProps {
  quote: string;
  title: string;
}

function QuoteSection({ quote, title }: QuoteSectionProps) {
  const t = useT();
  const quoteIsAr = hasArabicScript(quote);
  return (
    <section aria-labelledby="q-heading">
      <h3
        id="q-heading"
        className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500"
      >
        {t('source_detail.section_quote')}
      </h3>
      <blockquote
        dir="auto"
        lang={quoteIsAr ? 'ar' : undefined}
        cite={title}
        className={
          quoteIsAr
            ? 'rounded-md border-s-4 border-accent-500/60 bg-accent-50/40 px-5 py-4 font-naskh text-lg leading-[1.95] text-ink-900 text-start dark:bg-accent-400/10'
            : 'rounded-md border-s-4 border-accent-500/60 bg-accent-50/40 px-5 py-4 text-base leading-relaxed text-ink-900 text-start dark:bg-accent-400/10'
        }
      >
        {quote}
      </blockquote>
    </section>
  );
}

interface ContextSectionProps {
  context: string;
}

function ContextSection({ context }: ContextSectionProps) {
  const t = useT();
  return (
    <section aria-labelledby="c-heading">
      <h3
        id="c-heading"
        className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500"
      >
        {t('source_detail.section_context')}
      </h3>
      <p
        dir="auto"
        className="rounded-md border border-dashed border-border px-4 py-3 text-sm italic leading-relaxed text-ink-600 text-start"
      >
        «{context}»
      </p>
    </section>
  );
}

interface FullReadingSectionProps {
  bookId: string;
  onNavigate: (to: string) => void;
}

function FullReadingSection({ bookId, onNavigate }: FullReadingSectionProps) {
  const t = useT();
  return (
    <section aria-labelledby="f-heading">
      <h3
        id="f-heading"
        className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500"
      >
        {t('source_detail.section_full_reading')}
      </h3>
      <button
        type="button"
        onClick={() => onNavigate(`/books/${bookId}`)}
        className="inline-flex items-center gap-2 rounded-md bg-accent-500 px-4 py-2 text-sm font-semibold text-white shadow-sh1 transition-colors hover:bg-accent-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
        data-testid="source-detail-open-full"
      >
        <BookOpen size={16} aria-hidden="true" />
        <span>{t('source_detail.button_open_full')}</span>
        <ExternalLink size={14} aria-hidden="true" className="opacity-70" />
      </button>
    </section>
  );
}

export default SourceDetailPanel;
