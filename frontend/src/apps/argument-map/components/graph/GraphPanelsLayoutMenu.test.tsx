import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReactFlowProvider } from '@xyflow/react';
import GraphPanels from './GraphPanels';
import { useLayoutAlgorithmStore } from '@/shared/stores/layoutAlgorithmStore';

// Мок elkjs - чтобы тест не загружал bundled.js 1.4MB в jsdom
vi.mock('elkjs/lib/elk.bundled.js', () => {
  class MockELK {
    async layout(graph: { children?: Array<{ id: string }> }) {
      return {
        ...graph,
        children: (graph.children ?? []).map((c) => ({ ...c, x: 0, y: 0 })),
      };
    }
  }
  return { default: MockELK };
});

function renderPanels(overrides: Partial<React.ComponentProps<typeof GraphPanels>> = {}) {
  const defaults: React.ComponentProps<typeof GraphPanels> = {
    showEdgeLabels: true,
    onToggleLabels: vi.fn(),
    canAddEdge: false,
    onAddNode: vi.fn(),
    onAddEdge: vi.fn(),
    selectedCount: 0,
    deleting: false,
    onDelete: vi.fn(),
    rfInstance: null,
    canWrite: true,
  };
  return render(
    <ReactFlowProvider>
      <GraphPanels {...defaults} {...overrides} />
    </ReactFlowProvider>,
  );
}

describe('GraphPanels - layout algorithm menu', () => {
  beforeEach(() => {
    window.localStorage.removeItem('argmap.layoutAlgorithm');
    useLayoutAlgorithmStore.setState({ algorithm: 'dagre' });
  });

  it('кнопка "Алгоритм раскладки" открывает меню с двумя радио-вариантами', async () => {
    renderPanels();
    const btn = screen.getByRole('button', { name: 'Алгоритм раскладки' });
    await userEvent.click(btn);

    expect(screen.getByRole('menuitemradio', { name: /Стандартный/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: /Умный/ })).toBeInTheDocument();
  });

  it('по default выбран dagre - aria-checked у "Стандартный"', async () => {
    renderPanels();
    await userEvent.click(screen.getByRole('button', { name: 'Алгоритм раскладки' }));
    const dagreItem = screen.getByRole('menuitemradio', { name: /Стандартный/ });
    expect(dagreItem).toHaveAttribute('aria-checked', 'true');
  });

  it('клик на "Умный (elkjs)" - меняет store и вызывает onApplyElkLayout', async () => {
    const onApplyElkLayout = vi.fn();
    renderPanels({ onApplyElkLayout });

    await userEvent.click(screen.getByRole('button', { name: 'Алгоритм раскладки' }));
    await userEvent.click(screen.getByRole('menuitemradio', { name: /Умный/ }));

    expect(useLayoutAlgorithmStore.getState().algorithm).toBe('elk');
    expect(onApplyElkLayout).toHaveBeenCalledTimes(1);
  });

  it('клик на тот же алгоритм - НЕ вызывает onApplyElkLayout (no-op)', async () => {
    const onApplyElkLayout = vi.fn();
    renderPanels({ onApplyElkLayout });

    await userEvent.click(screen.getByRole('button', { name: 'Алгоритм раскладки' }));
    await userEvent.click(screen.getByRole('menuitemradio', { name: /Стандартный/ }));

    expect(onApplyElkLayout).not.toHaveBeenCalled();
    expect(useLayoutAlgorithmStore.getState().algorithm).toBe('dagre');
  });
});
