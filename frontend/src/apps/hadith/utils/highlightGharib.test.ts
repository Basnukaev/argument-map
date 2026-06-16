import { describe, it, expect } from 'vitest';
import { normalizeArabic, buildMatnSegments } from './highlightGharib';
import type { ExplanationDto } from '@/apps/hadith/types';

function gharib(reference: string | null, text = 'تفسير', extra: Partial<ExplanationDto> = {}): ExplanationDto {
  return {
    kind: 'GHARIB',
    bookName: 'النهاية',
    author: 'ابن الأثير',
    page: null,
    volume: null,
    text,
    reference,
    ...extra,
  };
}

describe('normalizeArabic', () => {
  it('снимает огласовки', () => {
    expect(normalizeArabic('سَنَوْتُ')).toBe('سنوت');
  });

  it('сводит алиф-максуру ى → ي (как бэк нормализует матн)', () => {
    expect(normalizeArabic('تَطْوَى')).toBe('تطوي');
  });

  it('сводит варианты алифа и та-марбуту', () => {
    expect(normalizeArabic('إآأ')).toBe('ااا');
    expect(normalizeArabic('صلاة')).toBe('صلاه');
  });

  it('удаляет одиночную хамзу и сводит хамза-носители', () => {
    expect(normalizeArabic('جاء')).toBe('جا');
    expect(normalizeArabic('مُؤْمِن')).toBe('مومن');
  });
});

describe('buildMatnSegments', () => {
  it('без гарибов → один plain-сегмент со всем матном', () => {
    const segs = buildMatnSegments('الأعمال بالنيات', []);
    expect(segs).toHaveLength(1);
    expect(segs[0]!.text).toBe('الأعمال بالنيات');
    expect(segs[0]!.gharib).toBeNull();
  });

  it('гариб без совпадения в матне → graceful plain (ничего не подсвечено)', () => {
    const segs = buildMatnSegments('الأعمال بالنيات', [gharib('بِخَمِيلَةٍ')]);
    expect(segs).toHaveLength(1);
    expect(segs.every((s) => s.gharib === null)).toBe(true);
  });

  it('подсвечивает слово несмотря на расхождение огласовки/формы буквы', () => {
    // матн folded (как с бэка), reference с огласовкой+алиф-максурой
    const segs = buildMatnSegments('قالت تطوي بطونهم', [gharib('تَطْوَى', 'يطوي = يضم')]);
    const hit = segs.find((s) => s.gharib);
    expect(hit).toBeDefined();
    expect(hit!.text).toBe('تطوي'); // оригинальный токен матна, не reference
    expect(hit!.gharib!.text).toBe('يطوي = يضم');
  });

  it('подсвечивает ВСЕ вхождения одного слова', () => {
    const segs = buildMatnSegments('سنوت ثم سنوت', [gharib('سَنَوْتُ')]);
    expect(segs.filter((s) => s.gharib).length).toBe(2);
  });

  it('сохраняет порядок и текст вокруг подсветки', () => {
    const segs = buildMatnSegments('قالت تطوي بطونهم', [gharib('تَطْوَى')]);
    const joined = segs.map((s) => s.text).join('');
    expect(joined).toBe('قالت تطوي بطونهم');
  });

  it('многословный reference матчится как последовательность токенов', () => {
    const segs = buildMatnSegments('وادع اهل الصفه', [gharib('أَهْلَ الصُّفَّةِ', 'فقراء المسجد')]);
    const hit = segs.find((s) => s.gharib);
    expect(hit).toBeDefined();
    expect(hit!.text).toBe('اهل الصفه');
  });
});
