import { useEffect, useRef, useState } from 'react';
import { MousePointer2, Trash2, CheckCircle2, ChevronUp, X, Check } from 'lucide-react';
import { useT } from '@/shared/i18n';
import { STATUS_TOKENS, type NodeStatus } from '@/shared/utils/designTokens';

type BulkStatus = Exclude<NodeStatus, 'UNVERIFIED'>;

/**
 * Bottom-center pill, появляется только когда `selectedNodeIds.size > 0`.
 * Семантика: bulk-операции над выделенным набором (delete + change status).
 *
 * Дизайн: тёмный slate-900 с indigo-акцентом на счётчике (см. design-
 * reference v1 MultiSelectScreen). Внутри графа React Flow не зеркалится
 * по RTL - поэтому используем стандартные left/right utilities (это
 * canvas pixel-space, не текстовая разметка), но aria-* атрибуты остаются
 * локаль-aware
 */
interface Props {
  /** Количество выделенных узлов */
  nodeCount: number;
  /** Количество выделенных рёбер (для второй строки счётчика) */
  edgeCount: number;
  /** Видна ли write-функциональность. Если false - бар скрывается целиком */
  canWrite: boolean;
  /** Bulk-delete handler. Owner логики - GraphCanvas (`runDelete`) */
  onDelete: () => void;
  /** Bulk status-change handler. Получает новый статус, GraphCanvas
   * фильтрует selected nodes + параллельные PATCH-ы */
  onChangeStatus: (status: BulkStatus) => void;
  /** Снять выделение */
  onClear: () => void;
  /** Заблокировать кнопки на время bulk-операции */
  busy?: boolean;
}

const STATUS_OPTIONS: BulkStatus[] = ['STANDING', 'DISPUTED', 'REFUTED'];

function FloatingActionBar({
  nodeCount,
  edgeCount,
  canWrite,
  onDelete,
  onChangeStatus,
  onClear,
  busy = false,
}: Props) {
  const t = useT();
  const [statusMenuOpen, setStatusMenuOpen] = useState(false);
  const statusMenuRef = useRef<HTMLDivElement>(null);

  // dismiss popover при клике вне и при изменении selection
  useEffect(() => {
    if (!statusMenuOpen) return;
    function onPointerDown(e: PointerEvent) {
      if (statusMenuRef.current && !statusMenuRef.current.contains(e.target as Node)) {
        setStatusMenuOpen(false);
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [statusMenuOpen]);

  if (!canWrite) return null;
  if (nodeCount === 0 && edgeCount === 0) return null;

  const counterText =
    edgeCount > 0
      ? t('bulk_actions.bar.counter_with_edges')
          .replace('{nodes}', String(nodeCount))
          .replace('{edges}', String(edgeCount))
      : t('bulk_actions.bar.counter').replace('{count}', String(nodeCount));

  function handleStatusPick(status: BulkStatus) {
    setStatusMenuOpen(false);
    onChangeStatus(status);
  }

  return (
    <div
      role="toolbar"
      aria-label={t('bulk_actions.bar.counter').replace('{count}', String(nodeCount))}
      className="pointer-events-auto fixed bottom-4 left-1/2 z-40 flex -translate-x-1/2 items-center gap-2 rounded-lg bg-ink-900 px-2 py-1.5 text-ink-0 shadow-sh3 pb-[max(0.375rem,env(safe-area-inset-bottom))]"
    >
      <div className="inline-flex items-center gap-1.5 px-2.5 text-xs">
        <MousePointer2 size={13} aria-hidden />
        <span>
          {counterText.split(/(\d+)/).map((part, i) =>
            /^\d+$/.test(part) ? (
              <span key={i} className="font-mono font-bold text-accent-500">
                {part}
              </span>
            ) : (
              <span key={i}>{part}</span>
            ),
          )}
        </span>
      </div>

      <div className="h-5 w-px bg-ink-0/20" aria-hidden />

      <button
        type="button"
        disabled={busy || nodeCount === 0}
        onClick={onDelete}
        className="inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-xs font-medium hover:bg-ink-0/10 focus:bg-ink-0/10 focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <Trash2 size={12} className="text-err-500" aria-hidden />
        {t('bulk_actions.bar.delete')}
      </button>

      <div ref={statusMenuRef} className="relative">
        <button
          type="button"
          disabled={busy || nodeCount === 0}
          aria-expanded={statusMenuOpen}
          aria-haspopup="menu"
          onClick={() => setStatusMenuOpen((v) => !v)}
          className={
            'inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-xs font-medium hover:bg-ink-0/10 focus:bg-ink-0/10 focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed ' +
            (statusMenuOpen ? 'bg-ink-0/10' : '')
          }
        >
          <CheckCircle2 size={12} aria-hidden />
          {t('bulk_actions.bar.change_status')}
          <ChevronUp size={11} aria-hidden className={statusMenuOpen ? '' : 'rotate-180'} />
        </button>

        {statusMenuOpen && (
          <div
            role="menu"
            aria-label={t('bulk_actions.bar.change_status')}
            className="absolute bottom-full left-0 z-50 mb-1.5 w-56 rounded-md border border-border bg-elevated py-1 text-ink-700 shadow-sh3"
          >
            <div className="border-b border-border px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-ink-500">
              {t('bulk_actions.bar.apply_to_all').replace('{count}', String(nodeCount))}
            </div>
            {STATUS_OPTIONS.map((s) => {
              const token = STATUS_TOKENS[s];
              const Icon = token.Icon;
              return (
                <button
                  key={s}
                  type="button"
                  role="menuitem"
                  onClick={() => handleStatusPick(s)}
                  className="flex w-full items-center gap-2.5 px-3 py-2 text-start text-xs font-medium text-ink-700 hover:bg-ink-100"
                >
                  <span
                    className={`grid h-5 w-5 place-items-center rounded ${token.badgeBg} ${token.badgeText}`}
                  >
                    <Icon size={12} aria-hidden />
                  </span>
                  <span className="flex-1">{t(token.labelKey)}</span>
                  <Check size={12} className="invisible shrink-0 text-accent-600" aria-hidden />
                </button>
              );
            })}
          </div>
        )}
      </div>

      <div className="h-5 w-px bg-ink-0/20" aria-hidden />

      <button
        type="button"
        onClick={onClear}
        className="inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-xs font-medium hover:bg-ink-0/10 focus:bg-ink-0/10 focus:outline-none"
      >
        <X size={12} aria-hidden />
        {t('bulk_actions.bar.clear')}
      </button>
    </div>
  );
}

export default FloatingActionBar;
