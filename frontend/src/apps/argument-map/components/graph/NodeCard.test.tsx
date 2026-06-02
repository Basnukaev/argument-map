import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ReactFlowProvider } from '@xyflow/react';
import NodeCard, { type NodeCardData } from './NodeCard';

/**
 * NodeCard теперь всегда рендерит только оригинальный content (двуязычный
 * режим выпилен). ReactFlowProvider обязателен потому что компонент
 * использует Handle. NodeProps в RF имеет много полей, для рендера
 * достаточно `data` + `selected` + `id` (остальное TS принимает через
 * unknown-cast - тесты НЕ проверяют RF integration, только наш JSX).
 *
 * Поле data.translations с бэка игнорируется - даже если оно присутствует,
 * NodeCard не падает и не показывает перевод.
 */

const BASE_DATA: NodeCardData = {
  id: 'node-1',
  topicId: 'topic-1',
  nodeType: 'EVIDENCE',
  content: 'إنما الأعمال بالنيات',
  status: 'STANDING',
  posX: 0,
  posY: 0,
  zIndex: 0,
  createdBy: 'user-1',
  createdAt: '2026-05-18T00:00:00Z',
  updatedAt: '2026-05-18T00:00:00Z',
  inlineCitations: [],
  translations: [],
};

function renderCard(data: NodeCardData) {
  // Жёсткий cast - NodeProps включает много RF-specific полей которые
  // не нужны для рендера NodeCard. Безопасно потому что компонент
  // использует только data + selected.
  const props = {
    id: data.id ?? 'node-1',
    data,
    selected: false,
    type: 'argumentNode',
    isConnectable: false,
    xPos: 0,
    yPos: 0,
    zIndex: 0,
    dragging: false,
  };
  return render(
    <ReactFlowProvider>
      {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
      <NodeCard {...(props as any)} />
    </ReactFlowProvider>,
  );
}

describe('NodeCard', () => {
  it('рендерит оригинальный content', () => {
    renderCard({ ...BASE_DATA });

    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
  });

  it('игнорирует data.translations - перевод не отображается', () => {
    renderCard({
      ...BASE_DATA,
      originalLang: 'ar',
      translations: [
        {
          id: 't-1',
          translatorName: 'Кулиев',
          language: 'ru',
          body: 'Деяния оцениваются по намерениям',
          isDefault: true,
        },
      ],
    });

    // оригинал виден
    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    // перевод НЕ рендерится (двуязычный режим выпилен)
    expect(
      screen.queryByText('Деяния оцениваются по намерениям'),
    ).not.toBeInTheDocument();
    // нет toggle переключения режима
    expect(
      screen.queryByRole('button', { name: /переключить режим/i }),
    ).not.toBeInTheDocument();
  });

  it('пустой content - показывает placeholder (...)', () => {
    renderCard({ ...BASE_DATA, content: '' });

    expect(screen.getByText('(...)')).toBeInTheDocument();
  });
});
