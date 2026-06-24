import { useState } from 'react';
import { ChevronDown, EyeOff } from 'lucide-react';
import Card from '@/shared/components/ui/Card';
import { useT } from '@/shared/i18n';
import HideToggle from '@/apps/hadith/components/curation/HideToggle';
import CurationFieldsPanel, {
  type CurationFieldSpec,
} from '@/apps/hadith/components/curation/CurationFieldsPanel';
import type { ExplanationDto } from '@/apps/hadith/types';

/** Вариант карточки = семантика секции. SHARH/ILAL делят раскладку «книга/автор
 *  + сворачиваемый текст»; GHARIB ставит слово (reference) заголовком. */
type ExplanationVariant = 'SHARH' | 'ILAL' | 'GHARIB';

/** Пилюля «Скрыто администратором: причина» (курация 4.b) — общая для обоих
 *  вариантов карточки толкования. */
function HiddenPill({ reason }: { reason: string | null }) {
  const t = useT();
  return (
    <div className="inline-flex items-center gap-1.5 rounded-sm bg-rose-50 px-1.5 py-0.5 text-xs text-rose-700">
      <EyeOff size={12} aria-hidden />
      <span dir="auto">
        {t('hadith.curation.hidden_by_admin')}
        {reason ? `: ${reason}` : ''}
      </span>
    </div>
  );
}

/** §5-редактируемые поля толкования (ADMIN inline-правка, курация Фаза 5).
 *  `text`/`author`/book/page/volume — общие для SHARH/ILAL/GHARIB. */
function editFieldsFor(exp: ExplanationDto, t: ReturnType<typeof useT>): CurationFieldSpec[] {
  return [
    { label: t('hadith.curation.field.explanation_text'), fieldName: 'text', value: exp.text, kind: 'text' },
    { label: t('hadith.curation.field.author'), fieldName: 'author', value: exp.author, kind: 'text' },
    { label: t('hadith.curation.field.author_death_year'), fieldName: 'author_death_year', value: null, kind: 'number' },
    { label: t('hadith.curation.field.book_name'), fieldName: 'book_name', value: exp.bookName, kind: 'text' },
    { label: t('hadith.curation.field.page'), fieldName: 'page', value: exp.page, kind: 'number' },
    { label: t('hadith.curation.field.volume'), fieldName: 'volume', value: exp.volume, kind: 'number' },
  ];
}

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
function BookHeadedItem({
  exp,
  role,
  onChanged,
}: {
  exp: ExplanationDto;
  role: string | undefined;
  onChanged: () => void;
}) {
  const t = useT();
  const [open, setOpen] = useState(false);

  const heading = [exp.bookName, exp.author].filter((p): p is string => Boolean(p)).join(' — ');
  const cite = citeLine(exp, t);

  return (
    <Card className={`overflow-hidden ${exp.hiddenByAdmin ? 'opacity-50' : ''}`}>
      {(exp.hiddenByAdmin || role) && (
        <div className="flex flex-wrap items-center justify-between gap-2 px-4 pt-3">
          {exp.hiddenByAdmin ? <HiddenPill reason={exp.hideReason} /> : <span />}
          <HideToggle
            entityTable="hd_explanations"
            entityId={exp.id}
            hiddenByAdmin={exp.hiddenByAdmin}
            hideReason={exp.hideReason}
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
      <div className="px-4 pb-3">
        <CurationFieldsPanel
          entityTable="hd_explanations"
          entityId={exp.id}
          fields={editFieldsFor(exp, t)}
          role={role}
          onChanged={onChanged}
        />
      </div>
    </Card>
  );
}

/**
 * Карточка GHARIB — заголовок = редкое СЛОВО из матна (reference): RTL, naskh,
 * крупно, акцентный цвет. Рядом словарь·автор (напр. النهاية في غريب الحديث ·
 * ابن الأثير) + цитата. Тело (толкование) сворачиваемое. reference null →
 * фолбэк на book/author-заголовок (рендерим BookHeadedItem).
 */
function GharibItem({
  exp,
  role,
  onChanged,
}: {
  exp: ExplanationDto;
  role: string | undefined;
  onChanged: () => void;
}) {
  const t = useT();
  const [open, setOpen] = useState(false);

  if (!exp.reference) return <BookHeadedItem exp={exp} role={role} onChanged={onChanged} />;

  const dict = [exp.bookName, exp.author].filter((p): p is string => Boolean(p)).join(' · ');
  const cite = citeLine(exp, t);

  return (
    <Card className={`overflow-hidden ${exp.hiddenByAdmin ? 'opacity-50' : ''}`}>
      {(exp.hiddenByAdmin || role) && (
        <div className="flex flex-wrap items-center justify-between gap-2 px-4 pt-3">
          {exp.hiddenByAdmin ? <HiddenPill reason={exp.hideReason} /> : <span />}
          <HideToggle
            entityTable="hd_explanations"
            entityId={exp.id}
            hiddenByAdmin={exp.hiddenByAdmin}
            hideReason={exp.hideReason}
            role={role}
            onChanged={onChanged}
          />
        </div>
      )}
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
      <div className="px-4 pb-3">
        <CurationFieldsPanel
          entityTable="hd_explanations"
          entityId={exp.id}
          fields={editFieldsFor(exp, t)}
          role={role}
          onChanged={onChanged}
        />
      </div>
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
  role,
  onChanged,
}: {
  explanations: ExplanationDto[];
  variant?: ExplanationVariant;
  /** Роль зрителя — гейт ADMIN record-hide (курация 4.b). */
  role: string | undefined;
  /** Рефетч detail после скрытия/показа записи. */
  onChanged: () => void;
}) {
  return (
    <ul className="space-y-3">
      {explanations.map((e) => (
        <li key={e.id}>
          {variant === 'GHARIB' ? (
            <GharibItem exp={e} role={role} onChanged={onChanged} />
          ) : (
            <BookHeadedItem exp={e} role={role} onChanged={onChanged} />
          )}
        </li>
      ))}
    </ul>
  );
}

export default ExplanationsList;
