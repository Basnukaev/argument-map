import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ReactFlowProvider } from '@xyflow/react';
import VersionGraphNode from './VersionGraphNode';
import type { VersionFlowNodeData } from '@/apps/hadith/types';

/**
 * Рендер карточки-«книги» version-узла. ReactFlowProvider обязателен —
 * узел использует Handle (как SanadGraphNode.test).
 */
const BASE: VersionFlowNodeData = {
  role: 'VERSION',
  hadithId: 'h-other',
  externalId: '200-1',
  collectionSlug: 'muslim',
  collectionNameAr: 'صحيح مسلم',
  collectionNameRu: 'Сахих Муслим',
  printedNumber: 1907,
  matnPreview: 'الأعمال بالنية',
  isCurrent: false,
};

function renderNode(data: VersionFlowNodeData) {
  return render(
    <ReactFlowProvider>
      <VersionGraphNode data={data} />
    </ReactFlowProvider>,
  );
}

describe('VersionGraphNode', () => {
  it('показывает арабское/русское имя сборника, номер и превью матна', () => {
    renderNode(BASE);
    expect(screen.getByText('صحيح مسلم')).toBeInTheDocument();
    expect(screen.getByText('Сахих Муслим')).toBeInTheDocument();
    expect(screen.getByText('№1907')).toBeInTheDocument();
    expect(screen.getByText('الأعمال بالنية')).toBeInTheDocument();
  });

  it('чужой узел (isCurrent=false) НЕ помечен «вы здесь»', () => {
    renderNode(BASE);
    expect(screen.queryByText('вы здесь')).not.toBeInTheDocument();
  });

  it('свой узел (isCurrent=true) помечен «вы здесь»', () => {
    renderNode({ ...BASE, isCurrent: true });
    expect(screen.getByText('вы здесь')).toBeInTheDocument();
  });
});
