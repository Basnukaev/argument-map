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
      {/* dt-метки следуют локали интерфейса. dd-значения держим в LTR через
          <bdi>: дата формата ru-RU, латиничный UUID, цифры - всё LTR-данные.
          Без bdi в RTL Unicode Bidi Algorithm склеивает цифры и пунктуацию
          с окружающими RTL-символами, и «8 мая 2026 г.» становится «мая 8» */}
      <dl className="grid grid-cols-[100px_1fr] gap-x-3 gap-y-2 text-[12px]">
        <dt className="text-slate-500">Создан</dt>
        <dd className="text-slate-700">
          <bdi dir="ltr">{formatDate(node.createdAt)}</bdi>
        </dd>

        {wasUpdated && (
          <>
            <dt className="text-slate-500">Обновлён</dt>
            <dd className="text-slate-700">
              <bdi dir="ltr">{formatDate(node.updatedAt)}</bdi>
            </dd>
          </>
        )}

        <dt className="text-slate-500">Автор</dt>
        <dd className="font-mono text-slate-700" title={node.createdBy}>
          <bdi dir="ltr">{shortId(node.createdBy)}</bdi>
        </dd>

        <dt className="text-slate-500">ID</dt>
        <dd className="font-mono text-slate-700" title={node.id}>
          <bdi dir="ltr">{shortId(node.id)}</bdi>
        </dd>
      </dl>
    </PanelSection>
  );
}

export default NodeMetadataSection;
