// Shared top app bar — used across all non-reader pages.
// Logo + nav + locale/user. Stays the same across pages so the
// system feels consistent.

function AppHeader({ currentPath = '/topics', locale = 'ru', density = 6 }) {
  const isCurrent = (path) => currentPath.startsWith(path);
  const nav = [
    { path: '/topics', label: 'Темы',  icon: 'graph' },
    { path: '/books',  label: 'Книги', icon: 'book' },
    { path: '/admin',  label: 'Админ', icon: 'sparkles' },
  ];

  return (
    <header style={{
      flex: 'none',
      height: 52,
      display: 'flex',
      alignItems: 'center',
      gap: 24,
      padding: '0 24px',
      background: 'var(--c-ink-0)',
      borderBottom: 'var(--br-hair)',
    }}>
      {/* Logo */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 26, height: 26,
          borderRadius: 6,
          background: 'var(--c-accent-600)',
          color: 'var(--c-ink-0)',
          display: 'grid', placeItems: 'center',
          fontWeight: 700,
          fontSize: 14,
          fontFamily: 'var(--font-serif)',
          letterSpacing: '-0.04em',
        }}>﷽</div>
        <span style={{
          fontWeight: 600,
          fontSize: 14,
          color: 'var(--c-ink-900)',
          letterSpacing: '-0.005em',
        }}>Argument Map</span>
      </div>

      {/* Nav */}
      <nav style={{ display: 'flex', gap: 2, flex: 1 }}>
        {nav.map((item) => {
          const cur = isCurrent(item.path);
          return (
            <a key={item.path} href={item.path} style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              padding: '6px 10px',
              borderRadius: 'var(--r-sm)',
              fontSize: 13,
              fontWeight: 500,
              color: cur ? 'var(--c-accent-700)' : 'var(--c-ink-700)',
              background: cur ? 'var(--c-accent-50)' : 'transparent',
              cursor: 'pointer',
            }}>
              <Icon name={item.icon} size={14} />
              {item.label}
            </a>
          );
        })}
      </nav>

      {/* Right side */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <button className="btn btn-ghost btn-sm">
          <Icon name="search" size={13} />
          <span className="kbd">⌘K</span>
        </button>
        {/* Locale switch */}
        <div style={{
          display: 'inline-flex',
          padding: 2,
          gap: 1,
          border: 'var(--br-hair)',
          borderRadius: 'var(--r-sm)',
          background: 'var(--c-ink-50)',
        }}>
          {['ru', 'ar', 'en'].map((l) => (
            <button key={l} style={{
              padding: '2px 7px',
              fontSize: 11,
              fontWeight: 500,
              fontFamily: 'var(--font-mono)',
              textTransform: 'uppercase',
              color: l === locale ? 'var(--c-ink-0)' : 'var(--c-ink-500)',
              background: l === locale ? 'var(--c-ink-900)' : 'transparent',
              borderRadius: 3,
              letterSpacing: '0.05em',
            }}>{l}</button>
          ))}
        </div>
        <button className="btn btn-ghost btn-sm btn-icon" aria-label="Уведомления">
          <Icon name="bookmark" size={14} />
        </button>
        {/* Avatar */}
        <div style={{
          width: 28, height: 28,
          borderRadius: '50%',
          background: 'var(--c-accent-100)',
          color: 'var(--c-accent-700)',
          display: 'grid', placeItems: 'center',
          fontSize: 12, fontWeight: 600,
        }}>МА</div>
      </div>
    </header>
  );
}

window.AppHeader = AppHeader;
