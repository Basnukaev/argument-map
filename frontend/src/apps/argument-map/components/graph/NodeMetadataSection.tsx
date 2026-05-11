import { Info } from 'lucide-react';
import PanelSection from '@/apps/argument-map/components/graph/PanelSection';
import { formatDate, shortId } from '@/apps/argument-map/components/graph/nodeDetailsUtils';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  node: NodeDto;
}

function NodeMetadataSection({ node }: Props) {
  const wasUpdated = node.updatedAt && node.createdAt && node.updatedAt !== node.createdAt;

  return (
    <PanelSection icon={Info} title="Метаданные" defaultOpen>
      <dl className="grid grid-cols-[100px_1fr] gap-x-3 gap-y-2 text-[12px]">
        <dt className="text-slate-500">Создан</dt>
        <dd className="text-slate-700">{formatDate(node.createdAt)}</dd>

        {wasUpdated && (
          <>
            <dt className="text-slate-500">Обновлён</dt>
            <dd className="text-slate-700">{formatDate(node.updatedAt)}</dd>
          </>
        )}

        <dt className="text-slate-500">Автор</dt>
        <dd className="font-mono text-slate-700" title={node.createdBy}>
          {shortId(node.createdBy)}
        </dd>

        <dt className="text-slate-500">ID</dt>
        <dd className="font-mono text-slate-700" title={node.id}>
          {shortId(node.id)}
        </dd>
      </dl>
    </PanelSection>
  );
}

export default NodeMetadataSection;
