import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HelpShortcuts from './HelpShortcuts';
import { useLocaleStore } from '@/shared/i18n/localeStore';

describe('HelpShortcuts', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'ru' });
  });

  const sampleShortcuts = [
    { group: 'Навигация', label: 'Уместить граф', keys: ['⌘', '1'] },
    { group: 'Навигация', label: 'Вписать выделение', keys: ['⌘', '2'] },
    { label: 'Отменить', keys: ['⌘', 'Z'] },
  ];

  function renderHelp(
    overrides: Partial<React.ComponentProps<typeof HelpShortcuts>> = {},
  ) {
    const defaults: React.ComponentProps<typeof HelpShortcuts> = {
      shortcuts: sampleShortcuts,
      title: 'Шорткаты',
    };
    return render(<HelpShortcuts {...defaults} {...overrides} />);
  }

  // ── Smoke ─────────────────────────────────────────────────────────

  it('рендерится без падений', () => {
    renderHelp();
    // trigger button присутствует
    expect(screen.getByRole('button', { name: 'Шорткаты' })).toBeInTheDocument();
  });

  it('попап закрыт по умолчанию (aria-hidden=true)', () => {
    renderHelp();
    const dialog = screen.getByRole('dialog', { hidden: true });
    expect(dialog).toHaveAttribute('aria-hidden', 'true');
  });

  // ── Hover trigger (default) ───────────────────────────────────────

  it('hover на триггере открывает попап', async () => {
    renderHelp({ trigger: 'hover' });
    const trigger = screen.getByRole('button', { name: 'Шорткаты' });
    await userEvent.hover(trigger);
    // после hover попап становится visible
    const dialog = screen.getByRole('dialog', { hidden: true });
    expect(dialog).toHaveAttribute('aria-hidden', 'false');
  });

  it('shortcut-строки видны когда попап открыт через hover', async () => {
    renderHelp({ trigger: 'hover' });
    await userEvent.hover(screen.getByRole('button', { name: 'Шорткаты' }));
    expect(screen.getByText('Уместить граф')).toBeInTheDocument();
    expect(screen.getByText('Отменить')).toBeInTheDocument();
  });

  // ── Click trigger ─────────────────────────────────────────────────

  it('click-trigger: клик на триггере открывает попап', async () => {
    renderHelp({ trigger: 'click' });
    await userEvent.click(screen.getByRole('button', { name: 'Шорткаты' }));
    const dialog = screen.getByRole('dialog', { hidden: true });
    expect(dialog).toHaveAttribute('aria-hidden', 'false');
  });

  it('click-trigger: повторный клик закрывает попап', async () => {
    renderHelp({ trigger: 'click' });
    const trigger = screen.getByRole('button', { name: 'Шорткаты' });
    await userEvent.click(trigger);
    await userEvent.click(trigger);
    const dialog = screen.getByRole('dialog', { hidden: true });
    expect(dialog).toHaveAttribute('aria-hidden', 'true');
  });

  it('click-trigger: Escape закрывает попап', async () => {
    renderHelp({ trigger: 'click' });
    await userEvent.click(screen.getByRole('button', { name: 'Шорткаты' }));
    await userEvent.keyboard('{Escape}');
    const dialog = screen.getByRole('dialog', { hidden: true });
    expect(dialog).toHaveAttribute('aria-hidden', 'true');
  });

  // ── Pin ───────────────────────────────────────────────────────────

  it('pin-кнопка вызывает stopPropagation (попап остаётся открытым после pin)', async () => {
    renderHelp({ trigger: 'hover' });
    // открываем через hover
    await userEvent.hover(screen.getByRole('button', { name: 'Шорткаты' }));
    // кликаем pin
    const pinBtn = screen.getByRole('button', { name: 'Закрепить' });
    await userEvent.click(pinBtn);
    // попап всё ещё открыт (pin зафиксировал его)
    const dialog = screen.getByRole('dialog', { hidden: true });
    expect(dialog).toHaveAttribute('aria-hidden', 'false');
  });

  it('pin → aria-label меняется на "Открепить"', async () => {
    renderHelp({ trigger: 'hover' });
    await userEvent.hover(screen.getByRole('button', { name: 'Шорткаты' }));
    await userEvent.click(screen.getByRole('button', { name: 'Закрепить' }));
    expect(screen.getByRole('button', { name: 'Открепить' })).toBeInTheDocument();
  });

  it('повторный клик по pin снимает фиксацию (label вернулся в "Закрепить")', async () => {
    renderHelp({ trigger: 'hover' });
    await userEvent.hover(screen.getByRole('button', { name: 'Шорткаты' }));
    await userEvent.click(screen.getByRole('button', { name: 'Закрепить' }));
    await userEvent.click(screen.getByRole('button', { name: 'Открепить' }));
    expect(screen.getByRole('button', { name: 'Закрепить' })).toBeInTheDocument();
  });

  // ── Groups ────────────────────────────────────────────────────────

  it('group-label "Навигация" рендерится один раз перед первым элементом группы', async () => {
    renderHelp({ trigger: 'hover' });
    await userEvent.hover(screen.getByRole('button', { name: 'Шорткаты' }));
    // Должен быть ровно один group-label "Навигация"
    const labels = screen.getAllByText('Навигация');
    expect(labels).toHaveLength(1);
  });

  // ── title prop ────────────────────────────────────────────────────

  it('кастомный title отображается в заголовке попапа', async () => {
    renderHelp({ trigger: 'hover', title: 'Горячие клавиши' });
    await userEvent.hover(screen.getByRole('button', { name: 'Горячие клавиши' }));
    // title должен быть внутри попапа тоже
    const titles = screen.getAllByText('Горячие клавиши');
    // один в trigger aria-label (виртуальный), один в header попапа (текстовый)
    expect(titles.length).toBeGreaterThanOrEqual(1);
  });

  // ── Empty shortcuts ───────────────────────────────────────────────

  it('рендерится с пустым массивом shortcuts без падений', () => {
    renderHelp({ shortcuts: [] });
    expect(screen.getByRole('button', { name: 'Шорткаты' })).toBeInTheDocument();
  });
});
