import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReactFlowProvider } from '@xyflow/react';
import NodeCard, { type NodeCardData } from './NodeCard';
import { usePreferencesStore } from '@/shared/stores/preferencesStore';

/**
 * NodeCard - смотрим только bilingual rendering. ReactFlowProvider обязателен
 * потому что компонент использует Handle. NodeProps в RF имеет много полей,
 * для рендера достаточно `data` + `selected` + `id` (остальное TS принимает
 * через unknown-cast - тесты НЕ проверяют RF integration, только наш JSX).
 */

const BASE_DATA: NodeCardData = {
  id: 'node-1',
  topicId: 'topic-1',
  nodeType: 'EVIDENCE',
  content: 'إنما الأعمال بالنيات',
  status: 'STANDING',
  posX: 0,
  posY: 0,
  zIndex: 0,
  createdBy: 'user-1',
  createdAt: '2026-05-18T00:00:00Z',
  updatedAt: '2026-05-18T00:00:00Z',
  voteUpvotes: 0,
  voteDownvotes: 0,
  voteScore: 0,
  userVote: undefined,
  inlineCitations: [],
};

function renderCard(data: NodeCardData) {
  // Жёсткий cast - NodeProps включает много RF-specific полей которые
  // не нужны для рендера NodeCard. Безопасно потому что компонент
  // использует только data + selected.
  const props = {
    id: data.id ?? 'node-1',
    data,
    selected: false,
    type: 'argumentNode',
    isConnectable: false,
    xPos: 0,
    yPos: 0,
    zIndex: 0,
    dragging: false,
  };
  return render(
    <ReactFlowProvider>
      {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
      <NodeCard {...(props as any)} />
    </ReactFlowProvider>,
  );
}

function setPrefMode(mode: 'original' | 'translation' | 'both') {
  usePreferencesStore.setState({
    locale: 'ru',
    arabicFont: 'naskh',
    textSize: 'medium',
    hideTashkeelByDefault: false,
    transliteration: false,
    theme: 'system',
    bilingualMode: mode,
    isLoading: false,
    loaded: true,
  });
}

describe('NodeCard bilingual', () => {
  beforeEach(() => {
    setPrefMode('both');
  });

  it('без translation - не показывает toggle и рендерит только оригинал', () => {
    renderCard({ ...BASE_DATA });

    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /переключить режим/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/перевод/i)).not.toBeInTheDocument();
  });

  it('режим both - показывает оригинал, разделитель и перевод', () => {
    renderCard({
      ...BASE_DATA,
      translation: 'Деяния оцениваются по намерениям',
      translationLang: 'ru',
      originalLang: 'ar',
    });

    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    expect(screen.getByText('Деяния оцениваются по намерениям')).toBeInTheDocument();
    // label «перевод» появляется только в режиме both
    expect(screen.getByText('перевод')).toBeInTheDocument();
    // toggle доступен
    expect(
      screen.getByRole('button', { name: /переключить режим/i }),
    ).toBeInTheDocument();
  });

  it('режим original (глобальный) - скрывает перевод', () => {
    setPrefMode('original');
    renderCard({
      ...BASE_DATA,
      translation: 'Деяния по намерениям',
      translationLang: 'ru',
      originalLang: 'ar',
    });

    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    expect(screen.queryByText('Деяния по намерениям')).not.toBeInTheDocument();
  });

  it('режим translation - показывает только translation, скрывает оригинал', () => {
    setPrefMode('translation');
    renderCard({
      ...BASE_DATA,
      translation: 'Деяния по намерениям',
      translationLang: 'ru',
      originalLang: 'ar',
    });

    expect(screen.queryByText('إنما الأعمال بالنيات')).not.toBeInTheDocument();
    expect(screen.getByText('Деяния по намерениям')).toBeInTheDocument();
  });

  it('toggle меняет local override - cyclic: both → original → translation → both', async () => {
    setPrefMode('both');
    renderCard({
      ...BASE_DATA,
      translation: 'перевод текст',
      translationLang: 'ru',
      originalLang: 'ar',
    });

    // both: оба видны
    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    expect(screen.getByText('перевод текст')).toBeInTheDocument();

    const toggle = screen.getByRole('button', { name: /переключить режим/i });

    // both → original
    await userEvent.click(toggle);
    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    expect(screen.queryByText('перевод текст')).not.toBeInTheDocument();

    // original → translation
    await userEvent.click(toggle);
    expect(screen.queryByText('إنما الأعمال بالنيات')).not.toBeInTheDocument();
    expect(screen.getByText('перевод текст')).toBeInTheDocument();
  });
});
