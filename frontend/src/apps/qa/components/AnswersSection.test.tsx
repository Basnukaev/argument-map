import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, delay } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import AnswersSection from './AnswersSection';
import Toaster from '@/shared/components/ui/Toaster';

const BASE = 'http://test.local';
const QUESTION_ID = 'qqqqqqqq-qqqq-qqqq-qqqq-qqqqqqqqqqqq';
// askedBy === VITE_DEV_USER_ID (см. test-setup) → isAsker true → видны
// кнопки «Принять».
const ASKER_ID = '00000000-0000-0000-0000-000000000001';

const ANSWER_A = {
  id: 'aaaaaaaa-0000-0000-0000-000000000001',
  body: 'Первый ответ',
  authorId: 'author-a',
  accepted: false,
  createdAt: '2026-05-01T10:00:00Z',
  voteScore: 0,
  userVote: 0,
};
const ANSWER_B = {
  id: 'bbbbbbbb-0000-0000-0000-000000000002',
  body: 'Второй ответ',
  authorId: 'author-b',
  accepted: false,
  createdAt: '2026-05-01T11:00:00Z',
  voteScore: 0,
  userVote: 0,
};

function renderSection() {
  return render(
    <>
      <AnswersSection
        questionId={QUESTION_ID}
        askedBy={ASKER_ID}
        acceptedAnswerId={undefined}
        onAcceptanceChange={() => {}}
      />
      <Toaster />
    </>,
  );
}

beforeEach(() => {
  server.use(
    http.get(`${BASE}/api/v1/questions/${QUESTION_ID}/answers`, () =>
      HttpResponse.json([ANSWER_A, ANSWER_B]),
    ),
  );
});

describe('AnswersSection — per-answer busy state', () => {
  it('операция над одним ответом блокирует ТОЛЬКО его кнопку, не кнопку другого', async () => {
    // Accept на ANSWER_A держим in-flight (delay), чтобы busy-state был
    // наблюдаем.
    server.use(
      http.post(
        `${BASE}/api/v1/questions/${QUESTION_ID}/accepted-answer/${ANSWER_A.id}`,
        async () => {
          await delay(100);
          return HttpResponse.json({}, { status: 200 });
        },
      ),
    );
    renderSection();

    await waitForApi(() => {
      expect(screen.getByText('Первый ответ')).toBeInTheDocument();
      expect(screen.getByText('Второй ответ')).toBeInTheDocument();
    });

    const acceptButtons = screen.getAllByRole('button', {
      name: /Принять как ответ/i,
    });
    expect(acceptButtons).toHaveLength(2);
    const [acceptA, acceptB] = acceptButtons;

    // До клика — обе доступны
    expect(acceptA).not.toBeDisabled();
    expect(acceptB).not.toBeDisabled();

    await userEvent.click(acceptA!);

    // Пока запрос на A in-flight: A disabled, B по-прежнему доступна
    // (busy теперь per-id Set, не один общий флаг).
    await waitForApi(() => {
      expect(acceptA).toBeDisabled();
    });
    expect(acceptB).not.toBeDisabled();

    // После завершения запроса A снова доступна (toast «Ответ принят»)
    await waitForApi(() => {
      expect(screen.getByText(/Ответ принят/i)).toBeInTheDocument();
    });
  });

  it('конкурентные операции над A и B держат обе кнопки disabled одновременно', async () => {
    // Регрессия: единый busyAnswerId при старте операции над B сбрасывал
    // busy для A (in-flight). Per-id Set делает их независимыми.
    server.use(
      http.post(
        `${BASE}/api/v1/questions/${QUESTION_ID}/accepted-answer/${ANSWER_A.id}`,
        async () => {
          await delay(150);
          return HttpResponse.json({}, { status: 200 });
        },
      ),
      http.post(
        `${BASE}/api/v1/questions/${QUESTION_ID}/accepted-answer/${ANSWER_B.id}`,
        async () => {
          await delay(150);
          return HttpResponse.json({}, { status: 200 });
        },
      ),
    );
    renderSection();

    await waitForApi(() => {
      expect(screen.getByText('Первый ответ')).toBeInTheDocument();
    });

    const acceptButtons = screen.getAllByRole('button', {
      name: /Принять как ответ/i,
    });
    const [acceptA, acceptB] = acceptButtons;

    await userEvent.click(acceptA!);
    await userEvent.click(acceptB!);

    // Обе операции in-flight → обе кнопки disabled. Старый единый флаг
    // оставил бы только B disabled.
    await waitForApi(() => {
      expect(acceptA).toBeDisabled();
      expect(acceptB).toBeDisabled();
    });
  });
});
