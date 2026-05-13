import { X } from 'lucide-react';
import IconButton from '@/shared/components/ui/IconButton';
import StatusBadge from '@/shared/components/ui/StatusBadge';
import { NODE_TYPE_TOKENS, type NodeType, type NodeStatus } from '@/shared/utils/designTokens';
import { shortId } from '@/apps/argument-map/components/graph/nodeDetailsUtils';
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
}

/**
 * Правая боковая панель деталей узла. Тонкий orchestrator над четырьмя
 * sections (Content / Metadata / Citations / Revisions) - каждая
 * инкапсулирует свой state и API-логику, не загрязняет panel-уровень.
 *
 * QUESTION-узлы не имеют цитат (ADR-002) - Citations скрыты для них.
 */
function NodeDetailsPanel({ node, onClose, onUpdated, initialEditing = false }: Props) {
  const nodeType: NodeType = node.nodeType ?? 'CLAIM';
  const typeToken = NODE_TYPE_TOKENS[nodeType];
  const TypeIcon = typeToken.Icon;
  const status: NodeStatus = node.status ?? 'UNVERIFIED';
  const content = node.content ?? '';

  return (
    <aside
      role="complementary"
      aria-label="Детали узла"
      className="absolute right-0 top-0 bottom-0 z-10 flex w-[400px] flex-col border-l border-slate-200 bg-white shadow-xl"
    >
      <header
        className={`relative border-b border-slate-200 bg-gradient-to-b ${typeToken.headerGradient} p-5`}
      >
        <div className="absolute right-3 top-3">
          <IconButton icon={X} label="Закрыть панель" size="sm" onClick={onClose} />
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`grid h-8 w-8 place-items-center rounded-md ${typeToken.iconBg} ${typeToken.iconText}`}
          >
            <TypeIcon size={16} aria-hidden="true" />
          </span>
          <div className="flex flex-col">
            <h2 className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              {typeToken.key} · {typeToken.label}
            </h2>
            <span className="font-mono text-[12px] text-slate-400">{shortId(node.id)}</span>
          </div>
        </div>
        <div className="mt-3 flex items-center gap-2">
          <StatusBadge status={status} size="lg" />
        </div>
      </header>

      <div className="flex-1 overflow-y-auto">
        <NodeContentEditor
          nodeId={node.id}
          content={content}
          initialEditing={initialEditing}
          onSaved={onUpdated}
        />
        <NodeMetadataSection node={node} />
        {nodeType !== 'QUESTION' && (
          <NodeCitationsSection nodeId={node.id} nodeContent={node.content ?? ''} />
        )}
        <NodeRevisionsSection nodeId={node.id} />
      </div>
    </aside>
  );
}

export default NodeDetailsPanel;
