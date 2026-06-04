import { describe, it, expect, beforeAll, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import HadithDetailPage from './HadithDetailPage';

const BASE = 'http://test.local';

// HadithSectionNav использует IntersectionObserver — jsdom его не даёт.
beforeAll(() => {
  class IOMock {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
    takeRecords(): [] {
      return [];
    }
  }
  vi.stubGlobal('IntersectionObserver', IOMock);
});

const DETAIL = {
  id: 'h1',
  collectionId: 'c1',
  primaryNumber: 1,
  normalizedMatn: 'إنما الأعمال بالنيات',
  status: 'CANONICAL',
  sourceId: null,
  createdAt: '2026-01-01',
  matns: [
    {
      id: 'm1',
      textAr: 'إنما الأعمال بالنيات',
      textRu: null,
      textEn: null,
      collectionId: 'c1',
      printedNumber: 1,
      pageNo: null,
      volume: null,
      isPrimary: true,
      divergenceSummary: null,
    },
  ],
  grades: [{ scholar: 'аль-Бухари', grade: 'Сахих', note: 'муттафакун алейхи' }],
};

const COLLECTIONS = [
  {
    id: 'c1',
    slug: 'bukhari',
    nameAr: 'صحيح البخاري',
    nameEn: 'Sahih al-Bukhari',
    nameRu: 'Сахих аль-Бухари',
    totalHadith: 7563,
    hadithCount: 100,
  },
];

const EMPTY_GRAPH = { hadithId: 'h1', nodes: [], edges: [], sanads: [] };

// alminasa-обогащённый detail: тип, глава, full_text с кликабельным рави,
// вердикт с provenance, шарх, такхридж (resolved + unresolved), издания.
const ALMINASA_DETAIL = {
  ...DETAIL,
  hadithType: 'مرفوع',
  chapterAr: 'كتاب بدء الوحي',
  subChapterAr: 'باب كيف كان بدء الوحي',
  fullTextAr:
    'حدثنا <a class=rawy id=5913>عُمَرُ بْنُ الْخَطَّابِ</a> ، قال : <a class=matn>إنما الأعمال بالنيات</a> .',
  editions: [{ editionName: 'الطبعة السلطانية', page: 5, volume: 1 }],
  rulings: [
    {
      rulerName: 'البخاري',
      rulerDeathYear: 256,
      rulingText: 'صحيح متفق عليه',
      bookName: 'الصحيح',
      page: 5,
      volume: 1,
      source: 'embedded',
      relatedExternalId: null,
    },
    {
      rulerName: 'ابن حجر',
      rulerDeathYear: 852,
      rulingText: 'حكم على الطريق',
      bookName: 'الفتح',
      page: null,
      volume: null,
      source: 'index',
      relatedExternalId: '999-2',
    },
  ],
  explanations: [
    {
      kind: 'SHARH',
      bookName: 'فتح الباري',
      author: 'ابن حجر',
      page: 11,
      volume: 1,
      text: 'شرح طويل جدا لهذا الحديث العظيم في النيات',
    },
  ],
  crossrefs: [
    { relatedExternalId: '200-1', relatedHadithId: 'h-sibling', note: 'م 1907' },
    { relatedExternalId: '300-1', relatedHadithId: null, note: 'د 2201' },
  ],
};

// Граф с externalId на узле рави (для клик-резолва из текста иснада).
const GRAPH_WITH_EXTERNAL = {
  hadithId: 'h1',
  nodes: [
    { id: 'prophet', role: 'PROPHET', data: { narratorId: null, nameAr: 'النبي محمد ﷺ', tier: 0 } },
    {
      id: 'narrator-1',
      role: 'COMPANION',
      data: {
        narratorId: 'n-umar',
        nameAr: 'عمر بن الخطاب',
        nameRu: 'Умар ибн аль-Хаттаб',
        reliabilityGrade: 'SAHABI',
        externalId: '5913',
        tier: 1,
      },
    },
  ],
  edges: [],
  sanads: [],
};

function mockEndpoints(
  detail: typeof DETAIL | typeof ALMINASA_DETAIL = DETAIL,
  graph: typeof EMPTY_GRAPH | typeof GRAPH_WITH_EXTERNAL = EMPTY_GRAPH,
) {
  server.use(
    http.get(`${BASE}/api/v1/hadith/hadiths/h1/detail`, () => HttpResponse.json(detail)),
    http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
    http.get(`${BASE}/api/v1/hadith/hadiths/:id/sanad-graph`, () => HttpResponse.json(graph)),
  );
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/hadith/hadiths/h1']}>
      <Routes>
        <Route path="/hadith/hadiths/:id" element={<HadithDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('HadithDetailPage', () => {
  it('рендерит четыре секции: текст, иснад, оценки, вариации', async () => {
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      // текст-герой (h1) — отдельно от копии текста в карточке вариации
      expect(
        screen.getByRole('heading', { level: 1, name: /إنما الأعمال بالنيات/ }),
      ).toBeInTheDocument();
      // имя сборника подтянуто из /collections
      expect(screen.getByText('Сахих аль-Бухари')).toBeInTheDocument();
      // секционная навигация
      expect(screen.getByRole('navigation', { name: 'Разделы хадиса' })).toBeInTheDocument();
      // оценки
      expect(screen.getByText('аль-Бухари')).toBeInTheDocument();
    });
  });

  it('секционная навигация ведёт якорными ссылками на секции', async () => {
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('link', { name: 'Текст' })).toHaveAttribute('href', '#text');
    });
    expect(screen.getByRole('link', { name: 'Иснад' })).toHaveAttribute('href', '#sanad');
    expect(screen.getByRole('link', { name: 'Оценки' })).toHaveAttribute('href', '#grades');
    expect(screen.getByRole('link', { name: 'Вариации' })).toHaveAttribute('href', '#variations');
  });

  it('показывает пояснение статуса CANONICAL', async () => {
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText(/Канонический — достоверный/)).toBeInTheDocument();
    });
  });

  it('пустой список оценок → дружелюбный empty-state', async () => {
    mockEndpoints({ ...DETAIL, grades: [] });
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('Оценки учёных пока не добавлены')).toBeInTheDocument();
    });
  });

  it('legacy-хадис без alminasa-полей: новые секции скрыты (graceful)', async () => {
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    });
    expect(screen.queryByRole('link', { name: 'Иснад (текст)' })).not.toBeInTheDocument();
    expect(screen.queryByText('Вердикты учёных')).not.toBeInTheDocument();
    expect(screen.queryByText('Такхридж')).not.toBeInTheDocument();
  });

  it('alminasa: бейдж типа + глава/подглава в шапке', async () => {
    mockEndpoints(ALMINASA_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('مرفوع')).toBeInTheDocument();
    });
    expect(screen.getByText('كتاب بدء الوحي')).toBeInTheDocument();
    expect(screen.getByText('باب كيف كان بدء الوحي')).toBeInTheDocument();
  });

  it('alminasa: вердикт с учёным, годом смерти и provenance-подписью', async () => {
    mockEndpoints(ALMINASA_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('البخاري')).toBeInTheDocument();
    });
    expect(screen.getByText('ум. 256 г.х.')).toBeInTheDocument();
    // embedded-вердикт — без подписи о параллели; index-вердикт — с подписью
    expect(screen.getByText('на параллельную передачу 999-2')).toBeInTheDocument();
  });

  it('alminasa: такхридж resolved → линк, unresolved → текст', async () => {
    mockEndpoints(ALMINASA_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    // resolved (relatedHadithId есть) → линк на сиблинга
    await waitForApi(() => {
      expect(screen.getByRole('link', { name: /Перейти к передаче 200-1/ })).toHaveAttribute(
        'href',
        '/hadith/hadiths/h-sibling',
      );
    });
    // счётчик в заголовке секции «Такхридж · передаётся в 2 местах»
    expect(
      screen.getByRole('heading', { name: /Такхридж.*передаётся в 2 местах/ }),
    ).toBeInTheDocument();
    // unresolved → внешний id текстом
    expect(screen.getByText('300-1')).toBeInTheDocument();
  });

  it('alminasa: шарх collapsible — текст скрыт пока не раскроют', async () => {
    mockEndpoints(ALMINASA_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('Шарх')).toBeInTheDocument();
    });
    const sharhText = 'شرح طويل جدا لهذا الحديث العظيم في النيات';
    expect(screen.queryByText(sharhText)).not.toBeInTheDocument();
    // раскрытие по клику на шапку (книга — fatḥ al-bārī)
    await userEvent.click(screen.getByRole('button', { name: /فتح الباري/ }));
    expect(screen.getByText(sharhText)).toBeInTheDocument();
  });

  it('клик по рави в тексте иснада открывает панель из графа (без доп. фетча)', async () => {
    let graphFetches = 0;
    server.use(
      http.get(`${BASE}/api/v1/hadith/hadiths/h1/detail`, () =>
        HttpResponse.json(ALMINASA_DETAIL),
      ),
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths/:id/sanad-graph`, () => {
        graphFetches += 1;
        return HttpResponse.json(GRAPH_WITH_EXTERNAL);
      }),
    );
    renderPage();
    // ждём пока граф загрузится (рави становится кликабельным — button)
    let rawyBtn: HTMLElement | null = null;
    await waitForApi(() => {
      rawyBtn = screen.getByRole('button', { name: 'عُمَرُ بْنُ الْخَطَّابِ' });
      expect(rawyBtn).toBeInTheDocument();
    });
    await userEvent.click(rawyBtn!);
    // панель (aside=complementary) открылась с данными ИЗ графа (перевод узла);
    // scope в панель — то же имя есть в карточке узла графа.
    const panel = screen.getByRole('complementary');
    expect(within(panel).getByText('Умар ибн аль-Хаттаб')).toBeInTheDocument();
    // граф запрошен ровно один раз (lifted fetch, без доп. фетча на клик)
    expect(graphFetches).toBe(1);
  });
});
