import { Link } from 'react-router';
import { ArrowRight, EyeOff } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import HideToggle from '@/apps/hadith/components/curation/HideToggle';
import CurationFieldsPanel, {
  type CurationFieldSpec,
} from '@/apps/hadith/components/curation/CurationFieldsPanel';
import type { RulingDto } from '@/apps/hadith/types';

/**
 * Список вердиктов учёных (rulings) на хадис. Каждый — учёный + год смерти
 * + текст вердикта + книга/том/страница. Бейдж параллельной передачи:
 *  - вердикт на эту же запись (relatedExternalId === externalId страницы) →
 *    бейдж НЕ показываем;
 *  - resolved (relatedHadithId) → бейдж-ссылка на detail сиблинга;
 *  - иначе → текстовый бейдж с именем сборника (или внешним id).
 */
function RulingItem({
  ruling,
  hadithExternalId,
  role,
  onChanged,
}: {
  ruling: RulingDto;
  hadithExternalId: string | null;
  /** Роль зрителя — гейт ADMIN record-hide (курация 4.b). */
  role: string | undefined;
  /** Рефетч detail после скрытия/показа записи. */
  onChanged: () => void;
}) {
  const t = useT();
  const cite = [
    ruling.bookName,
    ruling.volume != null ? `${t('hadith.matn.vol')}${ruling.volume}` : null,
    ruling.page != null ? `${t('hadith.matn.page')}${ruling.page}` : null,
  ]
    .filter((p): p is string => Boolean(p))
    .join(' · ');

  // §5-редактируемые поля вердикта (ADMIN inline-правка, курация Фаза 5).
  const editFields: CurationFieldSpec[] = [
    { label: t('hadith.curation.field.ruling_text'), fieldName: 'ruling_text', value: ruling.rulingText, kind: 'text' },
    { label: t('hadith.curation.field.ruler_name'), fieldName: 'ruler_name', value: ruling.rulerName, kind: 'text' },
    { label: t('hadith.curation.field.ruler_death_year'), fieldName: 'ruler_death_year', value: ruling.rulerDeathYear, kind: 'number' },
    { label: t('hadith.curation.field.book_name'), fieldName: 'book_name', value: ruling.bookName, kind: 'text' },
    { label: t('hadith.curation.field.page'), fieldName: 'page', value: ruling.page, kind: 'number' },
    { label: t('hadith.curation.field.volume'), fieldName: 'volume', value: ruling.volume, kind: 'number' },
  ];

  // Вердикт на эту же запись (своя alminasa-id) — бейдж параллели не нужен.
  const selfRuling =
    ruling.relatedExternalId != null && ruling.relatedExternalId === hadithExternalId;
  // Параллель: есть relatedExternalId, и это НЕ своя запись.
  const onParallel = ruling.relatedExternalId != null && !selfRuling;
  const label = ruling.relatedCollectionNameRu ?? ruling.relatedExternalId ?? '';

  return (
    <Card className={`p-4 ${ruling.hiddenByAdmin ? 'opacity-50' : ''}`}>
      {/* ADMIN record-hide (курация 4.b): пилюля причины + тогл показать/скрыть. */}
      {ruling.hiddenByAdmin && (
        <div className="mb-2 inline-flex items-center gap-1.5 rounded-sm bg-rose-50 px-1.5 py-0.5 text-xs text-rose-700">
          <EyeOff size={12} aria-hidden />
          <span dir="auto">
            {t('hadith.curation.hidden_by_admin')}
            {ruling.hideReason ? `: ${ruling.hideReason}` : ''}
          </span>
        </div>
      )}
      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
        {ruling.rulerName && (
          <span className="font-arabic text-base font-semibold text-ink-900" dir="rtl">
            {ruling.rulerName}
          </span>
        )}
        {ruling.rulerDeathYear != null && (
          <span className="text-xs text-ink-500">
            {t('hadith.detail.ruling.died').replace('{year}', String(ruling.rulerDeathYear))}
          </span>
        )}
      </div>

      {ruling.rulingText && (
        <p className="mt-2 text-sm leading-relaxed text-ink-800" dir="auto">
          {ruling.rulingText}
        </p>
      )}

      <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-500">
        {cite && (
          <span dir="auto" className="text-ink-600">
            {cite}
          </span>
        )}
        {onParallel && ruling.relatedHadithId && (
          // resolved → бейдж-ссылка на detail параллельной передачи
          <Link
            to={`/hadith/hadiths/${ruling.relatedHadithId}`}
            className="inline-flex items-center gap-1 rounded-sm bg-amber-50 px-1.5 py-0.5 text-amber-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500"
            dir="auto"
          >
            <ArrowRight size={12} aria-hidden />
            <span>{label}</span>
            {ruling.relatedExternalId && (
              <span className="font-mono text-[11px] text-amber-600">
                {ruling.relatedExternalId}
              </span>
            )}
          </Link>
        )}
        {onParallel && !ruling.relatedHadithId && (
          // unresolved → текстовый бейдж с именем сборника / внешним id
          <span
            className="inline-flex items-center gap-1 rounded-sm bg-amber-50 px-1.5 py-0.5 text-amber-700"
            dir="auto"
          >
            <span>{t('hadith.detail.ruling.on_parallel').replace('{id}', label)}</span>
          </span>
        )}
        <span className="ms-auto">
          <HideToggle
            entityTable="hd_rulings"
            entityId={ruling.id}
            hiddenByAdmin={ruling.hiddenByAdmin}
            hideReason={ruling.hideReason}
            role={role}
            onChanged={onChanged}
          />
        </span>
      </div>
      <CurationFieldsPanel
        entityTable="hd_rulings"
        entityId={ruling.id}
        fields={editFields}
        role={role}
        onChanged={onChanged}
      />
    </Card>
  );
}

function RulingsList({
  rulings,
  hadithExternalId,
  role,
  onChanged,
}: {
  rulings: RulingDto[];
  /** Своя alminasa-id хадиса страницы — для скрытия self-вердикт-бейджа. */
  hadithExternalId: string | null;
  /** Роль зрителя — гейт ADMIN record-hide (курация 4.b). */
  role: string | undefined;
  /** Рефетч detail после скрытия/показа записи. */
  onChanged: () => void;
}) {
  return (
    <ul className="space-y-3">
      {rulings.map((r) => (
        <li key={r.id}>
          <RulingItem
            ruling={r}
            hadithExternalId={hadithExternalId}
            role={role}
            onChanged={onChanged}
          />
        </li>
      ))}
    </ul>
  );
}

export default RulingsList;
