import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import AddAuthorityModal from './AddAuthorityModal';

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
const AUTH1 = '22222222-2222-2222-2222-222222222222';
const AUTH2 = '33333333-3333-3333-3333-333333333333';

function fixtureAuthorities() {
  return [
    {
      id: AUTH1,
      name: 'Ибн Хаджар аль-Аскаляни',
      era: 'VIII–IX в.х.',
      madhab: 'шафиитский',
    },
    {
      id: AUTH2,
      name: 'Ибн Таймия',
      era: 'VII–VIII в.х.',
      madhab: 'ханбалитский',
    },
  ];
}

function renderModal(over: Partial<Parameters<typeof AddAuthorityModal>[0]> = {}) {
  const onClose = vi.fn();
  const onAttached = vi.fn();
  const result = render(
    <AddAuthorityModal nodeId={NODE_ID} onClose={onClose} onAttached={onAttached} {...over} />,
  );
  return { ...result, onClose, onAttached };
}

describe('AddAuthorityModal', () => {
  it('при mount грузит справочник и показывает список', async () => {
    server.use(
      http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json(fixtureAuthorities())),
    );
    renderModal();
    expect(await screen.findByText('Ибн Хаджар аль-Аскаляни')).toBeInTheDocument();
    expect(screen.getByText('Ибн Таймия')).toBeInTheDocument();
    expect(screen.getByText(/шафиитский/)).toBeInTheDocument();
  });

  it('поиск фильтрует список', async () => {
    server.use(
      http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json(fixtureAuthorities())),
    );
    renderModal();
    await screen.findByText('Ибн Хаджар аль-Аскаляни');
    await userEvent.type(screen.getByLabelText('Поиск авторитета'), 'Таймия');
    await waitFor(() =>
      expect(screen.queryByText('Ибн Хаджар аль-Аскаляни')).not.toBeInTheDocument(),
    );
    expect(screen.getByText('Ибн Таймия')).toBeInTheDocument();
  });

  it('после выбора показывает StancePicker, дефолтный stance = HOLDS', async () => {
    server.use(
      http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json(fixtureAuthorities())),
    );
    renderModal();
    const item = await screen.findByRole('option', { name: /Ибн Хаджар/ });
    await userEvent.click(item);
    expect(screen.getByText(/Позиция авторитета/)).toBeInTheDocument();
    const holds = screen.getByRole('radio', { name: /Поддерживает/ });
    expect(holds).toBeChecked();
  });

  it('Привязать делает POST с правильным stance', async () => {
    let receivedBody: Record<string, unknown> | null = null;
    server.use(
      http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json(fixtureAuthorities())),
      http.post(
        `${BASE}/api/v1/nodes/${NODE_ID}/authorities`,
        async ({ request }) => {
          receivedBody = (await request.json()) as Record<string, unknown>;
          return HttpResponse.json({
            nodeId: NODE_ID,
            authorityId: AUTH2,
            stance: 'OPPOSES',
          });
        },
      ),
    );
    const { onAttached, onClose } = renderModal();
    await userEvent.click(await screen.findByRole('option', { name: /Ибн Таймия/ }));
    await userEvent.click(screen.getByRole('radio', { name: /Возражает/ }));
    await userEvent.click(screen.getByRole('button', { name: /Привязать/ }));
    await waitFor(() => expect(onAttached).toHaveBeenCalledTimes(1));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(receivedBody).toEqual({ authorityId: AUTH2, stance: 'OPPOSES' });
  });

  it('кнопка "Создать нового авторитета" переключает в create-mode', async () => {
    server.use(http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json([])));
    renderModal();
    await screen.findByText(/Справочник пуст/);
    await userEvent.click(
      screen.getByRole('button', { name: /Создать нового авторитета/ }),
    );
    expect(screen.getByLabelText('Имя')).toBeInTheDocument();
    expect(screen.getByLabelText('Эпоха')).toBeInTheDocument();
    expect(screen.getByLabelText('Мазхаб')).toBeInTheDocument();
  });

  it('Submit в create-mode делает POST /authorities, потом POST /nodes/{id}/authorities', async () => {
    const NEW_AUTH_ID = '99999999-9999-9999-9999-999999999999';
    let createBody: Record<string, unknown> | null = null;
    let attachBody: Record<string, unknown> | null = null;
    server.use(
      http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json([])),
      http.post(`${BASE}/api/v1/authorities`, async ({ request }) => {
        createBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({
          id: NEW_AUTH_ID,
          name: createBody.name,
          era: createBody.era,
          madhab: createBody.madhab,
        });
      }),
      http.post(`${BASE}/api/v1/nodes/${NODE_ID}/authorities`, async ({ request }) => {
        attachBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({
          nodeId: NODE_ID,
          authorityId: NEW_AUTH_ID,
          stance: 'NEUTRAL',
        });
      }),
    );
    const { onAttached } = renderModal();
    await screen.findByText(/Справочник пуст/);
    await userEvent.click(
      screen.getByRole('button', { name: /Создать нового авторитета/ }),
    );
    await userEvent.type(screen.getByLabelText('Имя'), 'Имам Малик');
    await userEvent.type(screen.getByLabelText('Эпоха'), 'II в.х.');
    await userEvent.type(screen.getByLabelText('Мазхаб'), 'маликитский');
    await userEvent.click(screen.getByRole('radio', { name: /Нейтрально/ }));
    await userEvent.click(screen.getByRole('button', { name: /Создать и привязать/ }));
    await waitFor(() => expect(onAttached).toHaveBeenCalledTimes(1));
    expect(createBody).toEqual({
      name: 'Имам Малик',
      era: 'II в.х.',
      madhab: 'маликитский',
    });
    expect(attachBody).toEqual({ authorityId: NEW_AUTH_ID, stance: 'NEUTRAL' });
  });

  it('пустое имя в create-mode блокирует submit', async () => {
    server.use(http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json([])));
    renderModal();
    await screen.findByText(/Справочник пуст/);
    await userEvent.click(
      screen.getByRole('button', { name: /Создать нового авторитета/ }),
    );
    expect(screen.getByRole('button', { name: /Создать и привязать/ })).toBeDisabled();
  });

  it('ошибка POST показывает сообщение', async () => {
    server.use(
      http.get(`${BASE}/api/v1/authorities`, () => HttpResponse.json(fixtureAuthorities())),
      http.post(`${BASE}/api/v1/nodes/${NODE_ID}/authorities`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/authority-not-found',
            title: 'Авторитет не найден',
            status: 404,
            detail: 'Авторитет был удалён',
          },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    const { onAttached } = renderModal();
    await userEvent.click(await screen.findByRole('option', { name: /Ибн Хаджар/ }));
    await userEvent.click(screen.getByRole('button', { name: /Привязать/ }));
    expect(await screen.findByText(/Авторитет был удалён/)).toBeInTheDocument();
    expect(onAttached).not.toHaveBeenCalled();
  });
});
