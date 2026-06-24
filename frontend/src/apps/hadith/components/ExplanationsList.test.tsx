import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ExplanationsList from './ExplanationsList';
import type { ExplanationDto } from '@/apps/hadith/types';

function exp(p: Partial<ExplanationDto>): ExplanationDto {
  return {
    id: 'e1',
    kind: 'SHARH',
    bookName: 'فتح الباري',
    author: 'ابن حجر',
    authorDeathYear: null,
    page: null,
    volume: null,
    text: 'شرح',
    reference: null,
    hiddenByAdmin: false,
    hideReason: null,
    ...p,
  };
}

describe('ExplanationsList — год смерти автора (Фаза 5.b)', () => {
  it('показывает «ум. {year} г.х.» рядом с атрибуцией когда authorDeathYear задан', () => {
    render(
      <ExplanationsList
        explanations={[exp({ authorDeathYear: 852 })]}
        variant="SHARH"
        role={undefined}
        onChanged={() => {}}
      />,
    );
    expect(screen.getByText('ум. 852 г.х.')).toBeInTheDocument();
  });

  it('не рендерит подпись года когда authorDeathYear = null', () => {
    render(
      <ExplanationsList
        explanations={[exp({ authorDeathYear: null })]}
        variant="SHARH"
        role={undefined}
        onChanged={() => {}}
      />,
    );
    expect(screen.queryByText(/г\.х\./)).not.toBeInTheDocument();
  });

  it('GHARIB-карточка (слово-заголовок) тоже показывает год смерти автора', () => {
    render(
      <ExplanationsList
        explanations={[exp({ kind: 'GHARIB', reference: 'أَبْعَدَ', authorDeathYear: 606 })]}
        variant="GHARIB"
        role={undefined}
        onChanged={() => {}}
      />,
    );
    expect(screen.getByText('أَبْعَدَ')).toBeInTheDocument();
    expect(screen.getByText('ум. 606 г.х.')).toBeInTheDocument();
  });
});
