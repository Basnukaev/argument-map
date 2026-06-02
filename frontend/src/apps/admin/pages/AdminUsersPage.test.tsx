import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import AdminUsersPage from './AdminUsersPage';
import { useAuthStore } from '@/shared/stores/authStore';
import { useLocaleStore } from '@/shared/i18n';

const BASE = 'http://test.local';
const USERS_URL = `${BASE}/api/v1/users`;

const CREATED_AT = '2026-01-15T08:30:00Z';

const USER_ROW = {
  id: '99999999-9999-9999-9999-999999999999',
  username: 'alice',
  email: 'alice@x.local',
  role: 'USER' as const,
  enabled: true,
  createdAt: CREATED_AT,
  updatedAt: CREATED_AT,
};

function pagedResponse() {
  return {
    items: [USER_ROW],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminUsersPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('auth.user');
  }
  useAuthStore.setState({
    user: {
      id: '00000000-0000-0000-0000-000000000001',
      username: 'admin',
      email: 'admin@x.local',
      role: 'ADMIN',
    },
    accessToken: 'fake-jwt',
    isLoading: false,
    initialized: true,
  });
  useLocaleStore.setState({ locale: 'ru' });
  server.use(http.get(USERS_URL, () => HttpResponse.json(pagedResponse())));
});

describe('AdminUsersPage — locale-aware дата создания', () => {
  it('createdAt форматируется по локали UI (ru), а не дефолтным toLocaleDateString', async () => {
    renderPage();

    await waitForApi(() => {
      expect(screen.getByText('alice')).toBeInTheDocument();
    });

    // Ожидаемый ru-формат (день месяц год + время) — тот же форматтер что
    // useFormatDate('full'). Содержит русское название месяца «января».
    const expected = new Intl.DateTimeFormat('ru-RU', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(CREATED_AT));

    expect(screen.getByText(expected)).toBeInTheDocument();
    // sanity: русское название месяца присутствует (локаль применилась)
    expect(screen.getByText(/января/i)).toBeInTheDocument();
  });
});
