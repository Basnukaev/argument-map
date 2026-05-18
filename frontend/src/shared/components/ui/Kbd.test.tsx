import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Kbd from './Kbd';

describe('Kbd', () => {
  it('рендерит как kbd-элемент c переданным children', () => {
    render(<Kbd>⌘</Kbd>);
    const node = screen.getByText('⌘');
    expect(node.tagName.toLowerCase()).toBe('kbd');
  });

  it('применяет базовые стили: monospace + border', () => {
    render(<Kbd>K</Kbd>);
    const node = screen.getByText('K');
    expect(node.className).toContain('font-mono');
    expect(node.className).toContain('border');
  });

  it('custom className суммируется с дефолтным', () => {
    render(<Kbd className="extra-test">K</Kbd>);
    const node = screen.getByText('K');
    expect(node.className).toContain('extra-test');
    expect(node.className).toContain('font-mono');
  });
});
