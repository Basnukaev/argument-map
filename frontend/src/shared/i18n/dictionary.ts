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

    // header navigation
    'nav.home_aria': 'На главную',
    'nav.topics': 'Темы',
    'nav.library': 'Библиотека',
    'nav.qa': 'Q&A',
    'nav.admin': 'Админ',
    'nav.disabled_hint': 'Будет в одном из следующих этапов',

    // reader (chapters sidebar + pagination toolbar)
    'reader.chapters': 'Содержание',
    'reader.chapters_empty': 'Глав пока нет',
    'reader.no_book_title': '(без названия)',
    'reader.page': 'Страница',
    'reader.page_short': 'Стр',
    'reader.page_of': '/',
    'reader.volume': 'Том',
    'reader.volume_aria': 'Выбор тома',
    'reader.prev': 'Предыдущая',
    'reader.next': 'Следующая',
    'reader.back_to_list': 'К списку',
    'reader.back_to_text': 'Назад к тексту',
    'reader.open_pdf': 'Открыть PDF',
    'reader.zoom_in': 'Увеличить',
    'reader.zoom_out': 'Уменьшить',
    'reader.download_pdf': 'Скачать PDF целиком',
    'reader.mode.text': 'Текст',
    'reader.mode.pdf': 'PDF',

    // topic list page
    'topic.list.title': 'Темы аргументации',
    'topic.list.subtitle_active': 'Структурированные дискуссии в виде графа',
    'topic.list.create_button': 'Создать тему',
    'topic.list.search_placeholder': 'Поиск по теме или описанию',
    'topic.list.empty': 'Пока нет тем. Создай первую',
    'topic.list.not_found': 'Ничего не найдено',
    'topic.list.aria_topic_count': 'активных',

    // book list page
    'book.list.title': 'Библиотека',
    'book.list.subtitle': 'Импортированные классические труды и источники',
    'book.list.books_suffix': 'книг',
    'book.list.search_placeholder': 'Поиск по названию',
    'book.list.filter_all': 'Все типы',

    // common
    'common.loading': 'Загрузка',
    'common.error': 'Ошибка',
    'common.close': 'Закрыть',
    'common.cancel': 'Отмена',
    'common.save': 'Сохранить',
    'common.saving': 'Сохраняем',
    'common.search': 'Поиск',
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

    'nav.home_aria': 'إلى الصفحة الرئيسية',
    'nav.topics': 'المواضيع',
    'nav.library': 'المكتبة',
    'nav.qa': 'الأسئلة والأجوبة',
    'nav.admin': 'الإدارة',
    'nav.disabled_hint': 'سيُتاح في إحدى المراحل القادمة',

    'reader.chapters': 'الفهرس',
    'reader.chapters_empty': 'لا توجد فصول بعد',
    'reader.no_book_title': '(بلا عنوان)',
    'reader.page': 'الصفحة',
    'reader.page_short': 'ص',
    'reader.page_of': '/',
    'reader.volume': 'الجزء',
    'reader.volume_aria': 'اختيار الجزء',
    'reader.prev': 'السابقة',
    'reader.next': 'التالية',
    'reader.back_to_list': 'إلى القائمة',
    'reader.back_to_text': 'العودة إلى النص',
    'reader.open_pdf': 'فتح PDF',
    'reader.zoom_in': 'تكبير',
    'reader.zoom_out': 'تصغير',
    'reader.download_pdf': 'تنزيل PDF كاملاً',
    'reader.mode.text': 'نص',
    'reader.mode.pdf': 'PDF',

    'topic.list.title': 'مواضيع الحجاج',
    'topic.list.subtitle_active': 'نقاشات منظمة على هيئة رسم بياني',
    'topic.list.create_button': 'إنشاء موضوع',
    'topic.list.search_placeholder': 'بحث بالموضوع أو الوصف',
    'topic.list.empty': 'لا توجد مواضيع بعد. أنشئ أوّل واحد',
    'topic.list.not_found': 'لا توجد نتائج',
    'topic.list.aria_topic_count': 'نشط',

    'book.list.title': 'المكتبة',
    'book.list.subtitle': 'كتب وأمهات مستوردة',
    'book.list.books_suffix': 'كتاب',
    'book.list.search_placeholder': 'بحث بالعنوان',
    'book.list.filter_all': 'كل الأنواع',

    'common.loading': 'جارٍ التحميل',
    'common.error': 'خطأ',
    'common.close': 'إغلاق',
    'common.cancel': 'إلغاء',
    'common.save': 'حفظ',
    'common.saving': 'جاري الحفظ',
    'common.search': 'بحث',
  },
} as const;

export type Locale = keyof typeof DICTIONARY;
export type DictKey = keyof (typeof DICTIONARY)['ru'];
