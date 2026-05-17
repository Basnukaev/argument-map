import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import AdminShamelaPage from './AdminShamelaPage';
import Toaster from '@/shared/components/ui/Toaster';

const BASE = 'http://test.local';

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminShamelaPage />
      <Toaster />
    </MemoryRouter>,
  );
}

describe('AdminShamelaPage · sync error UX', () => {
  it('502 shamela-api-error показывает локализованный toast вместо сырого Problem Details', async () => {
    server.use(
      http.get(`${BASE}/api/v1/admin/shamela/sync-status`, () =>
        HttpResponse.json({ masterVersion: 0, categoriesCount: 0, authorsCount: 0, booksCount: 0, mappedBooksCount: 0 }),
      ),
      http.post(`${BASE}/api/v1/admin/shamela/sync-master`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/shamela-api-error',
            title: 'shamela API недоступна',
            status: 502,
            detail: 'GET https://dev.shamela.ws/api/v1/patches/master?api_key=***&version=1261 - status 503',
          },
          { status: 502, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderPage();

    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Синхронизировать каталог/i })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole('button', { name: /Синхронизировать каталог/i }));

    // в toast должно быть локализованное сообщение, не сырой detail с api_key
    const toast = await screen.findByText(/внешний сервис shamela\.ws недоступен/i);
    expect(toast).toBeInTheDocument();
    // сырой detail с api_key=*** не показывается
    expect(screen.queryByText(/api_key/)).not.toBeInTheDocument();
  });

  it('archive error мапится в свой локализованный текст', async () => {
    server.use(
      http.get(`${BASE}/api/v1/admin/shamela/sync-status`, () =>
        HttpResponse.json({ masterVersion: 0, categoriesCount: 0, authorsCount: 0, booksCount: 0, mappedBooksCount: 0 }),
      ),
      http.post(`${BASE}/api/v1/admin/shamela/sync-master`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/shamela-archive-error',
            title: 'Ошибка распаковки архива shamela',
            status: 500,
            detail: 'corrupted zip header',
          },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Синхронизировать каталог/i })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole('button', { name: /Синхронизировать каталог/i }));

    expect(await screen.findByText(/не удалось распаковать архив shamela/i)).toBeInTheDocument();
  });

  it('неизвестный тип ошибки фолбэк на title+detail', async () => {
    server.use(
      http.get(`${BASE}/api/v1/admin/shamela/sync-status`, () =>
        HttpResponse.json({ masterVersion: 0, categoriesCount: 0, authorsCount: 0, booksCount: 0, mappedBooksCount: 0 }),
      ),
      http.post(`${BASE}/api/v1/admin/shamela/sync-master`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/some-other-error',
            title: 'Что-то пошло не так',
            status: 500,
            detail: 'детали ошибки',
          },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('button', { name: /Синхронизировать каталог/i })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole('button', { name: /Синхронизировать каталог/i }));

    expect(await screen.findByText(/Что-то пошло не так.*детали ошибки/)).toBeInTheDocument();
  });
});
