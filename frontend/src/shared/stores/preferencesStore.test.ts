import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { usePreferencesStore } from './preferencesStore';

const API = 'http://test.local';

function resetStore() {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('app.preferences');
  }
  usePreferencesStore.setState({
    locale: 'ru',
    arabicFont: 'naskh',
    textSize: 'medium',
    hideTashkeelByDefault: false,
    transliteration: false,
    theme: 'system',
    bilingualMode: 'both',
    isLoading: false,
    loaded: false,
  });
}

describe('preferencesStore', () => {
  beforeEach(() => {
    resetStore();
  });

  it('loadFromBackend - сохраняет prefs в state и persistCache', async () => {
    server.use(
      http.get(`${API}/api/v1/preferences`, () =>
        HttpResponse.json({
          locale: 'ar',
          textSize: 'large',
          transliteration: true,
        }),
      ),
    );

    await usePreferencesStore.getState().loadFromBackend();

    const s = usePreferencesStore.getState();
    expect(s.locale).toBe('ar');
    expect(s.textSize).toBe('large');
    expect(s.transliteration).toBe(true);
    // дефолты на отсутствующие ключи
    expect(s.arabicFont).toBe('naskh');
    expect(s.loaded).toBe(true);
    expect(window.localStorage.getItem('app.preferences')).toContain('ar');
  });

  it('set - optimistic update + PUT на бэк', async () => {
    let receivedBody: unknown = null;
    server.use(
      http.put(`${API}/api/v1/preferences/locale`, async ({ request }) => {
        receivedBody = await request.json();
        return HttpResponse.json({ locale: 'ar' });
      }),
    );

    await usePreferencesStore.getState().set('locale', 'ar');

    expect(usePreferencesStore.getState().locale).toBe('ar');
    expect(receivedBody).toEqual({ value: 'ar' });
    expect(window.localStorage.getItem('app.preferences')).toContain('ar');
  });

  it('set - revert при ошибке backend', async () => {
    usePreferencesStore.setState({ locale: 'ru' });
    server.use(
      http.put(`${API}/api/v1/preferences/locale`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/illegal-argument',
            title: 'Bad',
            status: 400,
          },
          { status: 400 },
        ),
      ),
    );

    await expect(
      usePreferencesStore.getState().set('locale', 'ar'),
    ).rejects.toMatchObject({ status: 400 });

    // revert на ru
    expect(usePreferencesStore.getState().locale).toBe('ru');
  });

  it('resetAll - возвращает все ключи к дефолтам', async () => {
    usePreferencesStore.setState({
      locale: 'ar',
      textSize: 'xl',
      transliteration: true,
    });
    server.use(
      http.delete(`${API}/api/v1/preferences/:key`, () =>
        HttpResponse.text('', { status: 204 }),
      ),
    );

    await usePreferencesStore.getState().resetAll();

    const s = usePreferencesStore.getState();
    expect(s.locale).toBe('ru');
    expect(s.textSize).toBe('medium');
    expect(s.transliteration).toBe(false);
  });

  it('resetLocal - чистит localStorage и state без сети', () => {
    usePreferencesStore.setState({ locale: 'ar' });
    window.localStorage.setItem('app.preferences', '{"locale":"ar"}');

    usePreferencesStore.getState().resetLocal();

    expect(usePreferencesStore.getState().locale).toBe('ru');
    expect(window.localStorage.getItem('app.preferences')).toBeNull();
  });
});
