// VARIANT 3 — Experimental Focus mode
// Inspired by iA Writer, Readwise, Tufte-style marginalia.
// Most chrome hidden; content fills the canvas; navigation is keyboard-first.
// Right margin holds footnotes as marginalia (not at end of page).
// Density: low. The "reading session" experience.

function ReaderVariantFocus({ data }) {
  const book = data;
  const page = book.pageContent;

  return (
    <div style={{
      width: '100%',
      height: '100%',
      position: 'relative',
      background: 'var(--c-paper)',
      color: 'var(--c-ink-900)',
      overflow: 'hidden',
      fontFamily: 'var(--font-ui)',
    }}>
      {/* ── Floating chrome (top corners only) ───────────────── */}
      {/* Top-left: book ident, small */}
      <div style={{
        position: 'absolute',
        top: 20,
        left: 24,
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        zIndex: 5,
      }}>
        <button className="btn btn-ghost btn-sm btn-icon" aria-label="Назад в библиотеку"
                style={{ background: 'rgba(255,255,255,0.7)', backdropFilter: 'blur(8px)' }}>
          <Icon name="arrow-left" size={14} />
        </button>
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
          <span style={{
            fontFamily: 'var(--font-serif)',
            fontSize: 13,
            fontWeight: 600,
          }}>{book.title}</span>
          <span style={{
            fontSize: 10,
            color: 'var(--c-ink-500)',
            letterSpacing: '0.04em',
          }}>{book.author}</span>
        </div>
      </div>

      {/* Top-right: minimal toolbar */}
      <div style={{
        position: 'absolute',
        top: 18,
        right: 20,
        display: 'flex',
        gap: 4,
        zIndex: 5,
        background: 'rgba(255,255,255,0.7)',
        backdropFilter: 'blur(8px)',
        padding: 4,
        borderRadius: 'var(--r-md)',
        border: 'var(--br-hair)',
      }}>
        <button className="btn btn-ghost btn-sm btn-icon" aria-label="Содержание"><Icon name="list" size={14} /></button>
        <button className="btn btn-ghost btn-sm btn-icon" aria-label="Сравнить с PDF"><Icon name="columns" size={14} /></button>
        <button className="btn btn-ghost btn-sm btn-icon" aria-label="Параллельный арабский"><Icon name="sparkles" size={14} /></button>
        <span style={{ width: 1, height: 18, background: 'var(--c-ink-150)', alignSelf: 'center' }} />
        <button className="btn btn-ghost btn-sm btn-icon is-active" aria-label="Закладка"><Icon name="bookmark" size={14} /></button>
      </div>

      {/* ── Reading column ───────────────────────────────────── */}
      <div style={{
        height: '100%',
        overflow: 'auto',
        display: 'grid',
        gridTemplateColumns: '1fr min(720px, calc(100% - 320px)) 240px 1fr',
        gridTemplateRows: 'auto 1fr auto',
        rowGap: 0,
        columnGap: 32,
        padding: '92px 32px 60px',
      }}>
        {/* Chapter eyebrow */}
        <div style={{
          gridColumn: '2 / 3',
          fontSize: 10,
          fontWeight: 600,
          letterSpacing: '0.18em',
          textTransform: 'uppercase',
          color: 'var(--c-ink-500)',
          marginBottom: 16,
          display: 'flex',
          alignItems: 'center',
          gap: 12,
        }}>
          <span>Книга очищения</span>
          <span style={{ flex: 1, height: 1, background: 'var(--c-ink-200)' }} />
          <span className="mono" style={{ fontWeight: 400, letterSpacing: '0.05em' }}>
            {book.currentPage} / {book.pagesCount}
          </span>
        </div>

        {/* Title */}
        <h1 style={{
          gridColumn: '2 / 3',
          fontFamily: 'var(--font-serif)',
          fontWeight: 600,
          fontSize: 44,
          lineHeight: 1.1,
          margin: '0 0 8px',
          letterSpacing: '-0.015em',
        }}>
          {page.heading}
        </h1>
        <div style={{
          gridColumn: '2 / 3',
          fontFamily: 'var(--font-serif)',
          fontStyle: 'italic',
          color: 'var(--c-ink-500)',
          fontSize: 16,
          marginBottom: 40,
        }}>
          {book.title} · {book.author}
        </div>

        {/* Body with margin notes column */}
        <div className="prose" style={{
          gridColumn: '2 / 3',
          fontSize: 19,
          lineHeight: 1.8,
        }}>
          {page.body.map((para, i) => (
            <FocusParagraph key={i} text={para} footnotes={page.footnotes} index={i} />
          ))}
        </div>

        {/* Margin notes column lives in grid col 3 — laid out by FocusParagraph via grid-area in float mode.
            For clarity we render footnotes here directly aligned roughly with their refs. */}
        <aside style={{
          gridColumn: '3 / 4',
          gridRow: '3 / 4',
          alignSelf: 'start',
          marginTop: -10,
        }} aria-label="Сноски">
          {page.footnotes.map((fn) => (
            <div key={fn.n} style={{
              fontFamily: 'var(--font-serif)',
              fontSize: 12.5,
              lineHeight: 1.55,
              color: 'var(--c-ink-700)',
              marginBottom: 18,
              paddingInlineStart: 12,
              borderInlineStart: '1px solid var(--c-ink-200)',
            }}>
              <span className="mono" style={{
                fontSize: 10,
                color: 'var(--c-accent-600)',
                fontWeight: 600,
                marginInlineEnd: 6,
              }}>{fn.n}</span>
              {fn.text}
            </div>
          ))}
        </aside>
      </div>

      {/* ── Bottom progress + keyboard hints ─────────────────── */}
      <div style={{
        position: 'absolute',
        left: 0,
        right: 0,
        bottom: 0,
        padding: '14px 24px',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        background: 'linear-gradient(to top, rgba(251,250,245,0.95), rgba(251,250,245,0))',
        pointerEvents: 'none',
      }}>
        <div style={{
          display: 'flex',
          gap: 8,
          alignItems: 'center',
          pointerEvents: 'auto',
        }}>
          <span className="kbd">J</span>
          <span style={{ fontSize: 11, color: 'var(--c-ink-500)' }}>дальше</span>
          <span className="kbd" style={{ marginInlineStart: 8 }}>K</span>
          <span style={{ fontSize: 11, color: 'var(--c-ink-500)' }}>назад</span>
          <span className="kbd" style={{ marginInlineStart: 8 }}>G</span>
          <span style={{ fontSize: 11, color: 'var(--c-ink-500)' }}>граф темы</span>
        </div>

        <span style={{ flex: 1 }} />

        <div style={{
          flex: '0 0 320px',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          pointerEvents: 'auto',
          background: 'rgba(255,255,255,0.7)',
          backdropFilter: 'blur(8px)',
          padding: '6px 12px',
          borderRadius: 99,
          border: 'var(--br-hair)',
        }}>
          <Icon name="chevron-left" size={12} style={{ color: 'var(--c-ink-500)' }} />
          <div style={{
            flex: 1,
            height: 4,
            background: 'var(--c-ink-150)',
            borderRadius: 99,
            overflow: 'hidden',
          }}>
            <div style={{
              width: `${(book.currentPage / book.pagesCount) * 100}%`,
              height: '100%',
              background: 'var(--c-accent-600)',
            }} />
          </div>
          <Icon name="chevron-right" size={12} style={{ color: 'var(--c-ink-500)' }} />
          <span className="mono" style={{ fontSize: 11, color: 'var(--c-ink-600)' }}>
            {Math.round((book.currentPage / book.pagesCount) * 100)}%
          </span>
        </div>
      </div>
    </div>
  );
}

function FocusParagraph({ text, index }) {
  // Replace {ref:N} with sup and drop {aside:...}, since asides aren't part
  // of focus-mode flow — let the marginal column carry the load.
  const cleaned = text.replace(/\{aside:[^}]+\}/g, '');
  return <p style={{ margin: '0 0 1.1em' }}>{renderProseBlock(cleaned)}</p>;
}

window.ReaderVariantFocus = ReaderVariantFocus;
