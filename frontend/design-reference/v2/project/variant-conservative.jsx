// VARIANT 1 — Editorial Conservative
// Inspired by Stripe Docs, MIT Press digital, academic journals.
// Wide gutters, serif body, calm chrome, paging by simple inline controls.
// Density: low. Used as the "safe" baseline.

function ReaderVariantConservative({ data }) {
  const book = data;
  const page = book.pageContent;

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      background: 'var(--c-bg-elevated)',
      fontFamily: 'var(--font-ui)',
      color: 'var(--c-ink-900)',
      overflow: 'hidden',
    }}>
      {/* ── Top breadcrumb header ─────────────────────────── */}
      <header style={{
        flex: 'none',
        height: 48,
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '0 24px',
        borderBottom: 'var(--br-hair)',
        fontSize: 13,
        color: 'var(--c-ink-700)',
        background: 'var(--c-paper)',
      }}>
        <Icon name="arrow-left" size={14} />
        <span style={{ color: 'var(--c-ink-500)' }}>Библиотека</span>
        <Icon name="chevron-right" size={12} style={{ color: 'var(--c-ink-300)' }} />
        <span style={{ color: 'var(--c-ink-500)' }}>Шафиитский фикх</span>
        <Icon name="chevron-right" size={12} style={{ color: 'var(--c-ink-300)' }} />
        <span style={{ fontWeight: 500, color: 'var(--c-ink-900)' }}>
          Раудат ат-талибин
        </span>
        <span style={{ flex: 1 }} />
        <button className="btn btn-ghost btn-sm">
          <Icon name="search" size={13} />
          Поиск
          <span className="kbd">⌘K</span>
        </button>
        <span style={{ width: 1, height: 16, background: 'var(--c-ink-150)', margin: '0 4px' }} />
        <button className="btn btn-ghost btn-sm">
          <Icon name="file-text" size={13} />
          PDF
        </button>
      </header>

      {/* ── Body ─────────────────────────────────────────────── */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

        {/* Left: chapter tree, narrow */}
        <aside style={{
          flex: 'none',
          width: 248,
          background: 'var(--c-paper)',
          borderRight: 'var(--br-hair)',
          overflow: 'auto',
          padding: '20px 0',
        }}>
          <div style={{ padding: '0 16px 12px' }}>
            <div style={{
              fontFamily: 'var(--font-serif)',
              fontSize: 16,
              fontWeight: 600,
              color: 'var(--c-ink-900)',
              lineHeight: 1.3,
              marginBottom: 4,
            }}>
              {book.title}
            </div>
            <div style={{ fontSize: 12, color: 'var(--c-ink-500)' }}>
              {book.author} · <span className="mono">{book.yearHijri}h</span>
            </div>
          </div>

          <div style={{
            padding: '12px 16px 8px',
            fontSize: 10,
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: '0.08em',
            color: 'var(--c-ink-400)',
          }}>
            Содержание
          </div>

          <ChapterTree
            chapters={book.chapters}
            activeClass="is-current"
          />
        </aside>

        {/* Center: serif reader */}
        <main style={{
          flex: 1,
          overflow: 'auto',
          background: 'var(--c-bg-elevated)',
          position: 'relative',
        }}>
          <article style={{
            maxWidth: 640,
            margin: '0 auto',
            padding: '56px 32px 80px',
          }}>
            {/* Book title block */}
            <div style={{
              paddingBottom: 28,
              marginBottom: 36,
              borderBottom: 'var(--br-hair)',
            }}>
              <div style={{
                fontSize: 11,
                fontWeight: 600,
                letterSpacing: '0.1em',
                textTransform: 'uppercase',
                color: 'var(--c-ink-500)',
                marginBottom: 6,
              }}>
                {book.discipline}
              </div>
              <h1 style={{
                fontFamily: 'var(--font-serif)',
                fontWeight: 600,
                fontSize: 36,
                lineHeight: 1.15,
                margin: '0 0 8px',
                letterSpacing: '-0.01em',
              }}>
                {book.title}
              </h1>
              <div style={{ fontSize: 13, color: 'var(--c-ink-600)' }}>
                {book.author} · <span className="mono">{book.yearCE} (671 АН)</span> · {book.pagesCount} стр.
              </div>
            </div>

            {/* Chapter heading */}
            <div style={{ marginBottom: 32 }}>
              <div style={{
                fontSize: 11,
                color: 'var(--c-ink-500)',
                marginBottom: 6,
                fontVariantNumeric: 'tabular-nums',
              }}>
                Том {book.currentVolume} · стр. <span className="mono">{book.currentPage}</span>
              </div>
              <h2 style={{
                fontFamily: 'var(--font-serif)',
                fontSize: 26,
                fontWeight: 600,
                lineHeight: 1.2,
                margin: 0,
              }}>
                {page.heading}
              </h2>
            </div>

            {/* Prose */}
            <div className="prose">
              <ProseBody paragraphs={page.body} />
            </div>

            {/* Footnotes */}
            <hr style={{
              margin: '48px 0 16px',
              border: 0,
              borderTop: 'var(--br-hair)',
              width: 96,
            }} />
            <ol style={{
              margin: 0,
              padding: 0,
              listStyle: 'none',
              fontFamily: 'var(--font-serif)',
              fontSize: 13,
              color: 'var(--c-ink-700)',
              lineHeight: 1.6,
            }}>
              {page.footnotes.map((fn) => (
                <li key={fn.n} style={{ display: 'flex', gap: 10, margin: '0 0 6px' }}>
                  <span className="mono" style={{
                    fontSize: 11,
                    color: 'var(--c-accent-600)',
                    fontWeight: 500,
                    paddingTop: 2,
                  }}>{fn.n}</span>
                  <span>{fn.text}</span>
                </li>
              ))}
            </ol>

            {/* Page nav */}
            <nav style={{
              marginTop: 64,
              paddingTop: 24,
              borderTop: 'var(--br-hair)',
              display: 'flex',
              justifyContent: 'space-between',
              gap: 16,
            }}>
              <a className="page-nav prev" style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
                padding: '12px 16px',
                border: 'var(--br-hair)',
                borderRadius: 'var(--r-md)',
                maxWidth: 240,
                flex: 1,
                cursor: 'pointer',
              }}>
                <span style={{ fontSize: 11, color: 'var(--c-ink-500)', display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Icon name="chevron-left" size={12} /> Предыдущая · стр. 73
                </span>
                <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontWeight: 500 }}>
                  Глава: Сивак
                </span>
              </a>
              <a className="page-nav next" style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
                padding: '12px 16px',
                border: 'var(--br-hair)',
                borderRadius: 'var(--r-md)',
                maxWidth: 240,
                flex: 1,
                cursor: 'pointer',
                textAlign: 'end',
                alignItems: 'flex-end',
              }}>
                <span style={{ fontSize: 11, color: 'var(--c-ink-500)', display: 'flex', alignItems: 'center', gap: 4 }}>
                  стр. 75 · Следующая <Icon name="chevron-right" size={12} />
                </span>
                <span style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontWeight: 500 }}>
                  Глава: Малое омовение
                </span>
              </a>
            </nav>
          </article>

          {/* Floating right-margin marker (sticky page number) — academic marginalia */}
          <div style={{
            position: 'absolute',
            top: 56,
            right: 16,
            fontSize: 10,
            fontFamily: 'var(--font-mono)',
            color: 'var(--c-ink-400)',
            letterSpacing: '0.1em',
            writingMode: 'vertical-rl',
            transform: 'rotate(180deg)',
            textTransform: 'uppercase',
          }}>
            ТОМ {book.currentVolume} · СТР {book.currentPage}
          </div>
        </main>
      </div>
    </div>
  );
}

window.ReaderVariantConservative = ReaderVariantConservative;
