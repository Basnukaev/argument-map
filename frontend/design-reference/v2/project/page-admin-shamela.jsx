// Redesigned AdminShamelaPage — internal-tool feel without the dryness.
// Sync status as a dense top dashboard, search + results as the main work surface.

const SAMPLE_STAGING = [
  { id: 16873, name: 'تفسير الطبري', author: 'الطبري',         major: 2, mapped: true },
  { id: 1503,  name: 'تفسير ابن كثير - ط ابن الجوزي', author: 'ابن كثير',  major: 2, mapped: true },
  { id: 23901, name: 'الأم', author: 'الشافعي',              major: 2, mapped: false, importing: true },
  { id: 5512,  name: 'منهاج الطالبين وعمدة المفتين', author: 'النووي',     major: 2, mapped: false },
  { id: 1129,  name: 'الموطأ', author: 'مالك بن أنس',          major: 1, mapped: false },
  { id: 17721, name: 'الرسالة', author: 'الشافعي',            major: 2, mapped: true },
  { id: 31288, name: 'كشف الأسرار عن أصول فخر الإسلام البزدوي', author: 'البخاري عبد العزيز',  major: 2, mapped: false },
];

function AdminShamelaBoard() {
  return (
    <div style={{
      width: '100%', height: '100%',
      display: 'flex', flexDirection: 'column',
      background: 'var(--c-bg)',
      fontFamily: 'var(--font-ui)',
      color: 'var(--c-ink-900)',
      overflow: 'hidden',
    }}>
      <AppHeader currentPath="/admin" />

      <main style={{ flex: 1, overflow: 'auto', padding: '20px 32px 60px' }}>
        {/* Title */}
        <div style={{
          display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between',
          gap: 16, marginBottom: 16,
        }}>
          <div>
            <div style={{
              fontSize: 11, fontWeight: 600,
              letterSpacing: '0.1em', textTransform: 'uppercase',
              color: 'var(--c-ink-500)', marginBottom: 4,
            }}>Админ · импорт</div>
            <h1 style={{
              margin: 0,
              fontFamily: 'var(--font-serif)',
              fontWeight: 600, fontSize: 24,
              letterSpacing: '-0.005em',
            }}>Каталог Shamela</h1>
            <p style={{
              margin: '4px 0 0',
              fontSize: 12.5, color: 'var(--c-ink-500)',
            }}>
              Поиск и импорт книг из staging-каталога shamela.ws через desktop-API
            </p>
          </div>

          <button className="btn btn-primary">
            <Icon name="sparkles" size={13} />
            Синхронизировать каталог
          </button>
        </div>

        {/* Status dashboard */}
        <div style={{
          background: 'var(--c-bg-elevated)',
          border: 'var(--br-hair)',
          borderRadius: 'var(--r-lg)',
          padding: '16px 20px',
          marginBottom: 20,
          display: 'grid',
          gridTemplateColumns: 'repeat(5, 1fr) auto',
          gap: 24,
          alignItems: 'center',
        }}>
          <Stat label="Версия" value="8 517" hint="Последний sync 14:22" mono />
          <Stat label="Категорий" value="42" />
          <Stat label="Авторов" value="3 681" />
          <Stat label="Книг в staging" value="8 423" />
          <Stat label="Замаплено" value="247" hint="из 8 423" accent />

          <div style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '6px 12px',
            background: 'var(--c-ok-100)',
            color: 'var(--c-ok-700)',
            borderRadius: 99,
            fontSize: 12, fontWeight: 500,
            border: '1px solid color-mix(in srgb, var(--c-ok-500) 30%, transparent)',
          }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--c-ok-500)' }} />
            Каталог актуален
          </div>
        </div>

        {/* Search */}
        <div style={{ marginBottom: 14 }}>
          <h2 style={{
            margin: '0 0 10px',
            fontSize: 13, fontWeight: 600,
            display: 'flex', alignItems: 'center', gap: 6,
          }}>
            <Icon name="search" size={14} style={{ color: 'var(--c-accent-600)' }} />
            Поиск в каталоге
          </h2>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 6,
            height: 40, padding: '0 12px', maxWidth: 540,
            background: 'var(--c-bg-elevated)',
            border: '1.5px solid var(--c-accent-500)',
            borderRadius: 'var(--r-sm)',
            boxShadow: '0 0 0 3px color-mix(in srgb, var(--c-accent-500) 14%, transparent)',
          }}>
            <Icon name="search" size={14} style={{ color: 'var(--c-ink-500)' }} />
            <span dir="auto" style={{
              flex: 1, fontSize: 14,
              color: 'var(--c-ink-900)',
              fontFamily: 'var(--font-arabic)',
            }}>تفسير</span>
            <span className="mono" style={{ fontSize: 11, color: 'var(--c-ink-500)' }}>
              7 совпадений
            </span>
          </div>
        </div>

        {/* Results table */}
        <div style={{
          background: 'var(--c-bg-elevated)',
          border: 'var(--br-hair)',
          borderRadius: 'var(--r-lg)',
          overflow: 'hidden',
        }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: '88px 1fr 200px 80px 200px',
            padding: '8px 16px',
            borderBottom: 'var(--br-hair)',
            background: 'var(--c-bg-sunken)',
            fontSize: 10, fontWeight: 600,
            color: 'var(--c-ink-500)',
            textTransform: 'uppercase',
            letterSpacing: '0.08em',
          }}>
            <div>ID</div>
            <div>Название</div>
            <div>Автор</div>
            <div>Major</div>
            <div style={{ textAlign: 'end' }}>Статус</div>
          </div>
          {SAMPLE_STAGING.map((b, i) => (
            <StagingRow key={b.id} book={b} isLast={i === SAMPLE_STAGING.length - 1} />
          ))}
        </div>

        {/* Activity log */}
        <section style={{ marginTop: 24 }}>
          <h2 style={{
            margin: '0 0 8px',
            fontSize: 13, fontWeight: 600,
            display: 'flex', alignItems: 'center', gap: 6,
          }}>
            <Icon name="file-text" size={14} style={{ color: 'var(--c-ink-500)' }} />
            Лог импорта
          </h2>
          <div style={{
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-hair)',
            borderRadius: 'var(--r-md)',
            padding: '8px 14px',
            fontSize: 12,
            fontFamily: 'var(--font-mono)',
            color: 'var(--c-ink-700)',
            lineHeight: 1.7,
            maxHeight: 140,
            overflow: 'auto',
          }}>
            {[
              { t: '14:22:08', lvl: 'ok',   msg: 'sync-master: ничего нового (v 8517)' },
              { t: '14:18:45', lvl: 'ok',   msg: 'import-book/1503 → 4720 стр., 239 глав' },
              { t: '14:18:45', lvl: 'ok',   msg: 'map-book/1503 → lib_books/02bcfa43-d269…' },
              { t: '14:12:11', lvl: 'warn', msg: 'import-book/23901 → 6 страниц без printedPage' },
              { t: '14:02:54', lvl: 'err',  msg: 'import-book/77810 → 422: PDF не найден на archive.org' },
            ].map((l, i) => (
              <div key={i} style={{ display: 'flex', gap: 12 }}>
                <span style={{ color: 'var(--c-ink-400)' }}>{l.t}</span>
                <span style={{
                  color: l.lvl === 'ok' ? 'var(--c-ok-700)' : l.lvl === 'warn' ? 'var(--c-warn-700)' : 'var(--c-err-700)',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  fontSize: 10,
                  paddingTop: 2,
                  minWidth: 36,
                }}>{l.lvl}</span>
                <span style={{ fontFamily: 'var(--font-mono)' }}>{l.msg}</span>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

function Stat({ label, value, hint, mono = true, accent = false }) {
  return (
    <div>
      <div style={{
        fontSize: 10, fontWeight: 600,
        letterSpacing: '0.08em', textTransform: 'uppercase',
        color: 'var(--c-ink-500)', marginBottom: 4,
      }}>{label}</div>
      <div style={{
        fontFamily: mono ? 'var(--font-mono)' : 'var(--font-ui)',
        fontSize: 22, fontWeight: 700,
        color: accent ? 'var(--c-accent-600)' : 'var(--c-ink-900)',
        fontVariantNumeric: 'tabular-nums',
        lineHeight: 1,
      }}>{value}</div>
      {hint && <div style={{ fontSize: 10, color: 'var(--c-ink-500)', marginTop: 4 }}>{hint}</div>}
    </div>
  );
}

function StagingRow({ book, isLast }) {
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '88px 1fr 200px 80px 200px',
      alignItems: 'center',
      padding: '10px 16px',
      borderBottom: isLast ? 'none' : 'var(--br-hair)',
      fontSize: 13,
    }}>
      <div className="mono" style={{ color: 'var(--c-ink-500)' }}>{book.id}</div>
      <div dir="auto" style={{
        fontFamily: 'var(--font-arabic)',
        fontSize: 15, fontWeight: 500,
        color: 'var(--c-ink-900)',
        lineHeight: 1.4,
        paddingInlineEnd: 8,
        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
      }}>{book.name}</div>
      <div dir="auto" style={{
        fontFamily: 'var(--font-arabic)',
        fontSize: 13,
        color: 'var(--c-ink-600)',
      }}>{book.author}</div>
      <div className="mono" style={{ fontSize: 11, color: 'var(--c-ink-500)' }}>
        v{book.major}
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        {book.mapped ? (
          <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <span className="chip chip-ok" style={{ fontSize: 10 }}>
              <Icon name="sparkles" size={10} /> Импортирована
            </span>
            <button className="btn btn-ghost btn-sm">
              <Icon name="book" size={11} /> Открыть
            </button>
          </div>
        ) : book.importing ? (
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '4px 10px',
            background: 'var(--c-accent-50)',
            color: 'var(--c-accent-700)',
            borderRadius: 'var(--r-sm)',
            fontSize: 12, fontWeight: 500,
            border: '1px solid color-mix(in srgb, var(--c-accent-600) 25%, transparent)',
          }}>
            <span style={{
              width: 10, height: 10, borderRadius: '50%',
              border: '2px solid var(--c-accent-600)',
              borderTopColor: 'transparent',
              animation: 'spin 0.8s linear infinite',
            }} />
            Импорт 23 / 487
          </span>
        ) : (
          <button className="btn btn-primary btn-sm">
            <Icon name="sparkles" size={11} /> Импортировать
          </button>
        )}
      </div>
    </div>
  );
}

window.AdminShamelaBoard = AdminShamelaBoard;
