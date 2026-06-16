import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MinimapCard, { type MinimapNode, type MinimapViewport } from './MinimapCard';
import { useLocaleStore } from '@/shared/i18n/localeStore';

describe('MinimapCard', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'ru' });
  });

  const defaultViewport: MinimapViewport = { x: 0, y: 0, w: 200, h: 150 };

  const sampleNodes: MinimapNode[] = [
    { id: 'n1', x: 0, y: 0, w: 100, h: 60, type: 'CLAIM' },
    { id: 'n2', x: 200, y: 100, w: 100, h: 60, type: 'QUESTION' },
  ];

  function renderMinimap(
    overrides: Partial<React.ComponentProps<typeof MinimapCard>> = {},
  ) {
    const defaults: React.ComponentProps<typeof MinimapCard> = {
      nodes: sampleNodes,
      viewport: defaultViewport,
      onViewportChange: vi.fn(),
    };
    return render(<MinimapCard {...defaults} {...overrides} />);
  }

  // ── Smoke ─────────────────────────────────────────────────────────

  it('рендерится без падений', () => {
    renderMinimap();
    expect(screen.getByText('Обзор')).toBeInTheDocument();
  });

  it('рендерится без узлов без падений', () => {
    renderMinimap({ nodes: [] });
    expect(screen.getByText('Обзор')).toBeInTheDocument();
  });

  // ── Collapse / expand toggle ──────────────────────────────────────

  it('по умолчанию показывает заголовок "Обзор" (expanded)', () => {
    renderMinimap();
    expect(screen.getByText('Обзор')).toBeInTheDocument();
  });

  it('клик на кнопку "Свернуть" скрывает заголовок "Обзор"', async () => {
    renderMinimap();
    await userEvent.click(screen.getByRole('button', { name: 'Свернуть' }));
    expect(screen.queryByText('Обзор')).not.toBeInTheDocument();
  });

  it('клик на "Развернуть карту" возвращает expanded-состояние', async () => {
    renderMinimap({ defaultCollapsed: true });
    // в collapsed-состоянии видна кнопка Expand
    await userEvent.click(screen.getByRole('button', { name: 'Развернуть карту' }));
    expect(screen.getByText('Обзор')).toBeInTheDocument();
  });

  it('onCollapsedChange вызывается при сворачивании', async () => {
    const onCollapsedChange = vi.fn();
    renderMinimap({ onCollapsedChange });
    await userEvent.click(screen.getByRole('button', { name: 'Свернуть' }));
    expect(onCollapsedChange).toHaveBeenCalledWith(true);
  });

  it('onCollapsedChange вызывается при разворачивании', async () => {
    const onCollapsedChange = vi.fn();
    renderMinimap({ defaultCollapsed: true, onCollapsedChange });
    await userEvent.click(screen.getByRole('button', { name: 'Развернуть карту' }));
    expect(onCollapsedChange).toHaveBeenCalledWith(false);
  });

  it('controlled collapsed=true всегда рендерит свёрнутый вид', () => {
    renderMinimap({ collapsed: true });
    expect(screen.queryByText('Обзор')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Развернуть карту' })).toBeInTheDocument();
  });

  // ── Click-to-jump (onViewportChange) ─────────────────────────────

  it('клик по canvas-области вызывает onViewportChange', async () => {
    const onViewportChange = vi.fn();
    renderMinimap({ onViewportChange });
    // canvas — div с cursor-pointer рядом с заголовком "Обзор"
    // Кликаем по нему через getByRole('generic') не работает в RTL;
    // используем querySelector через container
    const { container } = render(
      <MinimapCard
        nodes={sampleNodes}
        viewport={defaultViewport}
        onViewportChange={onViewportChange}
      />,
    );
    const canvas = container.querySelector<HTMLDivElement>('.cursor-pointer');
    expect(canvas).not.toBeNull();
    await userEvent.click(canvas!);
    expect(onViewportChange).toHaveBeenCalled();
  });

  // ── Zoom label ────────────────────────────────────────────────────

  it('отображает zoom в процентах в collapsed-режиме', () => {
    renderMinimap({ collapsed: true, zoom: 0.5 });
    expect(screen.getByText('50%')).toBeInTheDocument();
  });

  it('отображает zoom в процентах в expanded-режиме', () => {
    renderMinimap({ zoom: 1.5 });
    expect(screen.getByText('150%')).toBeInTheDocument();
  });

  // ── Edges toggle ──────────────────────────────────────────────────

  it('кнопка "Скрыть связи" / "Показать связи" переключается', async () => {
    renderMinimap();
    // по умолчанию связи видны → кнопка "Скрыть связи"
    const hideBtn = screen.getByRole('button', { name: 'Скрыть связи' });
    expect(hideBtn).toBeInTheDocument();
    await userEvent.click(hideBtn);
    expect(screen.getByRole('button', { name: 'Показать связи' })).toBeInTheDocument();
  });

  // ── Center-on-selection ───────────────────────────────────────────

  it('кнопка "Центр на выделении" не рендерится без выделения', () => {
    renderMinimap({ nodes: sampleNodes }); // ни один не selected
    expect(
      screen.queryByRole('button', { name: 'Центр на выделении' }),
    ).not.toBeInTheDocument();
  });

  it('кнопка "Центр на выделении" рендерится при наличии selected-узла и onCenterOnSelection', async () => {
    const selectedNodes: MinimapNode[] = [
      { id: 'n1', x: 0, y: 0, w: 100, h: 60, type: 'CLAIM', selected: true },
      { id: 'n2', x: 200, y: 100, w: 100, h: 60, type: 'QUESTION' },
    ];
    const onCenterOnSelection = vi.fn();
    renderMinimap({ nodes: selectedNodes, onCenterOnSelection });
    const btn = screen.getByRole('button', { name: 'Центр на выделении' });
    await userEvent.click(btn);
    expect(onCenterOnSelection).toHaveBeenCalledOnce();
  });

  // ── Node count in collapsed view ──────────────────────────────────

  it('collapsed: показывает количество узлов', () => {
    renderMinimap({ collapsed: true, nodes: sampleNodes });
    // 2 узла → "2 узла"
    expect(screen.getByText(/2/)).toBeInTheDocument();
  });
});
