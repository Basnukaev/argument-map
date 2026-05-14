import { useState } from 'react';
import { ChevronDown, ChevronRight, History } from 'lucide-react';
import { apiGetRaw, formatApiError } from '@/shared/api/client';
import { formatDate, shortId } from '@/apps/argument-map/components/graph/nodeDetailsUtils';
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
  const [historyOpen, setHistoryOpen] = useState(false);
  const [state, setState] = useState<RevisionsState>({ kind: 'not-loaded' });

  async function loadRevisions() {
    if (!nodeId) return;
    setState({ kind: 'loading' });
    try {
      const list = await apiGetRaw<RevisionDto[]>(`/api/v1/nodes/${nodeId}/revisions`);
      setState({ kind: 'loaded', revisions: list });
    } catch (e: unknown) {
      setState({ kind: 'error', message: formatApiError(e, 'Не удалось загрузить историю') });
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
    <section className="border-t border-slate-200">
      <button
        type="button"
        onClick={toggle}
        aria-expanded={historyOpen}
        className="flex w-full items-center gap-2 px-5 py-3 text-start transition-colors hover:bg-slate-50"
      >
        <History size={14} className="text-slate-500" aria-hidden="true" />
        <span className="text-[12px] font-semibold uppercase tracking-wider text-slate-700">
          История изменений
        </span>
        {state.kind === 'loaded' && (
          <span className="text-[11px] font-mono text-slate-400">{state.revisions.length}</span>
        )}
        {historyOpen ? (
          <ChevronDown size={14} className="ms-auto text-slate-400" aria-hidden="true" />
        ) : (
          <ChevronRight size={14} className="ms-auto text-slate-400 rtl:-scale-x-100" aria-hidden="true" />
        )}
      </button>

      {historyOpen && (
        <div className="space-y-2 px-5 pb-4">
          {state.kind === 'loading' && <p className="text-[12px] text-slate-500">Загрузка</p>}
          {state.kind === 'error' && (
            <p className="text-[12px] text-red-700">Ошибка: {state.message}</p>
          )}
          {state.kind === 'loaded' && state.revisions.length === 0 && (
            <p className="text-[12px] italic text-slate-500">Изменений ещё не было</p>
          )}
          {state.kind === 'loaded' &&
            state.revisions.length > 0 &&
            [...state.revisions]
              .sort((a, b) => (b.changedAt ?? '').localeCompare(a.changedAt ?? ''))
              .map((r) => (
                <article
                  key={r.id}
                  className="overflow-hidden rounded-md border border-slate-200 bg-white"
                >
                  <header className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-3 py-1.5 text-[11px]">
                    <span className="font-mono text-slate-500">ревизия</span>
                    <span className="text-slate-500">
                      {formatDate(r.changedAt)} ·{' '}
                      <span className="font-mono" title={r.changedBy}>
                        {shortId(r.changedBy)}
                      </span>
                    </span>
                  </header>
                  <div className="divide-y divide-slate-100 text-[12px] font-mono">
                    {r.contentBefore && (
                      <div className="whitespace-pre-wrap break-words bg-red-50/40 px-3 py-1.5 text-red-800">
                        <span className="select-none text-red-500">- </span>
                        {r.contentBefore}
                      </div>
                    )}
                    {r.contentAfter && (
                      <div className="whitespace-pre-wrap break-words bg-emerald-50/40 px-3 py-1.5 text-emerald-800">
                        <span className="select-none text-emerald-600">+ </span>
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
