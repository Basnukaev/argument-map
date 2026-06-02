import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useViewTracking } from '@/shared/hooks/useViewTracking';
import { ArrowLeft, Trash2, AlertCircle, Loader2, CalendarClock, History } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import OverflowMenu from '@/shared/components/ui/OverflowMenu';
import type { ContextMenuItem } from '@/shared/components/ui/ContextMenu';
import QuestionStatusBadge from '@/apps/qa/components/QuestionStatusBadge';
import VoteWidget from '@/shared/components/ui/VoteWidget';
import QuestionCitationsSection from '@/apps/qa/components/QuestionCitationsSection';
import AnswersSection from '@/apps/qa/components/AnswersSection';
import {
  QUESTION_STATUS_ORDER,
  QUESTION_STATUS_TOKENS,
  type QuestionStatus,
} from '@/apps/qa/utils/statusTokens';
import {
  apiDeleteRaw,
  apiGetRaw,
  apiPatchRaw,
  ApiError,
} from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { askConfirm } from '@/shared/stores/confirmStore';
import { useT, useFormatDate, hasArabicScript } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

type Question = components['schemas']['QuestionResponse'];

const DEV_USER_ID = import.meta.env.VITE_DEV_USER_ID ?? '';

function QuestionDetailPage() {
  const t = useT();
  const navigate = useNavigate();
  const { questionId } = useParams<{ questionId: string }>();
  const [state, setState] = useState<AsyncState<Question>>({ kind: 'loading' });
  const [updating, setUpdating] = useState(false);

  // Vision 49d Phase 2 — track view для popularity ranking
  useViewTracking('questions', questionId ?? null);

  useEffect(() => {
    if (!questionId) return;
    const controller = new AbortController();
    apiGetRaw<Question>(`/api/v1/questions/${questionId}`, {
      signal: controller.signal,
    })
      .then((q) => setState({ kind: 'success', data: q }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        const message =
          e instanceof ApiError
            ? `${e.problem.title}${e.problem.detail ? ': ' + e.problem.detail : ''}`
            : e instanceof Error
              ? e.message
              : 'failed';
        setState({ kind: 'error', message });
      });
    return () => controller.abort();
  }, [questionId]);

  const refetchQuestion = async () => {
    if (!questionId) return;
    try {
      const fresh = await apiGetRaw<Question>(`/api/v1/questions/${questionId}`);
      setState({ kind: 'success', data: fresh });
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      toast.error(message);
    }
  };

  const handleStatusChange = async (status: QuestionStatus) => {
    if (!questionId) return;
    setUpdating(true);
    try {
      const updated = await apiPatchRaw<Question>(
        `/api/v1/questions/${questionId}`,
        { status },
      );
      setState({ kind: 'success', data: updated });
      toast.success(t('qa.detail.status_changed'));
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      toast.error(message);
    } finally {
      setUpdating(false);
    }
  };

  const handleDelete = async () => {
    if (!questionId) return;
    if (!(await askConfirm({ message: t('qa.detail.delete_confirm'), danger: true }))) return;
    try {
      await apiDeleteRaw(`/api/v1/questions/${questionId}`);
      toast.success(t('qa.detail.deleted'));
      navigate('/qa');
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      toast.error(message);
    }
  };

  return (
    <main className="min-h-screen bg-bg">
      <Header />
      {/* Читаемая центрированная колонка - длинный текст не на весь экран */}
      <div className="mx-auto max-w-3xl px-4 py-6 sm:px-6 sm:py-8">
        <div className="mb-5">
          <Link
            to="/qa"
            className="inline-flex items-center gap-1 text-xs text-ink-500 transition-colors hover:text-ink-700"
          >
            <ArrowLeft size={14} aria-hidden />
            {t('qa.detail.back')}
          </Link>
        </div>

        {state.kind === 'loading' && (
          <div className="flex items-center gap-2 text-sm text-ink-500">
            <Loader2 size={16} className="animate-spin" aria-hidden />
            {t('common.loading')}
          </div>
        )}

        {state.kind === 'error' && (
          <Card className="border-err-500/40 bg-err-100 p-5">
            <div className="flex items-start gap-3">
              <AlertCircle size={18} className="mt-0.5 text-err-700" aria-hidden />
              <div>
                <p className="text-sm font-semibold text-err-700">
                  {t('common.error')}
                </p>
                <p className="mt-1 text-xs text-err-700">{state.message}</p>
              </div>
            </div>
          </Card>
        )}

        {state.kind === 'success' && (
          <Detail
            question={state.data}
            updating={updating}
            onStatusChange={handleStatusChange}
            onDelete={handleDelete}
            onRefetchQuestion={refetchQuestion}
          />
        )}
      </div>
    </main>
  );
}

