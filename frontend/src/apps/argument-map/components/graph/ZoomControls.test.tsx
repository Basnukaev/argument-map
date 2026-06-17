import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ZoomControls from './ZoomControls';
import { useLocaleStore } from '@/shared/i18n/localeStore';

describe('ZoomControls', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'ru' });
  });

  function renderControls(overrides: Partial<React.ComponentProps<typeof ZoomControls>> = {}) {
    const defaults: React.ComponentProps<typeof ZoomControls> = {
      zoom: 1,
      onZoomChange: vi.fn(),
      onFit: vi.fn(),
    };
    return render(<ZoomControls {...defaults} {...overrides} />);
  }

  // ── Preset menu ────────────────────────────────────────────────────

  it('пресет-меню открывается по клику на кнопку с процентом', async () => {
    renderControls();
    const trigger = screen.getByRole('button', { name: 'Пресеты масштаба' });
    await userEvent.click(trigger);
    expect(screen.getByRole('menu')).toBeInTheDocument();
  });

  it('пресет-меню закрывается по Escape', async () => {
    renderControls();
    const trigger = screen.getByRole('button', { name: 'Пресеты масштаба' });
    await userEvent.click(trigger);
    expect(screen.getByRole('menu')).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('клик по пресету вызывает onZoomChange с нужным значением', async () => {
    const onZoomChange = vi.fn();
    renderControls({ zoom: 1, onZoomChange });
    await userEvent.click(screen.getByRole('button', { name: 'Пресеты масштаба' }));
    // 25% — уникальный пресет в меню (не совпадает с частью других значений)
    const preset25 = screen.getByRole('menuitem', { name: /^25%/ });
    await userEvent.click(preset25);
    expect(onZoomChange).toHaveBeenCalledWith(0.25);
  });

  it('клик по пресету закрывает меню', async () => {
    const onZoomChange = vi.fn();
    renderControls({ onZoomChange });
    await userEvent.click(screen.getByRole('button', { name: 'Пресеты масштаба' }));
    await userEvent.click(screen.getByRole('menuitem', { name: /^25%/ }));
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('клик по "Уместить граф" вызывает onFit', async () => {
    const onFit = vi.fn();
    renderControls({ onFit });
    await userEvent.click(screen.getByRole('button', { name: 'Пресеты масштаба' }));
    const fitItem = screen.getByRole('menuitem', { name: /Уместить граф/ });
    await userEvent.click(fitItem);
    expect(onFit).toHaveBeenCalledOnce();
  });

  it('"Вписать выделение" не отображается без hasSelection', async () => {
    renderControls({ hasSelection: false });
    await userEvent.click(screen.getByRole('button', { name: 'Пресеты масштаба' }));
    expect(screen.queryByRole('menuitem', { name: /Вписать выделение/ })).not.toBeInTheDocument();
  });

  it('"Вписать выделение" отображается при hasSelection=true', async () => {
    renderControls({ hasSelection: true, onFitSelection: vi.fn() });
    await userEvent.click(screen.getByRole('button', { name: 'Пресеты масштаба' }));
    expect(screen.getByRole('menuitem', { name: /Вписать выделение/ })).toBeInTheDocument();
  });

  // ── Disabled on limits ─────────────────────────────────────────────

  it('кнопка "Уменьшить" disabled когда zoom <= min', () => {
    renderControls({ zoom: 0.1, min: 0.1 });
    expect(screen.getByRole('button', { name: 'Уменьшить' })).toBeDisabled();
  });

  it('кнопка "Увеличить" disabled когда zoom >= max', () => {
    renderControls({ zoom: 5, max: 5 });
    expect(screen.getByRole('button', { name: 'Увеличить' })).toBeDisabled();
  });

  it('кнопка "Уменьшить" enabled когда zoom > min', () => {
    renderControls({ zoom: 1, min: 0.1 });
    expect(screen.getByRole('button', { name: 'Уменьшить' })).not.toBeDisabled();
  });

  it('кнопка "Увеличить" enabled когда zoom < max', () => {
    renderControls({ zoom: 1, max: 5 });
    expect(screen.getByRole('button', { name: 'Увеличить' })).not.toBeDisabled();
  });

  // ── Zoom in / out ──────────────────────────────────────────────────

  it('клик "Увеличить" вызывает onZoomChange с zoom + step', async () => {
    const onZoomChange = vi.fn();
    renderControls({ zoom: 1, step: 0.1, onZoomChange });
    await userEvent.click(screen.getByRole('button', { name: 'Увеличить' }));
    expect(onZoomChange).toHaveBeenCalledWith(1.1);
  });

  it('клик "Уменьшить" вызывает onZoomChange с zoom - step', async () => {
    const onZoomChange = vi.fn();
    renderControls({ zoom: 1, step: 0.1, onZoomChange });
    await userEvent.click(screen.getByRole('button', { name: 'Уменьшить' }));
    expect(onZoomChange).toHaveBeenCalledWith(0.9);
  });

  // ── Fullscreen ─────────────────────────────────────────────────────

  it('кнопка полного экрана не рендерится без onFullscreen', () => {
    renderControls();
    expect(screen.queryByRole('button', { name: 'На весь экран' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Выйти из полного экрана' })).not.toBeInTheDocument();
  });

  it('кнопка "На весь экран" рендерится и кликается при наличии onFullscreen', async () => {
    const onFullscreen = vi.fn();
    renderControls({ onFullscreen });
    const btn = screen.getByRole('button', { name: 'На весь экран' });
    await userEvent.click(btn);
    expect(onFullscreen).toHaveBeenCalledOnce();
  });

  it('кнопка показывает "Выйти из полного экрана" при isFullscreen=true', () => {
    renderControls({ onFullscreen: vi.fn(), isFullscreen: true });
    expect(screen.getByRole('button', { name: 'Выйти из полного экрана' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'На весь экран' })).not.toBeInTheDocument();
  });

  // ── Current zoom display ───────────────────────────────────────────

  it('отображает текущий zoom в процентах', () => {
    renderControls({ zoom: 0.75 });
    expect(screen.getByRole('button', { name: 'Пресеты масштаба' })).toHaveTextContent('75%');
  });
});
