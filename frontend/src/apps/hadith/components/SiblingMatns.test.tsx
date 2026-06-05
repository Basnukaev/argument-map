import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import SiblingMatns from './SiblingMatns';

const HADITH_ID = 'aaaaaaaa-0000-0000-0000-000000000001';
const ENDPOINT = `http://test.local/api/v1/hadith/hadiths/${HADITH_ID}/sibling-matns`;

const SIBLINGS = [
  {
    hadithId: 'bbbbbbbb-0000-0000-0000-000000000002',
    externalId: 'ext-42',
    collectionNameAr: 'صحيح البخاري',
    collectionNameRu: 'Сахих аль-Бухари',
    printedNumber: 42,
    textAr: 'إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ',
  },
];

function renderSiblings(count = 2) {
  return render(
    <MemoryRouter>
      <SiblingMatns hadithId={HADITH_ID} resolvedTuruqCount={count} />
    </MemoryRouter>,
  );
}

describe('SiblingMatns', () => {
  it('отображает кнопку с числом передач до загрузки', () => {
    renderSiblings(3);
    expect(
      screen.getByRole('button', { name: /параллельных передач \(3\)/i }),
    ).toBeInTheDocument();
  });

  it('клик → fetch → карточки: имя сборника, текст и ссылка «Перейти»', async () => {
    server.use(http.get(ENDPOINT, () => HttpResponse.json(SIBLINGS)));

    renderSiblings(2);
    await userEvent.click(
      screen.getByRole('button', { name: /параллельных передач \(2\)/i }),
    );

    await waitForApi(() => {
      expect(screen.getByText('Сахих аль-Бухари')).toBeInTheDocument();
    });

    expect(screen.getByText('إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: /перейти/i });
    expect(link).toHaveAttribute('href', `/hadith/hadiths/${SIBLINGS[0]!.hadithId}`);
    // Кнопка исчезает после загрузки
    expect(
      screen.queryByRole('button', { name: /параллельных передач/i }),
    ).not.toBeInTheDocument();
  });

  it('пустой ответ → сообщение «ещё не импортированы»', async () => {
    server.use(http.get(ENDPOINT, () => HttpResponse.json([])));

    renderSiblings(1);
    await userEvent.click(
      screen.getByRole('button', { name: /параллельных передач \(1\)/i }),
    );

    await waitForApi(() => {
      expect(
        screen.getByText(/ещё не импортированы/i),
      ).toBeInTheDocument();
    });
  });

  it('ошибка сети → inline сообщение об ошибке', async () => {
    server.use(
      http.get(ENDPOINT, () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Server Error',
            status: 500,
            detail: 'внутренняя ошибка',
          },
          { status: 500 },
        ),
      ),
    );

    renderSiblings(2);
    await userEvent.click(
      screen.getByRole('button', { name: /параллельных передач \(2\)/i }),
    );

    await waitForApi(() => {
      expect(screen.getByText(/внутренняя ошибка/i)).toBeInTheDocument();
    });
    // Кнопка остаётся — пользователь может повторить
    expect(
      screen.getByRole('button', { name: /параллельных передач/i }),
    ).toBeInTheDocument();
  });
});
