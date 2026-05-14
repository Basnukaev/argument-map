/**
 * Translation dictionary. Минимальный ручной i18n (без i18next) - проект
 * на этапе MVP, словарь компактный, ручной dictionary даёт type-safety
 * через `DictKey` без overhead библиотеки.
 *
 * Когда переключитель локалей появится в UI (Этап 21+ multi-user / auth),
 * запись в localStorage / user profile + useLocale().setLocale(l) сделает
 * весь UI переключаемым без перезагрузки
 */

export const DICTIONARY = {
  ru: {
    // citation chips
    'cite.chip.library': 'из библиотеки',
    'cite.chip.free': 'свободная',

    // citation field labels (in metadata accordion)
    'cite.label.author': 'Автор',
    'cite.label.death_year': 'Год смерти',
    'cite.label.title': 'Название',
    'cite.label.muhaqqiq': 'Тахкик',
    'cite.label.publisher': 'Издатель',
    'cite.label.edition': 'Издание',
    'cite.label.year': 'Год',
    'cite.label.metadata': 'Метаданные',

    // citation actions
    'cite.action.gotoSource': 'Перейти к источнику',
    'cite.action.detach': 'Отвязать опору',

    // page/year suffixes (in mono numerals row)
    'cite.page.short': 'стр.',
    'cite.year.gregorian_suffix': 'г.',
    'cite.edition.suffix': '-е изд.',

    // book metadata page (header sigtype label)
    'book.pages_count_suffix': 'стр.',
    'book.type.BOOK': 'Книга',
    'book.type.QURAN': 'Коран',
    'book.type.HADITH_COLLECTION': 'Сборник хадисов',
    'book.type.ARTICLE': 'Статья',
    'book.type.MANUSCRIPT': 'Рукопись',
  },
  ar: {
    'cite.chip.library': 'من المكتبة',
    'cite.chip.free': 'حرّة',

    'cite.label.author': 'المؤلف',
    'cite.label.death_year': 'سنة الوفاة',
    'cite.label.title': 'العنوان',
    'cite.label.muhaqqiq': 'التحقيق',
    'cite.label.publisher': 'الناشر',
    'cite.label.edition': 'الطبعة',
    'cite.label.year': 'السنة',
    'cite.label.metadata': 'البيانات الوصفية',

    'cite.action.gotoSource': 'اذهب إلى المصدر',
    'cite.action.detach': 'فصل الاستناد',

    'cite.page.short': 'ص.',
    'cite.year.gregorian_suffix': 'م.',
    'cite.edition.suffix': ' (طبعة)',

    'book.pages_count_suffix': 'صفحة',
    'book.type.BOOK': 'كتاب',
    'book.type.QURAN': 'القرآن',
    'book.type.HADITH_COLLECTION': 'مجموعة أحاديث',
    'book.type.ARTICLE': 'مقالة',
    'book.type.MANUSCRIPT': 'مخطوطة',
  },
} as const;

export type Locale = keyof typeof DICTIONARY;
export type DictKey = keyof (typeof DICTIONARY)['ru'];
