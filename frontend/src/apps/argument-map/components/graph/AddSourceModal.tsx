import { useEffect, useState } from 'react';
import { Link as LinkIcon, Plus } from 'lucide-react';
import Modal from '@/shared/components/ui/Modal';
import Button from '@/shared/components/ui/Button';
import Kbd from '@/shared/components/ui/Kbd';
import { apiGetRaw, apiPost, apiPostRaw, formatApiError } from '@/shared/api/client';
import { useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';
import SourceSearchForm, {
  type SourceLoadState,
} from '@/apps/argument-map/components/graph/SourceSearchForm';
import SourceCreateForm, {
  INITIAL_CREATE_FORM,
  type CreateForm,
} from '@/apps/argument-map/components/graph/SourceCreateForm';
import { parseIntOrNull } from '@/shared/components/citation/AcademicMetadataFields';

type NodeSourceDto = components['schemas']['NodeSourceResponse'];
type BookResponseDto = components['schemas']['BookResponse'];
type PagedSources = components['schemas']['PagedResponseSourceResponse'];

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
  const t = useT();
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
    // GET /api/v1/sources возвращает PagedResponse (commit 306e0c0) -
    // unwrap .items. size=100 для одной страницы при поиске
    apiGetRaw<PagedSources>('/api/v1/sources?size=100')
      .then((page) => {
        if (!cancelled) setState({ kind: 'loaded', sources: page.items ?? [] });
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setState({ kind: 'error', message: formatApiError(e, t('common.list_search_failed')) });
      });
    return () => {
      cancelled = true;
    };
  }, [t]);

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
    // Этап 20.e: для sourceType=BOOK с заполненным academic - 2-step flow:
    // 1) POST /library/books -> bookId
    // 2) POST /sources с bookId
    // 3) attach
    // Иначе - legacy single-step без bookId.
    let bookId: string | undefined;
    if (createForm.sourceType === 'BOOK' && hasAcademicData(createForm)) {
      const a = createForm.academic;
      const createdBook = await apiPostRaw<BookResponseDto>(
        '/api/v1/library/books',
        {
          bookType: 'BOOK',
          title: createForm.title.trim(),
          language: 'ar',
          muhaqqiqName: a.muhaqqiq.trim() || undefined,
          publisherName: a.publisher.trim() || undefined,
          publicationPlaceName: a.place.trim() || undefined,
          editionNumber: parseIntOrNull(a.edition) ?? undefined,
          publishedYearHijri: parseIntOrNull(a.yearHijri) ?? undefined,
          publishedYearGregorian: parseIntOrNull(a.yearGregorian) ?? undefined,
        },
      );
      bookId = createdBook.id ?? undefined;
    }

    const created = await apiPost('/api/v1/sources', {
      sourceType: createForm.sourceType,
      title: createForm.title.trim(),
      citation: createForm.citation.trim() || undefined,
      reliability:
        createForm.sourceType === 'HADITH' && createForm.reliability
          ? createForm.reliability
          : undefined,
      ...(bookId ? { bookId } : {}),
    });
    if (!created.id) {
      throw new Error(t('common.unknown_error'));
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
      setSubmitError(formatApiError(e, t('add_source.error.create_failed')));
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
      title={mode === 'create' ? t('add_source.title') : t('node.citation_add_library')}
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
          <div className="rounded-md border border-err-500/40 bg-err-100 p-3 text-xs text-err-700">
            {submitError}
          </div>
        )}

        <div className="flex items-center justify-between border-t border-border pt-3">
          <span className="hidden items-center gap-1 text-xs text-ink-500 sm:inline-flex">
            <Kbd>Esc</Kbd> {t('common.cancel')}
          </span>
          <div className="ms-auto flex items-center gap-2">
            <Button type="button" variant="ghost" onClick={handleClose} disabled={submitting}>
              {t('common.cancel')}
            </Button>
            <Button
              type="button"
              icon={mode === 'create' ? Plus : LinkIcon}
              onClick={handleSubmit}
              disabled={submitting || !(canAttach || canCreate)}
            >
              {submitting
                ? t('common.saving')
                : mode === 'create'
                  ? t('add_source.create_submit')
                  : t('citation_picker.submit')}
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}

function hasAcademicData(form: CreateForm): boolean {
  const a = form.academic;
  return (
    a.muhaqqiq.trim() !== '' ||
    a.publisher.trim() !== '' ||
    a.place.trim() !== '' ||
    a.edition.trim() !== '' ||
    a.yearHijri.trim() !== '' ||
    a.yearGregorian.trim() !== ''
  );
}

export default AddSourceModal;
