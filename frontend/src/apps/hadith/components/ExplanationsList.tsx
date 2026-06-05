import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import type { ExplanationDto } from '@/apps/hadith/types';

/** Вариант карточки = семантика секции. SHARH/ILAL делят раскладку «книга/автор
 *  + сворачиваемый текст»; GHARIB ставит слово (reference) заголовком. */
type ExplanationVariant = 'SHARH' | 'ILAL' | 'GHARIB';

/** Цитата «т.N · с.M» — общая для всех вариантов карточки. */
function citeLine(exp: ExplanationDto, t: ReturnType<typeof useT>): string {
  return [
    exp.volume != null ? `${t('hadith.matn.vol')}${exp.volume}` : null,
    exp.page != null ? `${t('hadith.matn.page')}${exp.page}` : null,
  ]
    .filter((p): p is string => Boolean(p))
    .join(' · ');
}

/** Сворачиваемое тело — текст разбора. Текст может быть огромным (до 59KB),
 *  поэтому по умолчанию свёрнут. */
function CollapsibleText({ text, open }: { text: string; open: boolean }) {
  if (!open) return null;
  return (
    <div className="border-t border-border px-4 pb-4 pt-3">
      <p className="font-arabic text-base leading-loose text-ink-800" dir="rtl">
        {text}
      </p>
    </div>
  );
}

/**
 * Карточка SHARH/ILAL — шапка (книга/автор + цитата) тоглит сворачиваемое
 * тело. Идентична исходному рендеру шарха; используется и для иляля, где
 * заголовок — название книги критика (напр. علل الدارقطني / الدارقطني).
 */
function BookHeadedItem({ exp }: { exp: ExplanationDto }) {
  const t = useT();
  const [open, setOpen] = useState(false);

  const heading = [exp.bookName, exp.author].filter((p): p is string => Boolean(p)).join(' — ');
  const cite = citeLine(exp, t);

  return (
    <Card className="overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-4 py-3 text-start hover:bg-ink-50"
      >
        <span className="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-sm text-ink-700">
          {heading && (
            <span className="min-w-0 truncate font-arabic text-ink-800" dir="auto">
              {heading}
            </span>
          )}
          {cite && <span className="text-xs text-ink-400">{cite}</span>}
        </span>
        <ChevronDown
          size={16}
          aria-hidden
          className={`shrink-0 text-ink-400 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>
      {exp.text && <CollapsibleText text={exp.text} open={open} />}
    </Card>
  );
}

/**
 * Карточка GHARIB — заголовок = редкое СЛОВО из матна (reference): RTL, naskh,
 * крупно, акцентный цвет. Рядом словарь·автор (напр. النهاية في غريب الحديث ·
 * ابن الأثير) + цитата. Тело (толкование) сворачиваемое. reference null →
 * фолбэк на book/author-заголовок (рендерим BookHeadedItem).
 */
function GharibItem({ exp }: { exp: ExplanationDto }) {
  const t = useT();
  const [open, setOpen] = useState(false);

  if (!exp.reference) return <BookHeadedItem exp={exp} />;

  const dict = [exp.bookName, exp.author].filter((p): p is string => Boolean(p)).join(' · ');
  const cite = citeLine(exp, t);

  return (
    <Card className="overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center gap-3 px-4 py-3 text-start hover:bg-ink-50"
      >
        <span className="shrink-0 font-arabic text-xl font-semibold text-accent-700" dir="rtl">
          {exp.reference}
        </span>
        <span className="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-sm text-ink-700">
          {dict && (
            <span className="min-w-0 truncate font-arabic text-ink-600" dir="auto">
              {dict}
            </span>
          )}
          {cite && <span className="text-xs text-ink-400">{cite}</span>}
        </span>
        <ChevronDown
          size={16}
          aria-hidden
          className={`shrink-0 text-ink-400 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>
      {exp.text && <CollapsibleText text={exp.text} open={open} />}
    </Card>
  );
}

/**
 * Список толкований одной секции (один kind). variant выбирает раскладку
 * карточки: SHARH/ILAL — книга/автор-заголовок, GHARIB — слово-заголовок.
 * explanations уже отфильтрованы по kind на странице (порядок с бэка стабилен).
 */
function ExplanationsList({
  explanations,
  variant = 'SHARH',
}: {
  explanations: ExplanationDto[];
  variant?: ExplanationVariant;
}) {
  return (
    <ul className="space-y-3">
      {explanations.map((e, i) => (
        // У толкования нет id с бэка; список — неизменяемый detail-снимок,
        // отфильтрованный по одному kind → стабильный индекс в секции ок.
        <li key={`${variant}-${e.reference ?? e.bookName ?? ''}-${i}`}>
          {variant === 'GHARIB' ? <GharibItem exp={e} /> : <BookHeadedItem exp={e} />}
        </li>
      ))}
    </ul>
  );
}

export default ExplanationsList;
