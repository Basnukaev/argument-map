import { describe, it, expect, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useToastStore } from '@/shared/stores/toastStore';
import MatnTranslateControls from './MatnTranslateControls';

const BASE = 'http://test.local';
const TRANSLATE = `${BASE}/api/v1/hadith/matns/m1/translate`;

afterEach(() => {
  useToastStore.getState().clear();
});

describe('MatnTranslateControls', () => {
  it('happy RU: кнопка → лоадер → перевод появился, POST с {lang:"ru"}', async () => {
    let bodyLang: string | null = null;
    server.use(
      http.post(TRANSLATE, async ({ request }) => {
        const body = (await request.json()) as { lang: string };
        bodyLang = body.lang;
        return HttpResponse.json({
          matnId: 'm1',
          lang: 'ru',
          text: 'Поистине, дела по намерениям',
          cached: false,
        });
      }),
    );

    render(<MatnTranslateControls matnId="m1" textRu={null} textEn={null} />);

    const ruBtn = screen.getByRole('button', { name: 'Перевод RU' });
    await userEvent.click(ruBtn);

    await waitForApi(() => {
      expect(screen.getByText('Поистине, дела по намерениям')).toBeInTheDocument();
    });
    // запрошенный язык
    expect(bodyLang).toBe('ru');
    // после успешного перевода RU-кнопка скрыта, EN-кнопка остаётся
    expect(screen.queryByRole('button', { name: 'Перевод RU' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Перевод EN' })).toBeInTheDocument();
  });

  it('уже-переведённый: textRu в пропсах → текст сразу, без кнопки и без POST', () => {
    // никаких handlers — любой запрос провалил бы тест (onUnhandledRequest: error)
    render(<MatnTranslateControls matnId="m1" textRu="Готовый перевод" textEn={null} />);

    expect(screen.getByText('Готовый перевод')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Перевод RU' })).not.toBeInTheDocument();
    // EN ещё нет → кнопка EN есть
    expect(screen.getByRole('button', { name: 'Перевод EN' })).toBeInTheDocument();
  });

  it('503 llm-not-configured → тост «AI-провайдер не настроен»', async () => {
    server.use(
      http.post(TRANSLATE, () =>
        HttpResponse.json(
          {
            type: 'https://argument-map/problems/llm-not-configured',
            title: 'Service Unavailable',
            status: 503,
          },
          { status: 503 },
        ),
      ),
    );

    render(<MatnTranslateControls matnId="m1" textRu={null} textEn={null} />);
    await userEvent.click(screen.getByRole('button', { name: 'Перевод RU' }));

    await waitForApi(() => {
      const toasts = useToastStore.getState().toasts;
      expect(toasts).toHaveLength(1);
      expect(toasts[0]!.message).toBe('AI-провайдер не настроен');
      expect(toasts[0]!.kind).toBe('error');
    });
    // кнопка осталась (перевод не получен)
    expect(screen.getByRole('button', { name: 'Перевод RU' })).toBeInTheDocument();
  });

  it('happy EN: POST с {lang:"en"} → перевод появился', async () => {
    let bodyLang: string | null = null;
    server.use(
      http.post(TRANSLATE, async ({ request }) => {
        const body = (await request.json()) as { lang: string };
        bodyLang = body.lang;
        return HttpResponse.json({ matnId: 'm1', lang: 'en', text: 'Deeds by intentions', cached: false });
      }),
    );

    render(<MatnTranslateControls matnId="m1" textRu={null} textEn={null} />);
    await userEvent.click(screen.getByRole('button', { name: 'Перевод EN' }));

    await waitForApi(() => {
      expect(screen.getByText('Deeds by intentions')).toBeInTheDocument();
    });
    expect(bodyLang).toBe('en');
  });
});
