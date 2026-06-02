import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import ReaderModeSwitch from './ReaderModeSwitch';

describe('ReaderModeSwitch / availableModes', () => {
  it('по умолчанию рендерит обе кнопки (Текст + PDF)', () => {
    render(<ReaderModeSwitch mode="text" onChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: /Текст/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /PDF/i })).toBeInTheDocument();
  });

  it('availableModes=[pdf] рендерит только кнопку PDF', () => {
    render(<ReaderModeSwitch mode="pdf" onChange={vi.fn()} availableModes={['pdf']} />);
    expect(screen.getByRole('button', { name: /PDF/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Текст/i })).not.toBeInTheDocument();
  });

  it('availableModes=[text] рендерит только кнопку Текст', () => {
    render(<ReaderModeSwitch mode="text" onChange={vi.fn()} availableModes={['text']} />);
    expect(screen.getByRole('button', { name: /Текст/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /PDF/i })).not.toBeInTheDocument();
  });
});
