import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useHotkey } from '@/shared/hooks/useHotkey';

function GraphHarness({ onTrigger }: { onTrigger: () => void }) {
  useHotkey('alt+k', onTrigger);
  return <div data-testid="graph">фокус не в форме</div>;
}

function FormHarness({ onTrigger }: { onTrigger: () => void }) {
  useHotkey('alt+k', onTrigger);
  return (
    <form>
      <input data-testid="text-input" />
    </form>
  );
}

function FormSubmitHarness({ onSubmit }: { onSubmit: () => void }) {
  // enableOnFormTags: true чтобы отрабатывало внутри textarea.
  // `mod` - platform-aware модификатор: Cmd на Mac, Ctrl на Win/Linux
  useHotkey('mod+enter', onSubmit, { enableOnFormTags: true });
  return (
    <form>
      <textarea data-testid="content" />
    </form>
  );
}

describe('useHotkey', () => {
  it('срабатывает на Alt+K вне формы (физическая клавиша KeyK)', async () => {
    const user = userEvent.setup();
    const fn = vi.fn();
    render(<GraphHarness onTrigger={fn} />);
    // Alt+K через userEvent посылает event.key='k' и event.code='KeyK'
    await user.keyboard('{Alt>}k{/Alt}');
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('НЕ срабатывает по умолчанию когда фокус в input (enableOnFormTags=false)', async () => {
    const user = userEvent.setup();
    const fn = vi.fn();
    render(<FormHarness onTrigger={fn} />);
    const input = screen.getByTestId('text-input');
    await user.click(input);
    await user.keyboard('{Alt>}k{/Alt}');
    expect(fn).not.toHaveBeenCalled();
  });

  it('срабатывает в textarea если enableOnFormTags=true (Cmd/Ctrl+Enter для submit)', async () => {
    const user = userEvent.setup();
    const fn = vi.fn();
    render(<FormSubmitHarness onSubmit={fn} />);
    const ta = screen.getByTestId('content');
    await user.click(ta);
    await user.keyboard('{Control>}{Enter}{/Control}');
    expect(fn).toHaveBeenCalledTimes(1);
  });
});
