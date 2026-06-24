import { useState } from 'react';
import { ChevronDown, EyeOff } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import MatnDiff from '@/apps/hadith/components/MatnDiff';
import MatnTranslateControls from '@/apps/hadith/components/MatnTranslateControls';
import HideToggle from '@/apps/hadith/components/curation/HideToggle';
import CurationFieldsPanel, {
  type CurationFieldSpec,
} from '@/apps/hadith/components/curation/CurationFieldsPanel';
import { hasWordDiff } from '@/apps/hadith/utils/matnDiff';
import type { AuthRole } from '@/shared/stores/authStore';
import type { MatnDto } from '@/apps/hadith/types';

/**
 * Одна вариация matn — сворачиваемая карточка. Шапка (источник +
 * метаданные) всегда видна и тогглит тело; основная редакция раскрыта
 * по умолчанию, остальные свёрнуты — так список вариаций не превращается
 * в «стену текста».
 */
function MatnItem({
  matn,
  primary,
  hideTranslate,
  role,
  onChanged,
}: {
  matn: MatnDto;
  primary: MatnDto | null;
  /** Контролы перевода уже показаны у hero-матна страницы — не дублируем. */
  hideTranslate?: boolean;
  /** Роль зрителя — гейт ADMIN record-hide + правки полей (курация Фаза 5). */
  role: string | undefined;
  /** Рефетч detail после скрытия/правки записи. */
  onChanged: () => void;
}) {
  const t = useT();
  const [open, setOpen] = useState(matn.isPrimary);
  const [showDiff, setShowDiff] = useState(false);
  // diff доступен только для НЕ-основной редакции с реальным пословным
  // расхождением (отличие лишь в огласовках → кнопку не показываем)
  const canDiff = !matn.isPrimary && primary !== null && hasWordDiff(primary.textAr, matn.textAr);

  // Превью первой строки для свёрнутого состояния (быстрая идентификация).
  const preview = matn.textAr.slice(0, 80);

  // §5-редактируемые поля вариации (ADMIN inline-правка, курация Фаза 5).
  // text_ar НЕ редактируем (first-source); text_ru/text_en — через
  // MatnTranslateControls (C9), не дублируем.
  const editFields: CurationFieldSpec[] = [
    { label: t('hadith.curation.field.printed_number'), fieldName: 'printed_number', value: matn.printedNumber, kind: 'number' },
    { label: t('hadith.curation.field.page_no'), fieldName: 'page_no', value: matn.pageNo, kind: 'number' },
    { label: t('hadith.curation.field.volume'), fieldName: 'volume', value: matn.volume, kind: 'number' },
    { label: t('hadith.curation.field.divergence_summary'), fieldName: 'divergence_summary', value: matn.divergenceSummary, kind: 'text' },
  ];

  return (
    <Card className={`overflow-hidden ${matn.hiddenByAdmin ? 'opacity-50' : ''}`}>
      {/* ADMIN record-hide (курация Фаза 5): пилюля причины + тогл показать/скрыть. */}
      {(matn.hiddenByAdmin || role) && (
        <div className="flex flex-wrap items-center justify-between gap-2 px-4 pt-3">
          {matn.hiddenByAdmin ? (
            <div className="inline-flex items-center gap-1.5 rounded-sm bg-rose-50 px-1.5 py-0.5 text-xs text-rose-700">
              <EyeOff size={12} aria-hidden />
              <span dir="auto">
                {t('hadith.curation.hidden_by_admin')}
                {matn.hideReason ? `: ${matn.hideReason}` : ''}
              </span>
            </div>
          ) : (
            <span />
          )}
          <HideToggle
            entityTable="hd_matns"
            entityId={matn.id}
            hiddenByAdmin={matn.hiddenByAdmin}
            hideReason={matn.hideReason}
            role={role}
            onChanged={onChanged}
          />
        </div>
      )}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-4 py-3 text-start hover:bg-ink-50"
      >
        <span className="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-xs text-ink-500">
          {matn.isPrimary && (
            <span className="rounded-sm bg-accent-50 px-1.5 py-0.5 font-semibold text-accent-700">
              {t('hadith.detail.primary')}
            </span>
          )}
          {matn.printedNumber != null && <span className="font-mono">№{matn.printedNumber}</span>}
          {matn.volume != null && (
            <span>
              {t('hadith.matn.vol')}
              {matn.volume}
            </span>
          )}
          {matn.pageNo != null && (
            <span>
              {t('hadith.matn.page')}
              {matn.pageNo}
            </span>
          )}
          {!open && (
            <span className="min-w-0 truncate font-arabic text-ink-400" dir="rtl">
              {preview}
            </span>
          )}
        </span>
        <ChevronDown
          size={16}
          aria-hidden
          className={`shrink-0 text-ink-400 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open && (
        <div className="border-t border-border px-4 pb-4 pt-3">
          {canDiff && (
            <div className="mb-2 flex justify-end">
              <button
                type="button"
                onClick={() => setShowDiff((v) => !v)}
                className="rounded-sm px-1 text-xs text-accent-700 hover:underline"
              >
                {showDiff ? t('hadith.matn.hide_diff') : t('hadith.matn.show_diff')}
              </button>
            </div>
          )}

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

          {!hideTranslate && (
            <MatnTranslateControls
              matnId={matn.id}
              textRu={matn.textRu}
              textEn={matn.textEn}
              role={role as AuthRole | null | undefined}
            />
          )}
          {matn.divergenceSummary && (
            <p className="mt-2 text-xs italic text-ink-500" dir="auto">
              {matn.divergenceSummary}
            </p>
          )}
        </div>
      )}
      <div className="px-4 pb-3">
        <CurationFieldsPanel
          entityTable="hd_matns"
          entityId={matn.id}
          fields={editFields}
          role={role}
          onChanged={onChanged}
        />
      </div>
    </Card>
  );
}

/**
 * Список вариаций matn хадиса. Каждая редакция — сворачиваемая карточка;
 * основная раскрыта, для остальных доступен пословный diff относительно
 * основной (toggle). Заголовок/счётчик секции владеет страница.
 */
function MatnVariations({
  matns,
  translateInHeroForId,
  role,
  onChanged,
}: {
  matns: MatnDto[];
  /** id матна, чьи переводы уже рендерит hero-секция страницы (без дубля). */
  translateInHeroForId?: string | null;
  /** Роль зрителя — гейт ADMIN record-hide + правки полей (курация Фаза 5). */
  role?: string | undefined;
  /** Рефетч detail после скрытия/правки записи (no-op по умолчанию). */
  onChanged?: () => void;
}) {
  const t = useT();
  const primary = matns.find((m) => m.isPrimary) ?? null;

  if (matns.length === 0) {
    return <p className="text-sm text-ink-500">{t('hadith.detail.no_matns')}</p>;
  }

  return (
    <ul className="space-y-3">
      {matns.map((m) => (
        <li key={m.id}>
          <MatnItem
            matn={m}
            primary={primary}
            hideTranslate={m.id === translateInHeroForId}
            role={role}
            onChanged={onChanged ?? (() => {})}
          />
        </li>
      ))}
    </ul>
  );
}

export default MatnVariations;
