import type { ReactNode } from 'react';

interface Props {
  /** Поле поиска. Растягивается, занимая свободное место (flex-1). */
  search?: ReactNode;
  /** Фильтры (FilterChips и/или дропдауны). На mobile - скроллящийся ряд. */
  filters?: ReactNode;
  /** Сорт-контрол (SortSelect). */
  sort?: ReactNode;
  /** Действия справа/в конце (кнопки Создать / Импорт и т.п.). */
  actions?: ReactNode;
  /** Доп. классы на внешнюю обёртку. */
  className?: string;
}

/**
 * ListToolbar - единый адаптивный контейнер для контролов списка. Один
 * связный «бар» с поверхностью и рамкой, консистентный gap/padding на
 * всех 4 list-страницах (была главная боль - каждая страница катала свою
 * раскладку фильтров/сортировки/поиска).
 *
 * Раскладка:
 * - desktop (md+): один ряд - search (start, растягивается), затем filters,
 *   затем sort, затем actions (прижаты к концу через me-/ms-auto на блоке).
 * - mobile: стопка - search на всю ширину сверху; затем ряд filters
 *   (горизонтально-скроллящийся внутри FilterChips); затем ряд sort+actions.
 *
 * Слоты опциональны - страница передаёт только нужные. Пустые слоты не
 * занимают место (нет wrapper-div для отсутствующего слота).
 */
function ListToolbar({
  search,
  filters,
  sort,
  actions,
  className = '',
}: Props) {
  return (
    <div
      className={`flex flex-col gap-3 rounded-md border border-border bg-bg-subtle p-3 md:flex-row md:flex-wrap md:items-center ${className}`}
    >
      {search && (
        <div className="w-full md:w-auto md:max-w-md md:flex-1">{search}</div>
      )}
      {filters && (
        <div className="min-w-0 md:flex md:items-center">{filters}</div>
      )}
      {(sort || actions) && (
        <div className="flex flex-wrap items-center gap-2 md:ms-auto">
          {sort}
          {actions}
        </div>
      )}
    </div>
  );
}

export default ListToolbar;
