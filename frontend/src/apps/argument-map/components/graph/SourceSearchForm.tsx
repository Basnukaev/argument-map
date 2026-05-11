import { useMemo } from 'react';
import { Search, Plus } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import type { components } from '@/shared/api/types';
import {
  SOURCE_TYPE_LABEL,
  SOURCE_TYPE_ICON,
  type SourceType,
} from '@/apps/argument-map/utils/attachmentTokens';
import AttachFields from '@/apps/argument-map/components/graph/AttachFields';

type SourceDto = components['schemas']['SourceResponse'];

export type SourceLoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; sources: SourceDto[] }
  | { kind: 'error'; message: string };

interface Props {
  state: SourceLoadState;
  query: string;
  onQueryChange: (v: string) => void;
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  onOpenCreate: () => void;
  quote: string;
  context: string;
  location: string;
  onQuoteChange: (v: string) => void;
  onContextChange: (v: string) => void;
  onLocationChange: (v: string) => void;
  submitting: boolean;
}

/**
 * Search-mode AddSourceModal: поиск существующего источника в справочнике
 * с фильтрацией, выбором, и AttachFields для метаданных привязки.
 */
function SourceSearchForm({
  state,
  query,
  onQueryChange,
  selectedId,
  onSelect,
  onOpenCreate,
  quote,
  context,
  location,
  onQuoteChange,
  onContextChange,
  onLocationChange,
  submitting,
}: Props) {
  const filtered = useMemo<SourceDto[]>(() => {
    if (state.kind !== 'loaded') return [];
    const q = query.trim().toLowerCase();
    if (!q) return state.sources;
    return state.sources.filter((s) => {
      const haystack = `${s.title ?? ''} ${s.citation ?? ''}`.toLowerCase();
      return haystack.includes(q);
    });
  }, [state, query]);

  const hasSelection = selectedId !== null && state.kind === 'loaded'
    && state.sources.some((s) => s.id === selectedId);

  return (
    <>
      <div className="relative">
        <Search
          size={14}
          aria-hidden="true"
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
        />
        <input
          type="text"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="Найти по названию или citation"
          aria-label="Поиск источника"
          disabled={submitting}
          className="block w-full rounded-md border border-slate-300 bg-white py-2 pl-9 pr-3 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
        />
      </div>

      <div className="max-h-[260px] overflow-y-auto rounded-md border border-slate-200 bg-white">
        {state.kind === 'loading' && (
          <p className="px-3 py-4 text-[12px] text-slate-500">Загрузка справочника</p>
        )}
        {state.kind === 'error' && (
          <p className="px-3 py-4 text-[12px] text-red-700">Ошибка: {state.message}</p>
        )}
        {state.kind === 'loaded' && state.sources.length === 0 && (
          <p className="px-3 py-4 text-[12px] italic text-slate-500">
            Справочник пуст - создайте первый источник кнопкой ниже
          </p>
        )}
        {state.kind === 'loaded' && state.sources.length > 0 && filtered.length === 0 && (
          <p className="px-3 py-4 text-[12px] italic text-slate-500">
            Ничего не нашлось по запросу «{query}»
          </p>
        )}
        {state.kind === 'loaded' && filtered.length > 0 && (
          <ul
            role="listbox"
            aria-label="Справочник источников"
            className="divide-y divide-slate-100"
          >
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
                    onClick={() => onSelect(src.id ?? null)}
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

      <Button
        type="button"
        variant="ghost"
        size="sm"
        icon={Plus}
        onClick={onOpenCreate}
        disabled={submitting}
        className="w-full justify-center"
      >
        Создать новый источник
      </Button>

      {hasSelection && (
        <AttachFields
          quote={quote}
          context={context}
          location={location}
          onQuoteChange={onQuoteChange}
          onContextChange={onContextChange}
          onLocationChange={onLocationChange}
          disabled={submitting}
        />
      )}
    </>
  );
}

export default SourceSearchForm;
