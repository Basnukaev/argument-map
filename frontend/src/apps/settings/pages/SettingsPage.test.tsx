import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import SettingsPage from './SettingsPage';

/**
 * SettingsPage теперь рендерит единственную секцию - FontSettings
 * (тема, пара шрифтов, арабский шрифт, масштаб, плотность, веса).
 * Дублирующая/мёртвая UserPreferencesSection (язык, textSize,
 * упрощённый арабский шрифт, tashkeel, транслит, bilingual) выпилена.
 */

function renderPage() {
  return render(
    <MemoryRouter>
      <SettingsPage />
    </MemoryRouter>,
  );
}

describe('SettingsPage', () => {
  it('рендерит секции FontSettings: тема, пара шрифтов, арабский шрифт', () => {
    renderPage();

    expect(screen.getByText('Настройки приложения')).toBeInTheDocument();
    // FontSettings контролы. «Тема» встречается дважды (группа + секция),
    // поэтому getAllByText
    expect(screen.getAllByText('Тема').length).toBeGreaterThan(0);
    expect(screen.getByText('Пара шрифтов')).toBeInTheDocument();
    expect(screen.getByText('Арабский шрифт')).toBeInTheDocument();
    expect(screen.getByText('Вес заголовка')).toBeInTheDocument();
    expect(screen.getByText('Плотность чтения')).toBeInTheDocument();
  });

  it('НЕ рендерит выпиленные дублирующие контролы (tashkeel / транслит / язык)', () => {
    renderPage();

    expect(screen.queryByText('Огласовки (Tashkeel)')).not.toBeInTheDocument();
    expect(screen.queryByText('Транслитерация')).not.toBeInTheDocument();
    expect(screen.queryByText('Язык интерфейса')).not.toBeInTheDocument();
    expect(screen.queryByText('Двуязычный режим узлов')).not.toBeInTheDocument();
  });
});
