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

type AnswerSourceDto = components['schemas']['AnswerSourceResponse'];
type SourceDto = components['schemas']['SourceResponse'];

interface Props {
  answerId: string;
  answerBodyPreview: string;
}

type State =
  | { kind: 'loading' }
  | { kind: 'success'; links: AnswerSourceDto[]; sourceLookup: Map<string, SourceDto> }
  | { kind: 'error'; message: string };

/**
 * Секция «Источники» для отдельного ответа (Этап 19.d). Зеркало
 * {@link QuestionCitationsSection} с подменой questionId → answerId.
 *
 * <p>3-я итерация ADR-033 параллельной иерархии. Используется общий
 * {@code CitationPicker} (через {@code targetType='answers'}) + общий
 * {@code SourceCard} без копирования. Backend reuse - тот же Source/Book/Page
 * stack, новый endpoint {@code /api/v1/answers/{id}/citations}.
 *
 * <p>Размещается внутри AnswerCard collapsed by default - на странице может
 * быть много ответов, разворачивать список citations всех сразу - визуальный
 * перегруз.
 */
function AnswerCitationsSection({ answerId, answerBodyPreview }: Props) {
  const t = useT();
  const navigate = useNavigate();
  const openSourceDetail = useSourceDetailPanelStore((s) => s.openWith);
  const [state, setState] = useState<State>({ kind: 'loading' });
  const [pickerOpen, setPickerOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      apiGetRaw<AnswerSourceDto[]>(`/api/v1/answers/${answerId}/sources`, {
        signal: controller.signal,
      }),
      apiGetRaw<SourceDto[]>('/api/v1/sources', { signal: controller.signal }),
    ])
      .then(([links, sources]) => {
        if (controller.signal.aborted) return;
        const sourceLookup = new Map<string, SourceDto>();
        for (const s of sources ?? []) {
          if (s.id) sourceLookup.set(s.id, s);
        }
        setState({ kind: 'success', links, sourceLookup });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'error', message: formatApiError(e, t('common.error')) });
      });
    return () => controller.abort();
  }, [answerId, t]);

  async function reload() {
    try {
      const links = await apiGetRaw<AnswerSourceDto[]>(`/api/v1/answers/${answerId}/sources`);
      setState((prev) => {
        if (prev.kind !== 'success') return prev;
        return { ...prev, links };
      });
    } catch (e) {
      toast.error(formatApiError(e, t('common.error')));
    }
  }

  async function detachCitation(answerSourceId: string) {
    if (state.kind !== 'success') return;
    const previous = state.links;
    setState({ ...state, links: previous.filter((l) => l.id !== answerSourceId) });
    try {
      await apiDeleteRaw(`/api/v1/answers/${answerId}/sources/${answerSourceId}`);
      toast.success(t('qa.sources.detached'));
    } catch (e) {
      toast.error(formatApiError(e, t('qa.sources.detach_failed')));
      setState({ ...state, links: previous });
    }
  }

  function buildDeepLink(link: AnswerSourceDto): string | null {
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
    <div className="mt-3 border-t border-ink-100 pt-3">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-500">
          {t('qa.sources.section_title')}
          {state.kind === 'success' && state.links.length > 0 && (
            <span className="ms-2 font-mono text-xs text-ink-400">
              <bdi dir="ltr">{state.links.length}</bdi>
            </span>
          )}
        </h3>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          icon={BookOpen}
          onClick={() => setPickerOpen(true)}
        >
          {t('qa.answers.sources_attach')}
        </Button>
      </div>

      {state.kind === 'loading' && (
        <div className="flex items-center gap-2 text-xs text-ink-500">
          <Loader2 size={14} className="animate-spin" aria-hidden />
          {t('common.loading')}
        </div>
      )}

      {state.kind === 'error' && (
        <Card className="border-err-500/40 bg-err-100 p-3">
          <div className="flex items-start gap-2">
            <AlertCircle size={14} className="mt-0.5 text-err-700" aria-hidden />
            <p className="text-xs text-err-700">{state.message}</p>
          </div>
        </Card>
      )}

      {state.kind === 'success' && state.links.length === 0 && (
        <p className="text-xs italic text-ink-400">{t('qa.sources.empty')}</p>
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
              />
            );
          })}
        </div>
      )}

      {pickerOpen && (
        <CitationPicker
          targetType="answers"
          targetId={answerId}
          targetLabel={answerBodyPreview}
          onClose={() => setPickerOpen(false)}
          onCreated={reload}
        />
      )}
    </div>
  );
}

export default AnswerCitationsSection;
