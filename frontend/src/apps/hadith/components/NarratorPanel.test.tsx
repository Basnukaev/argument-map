import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NarratorPanel from './NarratorPanel';
import type { SanadFlowNodeData } from '@/apps/hadith/types';

const DATA: SanadFlowNodeData = {
  narratorId: 'n1',
  nameAr: 'مالك بن أنس',
  nameLatin: 'malik ibn anas',
  nameRu: 'Малик ибн Анас',
  kunya: 'أبو عبد الله',
  laqab: 'إمام دار الهجرة',
  yearBirthHijri: 93,
  yearDeathHijri: 179,
  birthplace: 'Медина',
  primaryResidence: 'Медина',
  deathPlace: 'Медина',
  reliabilityGrade: 'THIQA',
  reliabilityComment: 'Имам Медины, автор Муватты',
  generation: 'Атба ат-табиин',
  tabaqa: null,
  gradeText: null,
  externalId: null,
  collection: null,
  tier: 5,
  role: 'COLLECTOR',
};

describe('NarratorPanel', () => {
  it('показывает имя, перевод, оценку надёжности, кунью и биографию', () => {
    render(<NarratorPanel data={DATA} onClose={() => {}} />);
    expect(screen.getByText('مالك بن أنس')).toBeInTheDocument();
    expect(screen.getByText('Малик ибн Анас')).toBeInTheDocument();
    expect(screen.getByText('ثقة')).toBeInTheDocument();
    expect(screen.getByText('أبو عبد الله')).toBeInTheDocument();
    expect(screen.getByText('Имам Медины, автор Муватты')).toBeInTheDocument();
    expect(screen.getByText(/93.*179/)).toBeInTheDocument();
  });

  it('вызывает onClose по кнопке закрытия', async () => {
    const onClose = vi.fn();
    render(<NarratorPanel data={DATA} onClose={onClose} />);
    await userEvent.click(screen.getByRole('button', { name: 'Закрыть' }));
    expect(onClose).toHaveBeenCalledOnce();
  });
});
