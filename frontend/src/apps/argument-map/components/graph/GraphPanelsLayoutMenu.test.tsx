import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReactFlowProvider } from '@xyflow/react';
import GraphPanels from './GraphPanels';
import { useLayoutPresetStore } from '@/shared/stores/layoutPresetStore';

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

describe('GraphPanels - layout preset menu', () => {
  beforeEach(() => {
    window.localStorage.removeItem('argmap.layoutPreset');
    window.localStorage.removeItem('argmap.layoutAlgorithm');
    useLayoutPresetStore.setState({ preset: 'tree-tb' });
  });

  it('кнопка «Раскладка» открывает меню с тремя preset-вариантами', async () => {
    renderPanels();
    const btn = screen.getByRole('button', { name: 'Раскладка' });
    await userEvent.click(btn);

    expect(screen.getByRole('menuitemradio', { name: /Дерево \(вертикальное\)/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: /Дерево \(горизонтальное\)/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: /Радиальное/ })).toBeInTheDocument();
  });

  it('по default выбран tree-tb - aria-checked у «Дерево (вертикальное)»', async () => {
    renderPanels();
    await userEvent.click(screen.getByRole('button', { name: 'Раскладка' }));
    const treeTb = screen.getByRole('menuitemradio', { name: /Дерево \(вертикальное\)/ });
    expect(treeTb).toHaveAttribute('aria-checked', 'true');
  });

  it('клик на «Радиальное» - меняет store и вызывает onApplyPreset с radial', async () => {
    const onApplyPreset = vi.fn();
    renderPanels({ onApplyPreset });

    await userEvent.click(screen.getByRole('button', { name: 'Раскладка' }));
    await userEvent.click(screen.getByRole('menuitemradio', { name: /Радиальное/ }));

    expect(useLayoutPresetStore.getState().preset).toBe('radial');
    expect(onApplyPreset).toHaveBeenCalledTimes(1);
    expect(onApplyPreset).toHaveBeenCalledWith('radial');
  });

  it('клик на тот же preset - НЕ вызывает onApplyPreset (no-op)', async () => {
    const onApplyPreset = vi.fn();
    renderPanels({ onApplyPreset });

    await userEvent.click(screen.getByRole('button', { name: 'Раскладка' }));
    await userEvent.click(screen.getByRole('menuitemradio', { name: /Дерево \(вертикальное\)/ }));

    expect(onApplyPreset).not.toHaveBeenCalled();
    expect(useLayoutPresetStore.getState().preset).toBe('tree-tb');
  });

  it('клик на «Дерево (горизонтальное)» из tree-tb - меняет store и вызывает onApplyPreset', async () => {
    const onApplyPreset = vi.fn();
    renderPanels({ onApplyPreset });

    await userEvent.click(screen.getByRole('button', { name: 'Раскладка' }));
    await userEvent.click(screen.getByRole('menuitemradio', { name: /Дерево \(горизонтальное\)/ }));

    expect(useLayoutPresetStore.getState().preset).toBe('tree-lr');
    expect(onApplyPreset).toHaveBeenCalledTimes(1);
    expect(onApplyPreset).toHaveBeenCalledWith('tree-lr');
  });

  it('reset кнопка появляется только если onResetLayout passed', async () => {
    const onResetLayout = vi.fn();
    renderPanels({ onResetLayout });
    await userEvent.click(screen.getByRole('button', { name: 'Раскладка' }));
    expect(screen.getByText('Сбросить ручную раскладку')).toBeInTheDocument();
  });

  it('reset кнопка скрыта если onResetLayout не passed', async () => {
    renderPanels();
    await userEvent.click(screen.getByRole('button', { name: 'Раскладка' }));
    expect(screen.queryByText('Сбросить ручную раскладку')).not.toBeInTheDocument();
  });
});
