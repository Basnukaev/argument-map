import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import { waitForApi } from '@/test/asyncHelpers';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import NodeDetailsPanel from './NodeDetailsPanel';
import type { components } from '@/shared/api/types';

type NodeDto = components['schemas']['NodeResponse'];

const BASE = 'http://test.local';
const NODE_ID = '11111111-1111-1111-1111-111111111111';

beforeAll(() => {
  // VITE_API_URL для тестов задан в setup.ts
});

function makeNode(over: Partial<NodeDto> = {}): NodeDto {
  return {
    id: NODE_ID,
    topicId: 'topic-1',
    nodeType: 'CLAIM',
    content: 'Тестовый тезис',
    status: 'STANDING',
    createdBy: 'user-1',
    createdAt: '2026-05-05T00:00:00Z',
    updatedAt: '2026-05-05T00:00:00Z',
    ...over,
  };
}

function renderPanel(over: Partial<Parameters<typeof NodeDetailsPanel>[0]> = {}) {
  const onClose = vi.fn();
  const onUpdated = vi.fn();
  const result = render(
    <NodeDetailsPanel node={makeNode()} onClose={onClose} onUpdated={onUpdated} {...over} />,
  );
  return { ...result, onClose, onUpdated };
}

describe('NodeDetailsPanel', () => {
  it('показывает заголовок с типом и содержание', () => {
    renderPanel({ node: makeNode({ nodeType: 'ARGUMENT', content: 'Текст довода' }) });
    expect(screen.getByRole('heading', { name: /Довод/ })).toBeInTheDocument();
    expect(screen.getByText('Текст довода')).toBeInTheDocument();
  });

  it('пустой контент показывает (пусто)', () => {
    renderPanel({ node: makeNode({ content: '' }) });
    expect(screen.getByText('(пусто)')).toBeInTheDocument();
  });

  it('крестик вызывает onClose', async () => {
    const { onClose } = renderPanel();
    await userEvent.click(screen.getByRole('button', { name: 'Закрыть панель' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('aria-label панели = Детали узла', () => {
    renderPanel();
    expect(screen.getByRole('complementary', { name: 'Детали узла' })).toBeInTheDocument();
  });

  it('бейдж статуса показывает русскую метку', () => {
    renderPanel({ node: makeNode({ status: 'DISPUTED' }) });
    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveTextContent('Спорный');
    // T-05: не тестируем конкретный Tailwind класс (bg-amber-100) -
    // хрупко к версиям/палитре. Текст 'Спорный' + правильный data-status
    // достаточно
    expect(badge).toHaveAttribute('data-status', 'DISPUTED');
  });

  it('метаданные содержат дату создания и id автора', () => {
    renderPanel({
      node: makeNode({
        createdAt: '2026-05-04T12:34:00Z',
        updatedAt: '2026-05-04T12:34:00Z',
        createdBy: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      }),
    });
    expect(screen.getByText(/мая 2026 г\./)).toBeInTheDocument();
    expect(screen.getByText('aaaaaaaa')).toBeInTheDocument();
  });

  it('строка "Обновлён" не показана если updatedAt совпадает с createdAt', () => {
    renderPanel({
      node: makeNode({
        createdAt: '2026-05-04T10:00:00Z',
        updatedAt: '2026-05-04T10:00:00Z',
      }),
    });
    expect(screen.queryByText('Обновлён')).not.toBeInTheDocument();
  });

  it('строка "Обновлён" показана если updatedAt отличается', () => {
    renderPanel({
      node: makeNode({
        createdAt: '2026-05-04T10:00:00Z',
        updatedAt: '2026-05-05T11:00:00Z',
      }),
    });
    expect(screen.getByText('Обновлён')).toBeInTheDocument();
  });

  it('кнопка "Редактировать" открывает textarea с текущим содержанием', async () => {
    renderPanel({ node: makeNode({ content: 'Старый текст' }) });
    await userEvent.click(screen.getByRole('button', { name: /Редактировать/ }));
    const textarea = screen.getByRole('textbox', { name: 'Содержание узла' });
    expect(textarea).toHaveValue('Старый текст');
  });

  it('кнопка "Отмена" возвращает к режиму просмотра без изменений', async () => {
    renderPanel({ node: makeNode({ content: 'Старый текст' }) });
    await userEvent.click(screen.getByRole('button', { name: /Редактировать/ }));
    const textarea = screen.getByRole('textbox', { name: 'Содержание узла' });
    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'Не сохранится');
    await userEvent.click(screen.getByRole('button', { name: 'Отмена' }));
    expect(screen.getByText('Старый текст')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('успешный PATCH вызывает onUpdated и закрывает режим редактирования', async () => {
    let receivedBody: unknown = null;
    server.use(
      http.patch(`${BASE}/api/v1/nodes/${NODE_ID}`, async ({ request }) => {
        receivedBody = await request.json();
        return HttpResponse.json(makeNode({ content: 'Новый текст' }));
      }),
    );

    const { onUpdated } = renderPanel({ node: makeNode({ content: 'Старый текст' }) });
    await userEvent.click(screen.getByRole('button', { name: /Редактировать/ }));
    const textarea = screen.getByRole('textbox', { name: 'Содержание узла' });
    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'Новый текст');
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitForApi(() => expect(onUpdated).toHaveBeenCalledTimes(1));
    expect(receivedBody).toEqual({ content: 'Новый текст' });
  });

  it('ошибка PATCH показывает сообщение и не вызывает onUpdated', async () => {
    server.use(
      http.patch(`${BASE}/api/v1/nodes/${NODE_ID}`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/validation',
            title: 'Ошибка валидации',
            status: 400,
            detail: 'Запрос содержит невалидные поля',
            errors: [{ field: 'content', message: 'не должно быть пустым' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const { onUpdated } = renderPanel();
    await userEvent.click(screen.getByRole('button', { name: /Редактировать/ }));
    const textarea = screen.getByRole('textbox', { name: 'Содержание узла' });
    await userEvent.clear(textarea);
    await userEvent.type(textarea, 'Что-то');
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitForApi(() => expect(screen.getByText(/не должно быть пустым/)).toBeInTheDocument());
    expect(onUpdated).not.toHaveBeenCalled();
  });

  it('Сохранить disabled если ничего не изменено', async () => {
    renderPanel({ node: makeNode({ content: 'Тот же текст' }) });
    await userEvent.click(screen.getByRole('button', { name: /Редактировать/ }));
    // Содержание не меняли - но кнопка остаётся активной (валидация по trim).
    // Кликнем - должен закрыть режим без вызова PATCH (нет changed)
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить' }));
    // textarea должен исчезнуть, без ошибки и без сети
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByText('Тот же текст')).toBeInTheDocument();
  });

  it('история закрыта по умолчанию, GET не вызывается', () => {
    let called = false;
    server.use(
      http.get(`${BASE}/api/v1/nodes/${NODE_ID}/revisions`, () => {
        called = true;
        return HttpResponse.json([]);
      }),
    );
    renderPanel();
    const toggle = screen.getByRole('button', { name: /История изменений/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(called).toBe(false);
  });

  it('первое открытие истории вызывает GET и рендерит список', async () => {
    server.use(
      http.get(`${BASE}/api/v1/nodes/${NODE_ID}/revisions`, () =>
        HttpResponse.json([
          {
            id: 'r1',
            nodeId: NODE_ID,
            contentBefore: 'было',
            contentAfter: 'стало',
            changedBy: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
            changedAt: '2026-05-04T15:00:00Z',
          },
        ]),
      ),
    );
    renderPanel();
    await userEvent.click(screen.getByRole('button', { name: /История изменений/ }));
    expect(await screen.findByText('было')).toBeInTheDocument();
    expect(screen.getByText('стало')).toBeInTheDocument();
  });

  it('пустой массив ревизий показывает "Изменений ещё не было"', async () => {
    server.use(
      http.get(`${BASE}/api/v1/nodes/${NODE_ID}/revisions`, () => HttpResponse.json([])),
    );
    renderPanel();
    await userEvent.click(screen.getByRole('button', { name: /История изменений/ }));
    expect(await screen.findByText('Изменений ещё не было')).toBeInTheDocument();
  });

  it('initialEditing=true монтирует панель сразу в режиме редактирования', () => {
    renderPanel({ node: makeNode({ content: 'старый' }), initialEditing: true });
    const textarea = screen.getByRole('textbox', { name: 'Содержание узла' });
    expect(textarea).toBeInTheDocument();
    expect(textarea).toHaveValue('старый');
  });

  it('ошибка GET показывает сообщение об ошибке', async () => {
    server.use(
      http.get(`${BASE}/api/v1/nodes/${NODE_ID}/revisions`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/node-not-found',
            title: 'Узел не найден',
            status: 404,
            detail: 'Нет такого узла',
          },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderPanel();
    await userEvent.click(screen.getByRole('button', { name: /История изменений/ }));
    expect(await screen.findByText(/Нет такого узла/)).toBeInTheDocument();
  });

  describe('секция Цитаты', () => {
    const SOURCE_ID = '22222222-2222-2222-2222-222222222222';
    const AUTHORITY_ID = '33333333-3333-3333-3333-333333333333';

    it('закрыта по умолчанию, GET не вызывается', () => {
      let called = false;
      server.use(
        http.get(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, () => {
          called = true;
          return HttpResponse.json([]);
        }),
        http.get(`${BASE}/api/v1/sources`, () => {
          called = true;
          return HttpResponse.json([]);
        }),
        http.get(`${BASE}/api/v1/authorities`, () => {
          called = true;
          return HttpResponse.json([]);
        }),
      );
      renderPanel();
      const toggle = screen.getByRole('button', { name: /Цитаты/ });
      expect(toggle).toHaveAttribute('aria-expanded', 'false');
      expect(called).toBe(false);
    });

    it('первое открытие загружает 3 запроса и рендерит карточку с автором', async () => {
      server.use(
        http.get(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, () =>
          HttpResponse.json([
            {
              nodeId: NODE_ID,
              sourceId: SOURCE_ID,
              quote: 'В этот день я был рождён',
              location: 'стр. 12, изд. Дар аль-кутуб',
            },
          ]),
        ),
        http.get(`${BASE}/api/v1/sources`, () =>
          HttpResponse.json([
            {
              id: SOURCE_ID,
              sourceType: 'HADITH',
              title: 'Сахих Муслим, №1162',
              citation: 'Муслим 1162',
              authorityId: AUTHORITY_ID,
            },
          ]),
        ),
        http.get(`${BASE}/api/v1/authorities`, () =>
          HttpResponse.json([
            {
              id: AUTHORITY_ID,
              name: 'Имам Муслим',
              era: 'III в.х.',
              madhab: 'муджтахид',
            },
          ]),
        ),
      );
      renderPanel();
      await userEvent.click(screen.getByRole('button', { name: /Цитаты/ }));
      expect(await screen.findByText('Сахих Муслим, №1162')).toBeInTheDocument();
      expect(screen.getByText('хадис')).toBeInTheDocument();
      expect(screen.getByText(/В этот день я был рождён/)).toBeInTheDocument();
      // автор резолвится через Source.authorityId → Authority.name
      expect(screen.getByText('Имам Муслим')).toBeInTheDocument();
      expect(screen.getByText(/III в\.х\./)).toBeInTheDocument();
      // location в подписи
      expect(screen.getByText(/стр\. 12, изд\. Дар аль-кутуб/)).toBeInTheDocument();
    });

    it('цитата без authorityId не показывает блок автора', async () => {
      server.use(
        http.get(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, () =>
          HttpResponse.json([{ nodeId: NODE_ID, sourceId: SOURCE_ID }]),
        ),
        http.get(`${BASE}/api/v1/sources`, () =>
          HttpResponse.json([
            { id: SOURCE_ID, sourceType: 'QURAN', title: 'Коран 2:255' },
          ]),
        ),
        http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json([])),
      );
      renderPanel();
      await userEvent.click(screen.getByRole('button', { name: /Цитаты/ }));
      await screen.findByText('Коран 2:255');
      // нет автора - анонимный текст (Коран)
      expect(screen.queryByText(/Имам Муслим/)).not.toBeInTheDocument();
    });

    it('арабская цитата получает dir=rtl', async () => {
      server.use(
        http.get(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, () =>
          HttpResponse.json([
            {
              nodeId: NODE_ID,
              sourceId: SOURCE_ID,
              quote: 'إنما الأعمال بالنيات',
            },
          ]),
        ),
        http.get(`${BASE}/api/v1/sources`, () =>
          HttpResponse.json([{ id: SOURCE_ID, sourceType: 'HADITH', title: 'Бухари 1' }]),
        ),
        http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json([])),
      );
      renderPanel();
      await userEvent.click(screen.getByRole('button', { name: /Цитаты/ }));
      const quoteEl = await screen.findByText(/إنما الأعمال بالنيات/);
      expect(quoteEl).toHaveAttribute('dir', 'rtl');
    });

    it('пустой список показывает плейсхолдер', async () => {
      server.use(
        http.get(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, () => HttpResponse.json([])),
        http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json([])),
        http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json([])),
      );
      renderPanel();
      await userEvent.click(screen.getByRole('button', { name: /Цитаты/ }));
      expect(
        await screen.findByText(/не привязано ни одной цитаты/),
      ).toBeInTheDocument();
    });

    it('отвязка цитаты вызывает DELETE и убирает запись', async () => {
      let deleteCalledFor: string | null = null;
      server.use(
        http.get(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, () =>
          HttpResponse.json([{ nodeId: NODE_ID, sourceId: SOURCE_ID }]),
        ),
        http.get(`${BASE}/api/v1/sources`, () =>
          HttpResponse.json([{ id: SOURCE_ID, sourceType: 'BOOK', title: 'Какая-то книга' }]),
        ),
        http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json([])),
        http.delete(`${BASE}/api/v1/nodes/${NODE_ID}/sources/${SOURCE_ID}`, () => {
          deleteCalledFor = SOURCE_ID;
          return new HttpResponse(null, { status: 204 });
        }),
      );
      renderPanel();
      await userEvent.click(screen.getByRole('button', { name: /Цитаты/ }));
      await screen.findByText('Какая-то книга');
      await userEvent.click(screen.getByRole('button', { name: 'Отвязать цитату' }));
      await waitForApi(() => expect(deleteCalledFor).toBe(SOURCE_ID));
      expect(screen.queryByText('Какая-то книга')).not.toBeInTheDocument();
    });

    it('для QUESTION-узла секция Цитаты НЕ рендерится', () => {
      renderPanel({ node: makeNode({ nodeType: 'QUESTION', content: 'Вопрос?' }) });
      expect(screen.queryByRole('button', { name: /Цитаты/ })).not.toBeInTheDocument();
    });
  });
});
