import { useEffect, useMemo, useState } from 'react';
import { Search, Link as LinkIcon, Plus, ArrowLeft } from 'lucide-react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Kbd from '@/components/ui/Kbd';
import { apiGetRaw, apiPost, apiPostRaw, ApiError } from '@/api/client';
import type { components } from '@/api/types';
import {
  STANCE_LABEL,
  STANCE_BADGE_STYLES,
  STANCE_ORDER,
  STANCE_RADIO_STYLES,
  type Stance,
} from '@/utils/attachmentTokens';

type AuthorityDto = components['schemas']['AuthorityResponse'];
type NodeAuthorityDto = components['schemas']['NodeAuthorityResponse'];

type Mode = 'search' | 'create';

interface CreateForm {
  name: string;
  era: string;
  madhab: string;
  bio: string;
}

const INITIAL_CREATE_FORM: CreateForm = {
  name: '',
  era: '',
  madhab: '',
  bio: '',
};

interface Props {
  nodeId: string;
  onClose: () => void;
  onAttached: () => void;
}

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; authorities: AuthorityDto[] }
  | { kind: 'error'; message: string };

function avatarInitials(name?: string): string {
  if (!name) return '·';
  const parts = name.trim().split(/\s+/).slice(0, 2);
  return parts.map((p) => p.charAt(0).toUpperCase()).join('') || '·';
}

/**
 * Монтируется только когда модалка открыта - state чистый при каждом открытии.
 * Родитель управляет жизненным циклом через `{open && <AddAuthorityModal .../>}`.
 */
