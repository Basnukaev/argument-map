import { useState } from 'react';
import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import MatnDiff from '@/apps/hadith/components/MatnDiff';
import { hasWordDiff } from '@/apps/hadith/utils/matnDiff';
import type { MatnDto } from '@/apps/hadith/types';

function MatnItem({ matn, primary }: { matn: MatnDto; primary: MatnDto | null }) {
  const t = useT();
  const [showDiff, setShowDiff] = useState(false);
  // diff доступен только для НЕ-основной редакции с реальным пословным
  // расхождением (отличие лишь в огласовках → кнопку не показываем)
  const canDiff = !matn.isPrimary && primary !== null && hasWordDiff(primary.textAr, matn.textAr);

  return (
    <Card className="p-4">
      <div className="mb-2 flex flex-wrap items-center gap-2 text-xs text-ink-500">
        {matn.isPrimary && (
          <span className="rounded-sm bg-accent-50 px-1.5 py-0.5 font-semibold text-accent-700">
            {t('hadith.detail.primary')}
          </span>
        )}
        {matn.printedNumber != null && <span className="font-mono">№{matn.printedNumber}</span>}
        {matn.volume != null && <span>vol.{matn.volume}</span>}
        {matn.pageNo != null && <span>p.{matn.pageNo}</span>}
        {canDiff && (
          <button
            type="button"
            onClick={() => setShowDiff((v) => !v)}
            className="ms-auto rounded-sm px-1 text-accent-700 hover:underline"
          >
            {showDiff ? t('hadith.matn.hide_diff') : t('hadith.matn.show_diff')}
          </button>
        )}
      </div>

      {canDiff && showDiff && primary ? (
        <>
          <MatnDiff base={primary.textAr} variant={matn.textAr} />
          <p className="mt-1 text-[11px] text-ink-400">{t('hadith.matn.diff_legend')}</p>
        </>
      ) : (
        <p className="font-arabic text-lg leading-loose text-ink-900" dir="rtl">
          {matn.textAr}
        </p>
      )}

      {matn.textRu && <p className="mt-2 text-sm text-ink-700" dir="ltr">{matn.textRu}</p>}
      {matn.divergenceSummary && (
        <p className="mt-2 text-xs italic text-ink-500" dir="auto">
          {matn.divergenceSummary}
        </p>
      )}
    </Card>
  );
}

/**
 * Список вариаций matn хадиса. Основная редакция рендерится как есть;
 * для остальных доступен пословный diff относительно основной (toggle).
 */
function MatnVariations({ matns }: { matns: MatnDto[] }) {
  const t = useT();
  const primary = matns.find((m) => m.isPrimary) ?? null;

  if (matns.length === 0) {
    return <p className="text-sm text-ink-500">{t('hadith.detail.no_matns')}</p>;
  }

  return (
    <ul className="space-y-3">
      {matns.map((m) => (
        <li key={m.id}>
          <MatnItem matn={m} primary={primary} />
        </li>
      ))}
    </ul>
  );
}

export default MatnVariations;
