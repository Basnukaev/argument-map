import { describe, it, expect, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import { useToastStore } from '@/shared/stores/toastStore';
import { useAuthStore } from '@/shared/stores/authStore';
import MatnTranslateControls from './MatnTranslateControls';

const BASE = 'http://test.local';
const TRANSLATE = `${BASE}/api/v1/hadith/matns/m1/translate`;
const PATCH_URL = `${BASE}/api/v1/hadith/matns/m1/translation`;

afterEach(() => {
  useToastStore.getState().clear();
  // Сброс роли между тестами (ADMIN-сценарии её выставляют).
  useAuthStore.setState({ user: null });
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

  // === C9: ADMIN-правка сохранённого перевода ===

  function asAdmin() {
    useAuthStore.setState({
      user: { id: 'u1', username: 'admin', email: 'a@x', role: 'ADMIN' },
    });
  }

  it('ADMIN + есть перевод: видна кнопка правки; клик → textarea с текущим текстом; PATCH → новый текст + toast', async () => {
    asAdmin();
    let body: { lang: string; text: string } | null = null;
    server.use(
      http.patch(PATCH_URL, async ({ request }) => {
        body = (await request.json()) as { lang: string; text: string };
        return HttpResponse.json({ matnId: 'm1', lang: 'ru', text: body.text, cached: true });
      }),
    );

    render(
      <MatnTranslateControls matnId="m1" textRu="Старый перевод" textEn={null} role="ADMIN" />,
    );

    // Кнопка правки видна (aria-label из i18n).
    const editBtn = screen.getByRole('button', { name: 'Редактировать перевод' });
    await userEvent.click(editBtn);

    // textarea предзаполнена текущим переводом.
    const textarea = screen.getByRole('textbox');
    expect(textarea).toHaveValue('Старый перевод');

    // Правим и сохраняем.
    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'Новый перевод');
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitForApi(() => {
      expect(screen.getByText('Новый перевод')).toBeInTheDocument();
    });
    // PATCH ушёл с {lang:"ru", text:"Новый перевод"}.
    expect(body).toEqual({ lang: 'ru', text: 'Новый перевод' });
    // Вышли из режима правки (textarea пропала).
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    // Тост успеха.
    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(1);
    expect(toasts[0]!.message).toBe('Перевод сохранён');
    expect(toasts[0]!.kind).toBe('success');
  });

  it('не-ADMIN + есть перевод: кнопки правки НЕТ', () => {
    render(<MatnTranslateControls matnId="m1" textRu="Готовый перевод" textEn={null} role="STUDENT" />);

    expect(screen.getByText('Готовый перевод')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Редактировать перевод' })).not.toBeInTheDocument();
  });

  it('без role-пропа (аноним) + есть перевод: кнопки правки НЕТ', () => {
    render(<MatnTranslateControls matnId="m1" textRu="Готовый перевод" textEn={null} />);

    expect(screen.queryByRole('button', { name: 'Редактировать перевод' })).not.toBeInTheDocument();
  });

  it('«Отмена» → возврат к тексту без изменений и без PATCH', async () => {
    // Никаких PATCH-handlers — запрос провалил бы тест.
    render(<MatnTranslateControls matnId="m1" textRu="Готовый перевод" textEn={null} role="ADMIN" />);

    await userEvent.click(screen.getByRole('button', { name: 'Редактировать перевод' }));
    const textarea = screen.getByRole('textbox');
    await userEvent.type(textarea, ' дополнение');

    await userEvent.click(screen.getByRole('button', { name: 'Отмена' }));

    // textarea пропала, исходный текст на месте.
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByText('Готовый перевод')).toBeInTheDocument();
  });

  it('«Сохранить» дизейблена при неизменённом тексте', async () => {
    render(<MatnTranslateControls matnId="m1" textRu="Готовый перевод" textEn={null} role="ADMIN" />);

    await userEvent.click(screen.getByRole('button', { name: 'Редактировать перевод' }));
    // Текст не менялся → Сохранить дизейблена.
    expect(screen.getByRole('button', { name: 'Сохранить' })).toBeDisabled();
  });

  it('ошибка PATCH (403) → toast.error, текст не изменён', async () => {
    server.use(
      http.patch(PATCH_URL, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Forbidden', status: 403, detail: 'Доступ запрещён' },
          { status: 403 },
        ),
      ),
    );

    render(<MatnTranslateControls matnId="m1" textRu="Старый перевод" textEn={null} role="ADMIN" />);

    await userEvent.click(screen.getByRole('button', { name: 'Редактировать перевод' }));
    const textarea = screen.getByRole('textbox');
    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'Попытка правки');
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitForApi(() => {
      const toasts = useToastStore.getState().toasts;
      expect(toasts).toHaveLength(1);
      expect(toasts[0]!.kind).toBe('error');
    });
    // Перевод в стейте не заменён (textarea ещё показывает черновик «Попытка правки»).
    expect(screen.getByRole('textbox')).toHaveValue('Попытка правки');
  });
});
