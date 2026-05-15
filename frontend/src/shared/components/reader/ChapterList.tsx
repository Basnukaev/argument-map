import { useEffect, useMemo, useRef, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import type { components } from '@/shared/api/types';
import { isArabicText } from '@/shared/components/reader/utils';

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
  bookLanguage?: string;
  expandedIds?: Set<string>;
  collapsedIds?: Set<string>;
  onToggle?: (chapterId: string) => void;
  autoExpandedIds?: Set<string>;
  currentChapterId?: string | null;
}

function getChapterLevelStyles(
  depth: number,
  isArabic: boolean,
): { font: string; color: string; weight: string } {
  const baseSize = isArabic
    ? ['text-base', 'text-sm', 'text-sm', 'text-xs']
    : ['text-sm', 'text-sm', 'text-xs', 'text-xs'];
  const fontClass = isArabic ? 'font-naskh' : '';
  const size = baseSize[Math.min(depth, baseSize.length - 1)];
  const color =
    depth === 0
      ? 'text-ink-900'
      : depth === 1
        ? 'text-ink-700'
        : depth === 2
          ? 'text-ink-600'
          : 'text-ink-500';
  const weight = depth === 0 ? 'font-semibold' : depth === 1 ? 'font-medium' : '';
  return { font: `${fontClass} ${size}`.trim(), color, weight };
}

/**
 * Текущая глава = chapter с наибольшим startPageNumber ≤ currentPage.
 * Sticky highlight: глава остаётся подсвеченной для всего своего диапазона.
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

interface ItemProps {
  isCurrent: boolean;
  indent: number;
  hasChildren: boolean;
  isExpanded: boolean;
  ChevronIcon: LucideIcon;
  onToggleClick: () => void;
  onTitleClick: () => void;
  clickable: boolean;
  stateClass: string;
  styles: { font: string; color: string; weight: string };
  isArabic: boolean;
  title: string;
  target: number | null;
  /** Nested ChapterList для children этой главы (если expanded) */
  nested?: React.ReactNode;
}

/**
 * Один пункт дерева. Выделен в отдельный компонент чтобы useEffect
 * scrollIntoView работал per-item: когда главa становится current,
 * scrollIntoView плавно прокручивает её в viewport (block: 'nearest' -
 * не скроллит если уже видна).
 */
