import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import CommandPalette from './CommandPalette';

// react-hotkeys-hook в jsdom не слышит keydown без дополнительной установки —
// мокаем useHotkey чтобы тесты могли проверять поведение arrow navigation
// без зависимости от browser keyboard events infrastructure.
vi.mock('@/shared/hooks/useHotkey', () => ({
  useHotkey: vi.fn(),
}));

// Мокаем useNavigate чтобы команды navigate не вызывали ошибок
vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useNavigate: () => vi.fn(),
  };
});

import { useHotkey } from '@/shared/hooks/useHotkey';

/** Хелпер: отдаёт зарегистрированный callback для hotkey по имени клавиши */
function getHotkeyCallback(key: string) {
  const calls = vi.mocked(useHotkey).mock.calls;
  const found = calls.find(([k]) => k === key);
  return found ? found[1] : undefined;
}

function renderPalette(open = true) {
  const onClose = vi.fn();
  const result = render(
    <MemoryRouter>
      <CommandPalette open={open} onClose={onClose} />
    </MemoryRouter>,
  );
  return { ...result, onClose };
}

describe('CommandPalette', () => {
  describe('scrollIntoView при arrow navigation', () => {
    beforeEach(() => {
      vi.mocked(useHotkey).mockReset();
      // scrollIntoView не реализован в jsdom — мокаем на прототипе
      HTMLElement.prototype.scrollIntoView = vi.fn();
    });

    it('scrollIntoView вызывается когда активный элемент уходит за viewport (arrow-down)', async () => {
      renderPalette();

      const listItems = screen.getAllByRole('option');
      expect(listItems.length).toBeGreaterThan(0);

      // Первый item активен по умолчанию
      expect(listItems[0]).toHaveAttribute('aria-selected', 'true');

      // Симулируем нажатие arrow-down через зарегистрированный callback
      const arrowDownCb = getHotkeyCallback('arrowdown');
      expect(arrowDownCb).toBeDefined();

      arrowDownCb!(new KeyboardEvent('keydown'), { keys: ['arrowdown'], hotkey: 'arrowdown' });

      // После обновления state второй item должен стать активным
      await vi.waitFor(() => {
        const items = screen.getAllByRole('option');
        expect(items[1]).toHaveAttribute('aria-selected', 'true');
      });

      // scrollIntoView должен был быть вызван на активном элементе
      expect(HTMLElement.prototype.scrollIntoView).toHaveBeenCalledWith({
        block: 'nearest',
      });
    });

    it('scrollIntoView вызывается с block:nearest при arrow-up', async () => {
      renderPalette();

      // Сначала arrow-down → на второй item
      const arrowDownCb = getHotkeyCallback('arrowdown');
      arrowDownCb!(new KeyboardEvent('keydown'), { keys: ['arrowdown'], hotkey: 'arrowdown' });

      await vi.waitFor(() => {
        const items = screen.getAllByRole('option');
        expect(items[1]).toHaveAttribute('aria-selected', 'true');
      });

      vi.mocked(HTMLElement.prototype.scrollIntoView).mockClear();

      // Теперь arrow-up → обратно на первый item
      const arrowUpCb = getHotkeyCallback('arrowup');
      expect(arrowUpCb).toBeDefined();

      arrowUpCb!(new KeyboardEvent('keydown'), { keys: ['arrowup'], hotkey: 'arrowup' });

      await vi.waitFor(() => {
        const items = screen.getAllByRole('option');
        expect(items[0]).toHaveAttribute('aria-selected', 'true');
      });

      expect(HTMLElement.prototype.scrollIntoView).toHaveBeenCalledWith({
        block: 'nearest',
      });
    });

    it('palette не рендерится когда open=false', () => {
      renderPalette(false);
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    });
  });

  describe('mouse hover navigation', () => {
    beforeEach(() => {
      vi.mocked(useHotkey).mockReset();
      HTMLElement.prototype.scrollIntoView = vi.fn();
    });

    it('hover на item делает его активным', async () => {
      const user = userEvent.setup();
      renderPalette();

      const items = screen.getAllByRole('option');
      expect(items.length).toBeGreaterThan(2);

      // Первый item активен изначально
      expect(items[0]).toHaveAttribute('aria-selected', 'true');

      // Hover на третий item
      const thirdItem = items[2]!;
      const firstItem = items[0]!;
      await user.hover(thirdItem.querySelector('button')!);

      expect(thirdItem).toHaveAttribute('aria-selected', 'true');
      expect(firstItem).toHaveAttribute('aria-selected', 'false');
    });
  });
});
