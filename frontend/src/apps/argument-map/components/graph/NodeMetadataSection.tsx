import { Info } from 'lucide-react';
import PanelSection from '@/apps/argument-map/components/graph/PanelSection';
import { shortId } from '@/apps/argument-map/components/graph/nodeDetailsUtils';
import { useFormatDate, useT } from '@/shared/i18n';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

interface Props {
  node: NodeDto;
}

function NodeMetadataSection({ node }: Props) {
  const t = useT();
  const formatDate = useFormatDate();
  const wasUpdated = node.updatedAt && node.createdAt && node.updatedAt !== node.createdAt;

  return (
    <PanelSection icon={Info} title={t('node.section.metadata')} defaultOpen>
      {/* dt-метки следуют локали интерфейса. dd-значения держим в LTR через
          <bdi>: латиничный UUID, числовые куски - всё LTR-данные. Без bdi
          в RTL Unicode Bidi Algorithm склеивает цифры с RTL-символами */}
      <dl className="grid grid-cols-[100px_1fr] gap-x-3 gap-y-2 text-[12px]">
        <dt className="text-slate-500">{t('node.created_at')}</dt>
        <dd className="text-slate-700">
          <bdi dir="ltr">{formatDate(node.createdAt)}</bdi>
        </dd>

        {wasUpdated && (
          <>
            <dt className="text-slate-500">{t('node.updated_at')}</dt>
            <dd className="text-slate-700">
              <bdi dir="ltr">{formatDate(node.updatedAt)}</bdi>
            </dd>
          </>
        )}

        <dt className="text-slate-500">{t('node.author')}</dt>
        <dd className="font-mono text-slate-700" title={node.createdBy}>
          <bdi dir="ltr">{shortId(node.createdBy)}</bdi>
        </dd>

        <dt className="text-slate-500">{t('node.id')}</dt>
        <dd className="font-mono text-slate-700" title={node.id}>
          <bdi dir="ltr">{shortId(node.id)}</bdi>
        </dd>
      </dl>
    </PanelSection>
  );
}

export default NodeMetadataSection;
