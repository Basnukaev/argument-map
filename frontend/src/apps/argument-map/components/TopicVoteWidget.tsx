import { useEffect, useState } from 'react';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { useT } from '@/shared/i18n/useT';
import { apiDeleteRaw, apiPostRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useAuthStore } from '@/shared/stores/authStore';
import type { components } from '@/shared/api/types';

type TopicVoteStats = components['schemas']['TopicVoteStatsResponse'];

export interface TopicVoteWidgetProps {
  topicId: string;
  /** Текущий счёт темы (TopicResponse.voteScore) */
  score: number;
  /** -1 / +1 / null. null - текущий user не голосовал */
  userVote: number | null;
  /** callback после успешного vote/remove - parent обновляет store/cache */
  onVoteChanged?: (stats: { score: number; userVote: number | null }) => void;
  /** stopPropagation на контейнере - нужно когда виджет внутри clickable-карточки */
  stopPropagation?: boolean;
  className?: string;
}

/**
 * Компактный виджет голосования за тему. Upvote/downvote с toggle:
 * клик по уже-активному голосу - снимает его (DELETE), клик по противоположному -
 * меняет (POST с другим weight). Optimistic UI с revert при error.
 *
 * Голосование перенесено с узлов графа на уровень темы: теперь голос отражает
 * отношение пользователя к теме целиком. В отличие от старого node-виджета
 * topic-ответы (TopicResponse) несут только агрегированный score + userVote
 * (не отдельные upvotes/downvotes), поэтому локальное состояние - score+userVote;
 * точный score после мутации берём из TopicVoteStatsResponse.
 *
 * Анонимный user видит цифры но клик показывает toast "Войдите чтобы голосовать"
 * (не block - просто signal что нужен login).
 */
function TopicVoteWidget({
  topicId,
  score,
  userVote,
  onVoteChanged,
  stopPropagation = false,
  className = '',
}: TopicVoteWidgetProps) {
  const t = useT();
  const user = useAuthStore((s) => s.user);
  const [pending, setPending] = useState(false);
  // Локальное состояние - оптимистичное обновление + база при ошибке
  const [local, setLocal] = useState({ score, userVote });

  // Синхронизируем local при изменении props (e.g. parent перезагрузил тему).
  // useEffect вместо render-phase setState - React 19 запрещает setState во
  // время рендера. pending check защищает от затирания optimistic update
  // в момент in-flight запроса. eslint-disable ниже намеренный: это derived
  // state sync (не side effect), аналог паттерна в useApiQuery.
  useEffect(() => {
    if (!pending) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLocal({ score, userVote });
    }
  }, [score, userVote, pending]);

  const handleVote = async (weight: 1 | -1) => {
    if (!user) {
      toast.info(t('vote.required_auth'));
      return;
    }
    if (pending) return;

    const wasVote = local.userVote;
    const newIsToggleOff = wasVote === weight; // тот же weight - снимаем

    // Оптимистичное обновление
    const optimistic = computeOptimisticTopic(local, weight, newIsToggleOff);
    setLocal(optimistic);
    setPending(true);

    try {
      if (newIsToggleOff) {
        await apiDeleteRaw(`/api/v1/topics/${topicId}/vote`);
        onVoteChanged?.(optimistic);
        toast.success(t('vote.removed'));
      } else {
        const stats = await apiPostRaw<TopicVoteStats>(`/api/v1/topics/${topicId}/vote`, {
          weight,
        });
        const next = {
          score: stats.score ?? 0,
          userVote: stats.userVote ?? null,
        };
        setLocal(next);
        onVoteChanged?.(next);
        toast.success(t('vote.success'));
      }
    } catch (e) {
      // Revert
      setLocal({ score, userVote });
      toast.error(formatApiError(e, t('vote.failed')));
    } finally {
      setPending(false);
    }
  };

  const upActive = local.userVote === 1;
  const downActive = local.userVote === -1;

  return (
    <div
      className={`inline-flex items-center gap-0.5 rounded border border-border bg-surface/60 px-1 py-0.5 text-xs ${className}`}
      aria-label={t('vote.topic.aria_widget')}
      onClick={stopPropagation ? (e) => e.stopPropagation() : undefined}
    >
      <button
        type="button"
        aria-label={t('vote.topic.upvote_tooltip')}
        title={t('vote.topic.upvote_tooltip')}
        aria-pressed={upActive}
        disabled={pending}
        onClick={(e) => {
          if (stopPropagation) e.stopPropagation();
          void handleVote(1);
        }}
        className={`flex h-5 w-5 items-center justify-center rounded transition-colors ${
          upActive
            ? 'text-emerald-600 bg-emerald-50'
            : 'text-ink-400 hover:text-emerald-600 hover:bg-emerald-50/60'
        } disabled:opacity-50 disabled:cursor-not-allowed`}
      >
        <ChevronUp size={14} aria-hidden="true" />
      </button>
      <span
        className={`min-w-[1.5em] text-center tabular-nums font-medium ${
          local.score > 0
            ? 'text-emerald-700'
            : local.score < 0
              ? 'text-rose-700'
              : 'text-ink-500'
        }`}
        title={t('vote.score_label').replace('{score}', String(local.score))}
      >
        {local.score}
      </span>
      <button
        type="button"
        aria-label={t('vote.topic.downvote_tooltip')}
        title={t('vote.topic.downvote_tooltip')}
        aria-pressed={downActive}
        disabled={pending}
        onClick={(e) => {
          if (stopPropagation) e.stopPropagation();
          void handleVote(-1);
        }}
        className={`flex h-5 w-5 items-center justify-center rounded transition-colors ${
          downActive
            ? 'text-rose-600 bg-rose-50'
            : 'text-ink-400 hover:text-rose-600 hover:bg-rose-50/60'
        } disabled:opacity-50 disabled:cursor-not-allowed`}
      >
        <ChevronDown size={14} aria-hidden="true" />
      </button>
    </div>
  );
}

/**
 * Чистая функция вычисления оптимистичного score-состояния. Не зависит от React -
 * легко покрывается unit-тестами без рендера. score меняется на дельту голоса:
 * снятие старого (+/-1) и применение нового (+/-1).
 */
function computeOptimisticTopic(
  current: { score: number; userVote: number | null },
  weight: 1 | -1,
  isToggleOff: boolean,
): { score: number; userVote: number | null } {
  let score = current.score;
  const next: number | null = isToggleOff ? null : weight;

  // снять старый голос (если был)
  if (current.userVote === 1) score -= 1;
  if (current.userVote === -1) score += 1;

  // добавить новый (если не toggle-off)
  if (!isToggleOff) {
    score += weight;
  }

  return { score, userVote: next };
}

export default TopicVoteWidget;
// computeOptimisticTopic экспортируется ради unit-тестирования pure logic без
// рендера компонента. Нужна только TopicVoteWidget'у - выносить в отдельный
// файл не оправдано.
// eslint-disable-next-line react-refresh/only-export-components
export { computeOptimisticTopic };
