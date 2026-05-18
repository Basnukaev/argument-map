import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { components } from '@/shared/api/types';
import InlineCitationMarker from './InlineCitationMarker';

type InlineCitationRef = components['schemas']['InlineCitationRef'];

const BOOK_CITATION: InlineCitationRef = {
  ordinal: 1,
  nodeSourceId: '00000000-0000-0000-0000-000000000001',
  sourceId: '00000000-0000-0000-0000-000000000002',
  sourceType: 'BOOK',
  title: 'Сахих аль-Бухари',
  citation: 'Бухари 1234',
  quote: 'кто чем будет воскрешён',
};

const HADITH_CITATION: InlineCitationRef = {
  ...BOOK_CITATION,
  sourceType: 'HADITH',
  reliability: 'SAHIH',
};

describe('InlineCitationMarker', () => {
  it('рендерит маркер [N] с переданным ordinal', () => {
    render(<InlineCitationMarker ordinal={1} citation={BOOK_CITATION} />);
    expect(screen.getByText('[1]')).toBeInTheDocument();
  });

  it('dead marker если citation undefined', () => {
    render(<InlineCitationMarker ordinal={5} />);
    expect(screen.getByTestId('inline-citation-dead-5')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('popover закрыт по умолчанию', () => {
    render(<InlineCitationMarker ordinal={1} citation={BOOK_CITATION} />);
    expect(screen.queryByTestId('inline-citation-popover')).not.toBeInTheDocument();
  });

  it('popover открывается по клику и показывает title + quote + citation', async () => {
    const user = userEvent.setup();
    render(<InlineCitationMarker ordinal={1} citation={BOOK_CITATION} />);

    await user.click(screen.getByRole('button'));

    const popover = screen.getByTestId('inline-citation-popover');
    expect(popover).toBeInTheDocument();
    expect(popover).toHaveTextContent('Сахих аль-Бухари');
    expect(popover).toHaveTextContent('кто чем будет воскрешён');
    expect(popover).toHaveTextContent('Бухари 1234');
  });

  it('reliability показывается только для HADITH типа', async () => {
    const user = userEvent.setup();
    render(<InlineCitationMarker ordinal={1} citation={HADITH_CITATION} />);

    await user.click(screen.getByRole('button'));

    expect(screen.getByText('SAHIH')).toBeInTheDocument();
  });

  it('reliability не показывается для BOOK даже если поле заполнено', async () => {
    const user = userEvent.setup();
    const bookWithReliability: InlineCitationRef = {
      ...BOOK_CITATION,
      reliability: 'SAHIH',
    };
    render(<InlineCitationMarker ordinal={1} citation={bookWithReliability} />);

    await user.click(screen.getByRole('button'));

    expect(screen.queryByText('SAHIH')).not.toBeInTheDocument();
  });

  it('повторный клик закрывает popover', async () => {
    const user = userEvent.setup();
    render(<InlineCitationMarker ordinal={1} citation={BOOK_CITATION} />);

    const button = screen.getByRole('button');
    await user.click(button);
    expect(screen.getByTestId('inline-citation-popover')).toBeInTheDocument();

    await user.click(button);
    expect(screen.queryByTestId('inline-citation-popover')).not.toBeInTheDocument();
  });

  it('Escape закрывает popover', async () => {
    const user = userEvent.setup();
    render(<InlineCitationMarker ordinal={1} citation={BOOK_CITATION} />);

    await user.click(screen.getByRole('button'));
    expect(screen.getByTestId('inline-citation-popover')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByTestId('inline-citation-popover')).not.toBeInTheDocument();
  });
});
