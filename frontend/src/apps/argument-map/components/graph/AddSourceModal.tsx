import { useEffect, useState } from 'react';
import { Link as LinkIcon, Plus } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Kbd from '@/shared/components/ui/Kbd';
import { apiGetRaw, apiPost, apiPostRaw, formatApiError } from '@/shared/api/client';
import type { components } from '@/shared/api/types';
import SourceSearchForm, {
  type SourceLoadState,
} from '@/apps/argument-map/components/graph/SourceSearchForm';
import SourceCreateForm, {
  INITIAL_CREATE_FORM,
  type CreateForm,
} from '@/apps/argument-map/components/graph/SourceCreateForm';

type SourceDto = components['schemas']['SourceResponse'];
type NodeSourceDto = components['schemas']['NodeSourceResponse'];

type Mode = 'search' | 'create';

interface Props {
  nodeId: string;
  onClose: () => void;
  /** вызывается после успешной привязки - чтобы родитель refetch'нул секцию */
  onAttached: () => void;
}

/**
 * Orchestrator модалки привязки источника к узлу. Управляет общим
 * состоянием (mode, attach fields, submitting), делегирует рендеринг
 * двух режимов в {@link SourceSearchForm} и {@link SourceCreateForm}.
 *
 * Монтируется только когда модалка открыта - state чистый при каждом
 * открытии. Родитель управляет жизненным циклом через conditional
 * render: `{open && <AddSourceModal .../>}`.
 */
function AddSourceModal({ nodeId, onClose, onAttached }: Props) {
  const [state, setState] = useState<SourceLoadState>({ kind: 'loading' });
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

  function handleClose() {
    if (submitting) return;
    onClose();
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
          <SourceSearchForm
            state={state}
            query={query}
            onQueryChange={setQuery}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onOpenCreate={() => {
              setMode('create');
              setSubmitError(null);
            }}
            quote={quote}
            context={context}
            location={location}
            onQuoteChange={setQuote}
            onContextChange={setContext}
            onLocationChange={setLocation}
            submitting={submitting}
          />
        ) : (
          <SourceCreateForm
            form={createForm}
            onFormChange={setCreateForm}
            onBack={() => {
              setMode('search');
              setSubmitError(null);
            }}
            quote={quote}
            context={context}
            location={location}
            onQuoteChange={setQuote}
            onContextChange={setContext}
            onLocationChange={setLocation}
            submitting={submitting}
          />
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

export default AddSourceModal;
