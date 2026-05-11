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
   * Это правильное направление для RTL: отступы должны начинаться у
   * "начала чтения", которое для арабского - правая граница.
   */
  bookLanguage?: string;
}

/**
 * Визуальная иерархия уровней глав (по design-reference
 * platform_reader.jsx::ChapterTreeRow):
 * - depth=0: 15px, font-semibold, slate-900 - root (книги, тома)
 * - depth=1: 14px, font-medium, slate-700 - sub-разделы
 * - depth=2: 13px, regular, slate-600 - главы
 * - depth>=3: 12px, regular, slate-500 - под-главы
 *
 * Для арабского ramp размеров чуть больше (на 1-2px) - арабские буквы
 * визуально мельче latin при том же font-size.
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
 * Рекурсивное дерево глав в боковой панели reader'а. Кликабельность
 * зависит от startPageNumber - главы без начальной страницы (например
 * декоративные разделители shamela) показываются disabled.
 *
 * Connector-rail (border-inline-start) - тонкая вертикальная линия для
 * визуальной связи parent → child. Для depth=0 не рисуется (root уровень).
 */
function ChapterList({ nodes, depth, onSelect, currentPage, bookLanguage }: Props) {
  const railClass = depth > 0 ? 'border-s border-slate-200/70 ms-[10px] ps-[6px]' : '';
  const isBookArabic = bookLanguage === 'ar';
  // dir на корневом ul задаёт направление для всех вложенных ul через
  // inherit. Для арабской книги - rtl, тогда border-s рисуется справа
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
        const isCurrent = clickable && target === currentPage;
        const indent = depth * 6;
        const stateClass = !clickable
          ? 'cursor-not-allowed opacity-50'
          : isCurrent
            ? 'bg-indigo-50 text-indigo-700 font-semibold'
            : `${styles.color} hover:bg-slate-100/70 hover:text-indigo-700 cursor-pointer`;
        const children = n.children ?? [];
        return (
          <li key={n.id}>
            <button
              type="button"
              onClick={clickable ? () => onSelect(target) : undefined}
              disabled={!clickable}
              className={`block w-full rounded-md px-2 py-1 text-start leading-snug transition-colors ${styles.font} ${styles.weight} ${stateClass}`}
              style={{ paddingInlineStart: `${indent + 8}px` }}
              dir={isArabic ? 'rtl' : 'ltr'}
              title={
                clickable
                  ? `${n.title ?? ''} (стр. ${target})`
                  : `${n.title ?? ''} (страница не указана)`
              }
            >
              {n.title ?? '(без названия)'}
            </button>
            {children.length > 0 && (
              <ChapterList
                nodes={children}
                depth={depth + 1}
                onSelect={onSelect}
                currentPage={currentPage}
                bookLanguage={bookLanguage}
              />
            )}
          </li>
        );
      })}
    </ul>
  );
}

export default ChapterList;
