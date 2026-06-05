import { Link } from 'react-router';
import { ArrowRight } from 'lucide-react';
import { useT } from '@/shared/i18n';
import type { CrossrefDto } from '@/apps/hadith/types';

/**
 * Такхридж/طرق — параллельные передачи хадиса. Строка: имя сборника (или
 * внешний id) + «№{numbers}». resolved (relatedHadithId есть) → линк
 * «Перейти» на detail сиблинга; unresolved → строка + подпись «не
 * импортирована». Внешний id — мелким mono справа.
 */
function CrossrefRow({ c }: { c: CrossrefDto }) {
  const t = useT();
  const name = c.collectionNameRu ?? c.relatedExternalId ?? '—';
  // numbers всегда массив с бэка; ?? [] — защита от malformed/частичного ответа.
  const nums = c.numbers ?? [];
  const numbers = nums.length > 0 ? `№${nums.join(', ')}` : null;

  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
      <span className="font-medium text-ink-700" dir="auto">
        {name}
      </span>
      {numbers && (
        <span className="font-mono text-xs text-ink-500" dir="auto">
          {numbers}
        </span>
      )}
      {c.relatedHadithId ? (
        <Link
          to={`/hadith/hadiths/${c.relatedHadithId}`}
          className="inline-flex items-center gap-1 rounded-sm text-accent-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
        >
          <ArrowRight size={14} aria-hidden />
          {t('hadith.detail.crossref.goto_short')}
        </Link>
      ) : (
        <span className="text-xs text-ink-400">{t('hadith.detail.crossref.not_imported')}</span>
      )}
      {c.relatedExternalId && (
        <span className="ms-auto font-mono text-[11px] text-ink-400">{c.relatedExternalId}</span>
      )}
    </div>
  );
}

function CrossrefsList({ crossrefs }: { crossrefs: CrossrefDto[] }) {
  return (
    <ul className="space-y-2">
      {crossrefs.map((c, i) => (
        // У crossref нет id с бэка; список неизменяемый → index в ключе ок.
        <li key={`${c.relatedExternalId ?? c.relatedHadithId ?? 'x'}-${i}`}>
          <CrossrefRow c={c} />
        </li>
      ))}
    </ul>
  );
}

export default CrossrefsList;
