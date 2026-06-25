import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { BookOpen, Pencil, Star } from 'lucide-react';
import { useT, type DictKey } from '@/shared/i18n';
import { RELIABILITY_TOKENS, ROLE_STRIP } from '@/apps/hadith/sanadTokens';
import type { SanadGraphNodeData } from '@/apps/hadith/types';
import VersionGraphNode from './VersionGraphNode';

export type SanadNode = Node<SanadGraphNodeData, 'sanad'>;

/**
 * Узел графа иснада — карточка передатчика. Read-only: handle'ы только
 * для рисования рёбер (верх = откуда получил, низ = кому передал),
 * перетаскивание отключено на уровне ReactFlow.
 *
 * Узел Пророка ﷺ рендерится особо (зелёная рамка, без оценки надёжности —
 * источник вне шкалы джарх ва тадиль). Version-узел (конец цепи = запись в
 * сборнике) делегируется в VersionGraphNode (data у него null).
 */
function SanadGraphNode({ data, selected }: NodeProps<SanadNode>) {
  const t = useT();

  if (data.role === 'VERSION') {
    return <VersionGraphNode data={data} />;
  }

  const rel = data.reliabilityGrade ? RELIABILITY_TOKENS[data.reliabilityGrade] : null;

  if (data.role === 'PROPHET') {
    return (
      <>
        <div className="w-[240px] rounded-xl border-2 border-emerald-400 bg-emerald-50 px-4 py-3 text-center shadow-sh2">
          {/* leading-loose: имя Пророка густо огласовано — при tight line-height
              харакаты вылезали в верхний паддинг, и зазор сверху/снизу был
              неодинаковым (С64). */}
          <div className="font-arabic text-xl leading-loose text-emerald-900" dir="rtl">
            {data.nameAr}
          </div>
          {data.nameRu && (
            <div className="text-xs font-medium text-emerald-700">{data.nameRu}</div>
          )}
        </div>
        <Handle
          type="source"
          position={Position.Bottom}
          className="!h-2 !w-2 !border-0 !bg-emerald-400"
        />
      </>
    );
  }

  const isCollector = data.role === 'COLLECTOR';
  const isCompanion = data.role === 'COMPANION';
  // Курация Фаза 5.b: admin-индикатор «отредактировано» — узел несёт непустой
  // overriddenFields (заполнен бэком только для ADMIN). Тонкий карандаш в углу.
  const isEdited = (data.overriddenFields?.length ?? 0) > 0;

  return (
    <>
      <Handle type="target" position={Position.Top} className="!h-2 !w-2 !border-0 !bg-ink-300" />
      <div
        className={`relative w-[240px] overflow-hidden rounded-lg border bg-elevated shadow-sh1 ${
          selected ? 'border-accent-500 ring-2 ring-accent-300' : 'border-border-strong'
        }`}
      >
        {isEdited && (
          <span
            className="absolute end-1 top-1 inline-flex h-4 w-4 items-center justify-center rounded-full bg-accent-50 text-accent-600"
            title={t('hadith.curation.fields_title')}
            aria-label={t('hadith.curation.fields_title')}
          >
            <Pencil size={9} aria-hidden />
          </span>
        )}
        <div className={`h-1 w-full ${ROLE_STRIP[data.role]}`} />
        {/* Центрированная компоновка (С64): имя по центру, оценка надёжности +
            поколение/смерть — в одной центрированной мета-строке. Раньше бейдж
            ثقة в justify-between смещал имя влево. */}
        <div className="px-3 py-2 text-center">
          <div className="font-arabic text-lg leading-snug text-ink-900" dir="rtl">
            {data.nameAr}
          </div>
          {data.nameRu && <div className="mt-0.5 text-xs text-ink-600">{data.nameRu}</div>}
          <div className="mt-1.5 flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-[11px] text-ink-500">
            {rel && data.reliabilityGrade && (
              <span
                className={`rounded-sm px-1.5 py-0.5 font-arabic text-[12px] font-semibold ${rel.chip}`}
                dir="rtl"
                title={t(`hadith.reliability.${data.reliabilityGrade}` as DictKey)}
              >
                {rel.ar}
              </span>
            )}
            {data.generation && (
              <span className="inline-flex items-center gap-1">
                {isCompanion && <Star size={10} className="text-violet-500" aria-hidden />}
                {data.generation}
              </span>
            )}
            {data.yearDeathHijri != null && (
              <span>
                {t('hadith.graph.died')} {data.yearDeathHijri} {t('hadith.graph.hijri')}
              </span>
            )}
          </div>
          {isCollector && data.collection && (
            <div className="mt-2 inline-flex items-center gap-1 rounded-sm bg-amber-50 px-1.5 py-0.5 text-[11px] font-medium text-amber-800">
              <BookOpen size={11} aria-hidden /> {data.collection}
            </div>
          )}
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} className="!h-2 !w-2 !border-0 !bg-ink-300" />
    </>
  );
}

export default SanadGraphNode;
