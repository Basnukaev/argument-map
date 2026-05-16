import { useEffect, useState } from 'react';
import {
  MessageSquare,
  Loader2,
  AlertCircle,
  CheckCircle,
  ChevronDown,
  ChevronUp,
  Trash2,
} from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Field from '@/shared/components/ui/Field';
import {
  apiDeleteRaw,
  apiGetRaw,
  apiPostRaw,
  formatApiError,
} from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { hasArabicScript, useFormatDate, useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';
import AnswerCitationsSection from './AnswerCitationsSection';

type AnswerDto = components['schemas']['AnswerResponse'];

const BODY_MAX = 10000;

const DEV_USER_ID = import.meta.env.VITE_DEV_USER_ID ?? '';

interface Props {
  questionId: string;
  askedBy: string | undefined;
  acceptedAnswerId: string | undefined;
  onAcceptanceChange: () => void;
}

type State =
  | { kind: 'loading' }
  | { kind: 'success'; answers: AnswerDto[] }
  | { kind: 'error'; message: string };

/**
 * Секция «Ответы» на странице вопроса (Этап 19.c, ADR-034). Загружает
 * список ответов через {@code GET /api/v1/questions/{id}/answers},
 * показывает inline-форму для нового ответа.
 *
 * <p>Принятый ответ отмечается зелёным ribbon (chip). Только asker'у видна
 * кнопка «Принять/Отозвать». Только author'у ответа видна кнопка «Удалить».
 * Сравнение по {@code VITE_DEV_USER_ID} - до Spring Security в Этапе 6.
 */
function AnswersSection({ questionId, askedBy, acceptedAnswerId, onAcceptanceChange }: Props) {
  const t = useT();
  const formatDate = useFormatDate();
  const [state, setState] = useState<State>({ kind: 'loading' });
  const [bodyInput, setBodyInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [busyAnswerId, setBusyAnswerId] = useState<string | null>(null);

  const isAsker = DEV_USER_ID && askedBy && DEV_USER_ID === askedBy;

  useEffect(() => {
    const controller = new AbortController();
    apiGetRaw<AnswerDto[]>(`/api/v1/questions/${questionId}/answers`, {
      signal: controller.signal,
    })
      .then((answers) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'success', answers });
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ kind: 'error', message: formatApiError(e, t('common.error')) });
      });
    return () => controller.abort();
    // acceptedAnswerId как dep - чтобы при принятии refetch'a (для derived `accepted`)
  }, [questionId, acceptedAnswerId, t]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = bodyInput.trim();
    if (!trimmed || submitting) return;
    setSubmitting(true);
    try {
      await apiPostRaw<AnswerDto>(
        `/api/v1/questions/${questionId}/answers`,
        { body: trimmed },
      );
      setBodyInput('');
      // Refetch чтобы новый ответ появился в списке с правильным порядком
      const fresh = await apiGetRaw<AnswerDto[]>(
        `/api/v1/questions/${questionId}/answers`,
      );
      setState({ kind: 'success', answers: fresh });
      toast.success(t('qa.answers.created'));
    } catch (e) {
      toast.error(formatApiError(e, t('common.error')));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleAccept(answerId: string) {
    setBusyAnswerId(answerId);
    try {
      await apiPostRaw(
        `/api/v1/questions/${questionId}/accepted-answer/${answerId}`,
        {},
      );
      toast.success(t('qa.answers.accepted'));
      onAcceptanceChange();
    } catch (e) {
      toast.error(formatApiError(e, t('common.error')));
    } finally {
      setBusyAnswerId(null);
    }
  }

  async function handleRevoke() {
    setBusyAnswerId(acceptedAnswerId ?? null);
    try {
      await apiDeleteRaw(`/api/v1/questions/${questionId}/accepted-answer`);
      toast.success(t('qa.answers.revoked'));
      onAcceptanceChange();
    } catch (e) {
      toast.error(formatApiError(e, t('common.error')));
    } finally {
      setBusyAnswerId(null);
    }
  }

  async function handleDelete(answerId: string) {
    if (!window.confirm(t('qa.answers.delete_confirm'))) return;
    setBusyAnswerId(answerId);
    try {
      await apiDeleteRaw(`/api/v1/answers/${answerId}`);
      setState((prev) => {
        if (prev.kind !== 'success') return prev;
        return { kind: 'success', answers: prev.answers.filter((a) => a.id !== answerId) };
      });
      // Если удалённый был accepted - parent тоже надо обновить (status может поменяться)
      if (answerId === acceptedAnswerId) {
        onAcceptanceChange();
      }
    } catch (e) {
      toast.error(formatApiError(e, t('common.error')));
    } finally {
      setBusyAnswerId(null);
    }
  }

  return (
    <section className="mt-6">
      <div className="mb-3 flex items-center">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-ink-500">
          {t('qa.answers.section_title')}
          {state.kind === 'success' && state.answers.length > 0 && (
            <span className="ms-2 font-mono text-xs text-ink-400">
              <bdi dir="ltr">{state.answers.length}</bdi>
            </span>
          )}
        </h2>
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

      {state.kind === 'success' && state.answers.length === 0 && (
        <p className="text-sm italic text-ink-400">{t('qa.answers.empty')}</p>
      )}

      {state.kind === 'success' && state.answers.length > 0 && (
        <div className="space-y-3">
          {state.answers.map((a) => (
            <AnswerCard
              key={a.id}
              answer={a}
              isAsker={Boolean(isAsker)}
              isAuthor={Boolean(DEV_USER_ID && a.authorId === DEV_USER_ID)}
              busy={busyAnswerId === a.id}
              onAccept={() => a.id && handleAccept(a.id)}
              onRevoke={handleRevoke}
              onDelete={() => a.id && handleDelete(a.id)}
              formatDate={formatDate}
            />
          ))}
        </div>
      )}

      <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-2">
        <Field
          label={t('qa.answers.add_button')}
          hint={t('qa.answers.placeholder')}
        >
          <Field.Textarea
            value={bodyInput}
            onChange={(e) => setBodyInput(e.target.value)}
            maxLength={BODY_MAX}
            rows={4}
            dir="auto"
            placeholder={t('qa.answers.placeholder')}
          />
          <Field.Meta left={`${bodyInput.length} / ${BODY_MAX}`} />
        </Field>
        <div className="flex justify-end">
          <Button
            type="submit"
            variant="primary"
            disabled={!bodyInput.trim() || submitting}
            icon={MessageSquare}
          >
            {submitting ? t('common.saving') : t('qa.answers.add_button')}
          </Button>
        </div>
      </form>
    </section>
  );
}

