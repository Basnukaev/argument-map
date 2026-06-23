import { describe, it, expect, vi } from 'vitest';
import { render, screen, type RenderResult, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';

// PNG-экспорт идёт через shared-утилиту (html-to-image внутри) — мокаем, чтобы
// проверить, что клик по кнопке вызывает экспорт (рендер canvas в jsdom не
// работает, мок достаточен; реальный снимок — Playwright на живом графе).
vi.mock('@/shared/utils/graphExport', () => ({
  exportGraphAsPngHighRes: vi.fn().mockResolvedValue(undefined),
}));

import { exportGraphAsPngHighRes } from '@/shared/utils/graphExport';
import SanadGraph from './SanadGraph';

const BASE = 'http://test.local';
const GRAPH_URL = `${BASE}/api/v1/hadith/hadiths/:id/sanad-graph`;

// SanadGraph использует useNavigate (клик по version-узлу) — Router обязателен.
function renderGraph(ui: React.ReactElement): RenderResult {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

describe('SanadGraph', () => {
  it('показывает ошибку, если эндпоинт вернул проблему', async () => {
    server.use(
      http.get(GRAPH_URL, () =>
        HttpResponse.json({ title: 'Граф недоступен' }, { status: 500 }),
      ),
    );
    renderGraph(<SanadGraph hadithId="h1" />);
    await waitForApi(() => {
      expect(screen.getByText('Граф недоступен')).toBeInTheDocument();
    });
  });

  it('показывает пустое состояние, если узлов нет', async () => {
    server.use(
      http.get(GRAPH_URL, () =>
        HttpResponse.json({ hadithId: 'h1', nodes: [], edges: [], sanads: [] }),
      ),
    );
    renderGraph(<SanadGraph hadithId="h1" />);
    await waitForApi(() => {
      expect(
        screen.getByText('Для этого хадиса иснад ещё не задокументирован'),
      ).toBeInTheDocument();
    });
  });

  it('controlled-режим: рендерит переданный graph без fetch по hadithId', async () => {
    let fetched = false;
    server.use(
      http.get(GRAPH_URL, () => {
        fetched = true;
        return HttpResponse.json({ hadithId: 'h1', nodes: [], edges: [], sanads: [] });
      }),
    );
    const graph = {
      hadithId: 'h-preview',
      nodes: [
        { id: 'prophet', role: 'PROPHET' as const, data: { narratorId: null, nameAr: 'النبي محمد ﷺ', tier: 0 } },
        {
          id: 'narrator-1',
          role: 'NARRATOR' as const,
          data: {
            narratorId: '1',
            nameAr: 'أبو هريرة',
            nameRu: 'Абу Хурайра',
            reliabilityGrade: 'SAHABI' as const,
            tier: 1,
          },
        },
      ],
      edges: [],
      sanads: [],
    };
    // graph как unknown — тест передаёт частичную форму NarratorData (как и
    // остальные кейсы в этом файле), полные поля рантайму не нужны.
    renderGraph(<SanadGraph graph={graph as unknown as never} />);

    // Узел из переданного графа отрендерился сразу, без сетевого запроса
    expect(await screen.findByText('Абу Хурайра')).toBeInTheDocument();
    expect(fetched).toBe(false);
  });

  it('controlled-режим: пустой/null граф показывает empty-state без fetch', async () => {
    let fetched = false;
    server.use(
      http.get(GRAPH_URL, () => {
        fetched = true;
        return HttpResponse.json({ hadithId: 'h1', nodes: [], edges: [], sanads: [] });
      }),
    );
    renderGraph(<SanadGraph graph={null} />);
    expect(
      await screen.findByText('Для этого хадиса иснад ещё не задокументирован'),
    ).toBeInTheDocument();
    expect(fetched).toBe(false);
  });

  it('такхридж (0 рави): показывает empty-state вместо графа', async () => {
    // Хадис с COLLECTOR-узлом, но без NARRATOR/COMPANION — такхридж/вариант.
    const graph = {
      hadithId: 'h-takhrij',
      nodes: [
        {
          id: 'collector-1',
          role: 'COLLECTOR' as const,
          data: { narratorId: null, nameAr: 'المستدرك', tier: 1, collectionAr: 'المستدرك' },
        },
      ],
      edges: [],
      sanads: [],
    };
    renderGraph(<SanadGraph graph={graph as unknown as never} />);
    expect(
      await screen.findByText(
        'Эта запись — вариант/такхридж без отдельной цепи передачи. Структурный иснад не извлечён; см. полный текст во вкладке «Текст».',
      ),
    ).toBeInTheDocument();
    // React Flow не рендерится — нет рави-узлов
    expect(screen.queryByText('المستدرك')).not.toBeInTheDocument();
  });

  it('controlled-выбор (onNarratorSelect задан): внутренняя панель не рендерится', async () => {
    // Контракт: когда выбором владеет родитель (onNarratorSelect передан),
    // SanadGraph НЕ рендерит свою NarratorPanel — единственная панель на
    // странице принадлежит родителю. (Сам клик-резолв из текста иснада в
    // единую панель покрыт интеграционно в HadithDetailPage.test —
    // клик по RF-узлу в jsdom неустойчив из-за d3-drag/event.view.)
    const graph = {
      hadithId: 'h1',
      nodes: [
        { id: 'prophet', role: 'PROPHET' as const, data: { narratorId: null, nameAr: 'النبي محمد ﷺ', tier: 0 } },
        {
          id: 'narrator-1',
          role: 'NARRATOR' as const,
          data: { narratorId: '1', nameAr: 'أبو هريرة', nameRu: 'Абу Хурайра', tier: 1 },
        },
      ],
      edges: [],
      sanads: [],
    };
    renderGraph(<SanadGraph graph={graph as unknown as never} onNarratorSelect={vi.fn()} />);
    expect(await screen.findByText('Абу Хурайра')).toBeInTheDocument();
    // внутренняя панель (aside=complementary) отсутствует — владеет родитель
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
  });

  it('рендерит узлы и легенду цепей при успешном ответе', async () => {
    server.use(
      http.get(GRAPH_URL, () =>
        HttpResponse.json({
          hadithId: 'h1',
          nodes: [
            { id: 'prophet', role: 'PROPHET', data: { narratorId: null, nameAr: 'النبي محمد ﷺ', tier: 0 } },
            {
              id: 'narrator-1',
              role: 'COMPANION',
              data: {
                narratorId: '1',
                nameAr: 'عمر بن الخطاب',
                nameRu: 'Умар ибн аль-Хаттаб',
                reliabilityGrade: 'SAHABI',
                yearDeathHijri: 23,
                tier: 1,
              },
            },
          ],
          edges: [
            {
              id: 'e0',
              source: 'prophet',
              target: 'narrator-1',
              data: { transmissionPhrase: 'سمعت', chainGrade: 'SAHIH', onPrimaryChain: true, sanadCount: 1 },
            },
          ],
          sanads: [
            {
              id: 's1',
              collectionRu: 'Сахих аль-Бухари',
              collectionAr: 'صحيح البخاري',
              chainGrade: 'SAHIH',
              primaryChain: true,
              collectorNodeId: 'narrator-1',
            },
          ],
        }),
      ),
    );
    renderGraph(<SanadGraph hadithId="h1" />);
    await waitForApi(() => {
      // узел-передатчик отрендерился
      expect(screen.getByText('Умар ибн аль-Хаттаб')).toBeInTheDocument();
    });
    // легенда свёрнута по умолчанию (FB-7) — разворачиваем для проверки цепей
    fireEvent.click(screen.getByRole('button', { name: 'Показать легенду' }));
    // легенда цепей содержит сборник
    expect(screen.getByText('Сахих аль-Бухари')).toBeInTheDocument();
  });

  it('version-узел рендерится карточкой-книгой; «свой» помечен «вы здесь»', async () => {
    const graph = {
      hadithId: 'h-current',
      nodes: [
        { id: 'prophet', role: 'PROPHET' as const, data: { narratorId: null, nameAr: 'النبي محمد ﷺ', tier: 0 } },
        {
          id: 'version-current',
          role: 'VERSION' as const,
          data: null,
          version: {
            hadithId: 'h-current',
            externalId: '1-1',
            collectionSlug: 'bukhari',
            collectionNameAr: 'صحيح البخاري',
            collectionNameRu: 'Сахих аль-Бухари',
            printedNumber: 1,
            matnPreview: 'إنما الأعمال بالنيات',
          },
        },
        {
          id: 'version-other',
          role: 'VERSION' as const,
          data: null,
          version: {
            hadithId: 'h-other',
            externalId: '200-1',
            collectionSlug: 'muslim',
            collectionNameAr: 'صحيح مسلم',
            collectionNameRu: 'Сахих Муслим',
            printedNumber: 1907,
            matnPreview: 'الأعمال بالنية',
          },
        },
      ],
      edges: [],
      sanads: [],
    };
    renderGraph(
      <SanadGraph
        graph={graph as unknown as never}
        currentHadithId="h-current"
        onNarratorSelect={vi.fn()}
      />,
    );
    // обе version-карточки отрендерились (арабские имена сборников; рус.
    // транслит убран С64)
    expect(await screen.findByText('صحيح البخاري')).toBeInTheDocument();
    expect(screen.getByText('صحيح مسلم')).toBeInTheDocument();
    // «свой» узел (h-current) помечен «вы здесь»
    expect(screen.getByText('вы здесь')).toBeInTheDocument();
    // легенда свёрнута по умолчанию (FB-7) — разворачиваем
    fireEvent.click(screen.getByRole('button', { name: 'Показать легенду' }));
    // строка легенды про version-узлы присутствует
    expect(screen.getByText(/запись в сборнике/)).toBeInTheDocument();
  });

  it('клик по строке легенды переключает активное состояние кнопки (подсветка цепи)', async () => {
    server.use(
      http.get(GRAPH_URL, () =>
        HttpResponse.json({
          hadithId: 'h1',
          nodes: [
            { id: 'prophet', role: 'PROPHET', data: { narratorId: null, nameAr: 'النبي محمد ﷺ', tier: 0 } },
            {
              id: 'narrator-1',
              role: 'COMPANION',
              data: {
                narratorId: '1',
                nameAr: 'عمر بن الخطاب',
                nameRu: 'Умар ибн аль-Хаттаб',
                reliabilityGrade: 'SAHABI',
                yearDeathHijri: 23,
                tier: 1,
              },
            },
          ],
          edges: [
            {
              id: 'e0',
              source: 'prophet',
              target: 'narrator-1',
              data: { transmissionPhrase: 'سمعت', chainGrade: 'SAHIH', onPrimaryChain: true, sanadCount: 1 },
            },
          ],
          sanads: [
            {
              id: 's1',
              collectionRu: 'Сахих аль-Бухари',
              collectionAr: 'صحيح البخاري',
              chainGrade: 'SAHIH',
              primaryChain: true,
              collectorNodeId: 'narrator-1',
            },
          ],
        }),
      ),
    );
    renderGraph(<SanadGraph hadithId="h1" />);
    // легенда свёрнута по умолчанию (FB-7) — разворачиваем
    fireEvent.click(await screen.findByRole('button', { name: 'Показать легенду' }));
    const chainBtn = await screen.findByTitle('Подсветить эту цепь в графе');
    expect(chainBtn).toBeInTheDocument();
    // Клик активирует подсветку: title меняется
    fireEvent.click(chainBtn);
    expect(screen.getByTitle('Снять подсветку')).toBeInTheDocument();
    // Повторный клик снимает подсветку
    fireEvent.click(screen.getByTitle('Снять подсветку'));
    expect(screen.getByTitle('Подсветить эту цепь в графе')).toBeInTheDocument();
  });

  it('кнопка «Скачать PNG» присутствует и клик вызывает экспорт графа', async () => {
    vi.mocked(exportGraphAsPngHighRes).mockClear();
    const graph = {
      hadithId: 'h-export',
      nodes: [
        { id: 'prophet', role: 'PROPHET' as const, data: { narratorId: null, nameAr: 'النبي محمد ﷺ', tier: 0 } },
        {
          id: 'narrator-1',
          role: 'NARRATOR' as const,
          data: { narratorId: '1', nameAr: 'أبو هريرة', nameRu: 'Абу Хурайра', tier: 1 },
        },
      ],
      edges: [
        {
          id: 'e0',
          source: 'prophet',
          target: 'narrator-1',
          data: { transmissionPhrase: 'عن', chainGrade: 'SAHIH', onPrimaryChain: true, sanadCount: 1 },
        },
      ],
      sanads: [],
    };
    renderGraph(<SanadGraph graph={graph as unknown as never} currentHadithId="h-export" />);

    // граф отрендерился (рави-узел виден) — кнопка экспорта в graph-chrome
    expect(await screen.findByText('Абу Хурайра')).toBeInTheDocument();
    const exportBtn = screen.getByRole('button', { name: 'Скачать PNG' });
    expect(exportBtn).toBeInTheDocument();

    fireEvent.click(exportBtn);

    // клик вызвал shared-утилиту экспорта (имя файла по hadithId графа)
    await waitFor(() => {
      expect(exportGraphAsPngHighRes).toHaveBeenCalledTimes(1);
    });
    const callArgs = vi.mocked(exportGraphAsPngHighRes).mock.calls[0]!;
    expect(callArgs[1]).toBe('isnad-h-export.png');
    // высокое разрешение: pixelRatio ≥ 2, передаётся bounds + transform всего графа
    const opts = callArgs[2]!;
    expect(opts.pixelRatio).toBeGreaterThanOrEqual(2);
    expect(opts.bounds).toBeDefined();
    expect(opts.transform).toBeDefined();
  });
});
