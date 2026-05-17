import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Modal from './Modal';

/**
 * matchMedia stub - useIsMobile подписан через addEventListener.
 * Меняем `matches` под нужный viewport до render
 */
function stubMatchMedia(mobile: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: mobile,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

// jsdom не реализует HTMLDialogElement.showModal/close - монки-патч
beforeEach(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function () {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close = function () {
      this.removeAttribute('open');
      this.dispatchEvent(new Event('close'));
    };
  }
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Modal', () => {
  describe('desktop (≥md)', () => {
    beforeEach(() => stubMatchMedia(false));

    it('показывает заголовок и контент', () => {
      render(
        <Modal open onClose={() => {}} title="Тестовый заголовок">
          <div>Контент</div>
        </Modal>,
      );
      expect(screen.getByRole('heading', { name: 'Тестовый заголовок' })).toBeInTheDocument();
      expect(screen.getByText('Контент')).toBeInTheDocument();
    });

    it('кнопка X (Закрыть) видна на desktop', () => {
      render(
        <Modal open onClose={() => {}} title="T">
          <div>C</div>
        </Modal>,
      );
      expect(screen.getByRole('button', { name: 'Закрыть' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Назад' })).not.toBeInTheDocument();
    });

    it('клик по X вызывает onClose', async () => {
      const onClose = vi.fn();
      render(
        <Modal open onClose={onClose} title="T">
          <div>C</div>
        </Modal>,
      );
      await userEvent.click(screen.getByRole('button', { name: 'Закрыть' }));
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });

  describe('mobile (<md)', () => {
    beforeEach(() => stubMatchMedia(true));

    it('кнопка back-arrow (Назад) вместо X на mobile', () => {
      render(
        <Modal open onClose={() => {}} title="T">
          <div>C</div>
        </Modal>,
      );
      expect(screen.getByRole('button', { name: 'Назад' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Закрыть' })).not.toBeInTheDocument();
    });

    it('клик по back-arrow вызывает onClose', async () => {
      const onClose = vi.fn();
      render(
        <Modal open onClose={onClose} title="T">
          <div>C</div>
        </Modal>,
      );
      await userEvent.click(screen.getByRole('button', { name: 'Назад' }));
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });
});
