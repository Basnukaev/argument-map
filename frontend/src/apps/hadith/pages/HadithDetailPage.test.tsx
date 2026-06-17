import { describe, it, expect, beforeAll, afterEach, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi, waitForUi } from '@/test/asyncHelpers';
import { useAuthStore, type AuthUser } from '@/shared/stores/authStore';
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

// Гейт «Добавить оценку» — SCHOLAR+. По умолчанию тесты анонимны (user=null →
// кнопки нет). После каждого теста сбрасываем сессию, чтобы SCHOLAR-юзер из
// grade-теста не протекал в соседние (где секция оценок ожидается скрытой).
afterEach(() => {
  useAuthStore.setState({ user: null, accessToken: null });
});

function loginAs(role: AuthUser['role']) {
  useAuthStore.setState({
    user: { id: 'u-test', username: 'tester', email: 'tester@test.local', role },
    accessToken: 'test-token',
  });
}

const DETAIL = {
  id: 'h1',
  collectionId: 'c1',
  primaryNumber: 1,
  normalizedMatn: 'إنما الأعمال بالنيات',
  status: 'CANONICAL',
  sourceId: null,
  createdAt: '2026-01-01',
  externalId: '1-1',
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
    {
      id: 'm2',
      textAr: 'الأعمال بالنية',
      textRu: null,
      textEn: null,
      collectionId: 'c1',
      printedNumber: 2,
      pageNo: null,
      volume: null,
      isPrimary: false,
      divergenceSummary: null,
    },
  ],
  grades: [
    {
      gradeId: 'gr1',
      scholarId: 'sc1',
      scholarName: 'аль-Бухари',
      scholarFullName: 'Мухаммад ибн Исмаил аль-Бухари',
      // 261, не 256 — иначе «ум. 256 г.х.» столкнётся с ruling البخاري (256)
      // в ALMINASA_DETAIL (он спредит DETAIL.grades) и getByText найдёт два.
      scholarDeathYearHijri: 261,
      grade: 'SAHIH',
      gradeCitation: null,
      note: 'муттафакун алейхи',
    },
  ],
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
  authenticity: 'SAHIH',
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
      relatedHadithId: null,
      relatedCollectionNameRu: null,
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
      relatedHadithId: 'h-ruling-sibling',
      relatedCollectionNameRu: 'Сунан ат-Тирмизи',
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
      reference: null,
    },
  ],
  crossrefs: [
    {
      relatedExternalId: '200-1',
      relatedHadithId: 'h-sibling',
      numbers: ['1907'],
      collectionNameAr: 'صحيح مسلم',
      collectionNameRu: 'Сахих Муслим',
    },
    {
      relatedExternalId: '300-1',
      relatedHadithId: null,
      numbers: ['2201'],
      collectionNameAr: 'سنن أبي داود',
      collectionNameRu: 'Сунан Абу Дауд',
    },
  ],
};

