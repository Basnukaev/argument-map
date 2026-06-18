import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Pagination from './Pagination';

/**
 * Pagination (C20) — нумерованная пагинация. Тестируем поведение
 * пользователя: видимые номера + эллипсис, счётчик «N–M из T», скрытие
 * при одной странице, клик/disabled на краях, клавиатуру.
 */
describe('Pagination', () => {
  it('скрыт целиком при totalPages <= 1', () => {
    const { container } = render(
      <Pagination
        page={1}
        totalPages={1}
        totalElements={5}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    expect(container.firstChild).toBeNull();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('рендерит все номера без эллипсиса при малом totalPages (<=7)', () => {
    render(
      <Pagination
        page={2}
        totalPages={5}
        totalElements={100}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    for (const n of [1, 2, 3, 4, 5]) {
      expect(screen.getByRole('button', { name: `Страница ${n}` })).toBeInTheDocument();
    }
    // эллипсиса нет
    expect(screen.queryByText('…')).not.toBeInTheDocument();
  });

  it('показывает эллипсис и сжатый набор номеров при большом totalPages', () => {
    render(
      <Pagination
        page={3}
        totalPages={62}
        totalElements={1240}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    // current=3 → слоты: 1 2 [3] 4 … 62 (один разрыв справа)
    for (const n of [1, 2, 3, 4, 62]) {
      expect(screen.getByRole('button', { name: `Страница ${n}` })).toBeInTheDocument();
    }
    // далёкие страницы скрыты
    expect(screen.queryByRole('button', { name: 'Страница 30' })).not.toBeInTheDocument();
    // ровно один эллипсис (справа)
    expect(screen.getAllByText('…')).toHaveLength(1);
  });

  it('в середине показывает эллипсис с обеих сторон', () => {
    render(
      <Pagination
        page={30}
        totalPages={62}
        totalElements={1240}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    // 1 … 29 [30] 31 … 62
    for (const n of [1, 29, 30, 31, 62]) {
      expect(screen.getByRole('button', { name: `Страница ${n}` })).toBeInTheDocument();
    }
    expect(screen.getAllByText('…')).toHaveLength(2);
  });

  it('счётчик «N–M из T» корректен на первой странице', () => {
    render(
      <Pagination
        page={1}
        totalPages={62}
        totalElements={1240}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    expect(screen.getByText('1–20 из 1240')).toBeInTheDocument();
  });

  it('счётчик «N–M из T» корректен на средней странице', () => {
    render(
      <Pagination
        page={3}
        totalPages={62}
        totalElements={1240}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    // (3-1)*20+1 = 41, 3*20 = 60
    expect(screen.getByText('41–60 из 1240')).toBeInTheDocument();
  });

  it('счётчик на последней (неполной) странице зажимает «to» по total', () => {
    render(
      <Pagination
        page={3}
        totalPages={3}
        totalElements={45}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    // (3-1)*20+1 = 41, min(60, 45) = 45
    expect(screen.getByText('41–45 из 45')).toBeInTheDocument();
  });

  it('активная страница помечена aria-current="page"', () => {
    render(
      <Pagination
        page={3}
        totalPages={5}
        totalElements={100}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: 'Страница 3' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByRole('button', { name: 'Страница 2' })).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('клик по номеру вызывает onPageChange с этим 1-based номером', async () => {
    const onPageChange = vi.fn();
    render(
      <Pagination
        page={1}
        totalPages={5}
        totalElements={100}
        pageSize={20}
        onPageChange={onPageChange}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Страница 4' }));
    expect(onPageChange).toHaveBeenCalledWith(4);
  });

  it('prev disabled на первой странице, next активна', () => {
    render(
      <Pagination
        page={1}
        totalPages={5}
        totalElements={100}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: 'Предыдущая страница' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Следующая страница' })).not.toBeDisabled();
  });

  it('next disabled на последней странице, prev активна', () => {
    render(
      <Pagination
        page={5}
        totalPages={5}
        totalElements={100}
        pageSize={20}
        onPageChange={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: 'Следующая страница' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Предыдущая страница' })).not.toBeDisabled();
  });

  it('next ведёт на page+1, prev на page-1', async () => {
    const onPageChange = vi.fn();
    render(
      <Pagination
        page={3}
        totalPages={5}
        totalElements={100}
        pageSize={20}
        onPageChange={onPageChange}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Следующая страница' }));
    expect(onPageChange).toHaveBeenCalledWith(4);
    await userEvent.click(screen.getByRole('button', { name: 'Предыдущая страница' }));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it('клавиатура: ArrowRight → next, ArrowLeft → prev (фокус внутри nav)', async () => {
    const onPageChange = vi.fn();
    render(
      <Pagination
        page={3}
        totalPages={5}
        totalElements={100}
        pageSize={20}
        onPageChange={onPageChange}
      />,
    );
    // Фокус на кнопке внутри nav — keydown всплывает до nav.onKeyDown.
    screen.getByRole('button', { name: 'Страница 3' }).focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(onPageChange).toHaveBeenLastCalledWith(4);
    await userEvent.keyboard('{ArrowLeft}');
    expect(onPageChange).toHaveBeenLastCalledWith(2);
  });

  it('клавиатура: Home → первая, End → последняя', async () => {
    const onPageChange = vi.fn();
    render(
      <Pagination
        page={3}
        totalPages={62}
        totalElements={1240}
        pageSize={20}
        onPageChange={onPageChange}
      />,
    );
    screen.getByRole('button', { name: 'Страница 3' }).focus();
    await userEvent.keyboard('{Home}');
    expect(onPageChange).toHaveBeenLastCalledWith(1);
    await userEvent.keyboard('{End}');
    expect(onPageChange).toHaveBeenLastCalledWith(62);
  });

  it('клавиатура: ArrowLeft на первой странице не зовёт onPageChange (clamp)', async () => {
    const onPageChange = vi.fn();
    render(
      <Pagination
        page={1}
        totalPages={5}
        totalElements={100}
        pageSize={20}
        onPageChange={onPageChange}
      />,
    );
    screen.getByRole('button', { name: 'Страница 1' }).focus();
    await userEvent.keyboard('{ArrowLeft}');
    expect(onPageChange).not.toHaveBeenCalled();
  });

  it('loading disables все кнопки', () => {
    render(
      <Pagination
        page={3}
        totalPages={5}
        totalElements={100}
        pageSize={20}
        onPageChange={() => {}}
        loading
      />,
    );
    expect(screen.getByRole('button', { name: 'Предыдущая страница' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Следующая страница' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Страница 2' })).toBeDisabled();
  });
});
