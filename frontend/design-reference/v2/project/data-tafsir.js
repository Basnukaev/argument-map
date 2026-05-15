// Real backend payload — Tafsir Ibn Kathir (Dar Ibn al-Jawzi edition).
// Source: user's argument-map backend. Classical Arabic, public domain.
//
// The full backend response includes ~200 chapters with deep nesting.
// We trim to the first 4 main sections + show their full subtree so the
// chapter sidebar exercises the tree-depth layout without being absurd.

(function () {
  // Pull chapters into a shape compatible with ChapterTree:
  // { id, title, page, children?, current? }
  function adaptChapter(c, currentId) {
    const out = {
      id: c.id,
      title: c.title,
      page: c.startPageNumber,
    };
    if (c.id === currentId) out.current = true;
    if (c.children && c.children.length) {
      out.children = c.children.map((ch) => adaptChapter(ch, currentId));
    }
    return out;
  }

  // The chapter id we mark as "current" — the page response carries chapterId=null,
  // but the textContent's title is "مقدمة الناشر" which is this chapter:
  const CURRENT_CHAPTER_ID = '65b72d29-3efb-427a-9d6a-47d73986b79d';

  const rawChapters = [
    {
      id: '65b72d29-3efb-427a-9d6a-47d73986b79d',
      title: 'مقدمة الناشر',
      startPageNumber: 1,
      children: [],
    },
    {
      id: 'b4fa0cff-53da-482b-b47c-82e2cdd357c5',
      title: 'مقدمة المحقق',
      startPageNumber: 3,
      children: [
        { id: 'c1', title: 'أسباب تحقيق الكتاب', startPageNumber: 16 },
        { id: 'c2', title: 'الفصل الأول: ترجمة مختصرة للحافظ ابن كثير', startPageNumber: 19 },
        {
          id: 'c3', title: 'الفصل الثاني: دراسة مختصرة للتفسير', startPageNumber: 23,
          children: [
            { id: 'c3-1', title: 'منهج الحافظ ابن كثير في التفسير', startPageNumber: 23 },
            { id: 'c3-2', title: 'منهج الحافظ في الترجيح', startPageNumber: 30 },
            { id: 'c3-3', title: 'القيمة العلمية للتفسير', startPageNumber: 33 },
            { id: 'c3-4', title: 'وصف النسخ الخطية', startPageNumber: 37 },
          ],
        },
      ],
    },
    {
      id: 'f04f7139-08c1-48d2-82e7-23fe5f01e9e1',
      title: 'مقدمة المؤلف',
      startPageNumber: 59,
      children: [
        { id: 'd1', title: 'فصل', startPageNumber: 66 },
      ],
    },
    {
      id: '174a09df-1b3a-49c1-9ec0-cdb2ce472015',
      title: 'كتاب فضائل القرآن',
      startPageNumber: 75,
      children: [
        { id: 'e1', title: 'جمع القرآن', startPageNumber: 85 },
        { id: 'e2', title: 'تأليف القرآن', startPageNumber: 121 },
        { id: 'e3', title: 'القراء من أصحاب النبي ﷺ', startPageNumber: 128 },
        { id: 'e4', title: 'فضل القرآن على سائر الكلام', startPageNumber: 136 },
        { id: 'e5', title: 'حسن الصوت بالقراءة', startPageNumber: 166 },
        { id: 'e6', title: 'البكاء عند قراءة القرآن', startPageNumber: 174 },
      ],
    },
    {
      id: '82782a61-4fc3-4073-868c-c1f88e4a2d43',
      title: 'سورة الفاتحة',
      startPageNumber: 200,
    },
    {
      id: '38aab96c-c341-4b32-8c13-8d73c7c004e8',
      title: 'تفسير سورة البقرة',
      startPageNumber: 283,
    },
    {
      id: 'cfa6e782-b789-4a40-acc1-227b7cf784da',
      title: 'سورة آل عمران',
      startPageNumber: 1014,
    },
    {
      id: '45f9d78c-c90c-435c-9981-3ca8d7ac0c09',
      title: 'سورة النساء',
      startPageNumber: 1211,
    },
    {
      id: 'da9591a1-262e-4879-b35e-93692a9bbe32',
      title: 'سورة المائدة',
      startPageNumber: 1499,
    },
    {
      id: '03c0a5dd-f9de-46dd-a366-1d7e43dd9a71',
      title: 'سورة الأنعام',
      startPageNumber: 1725,
    },
    {
      id: '8a14ba80-976b-4e9d-b43c-226c54a9a156',
      title: 'تفسير سورة الأعراف',
      startPageNumber: 1864,
    },
    {
      id: 'b7024e83-a2ad-4258-9fdb-006534ffaab3',
      title: 'سورة الأنفال',
      startPageNumber: 2009,
    },
  ];

  // Page 1 content from the backend payload. We split textContent by \r and
  // strip the leading <span data-type='title'> marker — its text becomes the
  // page heading.
  const heading = 'مقدمة الناشر';
  const bodyParagraphs = [
    'الحمد لله رب العالمين، والصلاة والسلام على عبده ورسوله محمد، وعلى آله وصحبه وسلَّم تسليمًا كثيرًا.',

    'أما بعد؛ فإن كتاب «تفسير القرآن العظيم» للحافظ ابن كثير ﵀ من أحسن وأنفع كتب تفسير القرآن، وأوسعها وأكثرها تداولًا وانتشارًا، ولا يزال كتابه مقصد اهتمام للراغبين في معرفة تفسير كتاب الله تعالى بالمأثور عن السلف، من تفسير القرآن بالقرآن، وتفسيره بالسنة، وأقوال الصحابة والتابعين.',

    'كما أن كتابه هذا يعد من المراجع المهمة في هذا العلم، وفي غيره من العلوم الأخرى، ومنزلته العلمية عند أهل العلم أعلى وأرفع من أن ينوه بها، فقد وصفه السيوطي فقال: «له في التفسير الذي لم يؤلف على نمطه»، وقال الشوكاني في وصفه: «وهو من أحسن التفاسير إن لم يكن أحسنها». وقال أحمد شاكر عنه: «فإن تفسير الحافظ ابن كثير من أحسن التفاسير التي رأينا وأجودها وأدقها، بعد تفسير إمام المفسرين أبي جعفر الطبري…».',

    'ومما يزيد من القيمة العلمية للكتاب وأهميته منزلة مؤلفه ﵀ فهو من كبار العلماء وأحد الأئمة الحفاظ المبرِّزين. قال ابن قاضي شهبة عنه: «إسماعيل بن عمر بن كثير الشيخ الإمام العالم العلامة الحافظ شيخ المفسرين، عمدة المحدثين والمؤرخين مفتي المسلمين…».',

    'وثناء العلماء على هذا الكتاب وعلى مؤلفه أمر مشهور ومعلوم، كله يدل على جلالة هذا التفسير العظيم وعظيم قدره عند الناس.',

    'ولذلك فقد رأت دار ابن الجوزي نشر هذا التفسير وطبعه طبعة علمية محققة ما وجدنا إلى ذلك سبيلًا، فكان أول من هيأ الله لنا لتحقيق هذا الكتاب فضيلة الشيخ أبي إسحاق الحويني -حفظه الله ورعاه- سنة ١٤١٢ هـ، فقمنا بإحضار النسخ الخطية للكتاب من مكة والقاهرة والكويت وإستنبول.',
  ];

  window.READER_DATA = window.READER_DATA || {};
  window.READER_DATA.tafsirIbnKathir = {
    id: '02bcfa43-d269-4545-8e8b-965ed56dfc93',
    bookType: 'BOOK',
    title: 'تفسير ابن كثير',
    subtitle: 'طبعة دار ابن الجوزي',
    author: 'الإمام ابن كثير الدمشقي',
    authorFull: 'إسماعيل بن عمر بن كثير الدمشقي',
    deathYearHijri: 774,
    muhaqqiq: 'سامي بن محمد السلامة',
    publisher: 'دار ابن الجوزي',
    publicationPlace: 'الرياض',
    editionNumber: 2,
    publishedYearHijri: 1420,
    publishedYearGregorian: 1999,
    language: 'ar',
    discipline: 'تفسير القرآن العظيم',
    pagesCount: 4720,
    volumes: 7,
    currentPage: 1,           // pageNumber
    currentPrintedPage: '3',  // what's printed on the original
    currentPart: 'المقدمة',
    currentVolume: 1,
    chapters: rawChapters.map((c) => adaptChapter(c, CURRENT_CHAPTER_ID)),
    pageContent: {
      heading,
      body: bodyParagraphs,
    },
    // Topics that cite this page in the argument-map app
    relatedTopics: [
      { id: 't1', title: 'منهج ابن كثير في الترجيح بين الأقوال', nodes: 14, edges: 22, status: 'standing' },
      { id: 't2', title: 'القيمة العلمية للتفسير بالمأثور', nodes: 9, edges: 11, status: 'disputed' },
    ],
  };
})();
