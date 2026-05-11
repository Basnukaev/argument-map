import { useEffect, useMemo, useState } from 'react';
import { Search, Link as LinkIcon, Plus, ArrowLeft } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Kbd from '@/shared/components/ui/Kbd';
import { apiGetRaw, apiPost, apiPostRaw, formatApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';
import {
  SOURCE_TYPE_LABEL,
  SOURCE_TYPE_ICON,
  SOURCE_TYPE_HINT,
  SOURCE_TYPE_ORDER,
  type SourceType,
} from '@/apps/argument-map/utils/attachmentTokens';

type SourceDto = components['schemas']['SourceResponse'];
type NodeSourceDto = components['schemas']['NodeSourceResponse'];

type Mode = 'search' | 'create';

interface CreateForm {
  sourceType: SourceType;
  title: string;
  citation: string;
  reliability: 'SAHIH' | 'HASAN' | 'DAIF' | '';
}

const INITIAL_CREATE_FORM: CreateForm = {
  sourceType: 'BOOK',
  title: '',
  citation: '',
  reliability: '',
};

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
  const [mode, setMode] = useState<Mode>('search');
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [quote, setQuote] = useState('');
  const [context, setContext] = useState('');
  const [location, setLocation] = useState('');
  const [createForm, setCreateForm] = useState<CreateForm>(INITIAL_CREATE_FORM);
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
        setState({ kind: 'error', message: formatApiError(e, 'Не удалось загрузить справочник') });
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

  function openCreateMode() {
    setMode('create');
    setSubmitError(null);
  }

  function backToSearch() {
    setMode('search');
    setSubmitError(null);
  }

  async function attachExisting(sourceId: string) {
    await apiPostRaw<NodeSourceDto>(`/api/v1/nodes/${nodeId}/sources`, {
      sourceId,
      quote: quote.trim() ? quote.trim() : undefined,
      context: context.trim() ? context.trim() : undefined,
      location: location.trim() ? location.trim() : undefined,
    });
  }

  async function createAndAttach(): Promise<void> {
    const created = await apiPost('/api/v1/sources', {
      sourceType: createForm.sourceType,
      title: createForm.title.trim(),
      citation: createForm.citation.trim() || undefined,
      reliability:
        createForm.sourceType === 'HADITH' && createForm.reliability
          ? createForm.reliability
          : undefined,
    });
    if (!created.id) {
      throw new Error('Бэк не вернул id нового источника');
    }
    await attachExisting(created.id);
  }

  async function handleSubmit() {
    setSubmitting(true);
    setSubmitError(null);
    try {
      if (mode === 'search') {
        if (!selectedId) return;
        await attachExisting(selectedId);
      } else {
        await createAndAttach();
      }
      onAttached();
      onClose();
    } catch (e: unknown) {
      setSubmitError(formatApiError(e, 'Не удалось привязать источник'));
      setSubmitting(false);
    }
  }

  const canCreate =
    mode === 'create' &&
    createForm.title.trim().length > 0 &&
    (createForm.sourceType !== 'HADITH' || createForm.reliability !== '');
  const canAttach = mode === 'search' && Boolean(selectedId);

  return (
    <Modal
      open
      onClose={handleClose}
      title={mode === 'create' ? 'Создать новый источник' : 'Привязать источник'}
    >
      <div className="space-y-4">
        {mode === 'search' ? (
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
                onChange={(e) => setQuery(e.target.value)}
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
              {state.kind === 'loaded' &&
                state.sources.length > 0 &&
                filtered.length === 0 && (
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

            <Button
              type="button"
              variant="ghost"
              size="sm"
              icon={Plus}
              onClick={openCreateMode}
              disabled={submitting}
              className="w-full justify-center"
            >
              Создать новый источник
            </Button>

            {selected && (
              <AttachFields
                quote={quote}
                context={context}
                location={location}
                onQuoteChange={setQuote}
                onContextChange={setContext}
                onLocationChange={setLocation}
                disabled={submitting}
              />
            )}
          </>
        ) : (
          <>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              icon={ArrowLeft}
              onClick={backToSearch}
              disabled={submitting}
            >
              К поиску в справочнике
            </Button>

            <fieldset disabled={submitting} className="space-y-3">
              <legend className="mb-1 text-[12px] font-medium text-slate-700">
                Тип источника
              </legend>
              <div className="grid grid-cols-5 gap-2">
                {SOURCE_TYPE_ORDER.map((type) => {
                  const Icon = SOURCE_TYPE_ICON[type];
                  const isSelected = createForm.sourceType === type;
                  return (
                    <label
                      key={type}
                      title={SOURCE_TYPE_HINT[type]}
                      className={`flex cursor-pointer flex-col items-center gap-1 rounded-md border p-2 text-center transition-colors ${
                        isSelected
                          ? 'border-indigo-500 bg-indigo-50/60 ring-1 ring-indigo-400'
                          : 'border-slate-300 hover:bg-slate-50'
                      }`}
                    >
                      <span className="grid h-6 w-6 place-items-center rounded bg-slate-100 text-slate-600">
                        <Icon size={13} aria-hidden="true" />
                      </span>
                      <input
                        type="radio"
                        name="source-type"
                        value={type}
                        checked={isSelected}
                        onChange={() =>
                          setCreateForm((f) => ({
                            ...f,
                            sourceType: type,
                            reliability: type === 'HADITH' ? f.reliability : '',
                          }))
                        }
                        className="sr-only"
                      />
                      <span className="text-[10px] font-semibold text-slate-700">
                        {SOURCE_TYPE_LABEL[type]}
                      </span>
                    </label>
                  );
                })}
              </div>

              <div>
                <label
                  htmlFor="create-title"
                  className="mb-1 block text-[12px] font-medium text-slate-700"
                >
                  Название
                </label>
                <input
                  id="create-title"
                  type="text"
                  value={createForm.title}
                  onChange={(e) =>
                    setCreateForm((f) => ({ ...f, title: e.target.value }))
                  }
                  required
                  maxLength={500}
                  placeholder="Например: Сахих аль-Бухари, №3000"
                  className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                />
              </div>

              <div>
                <label
                  htmlFor="create-citation"
                  className="mb-1 block text-[12px] font-medium text-slate-700"
                >
                  Цитата для подписи (опционально)
                </label>
                <input
                  id="create-citation"
                  type="text"
                  value={createForm.citation}
                  onChange={(e) =>
                    setCreateForm((f) => ({ ...f, citation: e.target.value }))
                  }
                  maxLength={500}
                  placeholder="Том · страница · глава"
                  className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                />
              </div>

              {createForm.sourceType === 'HADITH' && (
                <div>
                  <label className="mb-1 block text-[12px] font-medium text-slate-700">
                    Степень достоверности (`reliability`)
                  </label>
                  <div className="grid grid-cols-3 gap-2">
                    {(['SAHIH', 'HASAN', 'DAIF'] as const).map((rel) => {
                      const isSelected = createForm.reliability === rel;
                      return (
                        <label
                          key={rel}
                          className={`flex cursor-pointer items-center justify-center rounded-md border px-2 py-1.5 font-mono text-[11px] uppercase transition-colors ${
                            isSelected
                              ? 'border-indigo-500 bg-indigo-50/60 text-indigo-800'
                              : 'border-slate-300 text-slate-700 hover:bg-slate-50'
                          }`}
                        >
                          <input
                            type="radio"
                            name="reliability"
                            value={rel}
                            checked={isSelected}
                            onChange={() =>
                              setCreateForm((f) => ({ ...f, reliability: rel }))
                            }
                            className="sr-only"
                          />
                          {rel}
                        </label>
                      );
                    })}
                  </div>
                  <p className="mt-1 text-[10px] text-slate-500">
                    Обязательно для типа `HADITH` хадис - бэк отвергнет без grade
                    (`InvalidSourceException` 422)
                  </p>
                </div>
              )}
            </fieldset>

            <AttachFields
              quote={quote}
              context={context}
              location={location}
              onQuoteChange={setQuote}
              onContextChange={setContext}
              onLocationChange={setLocation}
              disabled={submitting}
            />
          </>
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
              icon={mode === 'create' ? Plus : LinkIcon}
              onClick={handleSubmit}
              disabled={submitting || !(canAttach || canCreate)}
            >
              {submitting
                ? mode === 'create'
                  ? 'Создаём'
                  : 'Привязываем'
                : mode === 'create'
                  ? 'Создать и привязать'
                  : 'Привязать'}
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}

interface AttachFieldsProps {
  quote: string;
  context: string;
  location: string;
  onQuoteChange: (v: string) => void;
  onContextChange: (v: string) => void;
  onLocationChange: (v: string) => void;
  disabled?: boolean;
}

function AttachFields({
  quote,
  context,
  location,
  onQuoteChange,
  onContextChange,
  onLocationChange,
  disabled,
}: AttachFieldsProps) {
  return (
    <fieldset
      disabled={disabled}
      className="space-y-2 rounded-md border border-slate-200 bg-slate-50/40 p-3"
    >
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
          onChange={(e) => onQuoteChange(e.target.value)}
          rows={2}
          placeholder="Конкретный фрагмент источника, который относится к этому узлу"
          className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
        />
      </div>
      <div>
        <label htmlFor="attach-location" className="mb-1 block text-[11px] text-slate-600">
          Место в источнике
        </label>
        <input
          id="attach-location"
          type="text"
          value={location}
          onChange={(e) => onLocationChange(e.target.value)}
          maxLength={200}
          placeholder="Например: т.13 с.137, №1162, 2:256"
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
          onChange={(e) => onContextChange(e.target.value)}
          placeholder="В какой главе, при каком обсуждении и т.п."
          className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
        />
      </div>
    </fieldset>
  );
}

export default AddSourceModal;
