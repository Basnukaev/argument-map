import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ThemeSwitch from './ThemeSwitch';
import { useThemeStore } from '@/shared/stores/themeStore';

describe('ThemeSwitch', () => {
  beforeEach(() => {
    localStorage.clear();
    // reset store к дефолту между тестами
    useThemeStore.setState({ mode: 'system', effectiveTheme: 'light', theme: 'light' });
  });

  it('рендерит trigger-кнопку с aria-label', () => {
    render(<ThemeSwitch />);
    expect(screen.getByRole('button', { name: 'Переключить тему' })).toBeInTheDocument();
  });

  it('клик по триггеру открывает меню с 3 опциями', async () => {
    const user = userEvent.setup();
    render(<ThemeSwitch />);

    await user.click(screen.getByRole('button', { name: 'Переключить тему' }));

    expect(screen.getByRole('menu', { name: 'Выбор темы' })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: /Системная/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: /Светлая/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitemradio', { name: /Тёмная/ })).toBeInTheDocument();
  });

  it('выбор Dark обновляет store на mode=dark', async () => {
    const user = userEvent.setup();
    render(<ThemeSwitch />);

    await user.click(screen.getByRole('button', { name: 'Переключить тему' }));
    await user.click(screen.getByRole('menuitemradio', { name: /Тёмная/ }));

    expect(useThemeStore.getState().mode).toBe('dark');
    expect(useThemeStore.getState().effectiveTheme).toBe('dark');
  });

  it('текущий выбор отмечен aria-checked=true', async () => {
    useThemeStore.getState().setMode('light');
    const user = userEvent.setup();
    render(<ThemeSwitch />);

    await user.click(screen.getByRole('button', { name: 'Переключить тему' }));

    const lightItem = screen.getByRole('menuitemradio', { name: /Светлая/ });
    expect(lightItem).toHaveAttribute('aria-checked', 'true');

    const darkItem = screen.getByRole('menuitemradio', { name: /Тёмная/ });
    expect(darkItem).toHaveAttribute('aria-checked', 'false');
  });

  it('клик вне меню закрывает popover', async () => {
    const user = userEvent.setup();
    render(
      <div>
        <ThemeSwitch />
        <button>outside</button>
      </div>,
    );

    await user.click(screen.getByRole('button', { name: 'Переключить тему' }));
    expect(screen.queryByRole('menu')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'outside' }));
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('выбор закрывает меню', async () => {
    const user = userEvent.setup();
    render(<ThemeSwitch />);

    await user.click(screen.getByRole('button', { name: 'Переключить тему' }));
    expect(screen.queryByRole('menu')).toBeInTheDocument();

    await user.click(screen.getByRole('menuitemradio', { name: /Тёмная/ }));
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });
});
