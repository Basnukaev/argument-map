import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoadMoreButton from './LoadMoreButton';

describe('LoadMoreButton', () => {
  it('рендерит кнопку «Показать ещё» когда hasNext', () => {
    render(<LoadMoreButton onClick={() => {}} loading={false} hasNext />);
    expect(
      screen.getByRole('button', { name: /Показать ещё/ }),
    ).toBeInTheDocument();
  });

  it('клик вызывает onClick', async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(<LoadMoreButton onClick={onClick} loading={false} hasNext />);
    await user.click(screen.getByRole('button', { name: /Показать ещё/ }));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('disabled и показывает Загрузка при loading', () => {
    render(<LoadMoreButton onClick={() => {}} loading hasNext />);
    const btn = screen.getByRole('button', { name: /Загрузка/ });
    expect(btn).toBeDisabled();
  });

  it('кнопка скрыта когда !hasNext', () => {
    render(
      <LoadMoreButton
        onClick={() => {}}
        loading={false}
        hasNext={false}
        shownCount={10}
        totalCount={10}
      />,
    );
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('строка счётчика показывается даже без hasNext если есть totalCount', () => {
    render(
      <LoadMoreButton
        onClick={() => {}}
        loading={false}
        hasNext={false}
        shownCount={10}
        totalCount={42}
      />,
    );
    expect(screen.getByText('Показано 10 из 42')).toBeInTheDocument();
  });

  it('рендерит null когда !hasNext и нет счётчика', () => {
    const { container } = render(
      <LoadMoreButton onClick={() => {}} loading={false} hasNext={false} />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
