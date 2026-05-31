import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MatnVariations from './MatnVariations';
import type { MatnDto } from '@/apps/hadith/types';

function matn(p: Partial<MatnDto>): MatnDto {
  return {
    id: 'm',
    textAr: '',
    textRu: null,
    textEn: null,
    sourceBookId: null,
    printedNumber: null,
    pageNo: null,
    volume: null,
    isPrimary: false,
    divergenceSummary: null,
    ...p,
  };
}

const SHOW = 'Показать отличия от основной редакции';

describe('MatnVariations', () => {
  it('кнопка diff есть только у НЕ-основной редакции', () => {
    render(
      <MatnVariations
        matns={[
          matn({ id: 'p', textAr: 'الأعمال بالنيات', isPrimary: true }),
          matn({ id: 'v', textAr: 'الأعمال بالنية' }),
        ]}
      />,
    );
    expect(screen.getByText('الأعمال بالنيات')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: SHOW })).toHaveLength(1);
  });

  it('toggle раскрывает пословный diff (легенда + кнопка скрытия)', async () => {
    render(
      <MatnVariations
        matns={[
          matn({ id: 'p', textAr: 'الأعمال بالنيات', isPrimary: true }),
          matn({ id: 'v', textAr: 'الأعمال بالنية' }),
        ]}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: SHOW }));
    expect(screen.getByText(/зелёным/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Скрыть отличия' })).toBeInTheDocument();
  });

  it('пустой список → сообщение об отсутствии', () => {
    render(<MatnVariations matns={[]} />);
    expect(screen.getByText('Текст ещё не загружен')).toBeInTheDocument();
  });
});
