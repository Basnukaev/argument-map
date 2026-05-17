import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { waitForApi } from '@/test/asyncHelpers';
import { MemoryRouter, Routes, Route } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import TopicGraphPage from './TopicGraphPage';

const BASE = 'http://test.local';
const TOPIC_ID = 't-1';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/topics/${TOPIC_ID}`]}>
      <Routes>
        <Route path="/topics/:topicId" element={<TopicGraphPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('TopicGraphPage', () => {
  it('показывает индикатор загрузки пока запрос идёт', () => {
    server.use(
      http.get(`${BASE}/api/v1/topics/${TOPIC_ID}/graph`, async () => {
        await new Promise((r) => setTimeout(r, 1000));
        return HttpResponse.json({ topic: {}, nodes: [], edges: [] });
      }),
    );
    renderPage();
    expect(screen.getByText('Загрузка')).toBeInTheDocument();
  });

  it('рендерит заголовок с title темы и описание', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics/${TOPIC_ID}/graph`, () =>
        HttpResponse.json({
          topic: { id: TOPIC_ID, title: 'Дозволенность мавлида', description: 'Разбор позиций' },
          nodes: [],
          edges: [],
        }),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('Дозволенность мавлида')).toBeInTheDocument();
    });
    expect(screen.getByText('Разбор позиций')).toBeInTheDocument();
  });

  it('показывает empty-state когда нет узлов', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics/${TOPIC_ID}/graph`, () =>
        HttpResponse.json({ topic: { id: TOPIC_ID, title: 'T' }, nodes: [], edges: [] }),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText(/В этом графе пока нет узлов/i)).toBeInTheDocument();
    });
  });

  it('показывает illustrated not-found state при 404 без UUID наружу', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics/${TOPIC_ID}/graph`, () =>
        HttpResponse.json(
          {
            type: 'https://argumentmap.example/errors/topic-not-found',
            title: 'Тема не найдена',
            status: 404,
            detail: `Тема с id=${TOPIC_ID} не найдена`,
          },
          { status: 404 },
        ),
      ),
    );
    renderPage();
    // 404 → illustrated panel с serif h2 + CTA «К списку тем». Raw
    // detail с UUID не утекает наружу (security/UX hygiene)
    await waitForApi(() => {
      expect(screen.getByText('Тема не найдена')).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: /к списку тем/i })).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(TOPIC_ID))).not.toBeInTheDocument();
  });

  it('ссылка "К списку" указывает на /topics', async () => {
    server.use(
      http.get(`${BASE}/api/v1/topics/${TOPIC_ID}/graph`, () =>
        HttpResponse.json({ topic: { id: TOPIC_ID, title: 'T' }, nodes: [], edges: [] }),
      ),
    );
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('T')).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: 'К списку' })).toHaveAttribute('href', '/topics');
  });
});
