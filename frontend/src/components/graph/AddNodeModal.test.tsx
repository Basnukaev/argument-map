import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import AddNodeModal from './AddNodeModal';

const BASE = 'http://test.local';
const TOPIC_ID = 'topic-1';

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

function renderModal(props: Partial<Parameters<typeof AddNodeModal>[0]> = {}) {
  const onClose = vi.fn();
  const onCreated = vi.fn();
  const result = render(
    <AddNodeModal
      open={true}
      topicId={TOPIC_ID}
      onClose={onClose}
      onCreated={onCreated}
      {...props}
    />,
  );
  return { ...result, onClose, onCreated };
}

describe('AddNodeModal', () => {
  it('кнопка "Создать" disabled пока content пустой', () => {
    renderModal();
    expect(screen.getByRole('button', { name: 'Создать' })).toBeDisabled();
  });

  it('успешный POST вызывает onCreated и onClose с правильным телом', async () => {
    let received: unknown = null;
    server.use(
      http.post(`${BASE}/api/v1/nodes`, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 'new-node', topicId: TOPIC_ID });
      }),
    );

    const user = userEvent.setup();
    const { onClose, onCreated } = renderModal();

    // выбрать тип ARGUMENT
    await user.click(screen.getByText('Довод'));
    await user.type(screen.getByLabelText(/Содержание/i), 'Тестовый аргумент');

    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitFor(() => {
      expect(onCreated).toHaveBeenCalledOnce();
    });
    expect(onClose).toHaveBeenCalledOnce();
    expect(received).toEqual({
      topicId: TOPIC_ID,
      nodeType: 'ARGUMENT',
      content: 'Тестовый аргумент',
      weight: 5,
    });
  });

  it('показывает ошибку при 400 с errors[]', async () => {
    server.use(
      http.post(`${BASE}/api/v1/nodes`, () =>
        HttpResponse.json(
          {
            type: 'https://example.com/errors/validation',
            title: 'Ошибка',
            status: 400,
            errors: [{ field: 'content', message: 'слишком короткое' }],
          },
          { status: 400 },
        ),
      ),
    );

    const user = userEvent.setup();
    const { onCreated, onClose } = renderModal();

    await user.type(screen.getByLabelText(/Содержание/i), 'X');
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitFor(() => {
      expect(screen.getByText(/слишком короткое/i)).toBeInTheDocument();
    });
    expect(onCreated).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('кнопка "Отмена" вызывает onClose без POST', async () => {
    const user = userEvent.setup();
    const { onClose, onCreated } = renderModal();

    await user.click(screen.getByRole('button', { name: 'Отмена' }));

    expect(onClose).toHaveBeenCalledOnce();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('по умолчанию выбран тип CLAIM, weight=5', async () => {
    let received: unknown = null;
    server.use(
      http.post(`${BASE}/api/v1/nodes`, async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ id: 'n1' });
      }),
    );

    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText(/Содержание/i), 'X');
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    await waitFor(() => {
      expect(received).toMatchObject({ nodeType: 'CLAIM', weight: 5 });
    });
  });
});
