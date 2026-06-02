import { useEffect, useRef, useState } from 'react';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { useT } from '@/shared/i18n';
import { apiDeleteRaw, apiPostRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useAuthStore } from '@/shared/stores/authStore';

interface VoteStatsLike {
  score?: number;
  userVote?: number;
}

export interface VoteWidgetProps {
  /**
   * Базовый URL голосования, например `/api/v1/topics/{id}/vote` или
   * `/api/v1/questions/{id}/vote`. POST {weight} ставит/меняет голос,
   * DELETE снимает. Виджету не нужен GET - score/userVote приходят props'ами.
   */
  voteUrl: string;
  /** Текущий агрегированный счёт сущности */
  score: number;
  /** -1 / +1 / null. null - текущий user не голосовал */
  userVote: number | null;
  /** callback после успешного vote/remove - parent обновляет store/cache */
  onVoteChanged?: (stats: { score: number; userVote: number | null }) => void;
  /** stopPropagation на контейнере - нужно когда виджет внутри clickable-карточки */
  stopPropagation?: boolean;
  /** aria-label контейнера (по умолчанию generic «Виджет голосования») */
  ariaLabel?: string;
  /** aria-label/title кнопки upvote (по умолчанию generic «Проголосовать за») */
  upvoteLabel?: string;
  /** aria-label/title кнопки downvote (по умолчанию generic «Проголосовать против») */
  downvoteLabel?: string;
  className?: string;
}

/**
 * Entity-agnostic компактный виджет голосования. Upvote/downvote с toggle:
 * клик по уже-активному голосу - снимает его (DELETE), клик по противоположному -
 * меняет (POST с другим weight). Optimistic UI с revert при error.
 *
 * Сущность задаётся через `voteUrl` (базовый URL без `s`-суффикса для GET -
 * виджет делает только POST/DELETE). Используется для тем и вопросов;
 * локальное состояние - только score+userVote (агрегаты), точный score после
 * мутации берётся из *VoteStatsResponse.
 *
 * Анонимный user видит цифры, но клик показывает toast «Войдите чтобы
 * голосовать» (не block - просто signal что нужен login).
 */
function VoteWidget({
  voteUrl,
  score,
  userVote,
  onVoteChanged,
  stopPropagation = false,
  ariaLabel,
  upvoteLabel,
  downvoteLabel,
  className = '',
}: VoteWidgetProps) {
  const t = useT();
  const user = useAuthStore((s) => s.user);
  const [pending, setPending] = useState(false);
  // Локальное состояние - оптимистичное обновление + база при ошибке.
  // `local` - единственный источник истины для отображения.
  const [local, setLocal] = useState({ score, userVote });

  // Синхронизируем props → local ТОЛЬКО когда входящие props реально
  // изменились (parent перезагрузил сущность), а не просто потому что pending
  // переключился. Иначе после успешного голоса pending→false перезатёр бы
  // оптимистичный local устаревшими props (списки рендерят виджет без
  // onVoteChanged → props не обновляются → счёт «отскакивал» назад).
  // prevPropsRef помнит последние увиденные props; setLocal только при
  // отличии от них. useEffect (не render-phase setState) - React 19 запрет.
  const prevPropsRef = useRef({ score, userVote });
  useEffect(() => {
    if (prevPropsRef.current.score !== score || prevPropsRef.current.userVote !== userVote) {
      prevPropsRef.current = { score, userVote };
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLocal({ score, userVote });
    }
  }, [score, userVote]);

  const handleVote = async (weight: 1 | -1) => {
    if (!user) {
      toast.info(t('vote.required_auth'));
      return;
    }
    if (pending) return;

    const wasVote = local.userVote;
    const newIsToggleOff = wasVote === weight; // тот же weight - снимаем

    // Оптимистичное обновление
    const optimistic = computeOptimisticVote(local, weight, newIsToggleOff);
    setLocal(optimistic);
    setPending(true);

    try {
      if (newIsToggleOff) {
        await apiDeleteRaw(voteUrl);
        onVoteChanged?.(optimistic);
        toast.success(t('vote.removed'));
      } else {
        const stats = await apiPostRaw<VoteStatsLike>(voteUrl, { weight });
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
  const upLabel = upvoteLabel ?? t('vote.upvote_action');
  const downLabel = downvoteLabel ?? t('vote.downvote_action');

  return (
    <div
      className={`inline-flex items-center gap-0.5 rounded border border-border bg-surface/60 px-1 py-0.5 text-xs ${className}`}
      aria-label={ariaLabel ?? t('vote.aria_widget')}
      onClick={stopPropagation ? (e) => e.stopPropagation() : undefined}
    >
      <button
        type="button"
        aria-label={upLabel}
        title={upLabel}
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
        aria-label={downLabel}
        title={downLabel}
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
function computeOptimisticVote(
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

export default VoteWidget;
// computeOptimisticVote экспортируется ради unit-тестирования pure logic без
// рендера компонента. Нужна только VoteWidget'у - выносить в отдельный файл
// не оправдано.
// eslint-disable-next-line react-refresh/only-export-components
export { computeOptimisticVote };
