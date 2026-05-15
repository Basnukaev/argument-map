import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ArrowLeft, Trash2, AlertCircle, Loader2 } from 'lucide-react';
import Button from '@/shared/components/ui/Button';
import Card from '@/shared/components/ui/Card';
import Header from '@/shared/components/layout/Header';
import {
  apiDeleteRaw,
  apiGetRaw,
  apiPatchRaw,
  ApiError,
} from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT, useFormatDate, hasArabicScript } from '@/shared/i18n';
import type { AsyncState } from '@/shared/types/async';
import type { components } from '@/shared/api/types';

type Question = components['schemas']['QuestionResponse'];
type Status = NonNullable<Question['status']>;

const STATUS_BADGE: Record<Status, string> = {
  OPEN: 'bg-ok-100 text-ok-700',
  ANSWERED: 'bg-accent-100 text-accent-700',
  CLOSED: 'bg-ink-100 text-ink-600',
};

function QuestionDetailPage() {
  const t = useT();
  const navigate = useNavigate();
  const { questionId } = useParams<{ questionId: string }>();
  const [state, setState] = useState<AsyncState<Question>>({ kind: 'loading' });
  const [updating, setUpdating] = useState(false);

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

  const handleStatusChange = async (status: Status) => {
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
    if (!window.confirm(t('qa.detail.delete_confirm'))) return;
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
      <div className="mx-auto max-w-3xl px-6 py-6">
        <div className="mb-6">
          <Link
            to="/qa"
            className="inline-flex items-center gap-1 text-xs text-ink-500 hover:text-ink-700"
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

        {state.kind === 'success' && <Detail
            question={state.data}
            updating={updating}
            onStatusChange={handleStatusChange}
            onDelete={handleDelete}
          />}
      </div>
    </main>
  );
}

interface DetailProps {
  question: Question;
  updating: boolean;
  onStatusChange: (s: Status) => void;
  onDelete: () => void;
}

function Detail({ question, updating, onStatusChange, onDelete }: DetailProps) {
  const t = useT();
  const formatDate = useFormatDate();
  const status = question.status ?? 'OPEN';
  const isTitleArabic = question.title ? hasArabicScript(question.title) : false;
  const isBodyArabic = question.body ? hasArabicScript(question.body) : false;

  return (
    <article>
      <div className="mb-3 flex items-center gap-2">
        <span
          className={`inline-flex items-center rounded-sm px-1.5 py-0.5 text-xs font-semibold uppercase tracking-wider ${STATUS_BADGE[status]}`}
        >
          {t(`qa.status.${status}` as never)}
        </span>
        <span className="text-xs text-ink-400">
          <bdi dir="ltr">
            {question.createdAt ? formatDate(question.createdAt, 'short') : ''}
          </bdi>
        </span>
      </div>

      <h1
        className={`mb-3 text-2xl font-bold tracking-tight text-ink-900 ${isTitleArabic ? 'font-arabic' : ''}`}
        dir="auto"
      >
        {question.title}
      </h1>

      {question.body && (
        <Card className="mb-4 p-4">
          <p
            className={`whitespace-pre-wrap text-sm leading-relaxed text-ink-800 ${isBodyArabic ? 'font-arabic' : ''}`}
            dir="auto"
          >
            {question.body}
          </p>
        </Card>
      )}

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <span className="text-xs text-ink-500">{t('qa.detail.change_status')}:</span>
        {(['OPEN', 'ANSWERED', 'CLOSED'] as const).map((s) => (
          <button
            key={s}
            type="button"
            disabled={updating || status === s}
            onClick={() => onStatusChange(s)}
            className={`inline-flex h-7 items-center rounded-sm px-3 text-xs font-semibold uppercase tracking-wider transition-colors disabled:opacity-50 ${
              status === s
                ? STATUS_BADGE[s]
                : 'text-ink-600 hover:bg-ink-100 hover:text-ink-900'
            }`}
          >
            {t(`qa.status.${s}` as never)}
          </button>
        ))}
      </div>

      <div className="flex justify-end border-t border-ink-100 pt-4">
        <Button
          variant="ghost"
          icon={Trash2}
          onClick={onDelete}
          className="text-err-700 hover:bg-err-100"
        >
          {t('qa.detail.delete')}
        </Button>
      </div>
    </article>
  );
}

export default QuestionDetailPage;
