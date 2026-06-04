import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import type { ExplanationDto } from '@/apps/hadith/types';

/**
 * Один шарх/комментарий — сворачиваемая карточка. Текст может быть огромным
 * (до 59KB), поэтому по умолчанию свёрнут: шапка (kind + книга/автор) тоглит
 * тело по клику. kind=ILAL/GHARIB пока не приходят (гейт) — рендерим generic
 * по kind, ничего не выдумывая.
 */
function ExplanationItem({ exp }: { exp: ExplanationDto }) {
  const t = useT();
  const [open, setOpen] = useState(false);

  const heading = [exp.bookName, exp.author].filter((p): p is string => Boolean(p)).join(' — ');
  const cite = [
    exp.volume != null ? `${t('hadith.matn.vol')}${exp.volume}` : null,
    exp.page != null ? `${t('hadith.matn.page')}${exp.page}` : null,
  ]
    .filter((p): p is string => Boolean(p))
    .join(' · ');

  return (
    <Card className="overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-4 py-3 text-start hover:bg-ink-50"
      >
        <span className="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-sm text-ink-700">
          {exp.kind && (
            <span className="rounded-sm bg-accent-50 px-1.5 py-0.5 text-xs font-semibold text-accent-700">
              {exp.kind}
            </span>
          )}
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

      {open && exp.text && (
        <div className="border-t border-border px-4 pb-4 pt-3">
          <p className="font-arabic text-base leading-loose text-ink-800" dir="rtl">
            {exp.text}
          </p>
        </div>
      )}
    </Card>
  );
}

function ExplanationsList({ explanations }: { explanations: ExplanationDto[] }) {
  return (
    <ul className="space-y-3">
      {explanations.map((e, i) => (
        // У шарха нет id с бэка; список неизменяемый (detail-снимок) → index ок.
        <li key={`${e.bookName ?? ''}-${i}`}>
          <ExplanationItem exp={e} />
        </li>
      ))}
    </ul>
  );
}

export default ExplanationsList;
