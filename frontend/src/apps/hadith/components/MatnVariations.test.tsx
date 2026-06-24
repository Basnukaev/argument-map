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
    collectionId: null,
    printedNumber: null,
    pageNo: null,
    volume: null,
    isPrimary: false,
    divergenceSummary: null,
    hiddenByAdmin: false,
    hideReason: null,
    ...p,
  };
}

const SHOW = 'Показать отличия от основной редакции';

describe('MatnVariations', () => {
  it('основная редакция раскрыта по умолчанию, остальные свёрнуты', () => {
    render(
      <MatnVariations
        matns={[
          matn({ id: 'p', textAr: 'الأعمال بالنيات', isPrimary: true }),
          matn({ id: 'v', textAr: 'الأعمال بالنية' }),
        ]}
      />,
    );
    // primary раскрыт → его текст виден; вариант свёрнут → кнопки diff ещё нет
    expect(screen.getByText('الأعمال بالنيات')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: SHOW })).not.toBeInTheDocument();
  });

  it('кнопка diff появляется после раскрытия НЕ-основной редакции', async () => {
    render(
      <MatnVariations
        matns={[
          matn({ id: 'p', textAr: 'الأعمال بالنيات', isPrimary: true }),
          matn({ id: 'v', textAr: 'الأعمال بالنية' }),
        ]}
      />,
    );
    // раскрываем вариант (его шапка содержит № — но проще по preview-тексту)
    const headers = screen.getAllByRole('button', { expanded: false });
    await userEvent.click(headers[0]!);
    expect(screen.getByRole('button', { name: SHOW })).toBeInTheDocument();
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
    const headers = screen.getAllByRole('button', { expanded: false });
    await userEvent.click(headers[0]!);
    await userEvent.click(screen.getByRole('button', { name: SHOW }));
    expect(screen.getByText(/зелёным/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Скрыть отличия' })).toBeInTheDocument();
  });

  it('пустой список → сообщение об отсутствии', () => {
    render(<MatnVariations matns={[]} />);
    expect(screen.getByText('Текст ещё не загружен')).toBeInTheDocument();
  });
});
