import { Link } from 'react-router';
import { ArrowRight } from 'lucide-react';
import { useT } from '@/shared/i18n';
import type { CrossrefDto } from '@/apps/hadith/types';

/**
 * Такхридж/طرق — параллельные передачи хадиса. resolved (relatedHadithId
 * есть) → линк на detail сиблинга; unresolved → текст external id + note.
 */
function CrossrefsList({ crossrefs }: { crossrefs: CrossrefDto[] }) {
  const t = useT();
  return (
    <ul className="space-y-2">
      {crossrefs.map((c, i) => {
        const note = c.note ? (
          <span className="text-xs text-ink-500" dir="auto">
            {c.note}
          </span>
        ) : null;

        if (c.relatedHadithId) {
          return (
            // У crossref нет id с бэка; список неизменяемый → index в ключе ок.
            <li key={`${c.relatedHadithId}-${i}`}>
              <Link
                to={`/hadith/hadiths/${c.relatedHadithId}`}
                className="inline-flex items-center gap-1.5 rounded-sm text-sm text-accent-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
              >
                <ArrowRight size={14} aria-hidden />
                {c.relatedExternalId
                  ? t('hadith.detail.crossref.goto').replace('{id}', c.relatedExternalId)
                  : t('hadith.detail.crossref.goto_generic')}
              </Link>
              {note && <span className="ms-2">{note}</span>}
            </li>
          );
        }

        return (
          <li key={`${c.relatedExternalId ?? 'x'}-${i}`} className="flex flex-wrap items-center gap-2 text-sm text-ink-700">
            {c.relatedExternalId && (
              <span className="font-mono text-ink-600">{c.relatedExternalId}</span>
            )}
            {note}
          </li>
        );
      })}
    </ul>
  );
}

export default CrossrefsList;
