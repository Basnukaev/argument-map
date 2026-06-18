import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { KeyboardEvent } from 'react';
import { useT } from '@/shared/i18n';

interface Props {
  /** Текущая страница, **1-based**. */
  page: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
  /** Вызывается с 1-based номером страницы. */
  onPageChange: (page1Based: number) => void;
  /** Идёт загрузка (переход на страницу) — кнопки disabled. */
  loading?: boolean;
}

/** Сентинел для эллипсиса в списке слотов (не кликабельный разрыв). */
const GAP = 'gap' as const;
type Slot = number | typeof GAP;

/**
 * Номера-слоты с эллипсисами. Макс ~7 «числовых» слотов: всегда первая,
 * текущая±1, последняя; разрывы между ними схлопываются в `…`.
 * Примеры (current=3): 1 2 [3] 4 … 62 | (current=30): 1 … 29 [30] 31 … 62.
 */
function buildSlots(page: number, totalPages: number): Slot[] {
  // Маленькое число страниц — показываем все без эллипсиса.
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i + 1);
  }
  const slots: Slot[] = [];
  const push = (n: Slot) => slots.push(n);

  // Окно вокруг текущей: [page-1, page, page+1], зажатое в [2, totalPages-1].
  const windowStart = Math.max(2, page - 1);
  const windowEnd = Math.min(totalPages - 1, page + 1);

  push(1);
  // Разрыв между первой и окном.
  if (windowStart > 2) push(GAP);
  for (let n = windowStart; n <= windowEnd; n++) push(n);
  // Разрыв между окном и последней.
  if (windowEnd < totalPages - 1) push(GAP);
  push(totalPages);

  return slots;
}

/**
 * Pagination — нумерованная пагинация (C20). Заменяет «Показать ещё» в
 * основных списках: ‹ prev | 1 2 [3] 4 … 62 | next ›, плюс счётчик
 * «{from}–{to} из {total}» под номерами.
 *
 * Поведение:
 * - скрыта целиком если `totalPages <= 1` (нечего листать);
 * - prev disabled на стр.1, next disabled на последней;
 * - активная страница — `aria-current="page"` + accent-заливка;
 * - эллипсис (`…`) — некликабельный разрыв (макс ~7 числовых слотов).
 *
 * Клавиатура (когда фокус внутри `<nav>`): ←/→ — предыдущая/следующая,
 * Home/End — первая/последняя. Стрелки логические (НЕ инвертируются в
 * RTL: Left=prev, Right=next в обеих локалях — предсказуемо для
 * клавиатуры). `preventDefault` на обработанных.
 *
 * Tailwind: семантические токены (`border-border-strong`, `bg-elevated`,
 * `text-ink-*`, accent для активной) — единый стиль с FilterChips.
 * Работает в light/dark (data-theme) и RTL (logical классы).
 */
function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  loading = false,
}: Props) {
  const t = useT();
  // Нечего листать — компонент не рендерится.
  if (totalPages <= 1) return null;

  const isFirst = page <= 1;
  const isLast = page >= totalPages;

  const go = (n: number) => {
    const clamped = Math.min(Math.max(n, 1), totalPages);
    if (clamped !== page) onPageChange(clamped);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLElement>) => {
    switch (e.key) {
      case 'ArrowLeft':
        e.preventDefault();
        go(page - 1);
        break;
      case 'ArrowRight':
        e.preventDefault();
        go(page + 1);
        break;
      case 'Home':
        e.preventDefault();
        go(1);
        break;
      case 'End':
        e.preventDefault();
        go(totalPages);
        break;
      default:
        break;
    }
  };

  const slots = buildSlots(page, totalPages);
  // Счётчик «{from}–{to} из {total}». from/to в терминах элементов (1-based).
  const from = totalElements === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(page * pageSize, totalElements);
  const counter = t('common.pagination.counter')
    .replace('{from}', String(from))
    .replace('{to}', String(to))
    .replace('{total}', String(totalElements));

  const arrowBtn =
    'inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-border-strong bg-elevated text-ink-700 transition-colors hover:bg-ink-100 hover:text-ink-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500/40 disabled:opacity-40 disabled:pointer-events-none';

  return (
    <nav
      aria-label={t('common.pagination.nav')}
      onKeyDown={handleKeyDown}
      className="mt-6 flex flex-col items-center gap-2"
    >
      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => go(page - 1)}
          disabled={isFirst || loading}
          aria-label={t('common.pagination.prev')}
          className={arrowBtn}
        >
          {/* Стрелки логические (prev/next), не зеркалятся: иконка
              указывает «назад» визуально для LTR; в RTL граф/навигация
              проекта направление-агностичны (как мини-граф RF). */}
          <ChevronLeft size={16} aria-hidden />
        </button>

        {slots.map((slot, i) => {
          if (slot === GAP) {
            return (
              <span
                // Эллипсис не уникален как значение — позиция в массиве
                // слотов стабильна (slots детерминирован от page/total),
                // поэтому индекс здесь корректный key (не данные).
                key={`gap-${i}`}
                aria-hidden
                className="inline-flex h-8 w-6 items-center justify-center text-sm text-ink-400 select-none"
              >
                …
              </span>
            );
          }
          const active = slot === page;
          return (
            <button
              key={slot}
              type="button"
              onClick={() => go(slot)}
              disabled={loading}
              aria-current={active ? 'page' : undefined}
              aria-label={t('common.pagination.page').replace('{n}', String(slot))}
              className={`inline-flex h-8 min-w-8 shrink-0 items-center justify-center rounded-md border px-2 text-sm font-medium tabular-nums transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500/40 disabled:pointer-events-none ${
                active
                  ? 'border-accent-600 bg-accent-600 text-ink-0'
                  : 'border-border-strong bg-elevated text-ink-700 hover:bg-ink-100 hover:text-ink-900'
              }`}
            >
              <bdi dir="ltr">{slot}</bdi>
            </button>
          );
        })}

        <button
          type="button"
          onClick={() => go(page + 1)}
          disabled={isLast || loading}
          aria-label={t('common.pagination.next')}
          className={arrowBtn}
        >
          <ChevronRight size={16} aria-hidden />
        </button>
      </div>

      <p className="text-xs text-ink-500">
        <bdi dir="ltr">{counter}</bdi>
      </p>
    </nav>
  );
}

export default Pagination;
