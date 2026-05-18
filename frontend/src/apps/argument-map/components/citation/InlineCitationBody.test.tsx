import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { components } from '@/shared/api/types';
import InlineCitationBody from './InlineCitationBody';

type InlineCitationRef = components['schemas']['InlineCitationRef'];

const REF1: InlineCitationRef = {
  ordinal: 1,
  nodeSourceId: '00000000-0000-0000-0000-000000000001',
  sourceId: '00000000-0000-0000-0000-000000000002',
  sourceType: 'BOOK',
  title: 'Книга 1',
};

const REF2: InlineCitationRef = {
  ordinal: 2,
  nodeSourceId: '00000000-0000-0000-0000-000000000003',
  sourceId: '00000000-0000-0000-0000-000000000004',
  sourceType: 'HADITH',
  title: 'Хадис 2',
  reliability: 'SAHIH',
};

describe('InlineCitationBody', () => {
  it('рендерит plain text без маркеров идентично переданному body', () => {
    render(<InlineCitationBody body="просто текст" citations={[]} />);
    expect(screen.getByText('просто текст')).toBeInTheDocument();
  });

  it('рендерит smешанный текст и маркеры', () => {
    render(
      <InlineCitationBody
        body="Доказательство [1] и хадис [2]"
        citations={[REF1, REF2]}
      />,
    );
    // text сегменты
    expect(screen.getByText(/Доказательство/)).toBeInTheDocument();
    expect(screen.getByText(/и хадис/)).toBeInTheDocument();
    // citation маркеры
    expect(screen.getByTestId('inline-citation-1')).toBeInTheDocument();
    expect(screen.getByTestId('inline-citation-2')).toBeInTheDocument();
  });

  it('dead marker для ordinal без citation ref', () => {
    render(
      <InlineCitationBody
        body="Есть [1] но нет [99]"
        citations={[REF1]}
      />,
    );
    expect(screen.getByTestId('inline-citation-1')).toBeInTheDocument();
    expect(screen.getByTestId('inline-citation-dead-99')).toBeInTheDocument();
  });

  it('citations undefined обрабатывается без падений', () => {
    render(<InlineCitationBody body="Маркер [1] без citations" />);
    // [1] рендерится как dead marker
    expect(screen.getByTestId('inline-citation-dead-1')).toBeInTheDocument();
  });

  it('пустой body не рендерит ничего внутри wrapper', () => {
    const { container } = render(<InlineCitationBody body="" citations={[]} />);
    const span = container.querySelector('span');
    expect(span).not.toBeNull();
    expect(span?.textContent).toBe('');
  });

  it('сохраняет переносы строк через whitespace-pre-wrap', () => {
    const { container } = render(
      <InlineCitationBody body="строка 1\nстрока 2" citations={[]} />,
    );
    const span = container.querySelector('span');
    expect(span?.className).toContain('whitespace-pre-wrap');
  });
});
