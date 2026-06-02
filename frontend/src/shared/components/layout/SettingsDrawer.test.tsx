import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import SettingsDrawer from './SettingsDrawer';
import { useSettingsDrawerStore } from '@/shared/stores/settingsDrawerStore';
import { useUiScaleStore } from '@/shared/stores/uiScaleStore';

function renderDrawer() {
  return render(
    <MemoryRouter>
      <SettingsDrawer />
    </MemoryRouter>,
  );
}

describe('SettingsDrawer', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useSettingsDrawerStore.setState({ open: false });
    useUiScaleStore.getState().reset();
  });

  it('не рендерит ничего пока store закрыт', () => {
    renderDrawer();
    expect(screen.queryByTestId('settings-drawer')).toBeNull();
  });

  it('рендерит slide-over когда store открыт', () => {
    useSettingsDrawerStore.setState({ open: true });
    renderDrawer();
    expect(screen.getByTestId('settings-drawer')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true');
  });

  it('клик по backdrop закрывает drawer (контекст страницы не теряется)', async () => {
    const user = userEvent.setup();
    useSettingsDrawerStore.setState({ open: true });
    renderDrawer();
    await user.click(screen.getByTestId('settings-drawer-backdrop'));
    expect(useSettingsDrawerStore.getState().open).toBe(false);
  });

  it('Escape закрывает drawer', async () => {
    const user = userEvent.setup();
    useSettingsDrawerStore.setState({ open: true });
    renderDrawer();
    await user.keyboard('{Escape}');
    expect(useSettingsDrawerStore.getState().open).toBe(false);
  });

  it('содержит контрол масштаба интерфейса с rollback к стандартному', async () => {
    const user = userEvent.setup();
    useSettingsDrawerStore.setState({ open: true });
    renderDrawer();
    // Дефолт - compact
    expect(useUiScaleStore.getState().scale).toBe('compact');
    // One-click rollback к базовому 100%
    const standardBtn = screen.getByRole('radio', {
      name: /Стандартный \(базовый\)/,
    });
    await user.click(standardBtn);
    expect(useUiScaleStore.getState().scale).toBe('standard');
    expect(standardBtn).toHaveAttribute('aria-checked', 'true');
  });
});
