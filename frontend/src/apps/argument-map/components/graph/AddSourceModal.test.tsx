import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import AddSourceModal from './AddSourceModal';

// jsdom не реализует HTMLDialogElement.showModal/close - мок
beforeAll(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  }
});

const BASE = 'http://test.local';
const NODE_ID = '11111111-1111-1111-1111-111111111111';
const SRC1 = '22222222-2222-2222-2222-222222222222';
const SRC2 = '33333333-3333-3333-3333-333333333333';

function fixtureSources() {
  return [
    {
      id: SRC1,
      sourceType: 'HADITH',
      title: 'Сахих Муслим, №1162',
      citation: 'Муслим 1162',
      reliability: 'SAHIH',
    },
    {
      id: SRC2,
      sourceType: 'BOOK',
      title: 'Аль-Бидая ва-н-нихая',
      citation: 'т.13, с.137',
    },
  ];
}

function renderModal(over: Partial<Parameters<typeof AddSourceModal>[0]> = {}) {
  const onClose = vi.fn();
  const onAttached = vi.fn();
  const result = render(
    <AddSourceModal nodeId={NODE_ID} onClose={onClose} onAttached={onAttached} {...over} />,
  );
  return { ...result, onClose, onAttached };
}

describe('AddSourceModal', () => {
  it('при mount грузит справочник и показывает список', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json(fixtureSources())),
    );
    renderModal();
    expect(await screen.findByText('Сахих Муслим, №1162')).toBeInTheDocument();
    expect(screen.getByText('Аль-Бидая ва-н-нихая')).toBeInTheDocument();
    expect(screen.getByText('SAHIH')).toBeInTheDocument();
  });

  it('поиск фильтрует список локально', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json(fixtureSources())),
    );
    renderModal();
    await screen.findByText('Сахих Муслим, №1162');
    const search = screen.getByLabelText('Поиск источника');
    await userEvent.type(search, 'Бидая');
    await waitFor(() =>
      expect(screen.queryByText('Сахих Муслим, №1162')).not.toBeInTheDocument(),
    );
    expect(screen.getByText('Аль-Бидая ва-н-нихая')).toBeInTheDocument();
  });

  it('пустой результат показывает плейсхолдер', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json(fixtureSources())),
    );
    renderModal();
    await screen.findByText('Сахих Муслим, №1162');
    const search = screen.getByLabelText('Поиск источника');
    await userEvent.type(search, 'нет такого');
    expect(await screen.findByText(/Ничего не нашлось/)).toBeInTheDocument();
  });

  it('после выбора показывает inputs quote/context, кнопка "Привязать" enabled', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json(fixtureSources())),
    );
    renderModal();
    const item = await screen.findByRole('option', { name: /Сахих Муслим/ });
    await userEvent.click(item);
    expect(screen.getByLabelText('Цитата')).toBeInTheDocument();
    expect(screen.getByLabelText('Контекст')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Привязать/ })).not.toBeDisabled();
  });

  it('Привязать делает POST и вызывает onAttached + onClose', async () => {
    let receivedBody: unknown = null;
    server.use(
      http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json(fixtureSources())),
      http.post(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, async ({ request }) => {
        receivedBody = await request.json();
        return HttpResponse.json({
          nodeId: NODE_ID,
          sourceId: SRC1,
          quote: 'Цитата',
          context: 'контекст',
        });
      }),
    );
    const { onAttached, onClose } = renderModal();
    await userEvent.click(await screen.findByRole('option', { name: /Сахих Муслим/ }));
    await userEvent.type(screen.getByLabelText('Цитата'), 'Цитата');
    await userEvent.type(screen.getByLabelText('Контекст'), 'контекст');
    await userEvent.click(screen.getByRole('button', { name: /Привязать/ }));
    await waitFor(() => expect(onAttached).toHaveBeenCalledTimes(1));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(receivedBody).toEqual({
      sourceId: SRC1,
      quote: 'Цитата',
      context: 'контекст',
    });
  });

  it('пустые quote/context не отправляются (undefined)', async () => {
    let receivedBody: Record<string, unknown> | null = null;
    server.use(
      http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json(fixtureSources())),
      http.post(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, async ({ request }) => {
        receivedBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ nodeId: NODE_ID, sourceId: SRC2 });
      }),
    );
    renderModal();
    await userEvent.click(await screen.findByRole('option', { name: /Бидая/ }));
    await userEvent.click(screen.getByRole('button', { name: /Привязать/ }));
    await waitFor(() => expect(receivedBody).not.toBeNull());
    expect(receivedBody).toEqual({ sourceId: SRC2 });
  });

  it('ошибка POST показывает сообщение', async () => {
    server.use(
      http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json(fixtureSources())),
      http.post(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/source-not-found',
            title: 'Источник не найден',
            status: 404,
            detail: 'Источник был удалён',
          },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    const { onAttached } = renderModal();
    await userEvent.click(await screen.findByRole('option', { name: /Сахих Муслим/ }));
    await userEvent.click(screen.getByRole('button', { name: /Привязать/ }));
    expect(await screen.findByText(/Источник был удалён/)).toBeInTheDocument();
    expect(onAttached).not.toHaveBeenCalled();
  });

  it('пустой справочник показывает подсказку и кнопку создания', async () => {
    server.use(http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json([])));
    renderModal();
    expect(await screen.findByText(/Справочник пуст/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Создать новый источник/ })).toBeInTheDocument();
  });

  describe('create-mode', () => {
    it('кнопка "Создать новый источник" переключает в create-mode и показывает форму', async () => {
      server.use(http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json([])));
      renderModal();
      await screen.findByText(/Справочник пуст/);
      await userEvent.click(
        screen.getByRole('button', { name: /Создать новый источник/ }),
      );
      expect(screen.getByRole('heading', { name: 'Создать новый источник' })).toBeInTheDocument();
      expect(screen.getByLabelText(/Название/)).toBeInTheDocument();
      expect(screen.getByLabelText(/Цитата для подписи/)).toBeInTheDocument();
    });

    it('Submit делает POST /sources, потом POST /nodes/{id}/sources с возвращенным id', async () => {
      const NEW_SRC_ID = '99999999-9999-9999-9999-999999999999';
      let createBody: Record<string, unknown> | null = null;
      let attachBody: Record<string, unknown> | null = null;
      server.use(
        http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json([])),
        http.post(`${BASE}/api/v1/sources`, async ({ request }) => {
          createBody = (await request.json()) as Record<string, unknown>;
          return HttpResponse.json({
            id: NEW_SRC_ID,
            sourceType: createBody.sourceType,
            title: createBody.title,
            citation: createBody.citation,
          });
        }),
        http.post(`${BASE}/api/v1/nodes/${NODE_ID}/sources`, async ({ request }) => {
          attachBody = (await request.json()) as Record<string, unknown>;
          return HttpResponse.json({ nodeId: NODE_ID, sourceId: NEW_SRC_ID });
        }),
      );
      const { onAttached, onClose } = renderModal();
      await screen.findByText(/Справочник пуст/);
      await userEvent.click(screen.getByRole('button', { name: /Создать новый источник/ }));
      await userEvent.type(
        screen.getByLabelText(/Название/),
        'AAOIFI Sharia Standard No. 21',
      );
      await userEvent.type(screen.getByLabelText(/Цитата для подписи/), '§4.2');
      await userEvent.click(screen.getByRole('button', { name: /Создать и привязать/ }));
      await waitFor(() => expect(onAttached).toHaveBeenCalledTimes(1));
      expect(onClose).toHaveBeenCalledTimes(1);
      expect(createBody).toEqual({
        sourceType: 'BOOK',
        title: 'AAOIFI Sharia Standard No. 21',
        citation: '§4.2',
      });
      expect(attachBody).toEqual({ sourceId: NEW_SRC_ID });
    });

    it('reliability показывается только для типа HADITH', async () => {
      server.use(http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json([])));
      renderModal();
      await screen.findByText(/Справочник пуст/);
      await userEvent.click(screen.getByRole('button', { name: /Создать новый источник/ }));
      // Дефолтный тип BOOK - reliability скрыт
      expect(screen.queryByText(/reliability/)).not.toBeInTheDocument();
      // Переключаем на HADITH
      await userEvent.click(screen.getByRole('radio', { name: /хадис/ }));
      expect(screen.getByText(/reliability/)).toBeInTheDocument();
      expect(screen.getByRole('radio', { name: /SAHIH/ })).toBeInTheDocument();
    });

    it('без reliability для HADITH кнопка submit disabled', async () => {
      server.use(http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json([])));
      renderModal();
      await screen.findByText(/Справочник пуст/);
      await userEvent.click(screen.getByRole('button', { name: /Создать новый источник/ }));
      await userEvent.type(screen.getByLabelText(/Название/), 'Какой-то хадис');
      await userEvent.click(screen.getByRole('radio', { name: /хадис/ }));
      // title заполнен но reliability ещё не выбрана - submit disabled
      expect(screen.getByRole('button', { name: /Создать и привязать/ })).toBeDisabled();
      // Выбираем reliability - кнопка становится активной
      await userEvent.click(screen.getByRole('radio', { name: /SAHIH/ }));
      expect(screen.getByRole('button', { name: /Создать и привязать/ })).not.toBeDisabled();
    });

    it('кнопка "К поиску" возвращает в search-mode', async () => {
      server.use(http.get(`${BASE}/api/v1/sources`, () => HttpResponse.json([])));
      renderModal();
      await screen.findByText(/Справочник пуст/);
      await userEvent.click(screen.getByRole('button', { name: /Создать новый источник/ }));
      expect(
        screen.getByRole('heading', { name: 'Создать новый источник' }),
      ).toBeInTheDocument();
      await userEvent.click(screen.getByRole('button', { name: /К поиску/ }));
      expect(
        screen.getByRole('heading', { name: 'Привязать источник' }),
      ).toBeInTheDocument();
      expect(screen.getByLabelText('Поиск источника')).toBeInTheDocument();
    });
  });
});
