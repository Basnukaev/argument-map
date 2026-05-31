import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ReactFlowProvider } from '@xyflow/react';
import SanadGraphNode from './SanadGraphNode';
import type { SanadFlowNodeData } from '@/apps/hadith/types';

/**
 * Смотрим только рендер карточки узла (наш JSX), не RF-интеграцию.
 * ReactFlowProvider обязателен — узел использует Handle. NodeProps имеет
 * много RF-specific полей, для рендера достаточно data — остальное даётся
 * через cast (паттерн из NodeCard.test.tsx).
 */

const BASE: SanadFlowNodeData = {
  narratorId: 'n1',
  nameAr: 'يحيى بن سعيد الأنصاري',
  nameLatin: 'yahya ibn said al-ansari',
  nameRu: 'Яхья ибн Саид аль-Ансари',
  kunya: 'أبو سعيد',
  laqab: 'الأنصاري',
  yearBirthHijri: null,
  yearDeathHijri: 143,
  birthplace: 'Медина',
  primaryResidence: 'Медина',
  deathPlace: 'Медина',
  reliabilityGrade: 'THIQA',
  reliabilityComment: 'Общее звено всех путей хадиса',
  generation: 'Табиин (мл.)',
  collection: null,
  tier: 4,
  role: 'NARRATOR',
};

function renderNode(data: SanadFlowNodeData) {
  const props = { id: 'narrator-n1', data, selected: false, type: 'sanad', isConnectable: false };
  return render(
    <ReactFlowProvider>
      {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
      <SanadGraphNode {...(props as any)} />
    </ReactFlowProvider>,
  );
}

describe('SanadGraphNode', () => {
  it('показывает арабское имя, перевод, оценку надёжности и год смерти', () => {
    renderNode(BASE);
    expect(screen.getByText('يحيى بن سعيد الأنصاري')).toBeInTheDocument();
    expect(screen.getByText('Яхья ибн Саид аль-Ансари')).toBeInTheDocument();
    expect(screen.getByText('ثقة')).toBeInTheDocument();
    expect(screen.getByText(/143/)).toBeInTheDocument();
  });

  it('у составителя (COLLECTOR) показывается название сборника', () => {
    renderNode({
      ...BASE,
      role: 'COLLECTOR',
      nameAr: 'محمد بن إسماعيل البخاري',
      nameRu: 'Мухаммад ибн Исмаиль аль-Бухари',
      collection: 'Сахих аль-Бухари',
    });
    expect(screen.getByText('Сахих аль-Бухари')).toBeInTheDocument();
  });

  it('узел Пророка ﷺ рендерится без оценки надёжности', () => {
    renderNode({
      ...BASE,
      role: 'PROPHET',
      nameAr: 'النبي محمد ﷺ',
      nameRu: 'Пророк Мухаммад ﷺ',
      reliabilityGrade: null,
      yearDeathHijri: null,
    });
    expect(screen.getByText('النبي محمد ﷺ')).toBeInTheDocument();
    expect(screen.queryByText('ثقة')).not.toBeInTheDocument();
  });
});