interface DetailProps {
  question: Question;
  updating: boolean;
  onStatusChange: (s: QuestionStatus) => void;
  onDelete: () => void;
  onRefetchQuestion: () => void;
}

function Detail({ question, updating, onStatusChange, onDelete, onRefetchQuestion }: DetailProps) {
  const t = useT();
  const formatDate = useFormatDate();
  const status = question.status ?? 'OPEN';
  const isTitleArabic = question.title ? hasArabicScript(question.title) : false;
  const isBodyArabic = question.body ? hasArabicScript(question.body) : false;

  // Владелец вопроса (asker) видит overflow-меню: смена статуса + удаление.
  // Сравнение по VITE_DEV_USER_ID - до Spring Security (как в AnswersSection).
  const isAsker = Boolean(DEV_USER_ID && question.askedBy && DEV_USER_ID === question.askedBy);

  const hasActivity =
    question.updatedAt &&
    question.createdAt &&
    question.updatedAt !== question.createdAt;

  // Пункты overflow-меню: переключение статуса (кроме текущего) + удаление.
  const ownerMenuItems: ContextMenuItem[] = isAsker
    ? [
        ...QUESTION_STATUS_ORDER.filter((s) => s !== status).map((s) => ({
          id: `status-${s}`,
          label: t('qa.detail.set_status_to').replace(
            '{status}',
            t(QUESTION_STATUS_TOKENS[s].labelKey),
          ),
          icon: QUESTION_STATUS_TOKENS[s].Icon,
          disabled: updating,
          onClick: () => onStatusChange(s),
        })),
        { id: 'sep', separator: true, label: '' },
        {
          id: 'delete',
          label: t('qa.detail.delete'),
          icon: Trash2,
          danger: true,
          onClick: onDelete,
        },
      ]
    : [];

  return (
    <article>
      {/* Шапка вопроса: eyebrow + статус-бейдж + overflow-меню владельца */}
      <header className="mb-5">
        <div className="mb-2 flex items-start justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-500">
              {t('qa.detail.eyebrow')}
            </span>
            <QuestionStatusBadge status={status} size="md" />
          </div>
          {isAsker && (
            <OverflowMenu items={ownerMenuItems} label={t('qa.detail.actions')} size="sm" />
          )}
        </div>

        {/* font-serif для latin, font-arabic для arabic - не миксовать
            editorial latin serif с арабской вязью (плохо смотрится) */}
        <h1
          className={`text-[26px] font-semibold leading-tight tracking-tight text-ink-900 sm:text-[30px] ${isTitleArabic ? 'font-arabic' : 'font-serif'}`}
          dir="auto"
        >
          {question.title}
        </h1>

        {/* Meta-строка: дата постановки + последняя активность + голосование.
            VoteWidget прижат к end (ms-auto). onVoteChanged рефетчит вопрос -
            подтягивает свежие voteScore/userVote (и любые серверные изменения). */}
        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-500">
          {question.createdAt && (
            <span className="inline-flex items-center gap-1">
              <CalendarClock size={13} aria-hidden className="text-ink-400" />
              {t('qa.detail.asked_prefix')}{' '}
              <bdi dir="ltr">{formatDate(question.createdAt, 'short')}</bdi>
            </span>
          )}
          {hasActivity && (
            <span className="inline-flex items-center gap-1">
              <History size={13} aria-hidden className="text-ink-400" />
              {t('qa.detail.updated_prefix')}{' '}
              <bdi dir="ltr">{formatDate(question.updatedAt, 'short')}</bdi>
            </span>
          )}
          {question.id && (
            <div className="ms-auto">
              <VoteWidget
                voteUrl={`/api/v1/questions/${question.id}/vote`}
                score={question.voteScore ?? 0}
                userVote={question.userVote ?? null}
                onVoteChanged={onRefetchQuestion}
                ariaLabel={t('vote.question.aria_widget')}
                upvoteLabel={t('vote.question.upvote_tooltip')}
                downvoteLabel={t('vote.question.downvote_tooltip')}
              />
            </div>
          )}
        </div>
      </header>

      {question.body && (
        <Card className="mb-7 p-5">
          <p
            className={`whitespace-pre-wrap text-[15px] leading-relaxed text-ink-800 ${isBodyArabic ? 'font-arabic' : ''}`}
            dir="auto"
          >
            {question.body}
          </p>
        </Card>
      )}

      {question.id && (
        <QuestionCitationsSection
          questionId={question.id}
          questionTitle={question.title ?? ''}
        />
      )}

      {question.id && (
        <AnswersSection
          questionId={question.id}
          askedBy={question.askedBy}
          acceptedAnswerId={question.acceptedAnswerId}
          onAcceptanceChange={onRefetchQuestion}
        />
      )}
    </article>
  );
}

export default QuestionDetailPage;
