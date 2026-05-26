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
  /** Сдвиг от правого края viewport в пикселях. Используется когда
   * NodeDetailsPanel/EdgeDetailsPanel открыт справа (400px) - бар
   * центрируется в оставшейся видимой области, не уезжает под
   * sidebar. На mobile sidebar fullscreen, offset=0. */
  offsetEndPx?: number;
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
  offsetEndPx = 0,
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

  // offsetEndPx сдвигает баР в сторону start (логически "влево" в LTR,
  // "вправо" в RTL) на половину sidebar width - тогда center bar'а
  // совпадает с center видимой canvas (между left edge и open
  // sidebar'ом справа). Через CSS transform + calc.
  const transform =
    offsetEndPx > 0
      ? `translate(calc(-50% - ${offsetEndPx / 2}px), 0)`
      : 'translate(-50%, 0)';
  return (
    <div
      role="toolbar"
      aria-label={t('bulk_actions.bar.counter').replace('{count}', String(nodeCount))}
      // bg-elevated + ink + accent palette: то же что у обычных card'ов
      // в проекте, автоматический контраст в обеих темах через token swap.
      // border-strong + shadow-sh3 даёт чёткую elevation от canvas.
      // Раньше использовалось bg-accent-800 - давало проблемы в обеих
      // темах: в light слишком тёмная плашка для overlay, в dark
      // визуально близка к background.
      className="pointer-events-auto fixed bottom-3 left-1/2 z-40 flex items-center gap-2 rounded-lg border border-border-strong bg-elevated px-2 py-1.5 text-ink-900 shadow-sh3 pb-[max(0.375rem,env(safe-area-inset-bottom))] transition-transform duration-200"
      style={{ transform }}
    >
      <div className="inline-flex items-center gap-1.5 px-2.5 text-xs">
        <MousePointer2 size={13} className="text-ink-500" aria-hidden />
        <span className="text-ink-700">
          {counterText.split(/(\d+)/).map((part, i) =>
            /^\d+$/.test(part) ? (
              <span key={i} className="font-mono font-bold text-accent-600">
                {part}
              </span>
            ) : (
              <span key={i}>{part}</span>
            ),
          )}
        </span>
      </div>

      <div className="h-5 w-px bg-border" aria-hidden />

      <button
        type="button"
        disabled={busy || nodeCount === 0}
        onClick={onDelete}
        className="inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-xs font-medium text-ink-700 hover:bg-ink-100 focus:bg-ink-100 focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <Trash2 size={12} className="text-err-600" aria-hidden />
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
            'inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-xs font-medium text-ink-700 hover:bg-ink-100 focus:bg-ink-100 focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed ' +
            (statusMenuOpen ? 'bg-ink-100' : '')
          }
        >
          <CheckCircle2 size={12} className="text-ink-500" aria-hidden />
          {t('bulk_actions.bar.change_status')}
          <ChevronUp size={11} className="text-ink-500" aria-hidden style={{ transform: statusMenuOpen ? undefined : 'rotate(180deg)' }} />
        </button>

        {statusMenuOpen && (
          <div
            role="menu"
            aria-label={t('bulk_actions.bar.change_status')}
            className="absolute bottom-full start-0 z-50 mb-1.5 w-56 rounded-md border border-border bg-elevated py-1 text-ink-700 shadow-sh3"
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

      <div className="h-5 w-px bg-border" aria-hidden />

      <button
        type="button"
        onClick={onClear}
        className="inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-xs font-medium text-ink-700 hover:bg-ink-100 focus:bg-ink-100 focus:outline-none"
      >
        <X size={12} className="text-ink-500" aria-hidden />
        {t('bulk_actions.bar.clear')}
      </button>
    </div>
  );
}

export default FloatingActionBar;
