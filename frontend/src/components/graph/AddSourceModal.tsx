import { useEffect, useMemo, useState } from 'react';
import { Search, Link as LinkIcon } from 'lucide-react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Kbd from '@/components/ui/Kbd';
import { apiGetRaw, apiPostRaw, ApiError } from '@/api/client';
import type { components } from '@/api/types';
import {
  SOURCE_TYPE_LABEL,
  SOURCE_TYPE_ICON,
  type SourceType,
} from '@/utils/attachmentTokens';

type SourceDto = components['schemas']['SourceResponse'];
type NodeSourceDto = components['schemas']['NodeSourceResponse'];

interface Props {
  nodeId: string;
  onClose: () => void;
  /** вызывается после успешной привязки - чтобы родитель refetch'нул секцию */
  onAttached: () => void;
}

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; sources: SourceDto[] }
  | { kind: 'error'; message: string };

/**
 * Монтируется только когда модалка открыта - state чистый при каждом открытии.
 * Родитель управляет жизненным циклом через conditional render: `{open && <AddSourceModal .../>}`
 */
function AddSourceModal({ nodeId, onClose, onAttached }: Props) {
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [quote, setQuote] = useState('');
  const [context, setContext] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiGetRaw<SourceDto[]>('/api/v1/sources')
      .then((sources) => {
        if (!cancelled) setState({ kind: 'loaded', sources });
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        const msg =
          e instanceof ApiError
            ? e.problem.detail || e.problem.title
            : e instanceof Error
              ? e.message
              : 'Не удалось загрузить справочник';
        setState({ kind: 'error', message: msg });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = useMemo<SourceDto[]>(() => {
    if (state.kind !== 'loaded') return [];
    const q = query.trim().toLowerCase();
    if (!q) return state.sources;
    return state.sources.filter((s) => {
      const haystack = `${s.title ?? ''} ${s.citation ?? ''}`.toLowerCase();
      return haystack.includes(q);
    });
  }, [state, query]);

  const selected = useMemo<SourceDto | undefined>(() => {
    if (state.kind !== 'loaded' || !selectedId) return undefined;
    return state.sources.find((s) => s.id === selectedId);
  }, [state, selectedId]);

  function handleClose() {
    if (submitting) return;
    onClose();
  }

  async function handleSubmit() {
    if (!selectedId) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await apiPostRaw<NodeSourceDto>(`/api/v1/nodes/${nodeId}/sources`, {
        sourceId: selectedId,
        quote: quote.trim() ? quote.trim() : undefined,
        context: context.trim() ? context.trim() : undefined,
      });
      onAttached();
      onClose();
    } catch (e: unknown) {
      const msg =
        e instanceof ApiError
          ? e.problem.detail || e.problem.title
          : e instanceof Error
            ? e.message
            : 'Не удалось привязать источник';
      setSubmitError(msg);
      setSubmitting(false);
    }
  }

  return (
    <Modal open onClose={handleClose} title="Привязать источник">
      <div className="space-y-4">
        <div className="relative">
          <Search
            size={14}
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
          />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Найти по названию или citation"
            aria-label="Поиск источника"
            disabled={submitting}
            className="block w-full rounded-md border border-slate-300 bg-white py-2 pl-9 pr-3 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
          />
        </div>

        <div className="max-h-[280px] overflow-y-auto rounded-md border border-slate-200 bg-white">
          {state.kind === 'loading' && (
            <p className="px-3 py-4 text-[12px] text-slate-500">Загрузка справочника</p>
          )}
          {state.kind === 'error' && (
            <p className="px-3 py-4 text-[12px] text-red-700">Ошибка: {state.message}</p>
          )}
          {state.kind === 'loaded' && state.sources.length === 0 && (
            <p className="px-3 py-4 text-[12px] italic text-slate-500">
              Справочник пуст. Создайте первый источник в подэтапе 12.c
            </p>
          )}
          {state.kind === 'loaded' && state.sources.length > 0 && filtered.length === 0 && (
            <p className="px-3 py-4 text-[12px] italic text-slate-500">
              Ничего не нашлось по запросу «{query}»
            </p>
          )}
          {state.kind === 'loaded' && filtered.length > 0 && (
            <ul role="listbox" aria-label="Справочник источников" className="divide-y divide-slate-100">
              {filtered.map((src) => {
                if (!src.id) return null;
                const sourceType: SourceType = src.sourceType ?? 'BOOK';
                const Icon = SOURCE_TYPE_ICON[sourceType];
                const kindLabel = SOURCE_TYPE_LABEL[sourceType];
                const isSelected = selectedId === src.id;
                return (
                  <li key={src.id}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      onClick={() => setSelectedId(src.id ?? null)}
                      disabled={submitting}
                      className={`flex w-full items-start gap-2 px-3 py-2 text-left transition-colors ${
                        isSelected ? 'bg-indigo-50/70' : 'hover:bg-slate-50'
                      }`}
                    >
                      <span className="mt-0.5 grid h-7 w-7 flex-shrink-0 place-items-center rounded bg-slate-100 text-slate-600">
                        <Icon size={14} aria-hidden="true" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-[10px] font-semibold uppercase text-slate-500">
                            {kindLabel}
                          </span>
                          {src.reliability && (
                            <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[9px] uppercase text-slate-600">
                              {src.reliability}
                            </span>
                          )}
                        </div>
                        <div className="text-[13px] font-semibold text-slate-800 line-clamp-1">
                          {src.title ?? '(без названия)'}
                        </div>
                        {src.citation && (
                          <div className="font-mono text-[11px] text-slate-500 line-clamp-1">
                            {src.citation}
                          </div>
                        )}
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        {selected && (
          <fieldset disabled={submitting} className="space-y-2 rounded-md border border-slate-200 bg-slate-50/40 p-3">
            <legend className="px-1 text-[11px] font-medium text-slate-600">
              Поля привязки (опционально)
            </legend>
            <div>
              <label htmlFor="attach-quote" className="mb-1 block text-[11px] text-slate-600">
                Цитата
              </label>
              <textarea
                id="attach-quote"
                value={quote}
                onChange={(e) => setQuote(e.target.value)}
                rows={2}
                placeholder="Конкретный фрагмент источника, который относится к этому узлу"
                className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              />
            </div>
            <div>
              <label htmlFor="attach-context" className="mb-1 block text-[11px] text-slate-600">
                Контекст
              </label>
              <input
                id="attach-context"
                type="text"
                value={context}
                onChange={(e) => setContext(e.target.value)}
                placeholder="В какой главе, при каком обсуждении и т.п."
                className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              />
            </div>
          </fieldset>
        )}

        {submitError && (
          <div className="rounded-md border border-red-300 bg-red-50 p-3 text-[12px] text-red-800">
            {submitError}
          </div>
        )}

        <div className="flex items-center justify-between border-t border-slate-200 pt-3">
          <span className="hidden items-center gap-1 text-[11px] text-slate-500 sm:inline-flex">
            <Kbd>Esc</Kbd> отмена
          </span>
          <div className="ml-auto flex items-center gap-2">
            <Button type="button" variant="ghost" onClick={handleClose} disabled={submitting}>
              Отмена
            </Button>
            <Button
              type="button"
              icon={LinkIcon}
              onClick={handleSubmit}
              disabled={submitting || !selectedId}
            >
              {submitting ? 'Привязываем' : 'Привязать'}
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}

export default AddSourceModal;
