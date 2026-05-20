import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { BookOpen, Loader2, AlertCircle } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import CitationPicker from '@/shared/components/citation/CitationPicker';
import { SourceCard } from '@/shared/components/citation/sourceCard';
import { apiGetRaw, apiDeleteRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useSourceDetailPanelStore } from '@/shared/stores/sourceDetailPanelStore';
import { hasArabicScript, useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type QuestionSourceDto = components['schemas']['QuestionSourceResponse'];
type SourceDto = components['schemas']['SourceResponse'];
type PagedSources = components['schemas']['PagedResponseSourceResponse'];

interface Props {
  questionId: string;
  questionTitle: string;
}

type State =
  | { kind: 'loading' }
  | { kind: 'success'; links: QuestionSourceDto[]; sourceLookup: Map<string, SourceDto> }
  | { kind: 'error'; message: string };

/**
 * Секция «Источники» для Q&A detail page. Валидация ADR-018 platform
 * pivot - используется общий {@code CitationPicker} (через {@code targetType})
 * + общий {@code SourceCard} без копирования. Backend reuse - тот же
 * Source/Book/Page stack, новый endpoint {@code /api/v1/questions/{id}/citations}.
 *
 * <p>На MVP - только library positional citation (TEXT/PDF/REGION). Freeform
 * (LEGACY mode) не показывается - schema поддерживает, UI добавим если
 * появится UX-кейс.
 */
function QuestionCitationsSection({ questionId, questionTitle }: Props) {
  const t = useT();
  const navigate = useNavigate();
  const openSourceDetail = useSourceDetailPanelStore((s) => s.openWith);
  const [state, setState] = useState<State>({ kind: 'loading' });
  const [pickerOpen, setPickerOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      apiGetRaw<QuestionSourceDto[]>(`/api/v1/questions/${questionId}/sources`, {
        signal: controller.signal,
      }),
      // GET /api/v1/sources возвращает PagedResponse (commit 306e0c0) -
      // unwrap .items. size=100 чтобы повышенная вероятность найти lookup
      // для всех link'ов на одной странице
      apiGetRaw<PagedSources>('/api/v1/sources?size=100', { signal: controller.signal }),
    ])
      .then(([links, sourcesPage]) => {
        if (controller.signal.aborted) return;
        const sourceLookup = new Map<string, SourceDto>();
        for (const s of sourcesPage.items ?? []) {
          if (s.id) sourceLookup.set(s.id, s);
        }
        setState({ kind: 'success', links, sourceLookup });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'error', message: formatApiError(e, t('common.error')) });
      });
    return () => controller.abort();
  }, [questionId, t]);

  async function reload() {
    try {
      const links = await apiGetRaw<QuestionSourceDto[]>(`/api/v1/questions/${questionId}/sources`);
      setState((prev) => {
        if (prev.kind !== 'success') return prev;
        return { ...prev, links };
      });
    } catch (e) {
      toast.error(formatApiError(e, t('common.error')));
    }
  }

  async function detachCitation(questionSourceId: string) {
    if (state.kind !== 'success') return;
    const previous = state.links;
    setState({ ...state, links: previous.filter((l) => l.id !== questionSourceId) });
    try {
      await apiDeleteRaw(`/api/v1/questions/${questionId}/sources/${questionSourceId}`);
      toast.success(t('qa.sources.detached'));
    } catch (e) {
      toast.error(formatApiError(e, t('qa.sources.detach_failed')));
      setState({ ...state, links: previous });
    }
  }

  function buildDeepLink(link: QuestionSourceDto): string | null {
    const c = link.citation;
    if (!c?.book?.id) return null;
    if (link.mode === 'TEXT' && c.location?.pageId) {
      const rangeStart = c.location.rangeStart;
      const rangeEnd = c.location.rangeEnd;
      const range = rangeStart != null && rangeEnd != null ? `&highlight=${rangeStart}-${rangeEnd}` : '';
      return `/books/${c.book.id}?pageId=${c.location.pageId}${range}`;
    }
    if (link.mode === 'PDF' && c.pdf?.fileId && c.pdf.pageNumber != null) {
      const bbox = c.pdf.bbox as
        | { x?: number; y?: number; width?: number; height?: number }
        | undefined;
      const bboxStr =
        bbox && bbox.x != null
          ? `&bbox=${bbox.x},${bbox.y},${bbox.width},${bbox.height}`
          : '';
      return `/books/${c.book.id}?pdf=1&pdfPageNumber=${c.pdf.pageNumber}${bboxStr}`;
    }
    return null;
  }

  function pickLatinTitle(source: SourceDto | undefined, bookTitle?: string | null): string {
    const st = source?.title;
    if (st && !hasArabicScript(st)) return st;
    if (bookTitle && !hasArabicScript(bookTitle)) return bookTitle;
    return t('source_form.untitled');
  }

  return (
    <section className="mt-6">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-ink-500">
          {t('qa.sources.section_title')}
          {state.kind === 'success' && state.links.length > 0 && (
            <span className="ms-2 font-mono text-xs text-ink-400">
              <bdi dir="ltr">{state.links.length}</bdi>
            </span>
          )}
        </h2>
        <Button
          type="button"
          size="sm"
          icon={BookOpen}
          onClick={() => setPickerOpen(true)}
        >
          {t('qa.sources.add_button')}
        </Button>
      </div>

      {state.kind === 'loading' && (
        <div className="flex items-center gap-2 text-sm text-ink-500">
          <Loader2 size={16} className="animate-spin" aria-hidden />
          {t('common.loading')}
        </div>
      )}

      {state.kind === 'error' && (
        <Card className="border-err-500/40 bg-err-100 p-4">
          <div className="flex items-start gap-2">
            <AlertCircle size={16} className="mt-0.5 text-err-700" aria-hidden />
            <p className="text-xs text-err-700">{state.message}</p>
          </div>
        </Card>
      )}

      {state.kind === 'success' && state.links.length === 0 && (
        <p className="text-sm italic text-ink-400">{t('qa.sources.empty')}</p>
      )}

      {state.kind === 'success' && state.links.length > 0 && (
        <div className="space-y-2">
          {state.links.map((link) => {
            const source = link.sourceId ? state.sourceLookup.get(link.sourceId) : undefined;
            const titleLatin = pickLatinTitle(source, link.citation?.book?.title);
            const deepLink = buildDeepLink(link);
            const openPanel = link.sourceId
              ? () =>
                  openSourceDetail({
                    sourceId: link.sourceId!,
                    nodeSourceId: link.id,
                    quote: link.quote ?? undefined,
                    context: link.context ?? undefined,
                  })
              : undefined;
            return (
              <SourceCard
                key={link.id}
                link={link}
                titleLatin={titleLatin}
                onDelete={link.id ? () => detachCitation(link.id!) : undefined}
                onPrimaryAction={deepLink ? () => navigate(deepLink) : undefined}
                onTitleClick={openPanel}
                sourceId={source?.id ?? link.sourceId}
                sourceType={source?.sourceType}
              />
            );
          })}
        </div>
      )}

      {pickerOpen && (
        <CitationPicker
          targetType="questions"
          targetId={questionId}
          targetLabel={questionTitle}
          onClose={() => setPickerOpen(false)}
          onCreated={reload}
        />
      )}
    </section>
  );
}

export default QuestionCitationsSection;
