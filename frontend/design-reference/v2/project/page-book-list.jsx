// Redesigned BookListPage.
// Replaces hover-lift slate cards with calmer, denser editorial cards
// that show what matters first: title (in correct script), author, type.

const SAMPLE_BOOKS = [
  { id: 'b1', type: 'BOOK', lang: 'ar', title: 'تفسير ابن كثير', author: 'الإمام ابن كثير', meta: '774h · 7 томов', accent: '#1e3a8a' },
  { id: 'b2', type: 'BOOK', lang: 'ar', title: 'منهاج الطالبين', author: 'الإمام النووي', meta: '676h · 1 том',  accent: '#0f766e' },
  { id: 'b3', type: 'BOOK', lang: 'ar', title: 'الأم', author: 'الإمام الشافعي', meta: '204h · 8 томов', accent: '#92400e' },
  { id: 'b4', type: 'HADITH_COLLECTION', lang: 'ar', title: 'صحيح البخاري', author: 'الإمام البخاري', meta: '256h · 6 томов', accent: '#7c2d12' },
  { id: 'b5', type: 'QURAN', lang: 'ar', title: 'المصحف الشريف', author: '', meta: 'Текст · 604 стр.', accent: '#14532d' },
  { id: 'b6', type: 'BOOK', lang: 'ru', title: 'Раудат ат-талибин', author: 'Имам ан-Навави', meta: 'Шафиитский фикх', accent: '#1e3a8a', current: true },
  { id: 'b7', type: 'ARTICLE', lang: 'ru', title: 'Разногласия о пятничном гусле', author: 'Сборник статей', meta: '2023 · 14 стр.', accent: '#3730a3' },
  { id: 'b8', type: 'MANUSCRIPT', lang: 'ar', title: 'مخطوطة الأزهر', author: '', meta: 'XIV в.', accent: '#5b21b6' },
];

const BOOK_TYPES = [
  { value: 'ALL',    label: 'Все' },
  { value: 'BOOK',   label: 'Книги' },
  { value: 'HADITH_COLLECTION', label: 'Хадисы' },
  { value: 'QURAN',  label: 'Коран' },
  { value: 'ARTICLE', label: 'Статьи' },
  { value: 'MANUSCRIPT', label: 'Рукописи' },
];

const TYPE_BADGE = {
  BOOK:              { bg: 'var(--c-accent-100)', fg: 'var(--c-accent-700)', label: 'Книга' },
  HADITH_COLLECTION: { bg: 'var(--c-warn-100)',   fg: 'var(--c-warn-700)',   label: 'Хадисы' },
  QURAN:             { bg: 'var(--c-ok-100)',     fg: 'var(--c-ok-700)',     label: 'Коран' },
  ARTICLE:           { bg: 'var(--c-ink-100)',    fg: 'var(--c-ink-700)',    label: 'Статья' },
  MANUSCRIPT:        { bg: '#ede9fe',             fg: '#5b21b6',             label: 'Рукопись' },
};

function BookListBoard() {
  return (
    <div style={{
      width: '100%', height: '100%',
      display: 'flex', flexDirection: 'column',
      background: 'var(--c-bg)',
      fontFamily: 'var(--font-ui)',
      color: 'var(--c-ink-900)',
      overflow: 'hidden',
    }}>
      <AppHeader currentPath="/books" />

      <main style={{ flex: 1, overflow: 'auto', padding: '24px 32px 48px' }}>
        {/* Page heading */}
        <div style={{
          display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between',
          gap: 20, marginBottom: 20,
        }}>
          <div>
            <h1 style={{
              margin: 0,
              fontFamily: 'var(--font-serif)',
              fontWeight: 600, fontSize: 28,
              letterSpacing: '-0.01em',
              color: 'var(--c-ink-900)',
            }}>
              Библиотека
            </h1>
            <div style={{ fontSize: 13, color: 'var(--c-ink-500)', marginTop: 4 }}>
              Классические источники для построения графа аргументации ·{' '}
              <span className="mono" style={{ color: 'var(--c-ink-700)', fontWeight: 600 }}>
                {SAMPLE_BOOKS.length}
              </span>{' '}
              записей
            </div>
          </div>
          <button className="btn btn-secondary">
            <Icon name="sparkles" size={13} />
            Импорт из Shamela
          </button>
        </div>

        {/* Filter bar */}
        <div style={{
          display: 'flex', gap: 10, alignItems: 'center', marginBottom: 24,
          flexWrap: 'wrap',
        }}>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 6,
            height: 36, padding: '0 12px', flex: '1 0 280px', maxWidth: 420,
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-soft)',
            borderRadius: 'var(--r-sm)',
          }}>
            <Icon name="search" size={14} style={{ color: 'var(--c-ink-500)' }} />
            <span style={{ flex: 1, fontSize: 13, color: 'var(--c-ink-400)' }}>
              Найти книгу по названию, автору или id
            </span>
            <span className="kbd">/</span>
          </div>
          <div style={{
            display: 'inline-flex',
            padding: 2,
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-soft)',
            borderRadius: 'var(--r-sm)',
          }}>
            {BOOK_TYPES.map((t, i) => (
              <button key={t.value} style={{
                padding: '5px 10px',
                fontSize: 12, fontWeight: 500,
                color: i === 0 ? 'var(--c-ink-0)' : 'var(--c-ink-600)',
                background: i === 0 ? 'var(--c-accent-600)' : 'transparent',
                borderRadius: 3,
              }}>{t.label}</button>
            ))}
          </div>
          <span style={{ flex: 1 }} />
          <div style={{ display: 'flex', gap: 4, alignItems: 'center', fontSize: 12, color: 'var(--c-ink-500)' }}>
            Сортировка:
            <KitSelect value="по добавлению" small />
          </div>
        </div>

        {/* Grid */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
          gap: 16,
        }}>
          {SAMPLE_BOOKS.map((b) => <BookCard key={b.id} book={b} />)}
        </div>
      </main>
    </div>
  );
}