interface CardProps {
  answer: AnswerDto;
  isAsker: boolean;
  isAuthor: boolean;
  busy: boolean;
  onAccept: () => void;
  onRevoke: () => void;
  onDelete: () => void;
  formatDate: (iso: string | undefined, style?: 'full' | 'short') => string;
}

function AnswerCard({
  answer,
  isAsker,
  isAuthor,
  busy,
  onAccept,
  onRevoke,
  onDelete,
  formatDate,
}: CardProps) {
  const t = useT();
  const isBodyArabic = answer.body ? hasArabicScript(answer.body) : false;
  const accepted = answer.accepted === true;
  const [sourcesOpen, setSourcesOpen] = useState(false);

  // body preview для CitationPicker header label (первые 80 символов)
  const bodyPreview = (() => {
    const body = answer.body ?? '';
    return body.length > 80 ? body.substring(0, 77) + '...' : body;
  })();

  return (
    <Card
      className={`p-4 ${accepted ? 'border-ok-500/40 bg-ok-50' : ''}`.trim()}
    >
      {accepted && (
        <div className="mb-2 inline-flex items-center gap-1 rounded-sm bg-ok-100 px-2 py-0.5 text-xs font-semibold uppercase tracking-wider text-ok-700">
          <CheckCircle size={12} aria-hidden />
          {t('qa.answers.accepted_label')}
        </div>
      )}

      <p
        className={`whitespace-pre-wrap text-sm leading-relaxed text-ink-800 ${isBodyArabic ? 'font-arabic' : ''}`}
        dir="auto"
      >
        {answer.body}
      </p>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-ink-100 pt-2">
        <span className="text-xs text-ink-400">
          <bdi dir="ltr">
            {answer.createdAt ? formatDate(answer.createdAt, 'short') : ''}
          </bdi>
        </span>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            variant="ghost"
            icon={sourcesOpen ? ChevronUp : ChevronDown}
            onClick={() => setSourcesOpen((v) => !v)}
          >
            {sourcesOpen
              ? t('qa.answers.sources_hide')
              : t('qa.answers.sources_show')}
          </Button>
          {isAsker && !accepted && (
            <Button
              type="button"
              size="sm"
              variant="ghost"
              icon={CheckCircle}
              onClick={onAccept}
              disabled={busy}
            >
              {t('qa.answers.accept_button')}
            </Button>
          )}
          {isAsker && accepted && (
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={onRevoke}
              disabled={busy}
            >
              {t('qa.answers.revoke_button')}
            </Button>
          )}
          {isAuthor && (
            <Button
              type="button"
              size="sm"
              variant="ghost"
              icon={Trash2}
              onClick={onDelete}
              disabled={busy}
              className="text-err-700 hover:bg-err-100"
            >
              {t('qa.answers.delete_button')}
            </Button>
          )}
        </div>
      </div>

      {sourcesOpen && answer.id && (
        <AnswerCitationsSection
          answerId={answer.id}
          answerBodyPreview={bodyPreview}
        />
      )}
    </Card>
  );
}

export default AnswersSection;
