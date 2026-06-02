import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ListToolbar from './ListToolbar';

describe('ListToolbar', () => {
  it('рендерит переданные слоты', () => {
    render(
      <ListToolbar
        search={<div>search-slot</div>}
        filters={<div>filters-slot</div>}
        sort={<div>sort-slot</div>}
        actions={<button type="button">action-slot</button>}
      />,
    );
    expect(screen.getByText('search-slot')).toBeInTheDocument();
    expect(screen.getByText('filters-slot')).toBeInTheDocument();
    expect(screen.getByText('sort-slot')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'action-slot' }),
    ).toBeInTheDocument();
  });

  it('не падает когда слоты не переданы', () => {
    const { container } = render(<ListToolbar />);
    // обёртка есть, но внутренних слот-блоков нет
    expect(container.firstChild).toBeInTheDocument();
    expect(container.querySelector('button')).toBeNull();
  });
});
