import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import HadithGradesList from './HadithGradesList';
import type { HadithGrade } from '@/apps/hadith/types';

describe('HadithGradesList', () => {
  it('рендерит оценки: учёный, степень (enum→i18n) и комментарий', () => {
    const grades: HadithGrade[] = [
      {
        gradeId: 'g1',
        scholarId: 's1',
        scholarName: 'аль-Бухари',
        scholarFullName: 'Мухаммад ибн Исмаил аль-Бухари',
        scholarDeathYearHijri: 256,
        grade: 'SAHIH',
        gradeCitation: 'Сахих 1/1',
        note: 'Хадис №1',
      },
      {
        gradeId: 'g2',
        scholarId: 's2',
        scholarName: 'аль-Албани',
        scholarFullName: null,
        scholarDeathYearHijri: null,
        grade: 'DAIF',
        gradeCitation: null,
        note: null,
      },
    ];
    render(<HadithGradesList grades={grades} />);
    expect(screen.getByText('аль-Бухари')).toBeInTheDocument();
    // enum SAHIH/DAIF → русские лейблы из словаря
    expect(screen.getByText('Сахих (достоверный)')).toBeInTheDocument();
    expect(screen.getByText('Даиф (слабый)')).toBeInTheDocument();
    expect(screen.getByText('Хадис №1')).toBeInTheDocument();
    expect(screen.getByText('Сахих 1/1')).toBeInTheDocument();
    // год смерти учёного отрендерен (reuse ruling.died ключа)
    expect(screen.getByText(/256 г\.х\./)).toBeInTheDocument();
  });

  it('показывает дружелюбный empty-state при пустом списке', () => {
    render(<HadithGradesList grades={[]} />);
    expect(screen.getByText('Оценки учёных пока не добавлены')).toBeInTheDocument();
  });
});
