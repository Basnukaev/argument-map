import { Handle, Position } from '@xyflow/react';
import { BookOpen } from 'lucide-react';
import { useT } from '@/shared/i18n';
import type { VersionFlowNodeData } from '@/apps/hadith/types';

interface Props {
  data: VersionFlowNodeData;
}

/**
 * Version-узел графа иснада — карточка-«книга» параллельной передачи (конец
 * цепи = запись в сборнике). Клик-навигация на detail той передачи живёт в
 * SanadGraph.onNodeClick; «свой» узел (isCurrent) помечается «вы здесь» и
 * визуально приглушён (не-кликабелен). У таких узлов нет нижнего source-handle
 * — это конец цепи.
 */
function VersionGraphNode({ data }: Props) {
  const t = useT();

  return (
    <>
      <Handle type="target" position={Position.Top} className="!h-2 !w-2 !border-0 !bg-sky-300" />
      <div
        className={`w-[240px] overflow-hidden rounded-lg border bg-sky-50 shadow-sh1 ${
          data.isCurrent
            ? 'border-sky-400 ring-2 ring-sky-200'
            : 'border-sky-300 hover:border-sky-500'
        }`}
      >
        <div className="px-3 py-2">
          <div className="flex items-start gap-2">
            <BookOpen size={16} className="mt-0.5 shrink-0 text-sky-600" aria-hidden />
            <div className="min-w-0 flex-1">
              {data.collectionNameAr && (
                <div className="font-arabic text-base leading-tight text-sky-900" dir="rtl">
                  {data.collectionNameAr}
                </div>
              )}
              {data.collectionNameRu && (
                <div className="mt-0.5 truncate text-xs text-sky-700">{data.collectionNameRu}</div>
              )}
            </div>
            {data.printedNumber != null && (
              <span className="shrink-0 font-mono text-[11px] text-sky-600">
                №{data.printedNumber}
              </span>
            )}
          </div>
          {data.matnPreview && (
            <p
              className="mt-1.5 line-clamp-2 font-arabic text-xs leading-snug text-sky-700/80"
              dir="rtl"
            >
              {data.matnPreview}
            </p>
          )}
          {data.isCurrent && (
            <div className="mt-1.5 inline-flex rounded-sm bg-sky-100 px-1.5 py-0.5 text-[10px] font-medium text-sky-700">
              {t('hadith.graph.you_are_here')}
            </div>
          )}
        </div>
      </div>
    </>
  );
}

export default VersionGraphNode;
