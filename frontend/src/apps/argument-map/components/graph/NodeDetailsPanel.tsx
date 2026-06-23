import { useState } from 'react';
import { Anchor, ArrowLeft, BookOpen, Quote, X } from 'lucide-react';
import IconButton from '@/shared/components/ui/IconButton';
import StatusBadge from '@/shared/components/ui/StatusBadge';
import { NODE_TYPE_TOKENS, type NodeType, type NodeStatus } from '@/shared/utils/designTokens';
import { shortId } from '@/apps/argument-map/components/graph/nodeDetailsUtils';
import { useT } from '@/shared/i18n';
import { useIsMobile } from '@/shared/hooks/useViewport';
import NodeContentEditor from '@/apps/argument-map/components/graph/NodeContentEditor';
import NodeMetadataSection from '@/apps/argument-map/components/graph/NodeMetadataSection';
import NodeCitationsSection from '@/apps/argument-map/components/graph/NodeCitationsSection';
import NodeRevisionsSection from '@/apps/argument-map/components/graph/NodeRevisionsSection';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  node: NodeDto;
  onClose: () => void;
  /** вызывается после успешного PATCH - чтобы родитель refetch'нул граф */
  onUpdated: () => void;
  /** если true - "Содержание" сразу открывается в режиме редактирования */
  initialEditing?: boolean;
  /** FB-2: гость/не-EDITOR — панель read-only (без edit-контента и add-цитат). */
  canWrite?: boolean;
}

/**
 * Боковая панель деталей узла. Тонкий orchestrator над четырьмя
 * sections (Content / Metadata / Citations / Revisions) - каждая
 * инкапсулирует свой state и API-логику, не загрязняет panel-уровень.
 *
 * QUESTION-узлы не имеют цитат (ADR-002) - Citations скрыты для них.
 *
 * **Responsive (Фаза 1 Сессия 39):**
 * - Desktop (md+): 400px right-side panel поверх графа
 * - Mobile (<md): full-screen overlay (fixed inset-0) - на mobile
 *   400px panel занимает весь viewport и блокирует граф;
 *   fullscreen с back-arrow в header делает чтение и редактирование
 *   узла независимой задачей. Закрыл → видишь граф
 */
function NodeDetailsPanel({ node, onClose, onUpdated, initialEditing = false, canWrite = true }: Props) {
  const t = useT();
  const isMobile = useIsMobile();
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const typeToken = NODE_TYPE_TOKENS[nodeType];
  const TypeIcon = typeToken.Icon;
  const status: NodeStatus = node.status ?? 'UNVERIFIED';
  const content = node.content ?? '';
  const [citationCounts, setCitationCounts] = useState<{ lib: number; free: number } | null>(null);
  const totalCitations = citationCounts ? citationCounts.lib + citationCounts.free : null;

  // На mobile - fullscreen z-50 (выше React Flow controls), без border.
  // Desktop - 400px right panel
  const containerClass = isMobile
    ? 'fixed inset-0 z-50 flex flex-col bg-elevated'
    : 'absolute end-0 top-0 bottom-0 z-10 flex w-[400px] flex-col border-s border-border bg-elevated shadow-sh3';

  const CloseIcon = isMobile ? ArrowLeft : X;
  const closeLabel = isMobile ? t('common.back') : t('common.close');

  return (
    <aside
      role="complementary"
      aria-label={t('graph.details_aria_node')}
      className={containerClass}
    >
      <header
        className={`relative border-b border-border ${typeToken.headerBg} p-5`}
      >
        <div className="absolute end-3 top-3">
          <IconButton icon={CloseIcon} label={closeLabel} size="sm" onClick={onClose} />
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`grid h-8 w-8 place-items-center rounded-md ${typeToken.iconBg} ${typeToken.iconText}`}
          >
            <TypeIcon size={16} aria-hidden="true" />
          </span>
          <div className="flex flex-col">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-ink-500">
              {typeToken.key} · {t(typeToken.labelKey)}
            </h2>
            <span className="font-mono text-xs text-ink-400">
              <bdi dir="ltr">{shortId(node.id)}</bdi>
            </span>
          </div>
        </div>
        <div className="mt-3 flex items-center gap-2">
          <StatusBadge status={status} size="lg" />
        </div>
        {nodeType !== 'QUESTION' && totalCitations !== null && totalCitations > 0 && (
          <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-700">
            <span className="inline-flex items-center gap-1.5">
              <Anchor size={14} className="text-accent-600" aria-hidden="true" />
              <span className="font-mono font-semibold">{totalCitations}</span>
              <span>{t('node.support_short')}</span>
            </span>
            <span className="text-ink-300">(</span>
            <span className="inline-flex items-center gap-1" title={t('node.from_library')}>
              <BookOpen size={13} className="text-accent-600" aria-hidden="true" />
              <span className="font-mono">{citationCounts!.lib}</span>
            </span>
            <span className="text-ink-300">·</span>
            <span className="inline-flex items-center gap-1" title={t('node.freeform_short')}>
              <Quote size={13} className="text-ink-500" aria-hidden="true" />
              <span className="font-mono">{citationCounts!.free}</span>
            </span>
            <span className="text-ink-300">)</span>
          </div>
        )}
      </header>

      <div className="flex-1 overflow-y-auto">
        <NodeContentEditor
          nodeId={node.id}
          content={content}
          initialEditing={initialEditing}
          onSaved={onUpdated}
          inlineCitations={node.inlineCitations}
          canWrite={canWrite}
        />
        <NodeMetadataSection node={node} />
        {nodeType !== 'QUESTION' && (
          <NodeCitationsSection
            nodeId={node.id}
            nodeContent={node.content ?? ''}
            onCountsChange={setCitationCounts}
            canWrite={canWrite}
          />
        )}
        <NodeRevisionsSection nodeId={node.id} />
      </div>
    </aside>
  );
}

export default NodeDetailsPanel;
