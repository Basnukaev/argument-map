import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import SettingsPage from './SettingsPage';
import { usePreferencesStore } from '@/shared/stores/preferencesStore';
import { useAuthStore } from '@/shared/stores/authStore';

const API = 'http://test.local';

function renderPage() {
  return render(
    <MemoryRouter>
      <SettingsPage />
    </MemoryRouter>,
  );
}

function resetStores() {
  if (typeof window !== 'undefined') {
    window.localStorage.clear();
  }
  usePreferencesStore.setState({
    locale: 'ru',
    arabicFont: 'naskh',
    textSize: 'medium',
    hideTashkeelByDefault: false,
    transliteration: false,
    theme: 'system',
    isLoading: false,
    loaded: false,
  });
  useAuthStore.setState({
    user: {
      id: '00000000-0000-0000-0000-000000000001',
      username: 'tester',
      email: 'tester@test.local',
      role: 'USER',
    },
    accessToken: 'fake.access.token',
    isLoading: false,
    initialized: true,
  });
}

describe('SettingsPage', () => {
  beforeEach(() => {
    resetStores();
  });

  it('рендерит секции настроек включая язык, размер текста, tashkeel и транслит', () => {
    renderPage();

    expect(screen.getByText('Язык интерфейса')).toBeInTheDocument();
    expect(screen.getByText('Размер текста')).toBeInTheDocument();
    expect(
      screen.getByText('Огласовки (Tashkeel)'),
    ).toBeInTheDocument();
    expect(screen.getByText('Транслитерация')).toBeInTheDocument();
  });

  it('клик на язык AR отправляет PUT на бэк и обновляет store', async () => {
    let receivedBody: unknown = null;
    server.use(
      http.put(`${API}/api/v1/preferences/locale`, async ({ request }) => {
        receivedBody = await request.json();
        return HttpResponse.json({ locale: 'ar' });
      }),
    );

    renderPage();

    const arButton = screen.getByRole('button', { name: 'العربية' });
    await userEvent.click(arButton);

    expect(receivedBody).toEqual({ value: 'ar' });
    expect(usePreferencesStore.getState().locale).toBe('ar');
  });

  it('toggle транслитерации меняет state', async () => {
    server.use(
      http.put(
        `${API}/api/v1/preferences/transliteration`,
        () => HttpResponse.json({ transliteration: true }),
      ),
    );

    renderPage();

    const transCheckbox = screen.getByLabelText(/Показывать транслит/);
    await userEvent.click(transCheckbox);

    expect(usePreferencesStore.getState().transliteration).toBe(true);
  });
});
