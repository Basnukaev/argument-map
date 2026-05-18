import { useEffect, useRef, useState } from 'react';
import { ExternalLink } from 'lucide-react';
import type { components } from '@/shared/api/types';
import { useT } from '@/shared/i18n';
import { useSourceDetailPanelStore } from '@/shared/stores/sourceDetailPanelStore';

type InlineCitationRef = components['schemas']['InlineCitationRef'];

interface Props {
  ordinal: number;
  /** Если undefined - dead marker (grey, без popover). [N] есть в тексте, но
   *  в `node.inlineCitations` нет ref'а с таким ordinal'ом */
  citation?: InlineCitationRef;
}

/**
 * Inline citation marker `[N]` - render `<sup>` superscript с click-popover.
 *
 * - есть citation: indigo-tinted clickable chip, popover показывает
 *   title / quote / citation / reliability (для HADITH)
 * - нет citation: grey "dead" marker с tooltip «Источник не найден»
 *
 * Popover управляется внутренним state'ом. Click outside / Escape - закрытие.
 * Native HTML popover attribute не используется - jsdom его не поддерживает,
 * тесты падают. Простой absolute-positioned tooltip покрывает MVP
 */
function InlineCitationMarker({ ordinal, citation }: Props) {
  const t = useT();
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLSpanElement>(null);

  // Close on click outside + Escape
  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!citation) {
    // Dead marker - не clickable, нет popover, grey стиль
    return (
      <sup
        className="mx-px inline-block cursor-not-allowed rounded bg-ink-100 px-1 text-[0.7em] font-medium text-ink-400"
        title={t('node.inline_citation.dead_marker_tooltip')}
        data-testid={`inline-citation-dead-${ordinal}`}
      >
        [{ordinal}]
      </sup>
    );
  }

  const handleClick = (e: React.MouseEvent) => {
    // stopPropagation - на NodeCard в графе click пробрасывается в ReactFlow
    // и узел становится "selected"; для popover это лишнее
    e.stopPropagation();
    setOpen((prev) => !prev);
  };

  return (
    <span ref={wrapperRef} className="relative inline-block" data-testid={`inline-citation-${ordinal}`}>
      <sup
        role="button"
        tabIndex={0}
        aria-haspopup="dialog"
        aria-expanded={open}
        className="mx-px inline-block cursor-pointer rounded bg-accent-500/10 px-1 text-[0.7em] font-medium text-accent-600 transition-colors hover:bg-accent-500/20 dark:bg-accent-400/15 dark:text-accent-300 dark:hover:bg-accent-400/25"
        onClick={handleClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            handleClick(e as unknown as React.MouseEvent);
          }
        }}
      >
        [{ordinal}]
      </sup>
      {open && <CitationPopover citation={citation} t={t} onClose={() => setOpen(false)} />}
    </span>
  );
}

interface PopoverProps {
  citation: InlineCitationRef;
  t: ReturnType<typeof useT>;
  onClose: () => void;
}

/**
 * Popover-карточка citation. Открывается на click на маркере, абсолютно
 * позиционирована относительно wrapper'а. Содержит title, quote (если есть),
 * citation (academic string), reliability (для HADITH) и кнопку
 * «Открыть подробнее» которая открывает full `SourceDetailPanel` (если у
 * citation есть sourceId)
 */
function CitationPopover({ citation, t, onClose }: PopoverProps) {
  const openSourceDetail = useSourceDetailPanelStore((s) => s.openWith);
  const handleOpenDetail = () => {
    if (!citation.sourceId) return;
    openSourceDetail({
      sourceId: citation.sourceId,
      nodeSourceId: citation.nodeSourceId,
      quote: citation.quote ?? undefined,
    });
    onClose();
  };

  return (
    <div
      role="dialog"
      data-testid="inline-citation-popover"
      className="absolute left-0 top-full z-50 mt-1 w-72 rounded-md border border-border bg-elevated p-3 text-xs leading-relaxed text-ink-700 shadow-sh3 dark:text-ink-200"
      onClick={(e) => e.stopPropagation()}
    >
      {citation.title && (
        <div dir="auto" className="mb-2 text-sm font-semibold text-ink-900 dark:text-ink-100">
          {citation.title}
        </div>
      )}

      {citation.quote && (
        <blockquote
          dir="auto"
          className="mb-2 border-s-2 border-accent-500/40 ps-2 italic text-ink-700 dark:text-ink-200"
        >
          {citation.quote}
        </blockquote>
      )}

      {citation.citation && (
        <div dir="auto" className="mb-2 text-ink-600 dark:text-ink-300">
          {citation.citation}
        </div>
      )}

      {citation.reliability && citation.sourceType === 'HADITH' && (
        <div className="mt-1 inline-flex items-center gap-1.5 text-[11px]">
          <span className="text-ink-500">{t('node.inline_citation.reliability_label')}:</span>
          <span className="rounded bg-ink-100 px-1.5 py-0.5 font-mono font-medium uppercase text-ink-700 dark:bg-ink-800 dark:text-ink-200">
            {citation.reliability}
          </span>
        </div>
      )}

      {citation.sourceId && (
        <div className="mt-2 border-t border-border pt-2">
          <button
            type="button"
            onClick={handleOpenDetail}
            data-testid="inline-citation-open-detail"
            className="inline-flex items-center gap-1 text-[11px] font-medium text-accent-600 hover:text-accent-700 hover:underline dark:text-accent-300 dark:hover:text-accent-200"
          >
            {t('node.inline_citation.open_detail')}
            <ExternalLink size={10} aria-hidden="true" />
          </button>
        </div>
      )}

      <button
        type="button"
        onClick={onClose}
        className="absolute end-1 top-1 rounded p-1 text-ink-400 hover:bg-ink-100 hover:text-ink-700 dark:hover:bg-ink-800 dark:hover:text-ink-200"
        aria-label={t('common.close')}
      >
        <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
          <path d="M2 2 L10 10 M10 2 L2 10" stroke="currentColor" strokeWidth="1.5" fill="none" />
        </svg>
      </button>
    </div>
  );
}

export default InlineCitationMarker;