function ChapterItem({
  isCurrent,
  indent,
  hasChildren,
  isExpanded,
  ChevronIcon,
  onToggleClick,
  onTitleClick,
  clickable,
  stateClass,
  styles,
  isArabic,
  title,
  target,
  nested,
}: ItemProps) {
  const liRef = useRef<HTMLLIElement>(null);
  useEffect(() => {
    if (isCurrent && liRef.current) {
      liRef.current.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  }, [isCurrent]);

  return (
    <li ref={liRef}>
      <div className="flex items-center" style={{ paddingInlineStart: `${indent}px` }}>
        {hasChildren ? (
          <button
            type="button"
            onClick={onToggleClick}
            className="grid h-5 w-5 shrink-0 place-items-center rounded text-ink-400 hover:bg-ink-100 hover:text-ink-700"
            aria-label={isExpanded ? 'Свернуть' : 'Развернуть'}
            aria-expanded={isExpanded}
          >
            <ChevronIcon size={12} />
          </button>
        ) : (
          <span className="h-5 w-5 shrink-0" aria-hidden="true" />
        )}
        <button
          type="button"
          onClick={hasChildren || clickable ? onTitleClick : undefined}
          disabled={!hasChildren && !clickable}
          className={`flex-1 rounded-md px-2 py-1 text-start leading-snug transition-colors ${styles.font} ${styles.weight} ${stateClass}`}
          dir={isArabic ? 'rtl' : 'ltr'}
          title={
            clickable
              ? `${title} (стр. ${target})`
              : `${title} (страница не указана)`
          }
        >
          {title}
        </button>
      </div>
      {nested}
    </li>
  );
}

/**
 * Рекурсивное сворачиваемое дерево глав. По умолчанию все главы свёрнуты
 * (shamela-pattern для длинных книг 200+ глав). Юзер кликает на title или
 * chevron - разворачивает.
 *
 * <p>Sticky highlight для всего range страниц через {@code findCurrentChapter}.
 * Auto-expand path до active chapter через {@code findAncestorPath}. Manual
 * collapse override - {@code collapsedIds} - юзер может явно свернуть
 * auto-expanded главу (нужно отдельный set чтобы collapse мог отменить
 * автоматический expand).
 *
 * <p>Scroll-into-view: active chapter автоматически прокручивается в
 * viewport при изменении - чтобы юзер не "терял" highlight при prev/next
 * navigation когда список длинный.
 */
function ChapterList(props: Props) {
  const {
    nodes,
    depth,
    onSelect,
    currentPage,
    bookLanguage,
    expandedIds: parentExpanded,
    collapsedIds: parentCollapsed,
    onToggle: parentToggle,
    autoExpandedIds: parentAutoExpanded,
    currentChapterId: parentCurrentChapterId,
  } = props;

  // State в корневом ChapterList (depth=0). Manual expand и collapse - два
  // отдельных set'а: collapse может переопределить auto-expand
  const [rootManualExpanded, setRootManualExpanded] = useState<Set<string>>(new Set());
  const [rootManualCollapsed, setRootManualCollapsed] = useState<Set<string>>(new Set());

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
  const collapsedIds = parentCollapsed ?? rootManualCollapsed;
  const autoExpandedIds = parentAutoExpanded ?? rootAutoExpanded;
  const currentChapterId = parentCurrentChapterId ?? rootCurrentChapter?.id ?? null;

  // Toggle: если глава сейчас expanded (manual или auto) - collapse её
  // (через collapsed set). Если collapsed - убрать из collapsed. Иначе
  // добавить в manual expanded
  const handleToggle =
    parentToggle ??
    ((chapterId: string) => {
      const currentlyExpanded =
        rootManualExpanded.has(chapterId) ||
        (rootAutoExpanded.has(chapterId) && !rootManualCollapsed.has(chapterId));
      if (currentlyExpanded) {
        setRootManualCollapsed((prev) => new Set(prev).add(chapterId));
        setRootManualExpanded((prev) => {
          const next = new Set(prev);
          next.delete(chapterId);
          return next;
        });
      } else {
        setRootManualCollapsed((prev) => {
          const next = new Set(prev);
          next.delete(chapterId);
          return next;
        });
        setRootManualExpanded((prev) => new Set(prev).add(chapterId));
      }
    });

  const railClass = depth > 0 ? 'border-s border-border/70 ms-[10px] ps-[6px]' : '';
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
        const isCurrent = chapterId !== '' && chapterId === currentChapterId;
        const indent = depth * 6;
        const stateClass = !clickable
          ? 'cursor-default text-ink-500'
          : isCurrent
            ? 'bg-accent-50 text-accent-700 font-semibold cursor-pointer'
            : `${styles.color} hover:bg-ink-100/70 hover:text-accent-700 cursor-pointer`;
        const children = n.children ?? [];
        const hasChildren = children.length > 0;
        // Expand состояние: manual override побеждает auto. collapsed
        // явно отключает (даже если auto хочет раскрыть). expanded
        // включает (если не collapsed)
        const isExpanded =
          hasChildren &&
          !collapsedIds.has(chapterId) &&
          (expandedIds.has(chapterId) || autoExpandedIds.has(chapterId));
        const ChevronIcon = isExpanded ? ChevronDown : ChevronRight;

        const handleTitleClick = () => {
          if (hasChildren) handleToggle(chapterId);
          if (clickable && target != null) onSelect(target);
        };

        const nested = hasChildren && isExpanded ? (
          <ChapterList
            nodes={children}
            depth={depth + 1}
            onSelect={onSelect}
            currentPage={currentPage}
            bookLanguage={bookLanguage}
            expandedIds={expandedIds}
            collapsedIds={collapsedIds}
            onToggle={handleToggle}
            autoExpandedIds={autoExpandedIds}
            currentChapterId={currentChapterId}
          />
        ) : null;

        return (
          <ChapterItem
            key={n.id}
            isCurrent={isCurrent}
            indent={indent}
            hasChildren={hasChildren}
            isExpanded={isExpanded}
            ChevronIcon={ChevronIcon}
            onToggleClick={() => handleToggle(chapterId)}
            onTitleClick={handleTitleClick}
            clickable={clickable}
            stateClass={stateClass}
            styles={styles}
            isArabic={isArabic}
            title={n.title ?? '(без названия)'}
            target={target}
            nested={nested}
          />
        );
      })}
    </ul>
  );
}

export default ChapterList;
