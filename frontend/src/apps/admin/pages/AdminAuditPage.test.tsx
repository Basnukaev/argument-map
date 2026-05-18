import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import AdminAuditPage from './AdminAuditPage';
import Toaster from '@/shared/components/ui/Toaster';
import { useAuthStore } from '@/shared/stores/authStore';

const BASE = 'http://test.local';
const AUDIT_URL = `${BASE}/api/v1/audit/admin`;

const ADMIN_USER = {
  id: '00000000-0000-0000-0000-000000000001',
  username: 'admin',
  email: 'admin@argumentmap.local',
  role: 'ADMIN' as const,
  enabled: true,
  createdAt: '2026-01-01T00:00:00Z',
};

interface AuditItem {
  id: string;
  entityType: string;
  entityId: string;
  parentEntityType?: string;
  parentEntityId?: string;
  action: string;
  actorUserId: string;
  actorUsername?: string;
  changes: string;
  createdAt: string;
}

function makeItem(overrides: Partial<AuditItem> = {}): AuditItem {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    entityType: 'TOPIC',
    entityId: '22222222-2222-2222-2222-222222222222',
    action: 'CREATE',
    actorUserId: ADMIN_USER.id,
    actorUsername: 'admin',
    changes: '{"created":{"title":"Test","visibility":"PUBLIC"}}',
    createdAt: '2026-05-18T12:00:00Z',
    ...overrides,
  };
}

function pagedResponse(items: AuditItem[], hasNext = false) {
  return {
    items,
    page: 0,
    size: 50,
    totalElements: items.length,
    totalPages: 1,
    hasNext,
    hasPrev: false,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminAuditPage />
      <Toaster />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('auth.user');
  }
  useAuthStore.setState({
    user: ADMIN_USER,
    accessToken: 'fake-jwt',
    isLoading: false,
    initialized: true,
  });
});

describe('AdminAuditPage', () => {
  it('рендерит таблицу с записями audit log', async () => {
    server.use(
      http.get(AUDIT_URL, () =>
        HttpResponse.json(
          pagedResponse([
            makeItem({ id: 'a1', action: 'CREATE', entityType: 'TOPIC' }),
            makeItem({
              id: 'a2',
              action: 'DELETE',
              entityType: 'NODE',
              entityId: '33333333-3333-3333-3333-333333333333',
              changes: '{"deleted":{"content":"hello"}}',
            }),
          ]),
        ),
      ),
    );
    renderPage();

    await waitForApi(() => {
      // header h1 проверим - страница загрузилась
      expect(screen.getByRole('heading', { name: /Audit log системы/i })).toBeInTheDocument();
    });

    // обе action-badge должны появиться - select dropdown тоже содержит
    // эти строки в `<option>`, поэтому фильтруем по span (badge в таблице)
    await waitForApi(() => {
      const createBadges = screen
        .getAllByText('CREATE')
        .filter((el) => el.tagName === 'SPAN');
      expect(createBadges.length).toBeGreaterThan(0);
    });
    const deleteBadges = screen
      .getAllByText('DELETE')
      .filter((el) => el.tagName === 'SPAN');
    expect(deleteBadges.length).toBeGreaterThan(0);
    // NODE entity type показан (entity-type column - <div>, есть и в <option>)
    const nodeCells = screen.getAllByText('NODE').filter((el) => el.tagName === 'DIV');
    expect(nodeCells.length).toBeGreaterThan(0);
  });

  it('Apply фильтра entityType=TOPIC отправляет правильный query', async () => {
    let capturedUrl = '';
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(pagedResponse([makeItem()]));
      }),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByRole('heading', { name: /Audit log системы/i })).toBeInTheDocument();
    });

    // первый запрос - без фильтров
    expect(capturedUrl).toContain('page=0');
    expect(capturedUrl).not.toContain('entityType=');

    // меняем entityType select на TOPIC
    const selects = screen.getAllByRole('combobox');
    // первый select - entityType
    await userEvent.selectOptions(selects[0]!, 'TOPIC');
    await userEvent.click(screen.getByRole('button', { name: /Применить/i }));

    await waitForApi(() => {
      expect(capturedUrl).toContain('entityType=TOPIC');
    });
  });

  it('View Details открывает modal с JSON changes', async () => {
    server.use(
      http.get(AUDIT_URL, () =>
        HttpResponse.json(
          pagedResponse([
            makeItem({
              id: 'a1',
              changes: '{"created":{"title":"My Topic","visibility":"PUBLIC"}}',
            }),
          ]),
        ),
      ),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByRole('heading', { name: /Audit log системы/i })).toBeInTheDocument();
    });

    // button "Подробнее" в строке
    const detailsBtn = await screen.findByRole('button', { name: /Подробнее/i });
    await userEvent.click(detailsBtn);

    // modal с pretty-printed JSON. "My Topic" должно быть видно
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/My Topic/)).toBeInTheDocument();
    expect(within(dialog).getByText(/PUBLIC/)).toBeInTheDocument();
  });

  it('empty state когда audit log пуст', async () => {
    server.use(http.get(AUDIT_URL, () => HttpResponse.json(pagedResponse([]))));
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText(/Audit log пуст/i)).toBeInTheDocument();
    });
  });

  it('Load More подгружает следующую страницу и аппендит к существующему списку', async () => {
    let callCount = 0;
    server.use(
      http.get(AUDIT_URL, ({ request }) => {
        callCount += 1;
        const url = new URL(request.url);
        const page = url.searchParams.get('page');
        if (page === '0') {
          return HttpResponse.json(
            pagedResponse(
              [makeItem({ id: 'a1', action: 'CREATE', entityType: 'TOPIC' })],
              true,
            ),
          );
        }
        return HttpResponse.json({
          items: [makeItem({ id: 'a2', action: 'UPDATE', entityType: 'NODE' })],
          page: 1,
          size: 50,
          totalElements: 2,
          totalPages: 2,
          hasNext: false,
          hasPrev: true,
        });
      }),
    );
    renderPage();

    await waitForApi(() => {
      const createBadges = screen
        .getAllByText('CREATE')
        .filter((el) => el.tagName === 'SPAN');
      expect(createBadges.length).toBeGreaterThan(0);
    });

    const loadMore = await screen.findByRole('button', { name: /Показать ещё/i });
    await userEvent.click(loadMore);

    await waitForApi(() => {
      const updateBadges = screen
        .getAllByText('UPDATE')
        .filter((el) => el.tagName === 'SPAN');
      expect(updateBadges.length).toBeGreaterThan(0);
    });
    // обе строки видны - аппенд, не replace
    const createBadges = screen
      .getAllByText('CREATE')
      .filter((el) => el.tagName === 'SPAN');
    expect(createBadges.length).toBeGreaterThan(0);
    expect(callCount).toBe(2);
  });
});
