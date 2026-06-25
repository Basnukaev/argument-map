import { memo, useState } from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  type Edge,
  type EdgeProps,
} from '@xyflow/react';
import { Loader2, Pencil } from 'lucide-react';
import { pickLabelPosition, type Point } from '@/apps/argument-map/utils/orthogonalPath';
import { visibleTransmissionPhrase } from '@/apps/hadith/utils/sanadEdge';
import { apiPatchRaw, formatApiError } from '@/shared/api/client';
import { toast } from '@/shared/stores/toastStore';
import { useT } from '@/shared/i18n';
import { hasRoleAtLeast, type AuthRole } from '@/shared/stores/authStore';

/** Прямой ортогональный path (90°-углы без скругления) по точкам ELK. */
function buildSharpOrthogonalPath(points: ReadonlyArray<Point>): string {
  if (points.length < 2) return '';
  const [first, ...rest] = points;
  return `M ${first!.x} ${first!.y} ` + rest.map((p) => `L ${p.x} ${p.y}`).join(' ');
}

export type SanadEdgeData = {
  /** Арабская формула передачи (حدثنا / عن / أخبرنا …) — подпись на ребре. */
  transmissionPhrase?: string;
  /**
   * ПОЛНАЯ ортогональная полилиния из ELK (startPoint→bends→endPoint, в flow-
   * координатах). Заполнена после async-раскладки; до неё undefined → fallback
   * на getSmoothStepPath. Рисуем строго по ней (не склеиваем с RF-хэндлом) —
   * иначе веер из узла даёт диагонали (С64).
   */
  points?: Array<{ x: number; y: number }>;
  /** Подсветка цепи по клику: приглушить чужие подписи (линия — через style.opacity). */
  dimmed?: boolean;
  /**
   * Курация Фаза 5.b — ADMIN inline-правка формулы передачи звена. Чип становится
   * кликабельным при наличии hadithId + position (звено иснада, не version-ребро)
   * + ADMIN-роли. Save → PATCH /sanad-narrators/transmission-phrase → onGraphEdited
   * рефетчит граф. Прокидываются страницей в data при сборке рёбер (SanadGraph).
   */
  hadithId?: string;
  /** Позиция звена-приёмника (0 = сподвижник); null/undefined у version-рёбер. */
  position?: number | null;
  /** admin-индикатор «формула отредактирована» (reveal, только ADMIN). */
  overridden?: boolean;
  /** Роль пользователя — гейт ADMIN-правки чипа. undefined = аноним. */
  role?: string;
  /** Рефетч графа после правки формулы (родитель владеет fetch'ем). */
  onGraphEdited?: () => void;
};

/**
 * Курация Фаза 5.b — подпись-чип формулы передачи. Не-ADMIN (или version-ребро
 * без звена) — read-only текст. ADMIN на звене — карандаш → инлайн-редактор
 * (input + Сохранить/Отмена) → PATCH {hadithId, position, phrase} →
 * onGraphEdited() рефетчит граф. Индикатор «отредактировано» — точка-маркер.
 *
 * Вынесен из SanadEdge отдельным компонентом: рендерится в EdgeLabelRenderer
 * (RF-портал), который не меряется в jsdom — тестируем чип в изоляции.
 */
