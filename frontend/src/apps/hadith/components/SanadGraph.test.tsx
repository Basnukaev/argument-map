import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { waitForApi } from '@/test/asyncHelpers';
import SanadGraph from './SanadGraph';

const BASE = 'http://test.local';
const GRAPH_URL = `${BASE}/api/v1/hadith/hadiths/:id/sanad-graph`;

describe('SanadGraph', () => {
  it('показывает ошибку, если эндпоинт вернул проблему', async () => {
    server.use(
      http.get(GRAPH_URL, () =>
        HttpResponse.json({ title: 'Граф недоступен' }, { status: 500 }),
      ),
    );
    render(<SanadGraph hadithId="h1" />);
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
    render(<SanadGraph hadithId="h1" />);
    await waitForApi(() => {
      expect(
        screen.getByText('Для этого хадиса иснад ещё не задокументирован'),
      ).toBeInTheDocument();
    });
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
    render(<SanadGraph hadithId="h1" />);
    await waitForApi(() => {
      // узел-передатчик отрендерился
      expect(screen.getByText('Умар ибн аль-Хаттаб')).toBeInTheDocument();
    });
    // легенда цепей содержит сборник
    expect(screen.getByText('Сахих аль-Бухари')).toBeInTheDocument();
  });
});
