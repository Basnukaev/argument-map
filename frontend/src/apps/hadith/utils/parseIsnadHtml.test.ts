import { describe, it, expect } from 'vitest';
import { parseIsnadHtml } from './parseIsnadHtml';

// Реальный фрагмент 146-1 (хадис «إنما الأعمال بالنيات») из backend-фикстуры
// alminasa/hadith-page.json _source.hadith — захардкожен для регресс-якоря.
const REAL_146_1 =
  'حَدَّثَنَا <a class=rawy id=4698>الْحُمَيْدِيُّ عَبْدُ اللَّهِ بْنُ الزُّبَيْرِ </a> ، قَالَ : حَدَّثَنَا <a class=rawy id=3443>سُفْيَانُ </a> ، قَالَ : حَدَّثَنَا <a class=rawy id=8272>يَحْيَى بْنُ سَعِيدٍ الْأَنْصَارِيُّ </a> ، قَالَ : أَخْبَرَنِي <a class=rawy id=6796>مُحَمَّدُ بْنُ إِبْرَاهِيمَ التَّيْمِيُّ </a> ، أَنَّهُ سَمِعَ <a class=rawy id=5719>عَلْقَمَةَ بْنَ وَقَّاصٍ اللَّيْثِيَّ </a> ، يَقُولُ : سَمِعْتُ <a class=rawy id=5913>عُمَرَ بْنَ الْخَطَّابِ </a> رَضِيَ اللَّهُ عَنْهُ عَلَى الْمِنْبَرِ ، قَالَ : سَمِعْتُ رَسُولَ اللَّهِ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ ، يَقُولُ : " <a class=matn>إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى ، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى دُنْيَا يُصِيبُهَا أَوْ إِلَى امْرَأَةٍ يَنْكِحُهَا ، فَهِجْرَتُهُ إِلَى مَا هَاجَرَ إِلَيْهِ " </a> .';

describe('parseIsnadHtml', () => {
  it('реальный 146-1: извлекает 6 рави с externalId и matn-сегмент', () => {
    const segs = parseIsnadHtml(REAL_146_1);

    const rawy = segs.filter((s) => s.kind === 'rawy');
    expect(rawy.map((s) => s.externalId)).toEqual([
      '4698',
      '3443',
      '8272',
      '6796',
      '5719',
      '5913',
    ]);
    // имя первого рави сохранено как inner-текст
    expect(rawy[0]?.text).toContain('الْحُمَيْدِيُّ');

    // ровно один matn-сегмент, без externalId, с текстом матна
    const matn = segs.filter((s) => s.kind === 'matn');
    expect(matn).toHaveLength(1);
    expect(matn[0]?.externalId).toBeUndefined();
    expect(matn[0]?.text).toContain('إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ');

    // литералы вокруг (حَدَّثَنَا, قَالَ, عَنه/رَضِيَ ...) попали в text-сегменты
    const text = segs.filter((s) => s.kind === 'text').map((s) => s.text).join('');
    expect(text).toContain('حَدَّثَنَا');
    expect(text).toContain('قَالَ');
  });

  it('пустая строка → пустой массив', () => {
    expect(parseIsnadHtml('')).toEqual([]);
    expect(parseIsnadHtml(null)).toEqual([]);
    expect(parseIsnadHtml(undefined)).toEqual([]);
  });

  it('строка без тегов → один text-сегмент как есть', () => {
    const segs = parseIsnadHtml('عن أبي هريرة رضي الله عنه');
    expect(segs).toEqual([{ kind: 'text', text: 'عن أبي هريرة رضي الله عنه' }]);
  });

  it('matn-only: один matn-сегмент, кликабельных рави нет', () => {
    const segs = parseIsnadHtml('<a class=matn>إنما الأعمال بالنيات</a>');
    expect(segs).toEqual([{ kind: 'matn', text: 'إنما الأعمال بالنيات' }]);
    expect(segs.some((s) => s.kind === 'rawy')).toBe(false);
  });

  it('незакрытый тег рави не падает — остаток уходит в имя рави', () => {
    const segs = parseIsnadHtml('حدثنا <a class=rawy id=42>سفيان بلا إغلاق');
    expect(segs).toEqual([
      { kind: 'text', text: 'حدثنا ' },
      { kind: 'rawy', text: 'سفيان بلا إغلاق', externalId: '42' },
    ]);
  });

  it('литералы `عنه` между тегами остаются plain-текстом, не рави', () => {
    const segs = parseIsnadHtml(
      '<a class=rawy id=1>عمر</a> رضي الله عنه عن <a class=rawy id=2>زيد</a>',
    );
    const text = segs.filter((s) => s.kind === 'text').map((s) => s.text).join('');
    expect(text).toContain('رضي الله عنه عن');
    expect(segs.filter((s) => s.kind === 'rawy').map((s) => s.externalId)).toEqual(['1', '2']);
  });

  it('имя с амперсандом/`<` не ломает парсер', () => {
    const segs = parseIsnadHtml('قبل < بعد <a class=rawy id=7>زيد & عمرو</a>');
    const rawy = segs.find((s) => s.kind === 'rawy');
    expect(rawy?.text).toBe('زيد & عمرو');
    expect(rawy?.externalId).toBe('7');
    // одинокий `<` сохранён как plain-текст
    expect(segs.filter((s) => s.kind === 'text').map((s) => s.text).join('')).toContain('قبل < بعد');
  });
});
