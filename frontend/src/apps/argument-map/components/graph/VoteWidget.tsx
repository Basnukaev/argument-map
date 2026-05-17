import { useState } from 'react';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { useT } from '@/shared/i18n/useT';
import { apiDeleteRaw, apiPostRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useAuthStore } from '@/shared/stores/authStore';
import type { components } from '@/shared/api/types';

type VoteStatsDto = components['schemas']['NodeVoteStatsResponse'];

export interface VoteWidgetProps {
  nodeId: string;
  upvotes: number;
  downvotes: number;
  score: number;
  /** -1 / +1 / null. null - текущий user не голосовал */
  userVote: number | null;
  /** callback после успешного vote/remove - parent обновляет store/cache */
  onVoteChanged?: (stats: { upvotes: number; downvotes: number; score: number; userVote: number | null }) => void;
}

/**
 * Компактный виджет голосования за вес узла. Upvote/downvote с toggle:
 * клик по уже-активному голосу - снимает его (DELETE), клик по противоположному -
 * меняет (POST с другим weight). Optimistic UI с revert при error.
 *
 * Анонимный user видит цифры но клик показывает toast "Войдите чтобы голосовать"
 * (не editor block - просто signal что нужен login).
 */
function VoteWidget({
  nodeId,
  upvotes,
  downvotes,
  score,
  userVote,
  onVoteChanged,
}: VoteWidgetProps) {
  const t = useT();
  const user = useAuthStore((s) => s.user);
  const [pending, setPending] = useState(false);
  // Локальное состояние - оптимистичное обновление + база при ошибке
  const [local, setLocal] = useState({ upvotes, downvotes, score, userVote });

  // Синхронизируем local при изменении props (e.g. parent перезагрузил граф)
  if (
    local.upvotes !== upvotes ||
    local.downvotes !== downvotes ||
    local.userVote !== userVote
  ) {
    // условный update только когда props свежее - избегаем зацикливания
    if (!pending) {
      setLocal({ upvotes, downvotes, score, userVote });
    }
  }

  const handleVote = async (weight: 1 | -1) => {
    if (!user) {
      toast.info(t('vote.required_auth'));
      return;
    }
    if (pending) return;

    const wasVote = local.userVote;
    const newIsToggleOff = wasVote === weight; // тот же weight - снимаем

    // Оптимистичное обновление
    const optimistic = computeOptimistic(local, weight, newIsToggleOff);
    setLocal(optimistic);
    setPending(true);

    try {
      if (newIsToggleOff) {
        await apiDeleteRaw(`/api/v1/nodes/${nodeId}/vote`);
        onVoteChanged?.(optimistic);
        toast.success(t('vote.removed'));
      } else {
        const stats = await apiPostRaw<VoteStatsDto>(`/api/v1/nodes/${nodeId}/vote`, {
          weight,
        });
        const next = {
          upvotes: stats.upvotes ?? 0,
          downvotes: stats.downvotes ?? 0,
          score: stats.score ?? 0,
          userVote: stats.userVote ?? null,
        };
        setLocal(next);
        onVoteChanged?.(next);
        toast.success(t('vote.success'));
      }
    } catch (e) {
      // Revert
      setLocal({ upvotes, downvotes, score, userVote });
      toast.error(formatApiError(e, t('vote.failed')));
    } finally {
      setPending(false);
    }
  };

  const upActive = local.userVote === 1;
  const downActive = local.userVote === -1;

  return (
    <div
      className="inline-flex items-center gap-0.5 rounded border border-border bg-surface/60 px-1 py-0.5 text-xs"
      aria-label={t('vote.aria_widget')}
      onClick={(e) => e.stopPropagation()}
    >
      <button
        type="button"
        tabIndex={-1}
        aria-label={t('vote.upvote_tooltip')}
        title={t('vote.upvote_tooltip')}
        aria-pressed={upActive}
        disabled={pending}
        onClick={() => handleVote(1)}
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
        tabIndex={-1}
        aria-label={t('vote.downvote_tooltip')}
        title={t('vote.downvote_tooltip')}
        aria-pressed={downActive}
        disabled={pending}
        onClick={() => handleVote(-1)}
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
 * Чистая функция вычисления оптимистичного состояния. Не зависит от React -
 * легко покрывается unit-тестами без рендера.
 */
function computeOptimistic(
  current: { upvotes: number; downvotes: number; userVote: number | null },
  weight: 1 | -1,
  isToggleOff: boolean,
): { upvotes: number; downvotes: number; score: number; userVote: number | null } {
  let up = current.upvotes;
  let down = current.downvotes;
  const next: number | null = isToggleOff ? null : weight;

  // снять старый голос (если был)
  if (current.userVote === 1) up -= 1;
  if (current.userVote === -1) down -= 1;

  // добавить новый (если не toggle-off)
  if (!isToggleOff) {
    if (weight === 1) up += 1;
    if (weight === -1) down += 1;
  }

  return { upvotes: up, downvotes: down, score: up - down, userVote: next };
}

export default VoteWidget;
export { computeOptimistic };
