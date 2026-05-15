// Shared helpers used by all reader variants.

function renderProseBlock(text) {
  // Parse simple inline markers: {ref:N} → sup; {aside:...} → block aside.
  // Returns array of React children to put in a fragment.
  const out = [];
  let rest = text;
  let keyCtr = 0;
  while (rest.length) {
    const m = rest.match(/\{(ref|aside):([^}]+)\}/);
    if (!m) { out.push(rest); break; }
    if (m.index > 0) out.push(rest.slice(0, m.index));
    if (m[1] === 'ref') {
      out.push(
        <sup key={`r${keyCtr++}`} className="ref" tabIndex={0} title={`Сноска ${m[2]}`}>
          {m[2]}
        </sup>
      );
    } else {
      out.push(<em key={`a${keyCtr++}`} className="aside">{m[2]}</em>);
    }
    rest = rest.slice(m.index + m[0].length);
  }
  return out;
}

function ProseBody({ paragraphs }) {
  return (
    <>
      {paragraphs.map((p, i) => (
        <p key={i}>{renderProseBlock(p)}</p>
      ))}
    </>
  );
}

// Compact chapter tree component. Variants pass their own className for
// the active row so each can style the "current" affordance differently.
function ChapterTree({ chapters, depth = 0, currentChapterId, activeClass = '', onClick, arabicFont = false }) {
  return (
    <ul className="chap-tree" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
      {chapters.map((c) => {
        const isCurrent = c.current || c.id === currentChapterId;
        return (
          <li key={c.id}>
            <button
              type="button"
              onClick={() => onClick?.(c)}
              className={`chap-row ${isCurrent ? activeClass : ''}`}
              style={{
                width: '100%',
                display: 'flex',
                alignItems: 'baseline',
                justifyContent: 'space-between',
                gap: 12,
                padding: `5px 12px 5px ${12 + depth * 16}px`,
                textAlign: 'start',
                fontSize: arabicFont ? (depth === 0 ? 14 : 13) : (depth === 0 ? 13 : 12),
                fontWeight: depth === 0 ? 500 : 400,
                fontFamily: arabicFont ? 'var(--font-arabic)' : undefined,
                color: isCurrent ? 'var(--c-accent-700)' : (depth === 0 ? 'var(--c-ink-900)' : 'var(--c-ink-700)'),
                background: isCurrent ? 'var(--c-accent-50)' : 'transparent',
                borderInlineStart: isCurrent ? '2px solid var(--c-accent-600)' : '2px solid transparent',
                lineHeight: arabicFont ? 1.55 : 1.4,
              }}
            >
              <span dir="auto" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {c.title}
              </span>
              <span className="mono" style={{ fontSize: 10, color: 'var(--c-ink-400)', flex: 'none' }}>
                {c.page}
              </span>
            </button>
            {c.children && (
              <ChapterTree
                chapters={c.children}
                depth={depth + 1}
                currentChapterId={currentChapterId}
                activeClass={activeClass}
                onClick={onClick}
                arabicFont={arabicFont}
              />
            )}
          </li>
        );
      })}
    </ul>
  );
}

window.renderProseBlock = renderProseBlock;
window.ProseBody = ProseBody;
window.ChapterTree = ChapterTree;