function AddAuthorityModal({ nodeId, onClose, onAttached }: Props) {
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [mode, setMode] = useState<Mode>('search');
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [stance, setStance] = useState<Stance>('HOLDS');
  const [createForm, setCreateForm] = useState<CreateForm>(INITIAL_CREATE_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiGetRaw<AuthorityDto[]>('/api/v1/authorities')
      .then((authorities) => {
        if (!cancelled) setState({ kind: 'loaded', authorities });
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

  const filtered = useMemo<AuthorityDto[]>(() => {
    if (state.kind !== 'loaded') return [];
    const q = query.trim().toLowerCase();
    if (!q) return state.authorities;
    return state.authorities.filter((a) => {
      const haystack = `${a.name ?? ''} ${a.era ?? ''} ${a.madhab ?? ''}`.toLowerCase();
      return haystack.includes(q);
    });
  }, [state, query]);

  const selected = useMemo<AuthorityDto | undefined>(() => {
    if (state.kind !== 'loaded' || !selectedId) return undefined;
    return state.authorities.find((a) => a.id === selectedId);
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

  async function attachExisting(authorityId: string) {
    await apiPostRaw<NodeAuthorityDto>(`/api/v1/nodes/${nodeId}/authorities`, {
      authorityId,
      stance,
    });
  }

  async function createAndAttach(): Promise<void> {
    const created = await apiPost('/api/v1/authorities', {
      name: createForm.name.trim(),
      era: createForm.era.trim() || undefined,
      madhab: createForm.madhab.trim() || undefined,
      bio: createForm.bio.trim() || undefined,
    });
    if (!created.id) {
      throw new Error('Бэк не вернул id нового авторитета');
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
      const msg =
        e instanceof ApiError
          ? (e.problem.errors?.map((er) => `${er.field}: ${er.message}`).join('; ') ||
            e.problem.detail ||
            e.problem.title)
          : e instanceof Error
            ? e.message
            : 'Не удалось привязать авторитет';
      setSubmitError(msg);
      setSubmitting(false);
    }
  }

  const canCreate = mode === 'create' && createForm.name.trim().length > 0;
  const canAttach = mode === 'search' && Boolean(selectedId);

  return (
    <Modal
      open
      onClose={handleClose}
      title={mode === 'create' ? 'Создать нового авторитета' : 'Привязать авторитета'}
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
                placeholder="Найти по имени, эпохе или мазхабу"
                aria-label="Поиск авторитета"
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
              {state.kind === 'loaded' && state.authorities.length === 0 && (
                <p className="px-3 py-4 text-[12px] italic text-slate-500">
                  Справочник пуст - создайте первого авторитета кнопкой ниже
                </p>
              )}
              {state.kind === 'loaded' &&
                state.authorities.length > 0 &&
                filtered.length === 0 && (
                  <p className="px-3 py-4 text-[12px] italic text-slate-500">
                    Ничего не нашлось по запросу «{query}»
                  </p>
                )}
              {state.kind === 'loaded' && filtered.length > 0 && (
                <ul
                  role="listbox"
                  aria-label="Справочник авторитетов"
                  className="divide-y divide-slate-100"
                >
                  {filtered.map((a) => {
                    if (!a.id) return null;
                    const isSelected = selectedId === a.id;
                    const meta = [a.era, a.madhab].filter(Boolean).join(' · ');
                    return (
                      <li key={a.id}>
                        <button
                          type="button"
                          role="option"
                          aria-selected={isSelected}
                          onClick={() => setSelectedId(a.id ?? null)}
                          disabled={submitting}
                          className={`flex w-full items-start gap-2 px-3 py-2 text-left transition-colors ${
                            isSelected ? 'bg-indigo-50/70' : 'hover:bg-slate-50'
                          }`}
                        >
                          <span
                            aria-hidden="true"
                            className="mt-0.5 grid h-7 w-7 flex-shrink-0 place-items-center rounded-full bg-slate-200 text-[11px] font-semibold text-slate-700"
                          >
                            {avatarInitials(a.name)}
                          </span>
                          <div className="min-w-0 flex-1">
                            <div className="text-[13px] font-semibold text-slate-800 line-clamp-1">
                              {a.name ?? '(без имени)'}
                            </div>
                            {meta && (
                              <div className="font-mono text-[11px] text-slate-500 line-clamp-1">
                                {meta}
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
              Создать нового авторитета
            </Button>

            {selected && (
              <StancePicker stance={stance} onChange={setStance} disabled={submitting} />
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
              <div>
                <label
                  htmlFor="auth-name"
                  className="mb-1 block text-[12px] font-medium text-slate-700"
                >
                  Имя
                </label>
                <input
                  id="auth-name"
                  type="text"
                  value={createForm.name}
                  onChange={(e) =>
                    setCreateForm((f) => ({ ...f, name: e.target.value }))
                  }
                  required
                  maxLength={300}
                  placeholder="Например: Ибн Хаджар аль-Аскаляни"
                  className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label
                    htmlFor="auth-era"
                    className="mb-1 block text-[12px] font-medium text-slate-700"
                  >
                    Эпоха
                  </label>
                  <input
                    id="auth-era"
                    type="text"
                    value={createForm.era}
                    onChange={(e) =>
                      setCreateForm((f) => ({ ...f, era: e.target.value }))
                    }
                    maxLength={100}
                    placeholder="VIII–IX в.х."
                    className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                  />
                </div>
                <div>
                  <label
                    htmlFor="auth-madhab"
                    className="mb-1 block text-[12px] font-medium text-slate-700"
                  >
                    Мазхаб
                  </label>
                  <input
                    id="auth-madhab"
                    type="text"
                    value={createForm.madhab}
                    onChange={(e) =>
                      setCreateForm((f) => ({ ...f, madhab: e.target.value }))
                    }
                    maxLength={100}
                    placeholder="шафиитский"
                    className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                  />
                </div>
              </div>

              <div>
                <label
                  htmlFor="auth-bio"
                  className="mb-1 block text-[12px] font-medium text-slate-700"
                >
                  Краткая биография (опционально)
                </label>
                <textarea
                  id="auth-bio"
                  value={createForm.bio}
                  onChange={(e) =>
                    setCreateForm((f) => ({ ...f, bio: e.target.value }))
                  }
                  rows={2}
                  maxLength={2000}
                  placeholder="Хадисовед, автор Фатх аль-Бари"
                  className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400 outline-none transition-colors focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                />
              </div>
            </fieldset>

            <StancePicker stance={stance} onChange={setStance} disabled={submitting} />
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

interface StancePickerProps {
  stance: Stance;
  onChange: (s: Stance) => void;
  disabled?: boolean;
}

function StancePicker({ stance, onChange, disabled }: StancePickerProps) {
  return (
    <fieldset disabled={disabled} className="space-y-2">
      <legend className="text-[12px] font-medium text-slate-700">
        Позиция авторитета
      </legend>
      <div className="grid grid-cols-3 gap-2">
        {STANCE_ORDER.map((s) => {
          const isSelected = stance === s;
          const styles = STANCE_RADIO_STYLES[s];
          return (
            <label
              key={s}
              className={`flex cursor-pointer items-center justify-center rounded-md border px-2 py-1.5 text-[12px] font-medium transition-colors ${
                isSelected ? styles.selected : styles.idle
              }`}
            >
              <input
                type="radio"
                name="stance"
                value={s}
                checked={isSelected}
                onChange={() => onChange(s)}
                className="sr-only"
              />
              <span
                className={`mr-1.5 inline-block h-2 w-2 rounded-full ${
                  STANCE_BADGE_STYLES[s].split(' ')[0]
                }`}
                aria-hidden="true"
              />
              {STANCE_LABEL[s]}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}

export default AddAuthorityModal;
