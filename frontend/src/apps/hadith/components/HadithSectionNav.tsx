import { useEffect, useState } from 'react';
import { useT, type DictKey } from '@/shared/i18n';

/**
 * Внутренняя якорная навигация по разделам страницы хадиса. Прилипает
 * под глобальным Header'ом (он `sticky top-0 h-12`), поэтому наша полоса
 * стоит на `top-12`. Якоря секций резервируют `scroll-mt` под обе
 * прилипшие полосы (Header + эта навигация), иначе заголовок секции
 * уезжает под них.
 *
 * Активная секция подсвечивается через IntersectionObserver (без
 * скролл-слушателей на каждый кадр). На мобильном — горизонтально
 * скроллящийся ряд чипов.
 */
export interface SectionNavItem {
  /** id целевой секции (`<section id={id}>`). */
  id: string;
  labelKey: DictKey;
}

interface Props {
  items: ReadonlyArray<SectionNavItem>;
}

function HadithSectionNav({ items }: Props) {
  const t = useT();
  const [active, setActive] = useState<string | null>(items[0]?.id ?? null);

  useEffect(() => {
    const sections = items
      .map((it) => document.getElementById(it.id))
      .filter((el): el is HTMLElement => el !== null);
    if (sections.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        // Берём самую верхнюю из пересекающих viewport секций — она и есть
        // «текущая». rootMargin сдвигает зону детекции под обе sticky-полосы.
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) setActive(visible[0].target.id);
      },
      { rootMargin: '-104px 0px -55% 0px', threshold: 0 },
    );
    sections.forEach((s) => observer.observe(s));
    return () => observer.disconnect();
  }, [items]);

  return (
    <nav
      aria-label={t('hadith.detail.nav.aria')}
      className="sticky top-12 z-30 -mx-3 mb-6 border-b border-border-strong bg-bg/90 px-3 backdrop-blur sm:-mx-6 sm:px-6"
    >
      <ul className="flex gap-1 overflow-x-auto py-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {items.map((it) => {
          const isActive = active === it.id;
          return (
            <li key={it.id} className="shrink-0">
              <a
                href={`#${it.id}`}
                aria-current={isActive ? 'true' : undefined}
                className={`inline-flex items-center rounded-full px-3 py-1.5 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500 ${
                  isActive
                    ? 'bg-accent-50 text-accent-700'
                    : 'text-ink-500 hover:bg-ink-100 hover:text-ink-700'
                }`}
              >
                {t(it.labelKey)}
              </a>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

export default HadithSectionNav;
