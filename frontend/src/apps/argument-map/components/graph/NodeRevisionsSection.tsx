import { useState } from 'react';
import { ChevronDown, ChevronRight, History } from 'lucide-react';
import { apiGetRaw, formatApiError } from '@/shared/api/client';
import { shortId } from '@/apps/argument-map/components/graph/nodeDetailsUtils';
import { useFormatDate, useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type RevisionDto = components['schemas']['RevisionResponse'];

type RevisionsState =
  | { kind: 'not-loaded' }
  | { kind: 'loading' }
  | { kind: 'loaded'; revisions: RevisionDto[] }
  | { kind: 'error'; message: string };

interface Props {
  nodeId: string | undefined;
}

/**
 * Секция "История изменений" - lazy-loaded список revisions с diff'ом
 * before/after. Custom chevron rendering (вместо PanelSection) потому
 * что count прокидывается только когда уже loaded, иначе hidden.
 */
function NodeRevisionsSection({ nodeId }: Props) {
  const t = useT();
  const formatDate = useFormatDate();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [state, setState] = useState<RevisionsState>({ kind: 'not-loaded' });

  async function loadRevisions() {
    if (!nodeId) return;
    setState({ kind: 'loading' });
    try {
      const list = await apiGetRaw<RevisionDto[]>(`/api/v1/nodes/${nodeId}/revisions`);
      setState({ kind: 'loaded', revisions: list });
    } catch (e: unknown) {
      setState({ kind: 'error', message: formatApiError(e, t('graph.toast.history_load_failed')) });
    }
  }

  function toggle() {
    if (historyOpen) {
      setHistoryOpen(false);
      return;
    }
    setHistoryOpen(true);
    if (state.kind === 'not-loaded') {
      void loadRevisions();
    }
  }

  return (
    <section className="border-t border-border">
      <button
        type="button"
        onClick={toggle}
        aria-expanded={historyOpen}
        className="flex w-full items-center gap-2 px-5 py-3 text-start transition-colors hover:bg-ink-50"
      >
        <History size={14} className="text-ink-500" aria-hidden="true" />
        <span className="text-xs font-semibold uppercase tracking-wider text-ink-700">
          {t('node.section.history')}
        </span>
        {state.kind === 'loaded' && (
          <span className="text-xs font-mono text-ink-400">{state.revisions.length}</span>
        )}
        {historyOpen ? (
          <ChevronDown size={14} className="ms-auto text-ink-400" aria-hidden="true" />
        ) : (
          <ChevronRight size={14} className="ms-auto text-ink-400 rtl:-scale-x-100" aria-hidden="true" />
        )}
      </button>

      {historyOpen && (
        <div className="space-y-2 px-5 pb-4">
          {state.kind === 'loading' && <p className="text-xs text-ink-500">{t('common.loading')}</p>}
          {state.kind === 'error' && (
            <p className="text-xs text-err-700">{t('common.error')}: {state.message}</p>
          )}
          {state.kind === 'loaded' && state.revisions.length === 0 && (
            <p className="text-xs italic text-ink-500">{t('node.history_empty')}</p>
          )}
          {state.kind === 'loaded' &&
            state.revisions.length > 0 &&
            [...state.revisions]
              .sort((a, b) => (b.changedAt ?? '').localeCompare(a.changedAt ?? ''))
              .map((r) => (
                <article
                  key={r.id}
                  className="overflow-hidden rounded-md border border-border bg-elevated"
                >
                  <header className="flex items-center justify-between border-b border-border bg-ink-50 px-3 py-1.5 text-xs">
                    <span className="font-mono text-ink-500">{t('node.revision_label')}</span>
                    <span className="text-ink-500">
                      {formatDate(r.changedAt)} ·{' '}
                      <span className="font-mono" title={r.changedBy}>
                        {shortId(r.changedBy)}
                      </span>
                    </span>
                  </header>
                  <div className="divide-y divide-border text-xs font-mono">
                    {r.contentBefore && (
                      <div className="whitespace-pre-wrap break-words bg-err-100/40 px-3 py-1.5 text-err-700">
                        <span className="select-none text-err-500">- </span>
                        {r.contentBefore}
                      </div>
                    )}
                    {r.contentAfter && (
                      <div className="whitespace-pre-wrap break-words bg-ok-100/40 px-3 py-1.5 text-ok-700">
                        <span className="select-none text-ok-700">+ </span>
                        {r.contentAfter}
                      </div>
                    )}
                  </div>
                </article>
              ))}
        </div>
      )}
    </section>
  );
}

export default NodeRevisionsSection;
