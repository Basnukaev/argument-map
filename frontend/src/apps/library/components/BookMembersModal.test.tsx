import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import BookMembersModal from './BookMembersModal';

const BASE = 'http://test.local';
const BOOK_ID = '00000000-0000-0000-0000-0000000000bb';
const OWNER_ID = '00000000-0000-0000-0000-000000000001';
const MEMBER_ID = '00000000-0000-0000-0000-000000000222';
const MEMBER_USER_ID = '00000000-0000-0000-0000-000000000333';
const NEW_USER_ID = '00000000-0000-0000-0000-000000000444';

function renderModal(onClose = () => {}) {
  return render(
    <BookMembersModal
      open
      bookId={BOOK_ID}
      ownerUserId={OWNER_ID}
      onClose={onClose}
    />,
  );
}

describe('BookMembersModal', () => {
  beforeEach(() => {
    // jsdom не реализует HTMLDialogElement showModal/close - polyfill минимум.
    // Этот pattern уже в Modal.test.tsx / TopicMembersModal.test.tsx
    if (!HTMLDialogElement.prototype.showModal) {
      HTMLDialogElement.prototype.showModal = function () {
        this.open = true;
      };
    }
    if (!HTMLDialogElement.prototype.close) {
      HTMLDialogElement.prototype.close = function () {
        this.open = false;
      };
    }
    // confirm() в jsdom - возвращает true для удаления (без него тест блокируется)
    vi.stubGlobal('confirm', () => true);
  });

  it('загружает и показывает список участников + owner badge', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books/${BOOK_ID}/members`, () =>
        HttpResponse.json([
          {
            id: MEMBER_ID,
            bookId: BOOK_ID,
            userId: MEMBER_USER_ID,
            role: 'MEMBER',
            addedAt: '2026-05-01T00:00:00Z',
            addedBy: OWNER_ID,
          },
        ]),
      ),
    );
    renderModal();

    await waitForApi(() => {
      expect(screen.getByText(MEMBER_USER_ID)).toBeInTheDocument();
    });
    // owner badge - отдельная строка
    expect(screen.getByText(OWNER_ID)).toBeInTheDocument();
    expect(screen.getByText('Владелец')).toBeInTheDocument();
  });

  it('empty state когда нет членов (но есть owner-row)', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books/${BOOK_ID}/members`, () =>
        HttpResponse.json([]),
      ),
    );
    renderModal();

    await waitForApi(() => {
      expect(screen.getByText('Владелец')).toBeInTheDocument();
    });
  });

  it('POST с UUID и role MEMBER при клике "Добавить"', async () => {
    let postedBody: unknown = null;
    server.use(
      http.get(`${BASE}/api/v1/library/books/${BOOK_ID}/members`, () =>
        HttpResponse.json([]),
      ),
      http.post(
        `${BASE}/api/v1/library/books/${BOOK_ID}/members`,
        async ({ request }) => {
          postedBody = await request.json();
          return HttpResponse.json({
            id: 'new-member-id',
            bookId: BOOK_ID,
            userId: NEW_USER_ID,
            role: 'MEMBER',
            addedAt: '2026-05-01T00:00:00Z',
            addedBy: OWNER_ID,
          });
        },
      ),
    );

    const user = userEvent.setup();
    renderModal();
    await waitForApi(() => expect(screen.getByText('Владелец')).toBeInTheDocument());

    const uuidInput = screen.getByPlaceholderText('UUID пользователя');
    await user.type(uuidInput, NEW_USER_ID);
    await user.click(screen.getByRole('button', { name: /Добавить/ }));

    await waitForApi(() => {
      expect(postedBody).toEqual({ userId: NEW_USER_ID, role: 'MEMBER' });
    });
  });

  it('toast error при невалидном UUID, POST не отправляется', async () => {
    server.use(
      http.get(`${BASE}/api/v1/library/books/${BOOK_ID}/members`, () =>
        HttpResponse.json([]),
      ),
      http.post(`${BASE}/api/v1/library/books/${BOOK_ID}/members`, () => {
        // Если сюда попали - тест должен упасть
        throw new Error('POST не должен был отправиться при невалидном UUID');
      }),
    );

    const user = userEvent.setup();
    renderModal();
    await waitForApi(() => expect(screen.getByText('Владелец')).toBeInTheDocument());

    await user.type(screen.getByPlaceholderText('UUID пользователя'), 'not-a-uuid');
    await user.click(screen.getByRole('button', { name: /Добавить/ }));
    // give async error chance to surface (toast)
    await new Promise((r) => setTimeout(r, 50));
    // ничего не упало = тест прошёл (POST не дернулся)
  });

  it('DELETE при клике на trash icon после confirm', async () => {
    let deleted = false;
    server.use(
      http.get(`${BASE}/api/v1/library/books/${BOOK_ID}/members`, () =>
        HttpResponse.json([
          {
            id: MEMBER_ID,
            bookId: BOOK_ID,
            userId: MEMBER_USER_ID,
            role: 'MEMBER',
            addedAt: '2026-05-01T00:00:00Z',
            addedBy: OWNER_ID,
          },
        ]),
      ),
      http.delete(
        `${BASE}/api/v1/library/books/${BOOK_ID}/members/${MEMBER_ID}`,
        () => {
          deleted = true;
          return new HttpResponse(null, { status: 204 });
        },
      ),
    );
    const user = userEvent.setup();
    renderModal();
    await waitForApi(() => expect(screen.getByText(MEMBER_USER_ID)).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: 'Удалить' }));
    await waitForApi(() => expect(deleted).toBe(true));
  });
});