function BookCard({ book }) {
  const badge = TYPE_BADGE[book.type];
  const isArabic = book.lang === 'ar';
  return (
    <a style={{
      display: 'flex', flexDirection: 'column',
      background: 'var(--c-bg-elevated)',
      border: book.current ? '1.5px solid var(--c-accent-600)' : 'var(--br-hair)',
      borderRadius: 'var(--r-lg)',
      overflow: 'hidden',
      cursor: 'pointer',
      transition: 'border-color 120ms, transform 120ms',
    }}>
      {/* "Cover" — solid color with first letter / glyph */}
      <div style={{
        aspectRatio: '5 / 3',
        background: book.accent,
        position: 'relative',
        display: 'grid', placeItems: 'center',
        overflow: 'hidden',
      }}>
        {/* subtle pattern */}
        <svg width="100%" height="100%" viewBox="0 0 200 120" style={{ position: 'absolute', inset: 0, opacity: 0.15 }} aria-hidden="true">
          <pattern id={`p-${book.id}`} width="20" height="20" patternUnits="userSpaceOnUse">
            <circle cx="1" cy="1" r="0.7" fill="white" />
          </pattern>
          <rect width="100%" height="100%" fill={`url(#p-${book.id})`} />
        </svg>
        <div style={{
          position: 'relative',
          fontFamily: isArabic ? 'var(--font-arabic)' : 'var(--font-serif)',
          fontSize: 36, fontWeight: 600,
          color: 'rgba(255,255,255,0.95)',
          letterSpacing: isArabic ? 0 : '-0.02em',
        }}>
          {isArabic ? book.title.charAt(0) : book.title.charAt(0)}
        </div>
        {book.current && (
          <div style={{
            position: 'absolute',
            top: 8, insetInlineStart: 8,
            fontSize: 10, fontWeight: 600,
            color: 'rgba(255,255,255,0.95)',
            background: 'rgba(0,0,0,0.3)',
            backdropFilter: 'blur(8px)',
            padding: '2px 6px',
            borderRadius: 3,
            display: 'flex', alignItems: 'center', gap: 4,
          }}>
            <Icon name="bookmark" size={10} /> Сейчас читаю
          </div>
        )}
      </div>

      {/* Body */}
      <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <span style={{
            display: 'inline-flex', alignItems: 'center',
            padding: '2px 6px', borderRadius: 3,
            background: badge.bg, color: badge.fg,
            fontSize: 10, fontWeight: 600,
            textTransform: 'uppercase', letterSpacing: '0.04em',
          }}>{badge.label}</span>
          <span className="mono" style={{
            fontSize: 10, color: 'var(--c-ink-500)',
            textTransform: 'uppercase',
          }}>{book.lang}</span>
        </div>
        <h3 dir="auto" style={{
          margin: 0,
          fontFamily: isArabic ? 'var(--font-arabic)' : 'var(--font-serif)',
          fontSize: isArabic ? 18 : 15,
          fontWeight: 600, lineHeight: 1.3,
          color: 'var(--c-ink-900)',
        }}>{book.title}</h3>
        {book.author && (
          <div dir="auto" style={{
            fontSize: isArabic ? 13 : 12,
            color: 'var(--c-ink-600)',
            fontFamily: isArabic ? 'var(--font-arabic)' : undefined,
          }}>{book.author}</div>
        )}
        <div style={{
          marginTop: 6, paddingTop: 8,
          borderTop: 'var(--br-hair)',
          display: 'flex', justifyContent: 'space-between',
          fontSize: 11, color: 'var(--c-ink-500)',
        }}>
          <span>{book.meta}</span>
          <span className="mono" style={{ color: 'var(--c-ink-400)' }}>{book.id}</span>
        </div>
      </div>
    </a>
  );
}

window.BookListBoard = BookListBoard;
