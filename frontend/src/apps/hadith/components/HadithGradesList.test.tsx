import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import HadithGradesList from './HadithGradesList';
import type { HadithGrade } from '@/apps/hadith/types';

describe('HadithGradesList', () => {
  it('рендерит оценки: учёный, степень и комментарий', () => {
    const grades: HadithGrade[] = [
      { scholar: 'аль-Бухари', grade: 'Сахих', note: 'Хадис №1' },
      { scholar: 'Критики', grade: 'Гариб, но сахих', note: null },
    ];
    render(<HadithGradesList grades={grades} />);
    expect(screen.getByText('аль-Бухари')).toBeInTheDocument();
    expect(screen.getByText('Сахих')).toBeInTheDocument();
    expect(screen.getByText('Хадис №1')).toBeInTheDocument();
    expect(screen.getByText('Гариб, но сахих')).toBeInTheDocument();
  });

  it('ничего не рендерит при пустом списке', () => {
    const { container } = render(<HadithGradesList grades={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
