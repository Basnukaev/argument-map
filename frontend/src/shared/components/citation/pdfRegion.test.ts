import { describe, it, expect } from 'vitest';
import { pixelRectToBbox, buildPdfDeepLinkQuery } from './pdfRegion';

describe('pixelRectToBbox', () => {
  it('нормализует пиксельный rect делением на размеры страницы', () => {
    const bbox = pixelRectToBbox(
      { left: 100, top: 50, width: 200, height: 100 },
      { width: 1000, height: 500 },
    );
    expect(bbox).not.toBeNull();
    expect(bbox?.x).toBeCloseTo(0.1, 10);
    expect(bbox?.y).toBeCloseTo(0.1, 10);
    expect(bbox?.width).toBeCloseTo(0.2, 10);
    expect(bbox?.height).toBeCloseTo(0.2, 10);
  });

  it('клампит координаты за пределами страницы в [0,1]', () => {
    // rect выходит за правый-нижний край и за левый-верхний (отрицательный top)
    const bbox = pixelRectToBbox(
      { left: 800, top: -50, width: 400, height: 600 },
      { width: 1000, height: 500 },
    );
    // left=800/1000=0.8, right=(800+400)/1000=1.2→clamp 1.0 → width=0.2
    // top=-50/500=-0.1→clamp 0, bottom=(−50+600)/500=1.1→clamp 1.0 → height=1.0
    expect(bbox?.x).toBeCloseTo(0.8, 10);
    expect(bbox?.y).toBeCloseTo(0, 10);
    expect(bbox?.width).toBeCloseTo(0.2, 10);
    expect(bbox?.height).toBeCloseTo(1, 10);
  });

  it('возвращает null при невалидных размерах страницы (0)', () => {
    expect(pixelRectToBbox({ left: 0, top: 0, width: 10, height: 10 }, { width: 0, height: 500 }))
      .toBeNull();
    expect(pixelRectToBbox({ left: 0, top: 0, width: 10, height: 10 }, { width: 1000, height: -1 }))
      .toBeNull();
  });
});

describe('buildPdfDeepLinkQuery', () => {
  it('добавляет &fileIndex= когда fileIndex задан (включая 0)', () => {
    expect(buildPdfDeepLinkQuery({ pageNumber: 7, fileIndex: 2 })).toBe(
      '?pdf=1&pdfPageNumber=7&fileIndex=2',
    );
    expect(buildPdfDeepLinkQuery({ pageNumber: 1, fileIndex: 0 })).toBe(
      '?pdf=1&pdfPageNumber=1&fileIndex=0',
    );
  });

  it('опускает &fileIndex= когда fileIndex null/undefined', () => {
    expect(buildPdfDeepLinkQuery({ pageNumber: 3, fileIndex: null })).toBe(
      '?pdf=1&pdfPageNumber=3',
    );
    expect(buildPdfDeepLinkQuery({ pageNumber: 3 })).toBe('?pdf=1&pdfPageNumber=3');
  });

  it('добавляет &bbox= когда bbox задан', () => {
    expect(
      buildPdfDeepLinkQuery({
        pageNumber: 7,
        fileIndex: 2,
        bbox: { x: 0.1, y: 0.2, width: 0.3, height: 0.4 },
      }),
    ).toBe('?pdf=1&pdfPageNumber=7&fileIndex=2&bbox=0.1,0.2,0.3,0.4');
  });

  it('опускает &bbox= когда bbox null или без x', () => {
    expect(buildPdfDeepLinkQuery({ pageNumber: 7, fileIndex: 2, bbox: null })).toBe(
      '?pdf=1&pdfPageNumber=7&fileIndex=2',
    );
  });
});
