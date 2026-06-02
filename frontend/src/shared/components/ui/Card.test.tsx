import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Card from './Card';

describe('Card', () => {
  it('рендерит children внутри div', () => {
    render(
      <Card>
        <span>содержимое</span>
      </Card>,
    );
    expect(screen.getByText('содержимое')).toBeInTheDocument();
  });

  it('по умолчанию НЕ interactive (без cursor-pointer)', () => {
    render(<Card data-testid="card">x</Card>);
    expect(screen.getByTestId('card').className).not.toContain('cursor-pointer');
  });

  it('interactive=true добавляет cursor-pointer и hover-стили', () => {
    render(
      <Card interactive data-testid="card">
        x
      </Card>,
    );
    expect(screen.getByTestId('card').className).toContain('cursor-pointer');
  });

  it('selected меняет border на accent', () => {
    render(
      <Card selected data-testid="card">
        x
      </Card>,
    );
    expect(screen.getByTestId('card').className).toContain('border-accent-600');
  });

  it('onClick срабатывает на корневом div', async () => {
    const handleClick = vi.fn();
    const user = userEvent.setup();
    render(
      <Card interactive onClick={handleClick} data-testid="card">
        x
      </Card>,
    );

    await user.click(screen.getByTestId('card'));

    expect(handleClick).toHaveBeenCalledOnce();
  });

  describe('Card.Title', () => {
    it('автодетект арабского контента включает font-arabic', () => {
      // Арабская строка → детектится hasArabicScript helper'ом
      render(<Card.Title>القرآن الكريم</Card.Title>);
      const heading = screen.getByRole('heading');
      expect(heading.className).toContain('font-arabic');
    });

    it('latin/кириллица → font-serif (book-title)', () => {
      render(<Card.Title>Священный Коран</Card.Title>);
      const heading = screen.getByRole('heading');
      expect(heading.className).toContain('font-serif');
      expect(heading.className).not.toContain('font-arabic');
    });

    it('arabic prop override autodetection', () => {
      // Передан явно arabic=true для не-арабского содержимого
      render(<Card.Title arabic>Custom</Card.Title>);
      const heading = screen.getByRole('heading');
      expect(heading.className).toContain('font-arabic');
    });
  });

  describe('Card.Cover - обложка', () => {
    it('без imageUrl → letter-обложка (children), без img', () => {
      render(<Card.Cover color="#123456">A</Card.Cover>);
      expect(screen.getByText('A')).toBeInTheDocument();
      expect(screen.queryByRole('img')).not.toBeInTheDocument();
    });

    it('с imageUrl → рендерит <img> с этим src (letter скрыта)', () => {
      render(
        <Card.Cover color="#123456" imageUrl="https://example.org/cover.jpg">
          A
        </Card.Cover>,
      );
      const img = screen.getByRole('presentation');
      expect(img).toHaveAttribute('src', 'https://example.org/cover.jpg');
      expect(screen.queryByText('A')).not.toBeInTheDocument();
    });

    it('img onError → graceful fallback на letter-обложку', () => {
      render(
        <Card.Cover color="#123456" imageUrl="https://example.org/404.jpg">
          A
        </Card.Cover>,
      );
      const img = screen.getByRole('presentation');
      // archive.org thumbnail 404'нул - симулируем ошибку загрузки
      fireEvent.error(img);
      expect(screen.queryByRole('presentation')).not.toBeInTheDocument();
      expect(screen.getByText('A')).toBeInTheDocument();
    });
  });

  describe('Card namespace компоненты', () => {
    it('Card.Cover, Card.Body, Card.Eyebrow, Card.Meta - рендерятся', () => {
      render(
        <Card>
          <Card.Cover color="#fff">A</Card.Cover>
          <Card.Body>
            <Card.Eyebrow>label</Card.Eyebrow>
            <Card.Title>Заголовок</Card.Title>
            <Card.Meta>метаданные</Card.Meta>
          </Card.Body>
        </Card>,
      );
      expect(screen.getByText('A')).toBeInTheDocument();
      expect(screen.getByText('label')).toBeInTheDocument();
      expect(screen.getByText('Заголовок')).toBeInTheDocument();
      expect(screen.getByText('метаданные')).toBeInTheDocument();
    });
  });
});