export function TransmissionPhraseChip({
  phrase,
  dimmed,
  hadithId,
  position,
  overridden,
  role,
  onGraphEdited,
}: {
  phrase: string;
  dimmed: boolean;
  hadithId?: string;
  position?: number | null;
  overridden?: boolean;
  role?: string;
  onGraphEdited?: () => void;
}) {
  const t = useT();
  const isAdmin = hasRoleAtLeast(role as AuthRole | undefined, 'ADMIN');
  // Правка доступна лишь у звена иснада (есть hadithId + position) для ADMIN
  // с колбэком рефетча. version-/merge-рёбра (position null) не редактируются.
  const canEdit =
    isAdmin && hadithId != null && position != null && onGraphEdited != null;

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);

  function startEdit(): void {
    setDraft(phrase);
    setEditing(true);
  }

  function cancelEdit(): void {
    setEditing(false);
    setDraft('');
  }

  async function save(): Promise<void> {
    if (saving || hadithId == null || position == null) return;
    const next = draft.trim();
    if (next === '') {
      toast.error(t('hadith.curation.save_failed'));
      return;
    }
    setSaving(true);
    try {
      await apiPatchRaw('/api/v1/hadith/sanad-narrators/transmission-phrase', {
        hadithId,
        position,
        phrase: next,
      });
      setEditing(false);
      setDraft('');
      toast.success(t('hadith.curation.saved'));
      onGraphEdited?.();
    } catch (error) {
      toast.error(formatApiError(error, t('hadith.curation.save_failed')));
    } finally {
      setSaving(false);
    }
  }

  if (editing) {
    return (
      <div className="pointer-events-auto inline-flex items-center gap-1 rounded-[4px] border border-accent-500 bg-elevated px-1 py-0.5 shadow-sh1">
        <input
          dir="rtl"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          aria-label={t('hadith.curation.edit_field')}
          className="w-20 rounded-sm border border-border bg-bg px-1 py-0.5 font-arabic text-[13px] text-ink-800 focus:border-accent-500 focus:outline-none focus:ring-1 focus:ring-accent-500"
        />
        <button
          type="button"
          disabled={saving}
          onClick={() => void save()}
          className="inline-flex items-center gap-0.5 rounded-sm border border-accent-500 bg-accent-50 px-1 py-0.5 text-[11px] font-medium text-accent-700 hover:bg-accent-100 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving && <Loader2 size={10} className="animate-spin" aria-hidden />}
          {t('common.save')}
        </button>
        <button
          type="button"
          disabled={saving}
          onClick={cancelEdit}
          className="inline-flex items-center rounded-sm border border-border px-1 py-0.5 text-[11px] font-medium text-ink-600 hover:bg-ink-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {t('common.cancel')}
        </button>
      </div>
    );
  }

  return (
    <div
      dir="rtl"
      className={`inline-flex items-center gap-1 rounded-[4px] border border-border-strong bg-elevated px-1 font-arabic text-[13px] font-semibold leading-tight text-ink-700 shadow-sh1 ${
        canEdit ? 'pointer-events-auto' : 'pointer-events-none'
      }`}
      style={{ opacity: dimmed ? 0.12 : 1 }}
    >
      <span>{phrase}</span>
      {/* §5.b admin-индикатор «отредактировано» — точка-маркер. */}
      {overridden && (
        <span
          className="inline-block h-1.5 w-1.5 shrink-0 rounded-full bg-accent-500"
          title={t('hadith.curation.fields_title')}
          aria-label={t('hadith.curation.fields_title')}
        />
      )}
      {canEdit && (
        <button
          type="button"
          onClick={startEdit}
          aria-label={t('hadith.curation.edit_field')}
          title={t('hadith.curation.edit_field')}
          className="rounded-sm p-0.5 text-ink-400 hover:bg-ink-50 hover:text-ink-600"
        >
          <Pencil size={11} aria-hidden />
        </button>
      )}
    </div>
  );
}

export type SanadCustomEdgeType = Edge<SanadEdgeData, 'sanad'>;

/**
 * Ребро графа иснада с ортогональной маршрутизацией по ELK bend-points.
 * Рисует path строго по изломам, которые ELK проложил ОГИБАЯ карточки
 * (Проблема 1 Абдулы), подпись-формулу ставит на середину самого длинного
 * сегмента — гарантированно не на узле (Проблема 3).
 *
 * Линия (stroke/strokeWidth/opacity) приходит через `style` — там же живёт
 * подсветка цепи по клику (SanadGraph highlight useMemo). Подпись приглушается
 * отдельно через `data.dimmed`, т.к. рендерится не в SVG, а в EdgeLabelRenderer.
 */
function SanadEdge(props: EdgeProps<SanadCustomEdgeType>) {
  const {
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    data,
    style,
    markerEnd,
  } = props;

  const points = data?.points;
  // Только НЕ-пустая (не-пробельная) формула рисует подпись-чип — см.
  // visibleTransmissionPhrase (пустой чип → «чёрный квадратик» в PNG-экспорте).
  const phrase = visibleTransmissionPhrase(data?.transmissionPhrase);
  const dimmed = data?.dimmed ?? false;

  let edgePath: string;
  let labelX: number;
  let labelY: number;

  if (points && points.length >= 2) {
    // Концы привязываем к РЕАЛЬНЫМ хэндлам (sourceY/targetY): ELK считает высоту
    // узла = NODE_HEIGHT (108), а карточка отрисована ниже → ELK startPoint висел
    // под нижней гранью, давая зазор (С64). X берём из ELK (порт/маршрут), Y
    // концов — из RF. Первый сегмент остаётся вертикальным (тот же port-X).
    const snapped = points.map((p, i) =>
      i === 0
        ? { x: p.x, y: sourceY }
        : i === points.length - 1
          ? { x: p.x, y: targetY }
          : p,
    );
    // Прямые 90°-углы строго по ELK-маршруту.
    edgePath = buildSharpOrthogonalPath(snapped);
    const pos = pickLabelPosition(snapped);
    labelX = pos.x;
    labelY = pos.y;
  } else {
    [edgePath, labelX, labelY] = getSmoothStepPath({
      sourceX,
      sourceY,
      sourcePosition,
      targetX,
      targetY,
      targetPosition,
      borderRadius: 0,
    });
  }

  return (
    <>
      <BaseEdge path={edgePath} markerEnd={markerEnd} style={style} interactionWidth={24} />
      {phrase && (
        <EdgeLabelRenderer>
          <div
            className="absolute"
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            }}
          >
            <TransmissionPhraseChip
              phrase={phrase}
              dimmed={dimmed}
              hadithId={data?.hadithId}
              position={data?.position}
              overridden={data?.overridden}
              role={data?.role}
              onGraphEdited={data?.onGraphEdited}
            />
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

export default memo(SanadEdge);