// Хадис с тремя kind толкований — для проверки разделения на секции.
const THREE_KINDS_DETAIL = {
  ...ALMINASA_DETAIL,
  explanations: [
    {
      kind: 'SHARH',
      bookName: 'فتح الباري',
      author: 'ابن حجر',
      page: 11,
      volume: 1,
      text: 'شرح طويل جدا لهذا الحديث العظيم في النيات',
      reference: null,
    },
    {
      kind: 'ILAL',
      bookName: 'علل الدارقطني',
      author: 'الدارقطني',
      page: 7,
      volume: 3,
      text: 'علة خفية في إسناد هذا الحديث رغم ظاهر الصحة',
      reference: null,
    },
    {
      kind: 'GHARIB',
      bookName: 'النهاية في غريب الحديث',
      author: 'ابن الأثير',
      page: 9,
      volume: 1,
      text: 'تفسير الكلمة الغريبة من المعجم',
      reference: 'أَبْعَدَ',
    },
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

  it('показывает пояснение статуса CANONICAL (провенанс)', async () => {
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText(/Канонический — из Сахихайн/)).toBeInTheDocument();
    });
  });

  it('пустой список оценок → секция и пункт навигации скрыты', async () => {
    mockEndpoints({ ...DETAIL, grades: [] });
    renderPage();
    await waitForApi(() => {
      // страница загрузилась (текст-герой виден)
      expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    });
    // ручные оценки платформы пусты → ни заголовка секции, ни пункта навигации
    expect(screen.queryByRole('link', { name: 'Оценки' })).not.toBeInTheDocument();
    expect(screen.queryByText('Оценки учёных')).not.toBeInTheDocument();
  });

  it('вариации скрыты при ≤1 матне, показаны при >1', async () => {
    // single-matn → секция «Вариации» и её пункт навигации скрыты
    mockEndpoints({ ...DETAIL, matns: DETAIL.matns.slice(0, 1) });
    const { unmount } = renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    });
    expect(screen.queryByRole('link', { name: 'Вариации' })).not.toBeInTheDocument();
    unmount();

    // multi-matn (DETAIL по умолчанию — 2 матна) → секция видна
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('link', { name: 'Вариации' })).toBeInTheDocument();
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

  it('alminasa: бейджи типа (i18n) + достоверности + глава/подглава в шапке', async () => {
    mockEndpoints(ALMINASA_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    // тип хадиса مرفوع рендерится локализованным термином «Марфуʿ» (i18n)
    await waitForApi(() => {
      expect(screen.getByText('Марфуʿ')).toBeInTheDocument();
    });
    // ось достоверности (authenticity=SAHIH) — отдельный бейдж «Сахих»
    expect(screen.getByText('Сахих')).toBeInTheDocument();
    // ось провенанса (status=CANONICAL) — бейдж «Сахихайн»
    expect(screen.getByText('Сахихайн')).toBeInTheDocument();
    expect(screen.getByText('كتاب بدء الوحي')).toBeInTheDocument();
    expect(screen.getByText('باب كيف كان بدء الوحي')).toBeInTheDocument();
  });

  it('бейдж достоверности скрыт когда authenticity отсутствует', async () => {
    // DETAIL (без authenticity) → бейджа достоверности нет, но провенанс есть
    mockEndpoints();
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('Сахихайн')).toBeInTheDocument();
    });
    // ни одного из лейблов достоверности
    expect(screen.queryByText('Сахих')).not.toBeInTheDocument();
    expect(screen.queryByText('Даиф')).not.toBeInTheDocument();
  });

  it('alminasa: вердикт с учёным, годом смерти и бейджем параллели', async () => {
    mockEndpoints(ALMINASA_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('البخاري')).toBeInTheDocument();
    });
    expect(screen.getByText('ум. 256 г.х.')).toBeInTheDocument();
    // index-вердикт с resolved relatedHadithId → бейдж-ссылка на сиблинга
    // с именем сборника и mono external id
    const badge = screen.getByRole('link', { name: /Сунан ат-Тирмизи/ });
    expect(badge).toHaveAttribute('href', '/hadith/hadiths/h-ruling-sibling');
    expect(within(badge).getByText('999-2')).toBeInTheDocument();
  });

  it('ruling-бейдж: self-вердикт (relatedExternalId === externalId страницы) без бейджа', async () => {
    // вердикт на эту же запись — relatedExternalId совпадает с externalId хадиса
    const selfRulingDetail = {
      ...ALMINASA_DETAIL,
      externalId: '1-1',
      rulings: [
        {
          rulerName: 'الترمذي',
          rulerDeathYear: 279,
          rulingText: 'حسن صحيح',
          bookName: 'الجامع',
          page: null,
          volume: null,
          source: 'index',
          relatedExternalId: '1-1',
          relatedHadithId: 'h1',
          relatedCollectionNameRu: 'Сахих аль-Бухари',
        },
      ],
    };
    mockEndpoints(selfRulingDetail, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(screen.getByText('الترمذي')).toBeInTheDocument();
    });
    // бейдж параллели НЕ показан (вердикт на эту же запись)
    expect(screen.queryByRole('link', { name: /Сахих аль-Бухари/ })).not.toBeInTheDocument();
  });

  it('alminasa: такхридж resolved → линк, unresolved → текст', async () => {
    mockEndpoints(ALMINASA_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    // resolved (relatedHadithId есть) → линк «Перейти» на сиблинга
    await waitForApi(() => {
      expect(screen.getByRole('link', { name: 'Перейти' })).toHaveAttribute(
        'href',
        '/hadith/hadiths/h-sibling',
      );
    });
    // имя сборника + номер печатного издания рендерятся
    expect(screen.getByText('Сахих Муслим')).toBeInTheDocument();
    expect(screen.getByText('№1907')).toBeInTheDocument();
    // счётчик в заголовке секции «Такхридж · передаётся в 2 местах»
    expect(
      screen.getByRole('heading', { name: /Такхридж.*передаётся в 2 местах/ }),
    ).toBeInTheDocument();
    // unresolved → подпись «не импортирована» + внешний id текстом
    expect(screen.getByText('не импортирована')).toBeInTheDocument();
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

  it('три kind толкований → три секции (шарх / иляль / гариб) с навигацией', async () => {
    mockEndpoints(THREE_KINDS_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    // заголовки трёх секций
    await waitForApi(() => {
      expect(screen.getByRole('heading', { name: /Шарх/ })).toBeInTheDocument();
    });
    expect(screen.getByRole('heading', { name: /Иляль/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Гариб/ })).toBeInTheDocument();
    // пункты навигации на все три секции
    expect(screen.getByRole('link', { name: 'Шарх' })).toHaveAttribute('href', '#explanations');
    expect(screen.getByRole('link', { name: 'Иляль' })).toHaveAttribute('href', '#ilal');
    expect(screen.getByRole('link', { name: 'Гариб' })).toHaveAttribute('href', '#gharib');
    // подзаголовки-пояснения иляля и гариба
    expect(screen.getByText('Скрытые дефекты передачи, отмеченные критиками')).toBeInTheDocument();
    expect(
      screen.getByText('Толкования редких слов матна из классических словарей'),
    ).toBeInTheDocument();
  });

  it('GHARIB-карточка: слово (reference) в заголовке, словарь рядом', async () => {
    mockEndpoints(THREE_KINDS_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    // слово أَبْعَدَ — заголовок карточки гариба (видно до раскрытия)
    await waitForApi(() => {
      expect(screen.getByText('أَبْعَدَ')).toBeInTheDocument();
    });
    // словарь·автор рядом со словом
    expect(screen.getByText('النهاية في غريب الحديث · ابن الأثير')).toBeInTheDocument();
    // толкование свёрнуто; кнопка-шапка раскрытия содержит слово
    const gharibText = 'تفسير الكلمة الغريبة من المعجم';
    expect(screen.queryByText(gharibText)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /أَبْعَدَ/ }));
    expect(screen.getByText(gharibText)).toBeInTheDocument();
  });

  it('гариб-слово подсвечено в hero-матне; клик открывает поповер с толкованием', async () => {
    // hero-матн содержит слово تطوي; GHARIB.reference تَطْوَى (с огласовкой +
    // алиф-максура) должно сматчиться после нормализации и подсветиться.
    const gharibInMatn = {
      ...ALMINASA_DETAIL,
      normalizedMatn: 'وادع اهل الصفه تطوي بطونهم',
      explanations: [
        {
          kind: 'GHARIB',
          bookName: 'النهاية في غريب الحديث',
          author: 'ابن الأثير',
          page: 9,
          volume: 1,
          text: 'يطوون = يضمون بطونهم من الجوع',
          reference: 'تَطْوَى',
        },
      ],
    };
    mockEndpoints(gharibInMatn, GRAPH_WITH_EXTERNAL);
    renderPage();
    // подсвеченное слово в hero — кнопка с aria-haspopup внутри h1
    let wordBtn: HTMLElement | null = null;
    await waitForApi(() => {
      wordBtn = screen.getByRole('button', { name: 'تطوي' });
      expect(wordBtn).toHaveAttribute('aria-haspopup', 'dialog');
    });
    // толкование скрыто пока не кликнуть
    const explainText = 'يطوون = يضمون بطونهم من الجوع';
    expect(screen.queryByText(explainText)).not.toBeInTheDocument();
    await userEvent.click(wordBtn!);
    // поповер открылся: толкование + словарь·автор
    const popover = screen.getByRole('dialog');
    expect(within(popover).getByText(explainText)).toBeInTheDocument();
    expect(within(popover).getByText('النهاية في غريب الحديث · ابن الأثير')).toBeInTheDocument();
  });

  it('гариб-слово отсутствует в hero-матне → матн без подсветки (graceful)', async () => {
    // THREE_KINDS_DETAIL: матн «إنما الأعمال بالنيات», reference أَبْعَدَ его НЕ
    // содержит → hero рендерится чистым текстом, кнопки-слова нет.
    mockEndpoints(THREE_KINDS_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(
        screen.getByRole('heading', { level: 1, name: /إنما الأعمال بالنيات/ }),
      ).toBeInTheDocument();
    });
    // слово أَبْعَدَ есть только в секции «غريب» (карточка), не как кнопка в hero
    const h1 = screen.getByRole('heading', { level: 1 });
    expect(within(h1).queryByRole('button')).not.toBeInTheDocument();
  });

  it('ILAL-карточка: книга/автор критика + сворачиваемый текст разбора', async () => {
    mockEndpoints(THREE_KINDS_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('heading', { name: /Иляль/ })).toBeInTheDocument();
    });
    const ilalText = 'علة خفية في إسناد هذا الحديث رغم ظاهر الصحة';
    expect(screen.queryByText(ilalText)).not.toBeInTheDocument();
    // раскрытие по шапке (книга — علل الدارقطني)
    await userEvent.click(screen.getByRole('button', { name: /علل الدارقطني/ }));
    expect(screen.getByText(ilalText)).toBeInTheDocument();
  });

  it('только SHARH → секции иляль/гариб скрыты и нет в навигации', async () => {
    // ALMINASA_DETAIL по умолчанию несёт только один SHARH
    mockEndpoints(ALMINASA_DETAIL, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('heading', { name: /Шарх/ })).toBeInTheDocument();
    });
    // секции иляля/гариба и их пункты навигации отсутствуют
    expect(screen.queryByRole('heading', { name: /Иляль/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Гариб/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Иляль' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Гариб' })).not.toBeInTheDocument();
  });

  it('GHARIB без reference → фолбэк на book/author-заголовок', async () => {
    const gharibNoRef = {
      ...ALMINASA_DETAIL,
      explanations: [
        {
          kind: 'GHARIB',
          bookName: 'لسان العرب',
          author: 'ابن منظور',
          page: 3,
          volume: 2,
          text: 'تفسير بلا كلمة عنوان',
          reference: null,
        },
      ],
    };
    mockEndpoints(gharibNoRef, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('heading', { name: /Гариб/ })).toBeInTheDocument();
    });
    // фолбэк-заголовок = book — author (как у шарха), текст сворачиваемый
    const fallbackText = 'تفسير بلا كلمة عنوان';
    expect(screen.queryByText(fallbackText)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /لسان العرب — ابن منظور/ }));
    expect(screen.getByText(fallbackText)).toBeInTheDocument();
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

  it('тогл «Все пути» виден при resolved>0 и лениво фетчит turuq-graph', async () => {
    let turuqFetches = 0;
    const TURUQ_GRAPH = {
      hadithId: 'h1',
      nodes: [
        { id: 'prophet', role: 'PROPHET', data: { narratorId: null, nameAr: 'النبي محمد ﷺ', tier: 0 } },
        {
          id: 'version-other',
          role: 'VERSION',
          data: null,
          version: {
            hadithId: 'h-sibling',
            externalId: '200-1',
            collectionSlug: 'muslim',
            collectionNameAr: 'صحيح مسلم',
            collectionNameRu: 'Сахих Муслим (طرق)',
            printedNumber: 1907,
            matnPreview: 'الأعمال بالنية',
          },
        },
      ],
      edges: [],
      sanads: [],
    };
    server.use(
      http.get(`${BASE}/api/v1/hadith/hadiths/h1/detail`, () => HttpResponse.json(ALMINASA_DETAIL)),
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths/:id/sanad-graph`, () =>
        HttpResponse.json(GRAPH_WITH_EXTERNAL),
      ),
      http.get(`${BASE}/api/v1/hadith/hadiths/h1/turuq-graph`, () => {
        turuqFetches += 1;
        return HttpResponse.json(TURUQ_GRAPH);
      }),
    );
    renderPage();
    // тогл виден (ALMINASA_DETAIL имеет 1 resolved crossref → «Все пути (1)»)
    let allPathsBtn: HTMLElement | null = null;
    await waitForApi(() => {
      allPathsBtn = screen.getByRole('button', { name: /Все пути \(1\)/ });
      expect(allPathsBtn).toBeInTheDocument();
    });
    // до клика turuq-graph не запрашивается (ленивый фетч)
    expect(turuqFetches).toBe(0);
    await userEvent.click(allPathsBtn!);
    // version-узел из turuq-графа отрендерился → turuq-graph запрошен
    await waitForApi(() => {
      expect(screen.getByText('Сахих Муслим (طرق)')).toBeInTheDocument();
    });
    expect(turuqFetches).toBe(1);
  });

  it('тогл «Все пути» скрыт при отсутствии resolved crossrefs', async () => {
    // crossref без relatedHadithId → resolved=0 → тогла нет
    const noResolved = {
      ...ALMINASA_DETAIL,
      crossrefs: [
        {
          relatedExternalId: '300-1',
          relatedHadithId: null,
          numbers: ['2201'],
          collectionNameAr: 'سنن أبي داود',
          collectionNameRu: 'Сунан Абу Дауд',
        },
      ],
    };
    mockEndpoints(noResolved, GRAPH_WITH_EXTERNAL);
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: /Все пути/ })).not.toBeInTheDocument();
  });

  it('аноним не видит кнопку «Добавить оценку»', async () => {
    mockEndpoints({ ...DETAIL, grades: [] });
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: 'Добавить оценку' })).not.toBeInTheDocument();
  });

  it('SCHOLAR видит секцию оценок с кнопкой даже при пустом списке', async () => {
    loginAs('SCHOLAR');
    mockEndpoints({ ...DETAIL, grades: [] });
    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('button', { name: 'Добавить оценку' })).toBeInTheDocument();
    });
    // секция оценок + empty-state видны (пункт навигации тоже)
    expect(screen.getByRole('link', { name: 'Оценки' })).toBeInTheDocument();
    expect(screen.getByText('Оценки учёных пока не добавлены')).toBeInTheDocument();
  });

  it('SCHOLAR: поиск учёного → выбор → отправка оценки → рефетч detail', async () => {
    loginAs('SCHOLAR');
    let posted: { scholarId: string; grade: string } | null = null;
    let detailCalls = 0;
    const GRADED_DETAIL = {
      ...DETAIL,
      grades: [
        {
          gradeId: 'g-new',
          scholarId: 'sc-albani',
          scholarName: 'аль-Албани',
          scholarFullName: 'Мухаммад Насир ад-Дин аль-Албани',
          scholarDeathYearHijri: 1420,
          grade: 'SAHIH',
          gradeCitation: null,
          note: null,
        },
      ],
    };
    server.use(
      // первый detail — без оценок; после POST (refetch с ?r=1) — с оценкой
      http.get(`${BASE}/api/v1/hadith/hadiths/h1/detail`, () => {
        detailCalls += 1;
        return HttpResponse.json(detailCalls === 1 ? { ...DETAIL, grades: [] } : GRADED_DETAIL);
      }),
      http.get(`${BASE}/api/v1/hadith/collections`, () => HttpResponse.json(COLLECTIONS)),
      http.get(`${BASE}/api/v1/hadith/hadiths/:id/sanad-graph`, () =>
        HttpResponse.json(EMPTY_GRAPH),
      ),
      // autocomplete учёных (PagedResponse) — отдаём SCHOLAR + не-SCHOLAR (фильтруется)
      http.get(`${BASE}/api/v1/authorities`, () =>
        HttpResponse.json({
          items: [
            {
              id: 'sc-albani',
              name: 'аль-Албани',
              fullName: 'Мухаммад Насир ад-Дин аль-Албани',
              deathYearHijri: 1420,
              type: 'SCHOLAR',
              bio: null,
              era: null,
              madhab: null,
              createdAt: '2026-01-01',
            },
            {
              id: 'pub-1',
              name: 'Дар аль-кутуб',
              fullName: null,
              deathYearHijri: null,
              type: 'PUBLISHER',
              bio: null,
              era: null,
              madhab: null,
              createdAt: '2026-01-01',
            },
          ],
          page: 0,
          size: 10,
          totalElements: 2,
          totalPages: 1,
          hasNext: false,
        }),
      ),
      http.post(`${BASE}/api/v1/hadith/hadiths/h1/grades`, async ({ request }) => {
        const body = (await request.json()) as { scholarId: string; grade: string };
        posted = { scholarId: body.scholarId, grade: body.grade };
        return HttpResponse.json(
          {
            id: 'g-new',
            sourceId: 'src-1',
            scholarId: body.scholarId,
            grade: body.grade,
            gradeCitation: null,
            comment: null,
            createdAt: '2026-06-17',
            createdBy: 'u-test',
          },
          { status: 201 },
        );
      }),
    );

    renderPage();
    await waitForApi(() => {
      expect(screen.getByRole('button', { name: 'Добавить оценку' })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole('button', { name: 'Добавить оценку' }));

    // модалка открылась
    expect(screen.getByRole('heading', { name: 'Оценка учёного' })).toBeInTheDocument();

    // поиск учёного — печатаем, ждём debounced (250мс) результат, выбираем
    await userEvent.type(screen.getByPlaceholderText('Начните вводить имя…'), 'аль');
    await waitForUi(() => {
      expect(screen.getByRole('button', { name: /Мухаммад Насир ад-Дин/ })).toBeInTheDocument();
    });
    // не-SCHOLAR (издатель) отфильтрован
    expect(screen.queryByText('Дар аль-кутуб')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Мухаммад Насир ад-Дин/ }));

    // отправка
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить оценку' }));

    // POST ушёл с правильным scholarId + grade
    await waitForUi(() => {
      expect(posted).toEqual({ scholarId: 'sc-albani', grade: 'SAHIH' });
    });
    // detail рефетчнут (≥2 вызова) и новая оценка показана
    await waitForUi(() => {
      expect(screen.getByText('аль-Албани')).toBeInTheDocument();
    });
    expect(detailCalls).toBeGreaterThanOrEqual(2);
  });
});
