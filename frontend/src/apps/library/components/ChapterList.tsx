import { useMemo, useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import type { components } from '@/shared/api/types';
import { isArabicText } from '@/apps/library/utils/bookReaderUtils';

// Дополняем ChapterResponse полем children - springdoc-openapi 2.x не
// выводит self-referential properties в /v3/api-docs (известная gotcha,
// см. gotchas.md). В runtime children приходит, intersection даёт
// type-safe доступ к рекурсивной структуре.
export type Chapter = components['schemas']['ChapterResponse'] & {
  children?: Chapter[];
};

interface Props {
  nodes: ReadonlyArray<Chapter>;
  depth: number;
  onSelect: (pageNumber: number) => void;
  currentPage: number;
  /**
   * Язык книги. Когда `ar` - корневой `<ul>` получает `dir="rtl"`, тогда
   * RTL-aware logical properties (`border-s`, `paddingInlineStart`)
   * автоматически рисуют connector rail и отступ **справа** от текста.
   */
  bookLanguage?: string;
  /**
   * Manual-expanded set (для рекурсивного проброса). Хранится в state
   * корневого ChapterList (depth=0), дочерние получают тот же объект.
   */
  expandedIds?: Set<string>;
  onToggle?: (chapterId: string) => void;
  /**
   * Set предков текущей главы - auto-expand чтобы active chapter был
   * виден без юзер-клика. Тоже sharing между recursive levels.
   */
  autoExpandedIds?: Set<string>;
  /**
   * Id «текущей» главы (chapter с наибольшим startPageNumber ≤ currentPage).
   * Передаётся для подсветки: глава остаётся highlighted для всего диапазона
   * страниц до следующей главы, не только на её startPageNumber.
   */
  currentChapterId?: string | null;
}

/**
 * Визуальная иерархия уровней глав (по design-reference
 * platform_reader.jsx::ChapterTreeRow):
 * - depth=0: 15px, font-semibold, slate-900 - root (книги, тома)
 * - depth=1: 14px, font-medium, slate-700 - sub-разделы
 * - depth=2: 13px, regular, slate-600 - главы
 * - depth>=3: 12px, regular, slate-500 - под-главы
 */
function getChapterLevelStyles(
  depth: number,
  isArabic: boolean,
): { font: string; color: string; weight: string } {
  const baseSize = isArabic
    ? ['text-[16px]', 'text-[14.5px]', 'text-[13.5px]', 'text-[12.5px]']
    : ['text-[14px]', 'text-[13px]', 'text-[12px]', 'text-[11.5px]'];
  const fontClass = isArabic ? 'font-naskh' : '';
  const size = baseSize[Math.min(depth, baseSize.length - 1)];
  const color =
    depth === 0
      ? 'text-slate-900'
      : depth === 1
        ? 'text-slate-700'
        : depth === 2
          ? 'text-slate-600'
          : 'text-slate-500';
  const weight = depth === 0 ? 'font-semibold' : depth === 1 ? 'font-medium' : '';
  return { font: `${fontClass} ${size}`.trim(), color, weight };
}

/**
 * Находит «текущую» главу - chapter с наибольшим `startPageNumber ≤ currentPage`.
 * Это даёт sticky-highlight для всего диапазона страниц главы: если глава
 * начинается с page 5 и следующая с page 13, главa подсвечена для pages 5-12.
 *
 * Traverse рекурсивно по всему дереву (включая subchapters) - subchapter
 * считается более specific match чем родитель если у него startPageNumber
 * ближе к currentPage.
 */
function findCurrentChapter(
  nodes: ReadonlyArray<Chapter>,
  currentPage: number,
): Chapter | null {
  let best: Chapter | null = null;
  const traverse = (n: Chapter): void => {
    if (n.startPageNumber != null && n.startPageNumber <= currentPage) {
      if (!best || (n.startPageNumber ?? 0) > (best.startPageNumber ?? 0)) {
        best = n;
      }
    }
    for (const child of n.children ?? []) traverse(child);
  };
  for (const n of nodes) traverse(n);
  return best;
}

/**
 * Возвращает массив id предков главы с {@code id === targetId}. Используется
 * для auto-expand: чтобы текущая глава была видима, все её предки должны
 * быть раскрыты.
 */
function findAncestorPath(
  nodes: ReadonlyArray<Chapter>,
  targetId: string,
  parents: string[] = [],
): string[] | null {
  for (const n of nodes) {
    if (n.id === targetId) return parents;
    const children = n.children ?? [];
    if (n.id && children.length > 0) {
      const found = findAncestorPath(children, targetId, [...parents, n.id]);
      if (found) return found;
    }
  }
  return null;
}

/**
 * Рекурсивное сворачиваемое дерево глав. По умолчанию все главы свёрнуты -
 * shamela-паттерн (длинные книги в 200+ глав, full-expanded непригляден).
 * Юзер кликает на ChevronRight чтобы развернуть, ChevronDown чтобы свернуть.
 *
 * <p>Авто-разворот пути до текущей страницы: при `currentPage` change
 * вычисляем все ancestor-id и считаем их раскрытыми независимо от manual
 * state. Когда юзер уходит со страницы - manual state preserved (его
 * собственные expand'ы), auto уходит вместе с currentPage.
 *
 * <p>Click на title всё ещё навигирует на startPageNumber (если есть);
 * click на ChevronRight/Down toggles collapse - они разнесены.
 */
function ChapterList(props: Props) {
  const {
    nodes,
    depth,
    onSelect,
    currentPage,
    bookLanguage,
    expandedIds: parentExpanded,
    onToggle: parentToggle,
    autoExpandedIds: parentAutoExpanded,
    currentChapterId: parentCurrentChapterId,
  } = props;

  // State живёт только в корневом ChapterList (depth=0), все nested
  // получают через props - чтобы expand-состояние шарилось
  const [rootManualExpanded, setRootManualExpanded] = useState<Set<string>>(new Set());

  // Текущая глава = наибольший startPageNumber ≤ currentPage. Подсвечена
  // на всё диапазон страниц этой главы (до следующей).
  const rootCurrentChapter = useMemo(
    () => findCurrentChapter(nodes, currentPage),
    [nodes, currentPage],
  );
  const rootAutoExpanded = useMemo(() => {
    const id = rootCurrentChapter?.id;
    if (!id) return new Set<string>();
    return new Set(findAncestorPath(nodes, id) ?? []);
  }, [nodes, rootCurrentChapter]);

  const expandedIds = parentExpanded ?? rootManualExpanded;
  const autoExpandedIds = parentAutoExpanded ?? rootAutoExpanded;
  const currentChapterId = parentCurrentChapterId ?? rootCurrentChapter?.id ?? null;
  const handleToggle =
    parentToggle ??
    ((chapterId: string) => {
      setRootManualExpanded((prev) => {
        const next = new Set(prev);
        if (next.has(chapterId)) next.delete(chapterId);
        else next.add(chapterId);
        return next;
      });
    });

  const railClass = depth > 0 ? 'border-s border-slate-200/70 ms-[10px] ps-[6px]' : '';
  const isBookArabic = bookLanguage === 'ar';
  const rootDir = depth === 0 ? (isBookArabic ? 'rtl' : 'ltr') : undefined;

  return (
    <ul
      className={`${depth === 0 ? 'space-y-0.5' : 'mt-0.5 space-y-0.5'} ${railClass}`}
      dir={rootDir}
    >
      {nodes.map((n) => {
        const isArabic = isArabicText(n.title);
        const styles = getChapterLevelStyles(depth, isArabic);
        const target = n.startPageNumber ?? null;
        const clickable = target != null && target > 0;
        const chapterId = n.id ?? '';
        // sticky highlight для всего диапазона страниц главы (не только её
        // startPageNumber). Глава "содержит" currentPage если её startPage
        // ≤ currentPage и она ближайшая снизу - см. findCurrentChapter
        const isCurrent = chapterId !== '' && chapterId === currentChapterId;
        const indent = depth * 6;
        const stateClass = !clickable
          ? 'cursor-default text-slate-500'
          : isCurrent
            ? 'bg-indigo-50 text-indigo-700 font-semibold cursor-pointer'
            : `${styles.color} hover:bg-slate-100/70 hover:text-indigo-700 cursor-pointer`;
        const children = n.children ?? [];
        const hasChildren = children.length > 0;
        const isExpanded = hasChildren && (expandedIds.has(chapterId) || autoExpandedIds.has(chapterId));
        const ChevronIcon = isExpanded ? ChevronDown : ChevronRight;

        // Click на title: navigate если есть target, и/или toggle expand
        // если есть children. Юзер ожидает что клик на главу с подглавами
        // одновременно открывает страницу И раскрывает дерево
        const handleTitleClick = () => {
          if (hasChildren) handleToggle(chapterId);
          if (clickable && target != null) onSelect(target);
        };

        return (
          <li key={n.id}>
            <div className="flex items-center" style={{ paddingInlineStart: `${indent}px` }}>
              {hasChildren ? (
                <button
                  type="button"
                  onClick={() => handleToggle(chapterId)}
                  className="grid h-5 w-5 shrink-0 place-items-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                  aria-label={isExpanded ? 'Свернуть' : 'Развернуть'}
                  aria-expanded={isExpanded}
                >
                  <ChevronIcon size={12} />
                </button>
              ) : (
                // spacer чтобы text был выровнен с теми что имеют chevron
                <span className="h-5 w-5 shrink-0" aria-hidden="true" />
              )}
              <button
                type="button"
                onClick={hasChildren || clickable ? handleTitleClick : undefined}
                disabled={!hasChildren && !clickable}
                className={`flex-1 rounded-md px-2 py-1 text-start leading-snug transition-colors ${styles.font} ${styles.weight} ${stateClass}`}
                dir={isArabic ? 'rtl' : 'ltr'}
                title={
                  clickable
                    ? `${n.title ?? ''} (стр. ${target})`
                    : `${n.title ?? ''} (страница не указана)`
                }
              >
                {n.title ?? '(без названия)'}
              </button>
            </div>
            {hasChildren && isExpanded && (
              <ChapterList
                nodes={children}
                depth={depth + 1}
                onSelect={onSelect}
                currentPage={currentPage}
                bookLanguage={bookLanguage}
                expandedIds={expandedIds}
                onToggle={handleToggle}
                autoExpandedIds={autoExpandedIds}
                currentChapterId={currentChapterId}
              />
            )}
          </li>
        );
      })}
    </ul>
  );
}

export default ChapterList;
