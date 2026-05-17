import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import ShortcutHint from './ShortcutHint';

function mockPlatform(value: string) {
  Object.defineProperty(window.navigator, 'platform', {
    value,
    configurable: true,
  });
}

describe('ShortcutHint', () => {
  const originalPlatform = window.navigator.platform;

  beforeEach(() => {
    // sanity: тест явно выставит платформу
  });

  afterEach(() => {
    mockPlatform(originalPlatform);
    vi.restoreAllMocks();
  });

  it('на Mac рендерит mod как ⌘', () => {
    mockPlatform('MacIntel');
    render(<ShortcutHint keys="mod+enter" />);
    expect(screen.getByText('⌘')).toBeInTheDocument();
    expect(screen.getByText('↵')).toBeInTheDocument();
  });

  it('на Windows/Linux рендерит mod как Ctrl', () => {
    mockPlatform('Win32');
    render(<ShortcutHint keys="mod+enter" />);
    expect(screen.getByText('Ctrl')).toBeInTheDocument();
    expect(screen.getByText('↵')).toBeInTheDocument();
    expect(screen.queryByText('⌘')).not.toBeInTheDocument();
  });

  it('на Mac alt → ⌥, на Linux alt → Alt', () => {
    mockPlatform('Linux x86_64');
    const { unmount } = render(<ShortcutHint keys="alt+k" />);
    expect(screen.getByText('Alt')).toBeInTheDocument();
    expect(screen.getByText('K')).toBeInTheDocument();
    unmount();

    mockPlatform('MacIntel');
    render(<ShortcutHint keys="alt+k" />);
    expect(screen.getByText('⌥')).toBeInTheDocument();
  });

  it('из перечисления берёт первую комбинацию (mod+enter,ctrl+enter)', () => {
    mockPlatform('MacIntel');
    render(<ShortcutHint keys="mod+enter,ctrl+enter" />);
    // первая - mod+enter
    expect(screen.getByText('⌘')).toBeInTheDocument();
    expect(screen.getByText('↵')).toBeInTheDocument();
    // ctrl glyph не должен попасть второй раз
    expect(screen.queryByText('⌃')).not.toBeInTheDocument();
  });

  it('буквенная клавиша рендерится заглавной', () => {
    mockPlatform('Linux x86_64');
    render(<ShortcutHint keys="k" />);
    expect(screen.getByText('K')).toBeInTheDocument();
  });
});
