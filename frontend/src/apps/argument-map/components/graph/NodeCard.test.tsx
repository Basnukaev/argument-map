import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReactFlowProvider } from '@xyflow/react';
import NodeCard, { type NodeCardData } from './NodeCard';
import { usePreferencesStore } from '@/shared/stores/preferencesStore';
import type { components } from '@/shared/api/types';

type TranslationRef = components['schemas']['NodeTranslationRef'];

/**
 * NodeCard - смотрим только bilingual rendering. ReactFlowProvider обязателен
 * потому что компонент использует Handle. NodeProps в RF имеет много полей,
 * для рендера достаточно `data` + `selected` + `id` (остальное TS принимает
 * через unknown-cast - тесты НЕ проверяют RF integration, только наш JSX).
 *
 * После миграции 45 - переводы в `data.translations[]` (с attribution
 * переводчика). Default-перевод первый, далее по created_at ASC.
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
  inlineCitations: [],
  translations: [],
};

function makeTranslation(
  id: string,
  translatorName: string | null,
  language: 'ru' | 'en',
  body: string,
  isDefault: boolean,
): TranslationRef {
  return {
    id,
    translatorName: translatorName ?? undefined,
    language,
    body,
    isDefault,
  };
}

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

  it('без translations - не показывает toggle и рендерит только оригинал', () => {
    renderCard({ ...BASE_DATA });

    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /переключить режим/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/перевод/i)).not.toBeInTheDocument();
  });

  it('режим both с одним переводом - показывает оригинал и перевод с именем переводчика', () => {
    renderCard({
      ...BASE_DATA,
      originalLang: 'ar',
      translations: [
        makeTranslation('t-1', 'Кулиев', 'ru', 'Деяния оцениваются по намерениям', true),
      ],
    });

    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    expect(screen.getByText('Деяния оцениваются по намерениям')).toBeInTheDocument();
    // имя переводчика отображается (single translation - просто label, не dropdown)
    expect(screen.getByText('Кулиев')).toBeInTheDocument();
    // toggle доступен
    expect(
      screen.getByRole('button', { name: /переключить режим/i }),
    ).toBeInTheDocument();
  });

  it('режим original (глобальный) - скрывает перевод', () => {
    setPrefMode('original');
    renderCard({
      ...BASE_DATA,
      originalLang: 'ar',
      translations: [makeTranslation('t-1', 'Кулиев', 'ru', 'Деяния по намерениям', true)],
    });

    expect(screen.getByText('إنما الأعمال بالنيات')).toBeInTheDocument();
    expect(screen.queryByText('Деяния по намерениям')).not.toBeInTheDocument();
  });

  it('режим translation - показывает только translation, скрывает оригинал', () => {
    setPrefMode('translation');
    renderCard({
      ...BASE_DATA,
      originalLang: 'ar',
      translations: [makeTranslation('t-1', 'Кулиев', 'ru', 'Деяния по намерениям', true)],
    });

    expect(screen.queryByText('إنما الأعمال بالنيات')).not.toBeInTheDocument();
    expect(screen.getByText('Деяния по намерениям')).toBeInTheDocument();
  });

  it('toggle меняет local override - cyclic: both → original → translation → both', async () => {
    setPrefMode('both');
    renderCard({
      ...BASE_DATA,
      originalLang: 'ar',
      translations: [makeTranslation('t-1', null, 'ru', 'перевод текст', true)],
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

  it('multi-translation - показывает dropdown с translators и переключает', async () => {
    setPrefMode('translation');
    renderCard({
      ...BASE_DATA,
      originalLang: 'ar',
      translations: [
        makeTranslation('t-1', 'Кулиев', 'ru', 'Кулиев перевод', true),
        makeTranslation('t-2', 'Османов', 'ru', 'Османов перевод', false),
      ],
    });

    // default перевод (Кулиев) отображается, dropdown показывает имя
    expect(screen.getByText('Кулиев перевод')).toBeInTheDocument();
    expect(screen.queryByText('Османов перевод')).not.toBeInTheDocument();

    // открыть dropdown
    const dropdownTrigger = screen.getByLabelText('Выбрать переводчика');
    await userEvent.click(dropdownTrigger);

    // выбрать Османов
    const osmanovOption = screen.getByRole('button', { name: /Османов ru/ });
    await userEvent.click(osmanovOption);

    // теперь Османов перевод виден, Кулиев скрыт
    expect(screen.getByText('Османов перевод')).toBeInTheDocument();
    expect(screen.queryByText('Кулиев перевод')).not.toBeInTheDocument();
  });

  it('анонимный переводчик отображается как "Анонимный переводчик"', () => {
    setPrefMode('translation');
    renderCard({
      ...BASE_DATA,
      originalLang: 'ar',
      translations: [
        makeTranslation('t-1', null, 'ru', 'Перевод 1', true),
        makeTranslation('t-2', null, 'en', 'Translation 2', false),
      ],
    });

    // dropdown trigger показывает «Анонимный переводчик»
    expect(screen.getAllByText('Анонимный переводчик').length).toBeGreaterThan(0);
  });
});
