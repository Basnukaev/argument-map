// UI Kit showcase — every primitive in one place so the system is documented
// at a glance: Card · Button · IconButton · Badge · Chip · TypeChip · StatusBadge
// · Select · Modal · ContextMenu · Toast · Kbd · empty/error states.

function UIKitBoard() {
  return (
    <div style={{
      width: '100%', height: '100%',
      background: 'var(--c-bg)',
      padding: 0,
      overflow: 'auto',
      fontFamily: 'var(--font-ui)',
      color: 'var(--c-ink-900)',
    }}>
      <div style={{
        padding: '24px 32px 12px',
        borderBottom: 'var(--br-hair)',
        background: 'var(--c-ink-0)',
      }}>
        <h2 style={{
          margin: 0,
          fontFamily: 'var(--font-serif)',
          fontSize: 24, fontWeight: 600,
          letterSpacing: '-0.01em',
        }}>UI Kit</h2>
        <p style={{
          margin: '4px 0 0',
          fontSize: 13,
          color: 'var(--c-ink-500)',
        }}>
          Все примитивы. Переключи тёмную тему в Tweaks справа внизу, чтобы увидеть оба варианта.
        </p>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(2, 1fr)',
        gap: 24,
        padding: 32,
      }}>
        {/* Buttons */}
        <KitSection title="Кнопки" hint="3 варианта · 2 размера">
          <KitRow>
            <button className="btn btn-primary">Primary</button>
            <button className="btn btn-secondary">Secondary</button>
            <button className="btn btn-ghost">Ghost</button>
            <button className="btn btn-primary" disabled>Disabled</button>
          </KitRow>
          <KitRow>
            <button className="btn btn-primary btn-sm"><Icon name="sparkles" size={12} /> Sm</button>
            <button className="btn btn-secondary btn-sm"><Icon name="search" size={12} /> Sm</button>
            <button className="btn btn-ghost btn-sm">Sm Ghost</button>
            <button className="btn btn-secondary btn-icon" aria-label="more"><Icon name="menu" size={14} /></button>
          </KitRow>
        </KitSection>

        {/* Chips & Badges */}
        <KitSection title="Чипы · Бейджи" hint="Семантика типов и статусов">
          <KitRow>
            <span className="chip">defaults</span>
            <span className="chip chip-accent"><Icon name="book" size={11} /> accent</span>
            <span className="chip chip-ok">OK / STANDING</span>
            <span className="chip" style={{ background: 'var(--c-warn-100)', color: 'var(--c-warn-700)' }}>DISPUTED</span>
            <span className="chip" style={{ background: 'var(--c-err-100)', color: 'var(--c-err-700)' }}>REFUTED</span>
          </KitRow>
          <KitRow>
            <TypeChip type="QUESTION" />
            <TypeChip type="CLAIM" />
            <TypeChip type="ARGUMENT" />
            <TypeChip type="EVIDENCE" />
          </KitRow>
          <KitRow>
            <StatusBadge status="STANDING" />
            <StatusBadge status="DISPUTED" />
            <StatusBadge status="REFUTED" />
            <StatusBadge status="UNVERIFIED" />
          </KitRow>
        </KitSection>

        {/* Inputs */}
        <KitSection title="Поля ввода">
          <FieldInput label="Название" value="Дозволенность мавлида" />
          <FieldInput label="Поиск" value="" placeholder="Найти книгу" icon="search" />
          <FieldInput label="С ошибкой" value="123" error="Не может быть короче 5 символов" />
          <FieldTextarea label="Описание" value="Краткое описание темы аргументации." />
        </KitSection>

        {/* Selects + Kbd */}
        <KitSection title="Selects · Keys · Avatar">
          <KitRow>
            <KitSelect value="Шафиитский фикх" />
            <KitSelect value="Все типы" small />
          </KitRow>
          <KitRow>
            <span className="kbd">⌘K</span>
            <span className="kbd">⌘</span>
            <span className="kbd">⇧</span>
            <span className="kbd">/</span>
            <span className="kbd">Esc</span>
            <span className="kbd">↵</span>
            <span style={{ marginInlineStart: 16 }} />
            <div style={{
              display: 'inline-flex', gap: 6, alignItems: 'center',
            }}>
              {['МА', 'АС', '+3'].map((s, i) => (
                <div key={i} style={{
                  width: 26, height: 26, borderRadius: '50%',
                  background: i < 2 ? 'var(--c-accent-100)' : 'var(--c-ink-100)',
                  color: i < 2 ? 'var(--c-accent-700)' : 'var(--c-ink-600)',
                  display: 'grid', placeItems: 'center',
                  fontSize: 10, fontWeight: 600,
                  marginInlineStart: i ? -10 : 0,
                  border: '2px solid var(--c-bg-elevated)',
                }}>{s}</div>
              ))}
            </div>
          </KitRow>
        </KitSection>

        {/* Cards */}
        <KitSection title="Карточки">
          <DemoCard>
            <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
              <div style={{ flex: 1 }}>
                <div className="mono" style={{ fontSize: 10, color: 'var(--c-ink-500)', marginBottom: 4 }}>BOOK · ar</div>
                <h4 dir="auto" style={{ margin: 0, fontSize: 14, fontWeight: 600, fontFamily: 'var(--font-arabic)' }}>
                  تفسير ابن كثير
                </h4>
                <p style={{ margin: '4px 0 0', fontSize: 12, color: 'var(--c-ink-500)' }}>Ибн Касир · 774h</p>
              </div>
              <Icon name="chevron-right" size={14} style={{ color: 'var(--c-ink-400)' }} />
            </div>
          </DemoCard>

          <DemoCard error>
            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
              <Icon name="help-circle" size={16} style={{ color: 'var(--c-err-500)', marginTop: 1 }} />
              <div>
                <div style={{ fontWeight: 600, color: 'var(--c-err-700)', fontSize: 13 }}>Не удалось загрузить</div>
                <div style={{ fontSize: 12, color: 'var(--c-err-700)', marginTop: 2 }}>Тема с id=t-99 не найдена</div>
              </div>
            </div>
          </DemoCard>
        </KitSection>

        {/* Modal preview */}
        <KitSection title="Модалка (preview)">
          <div style={{
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-hair)',
            borderRadius: 'var(--r-lg)',
            boxShadow: 'var(--sh-3)',
            padding: 16,
            maxWidth: 360,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
              <h4 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Добавить узел</h4>
              <span style={{ flex: 1 }} />
              <button className="btn btn-ghost btn-sm btn-icon"><Icon name="x" size={14} /></button>
            </div>
            <FieldInput label="Содержание" value="Большинство шафиитов считают пятничный гусль желательным…" />
            <div style={{ marginTop: 12 }}>
              <div style={{ fontSize: 11, fontWeight: 500, color: 'var(--c-ink-600)', marginBottom: 6 }}>Тип узла</div>
              <KitRow>
                <TypeChip type="CLAIM" selected />
                <TypeChip type="ARGUMENT" />
                <TypeChip type="EVIDENCE" />
              </KitRow>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className="btn btn-ghost btn-sm">Отмена</button>
              <button className="btn btn-primary btn-sm">Создать</button>
            </div>
          </div>
        </KitSection>

        {/* Context menu */}
        <KitSection title="Контекстное меню">
          <div style={{
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-hair)',
            borderRadius: 'var(--r-md)',
            boxShadow: 'var(--sh-3)',
            padding: 4,
            maxWidth: 260,
          }}>
            {[
              { icon: 'sparkles', label: 'Открыть', kbd: '↵' },
              { icon: 'graph',    label: 'Создать связанный узел', kbd: '⌘N' },
              { icon: 'quote',    label: 'Скопировать как цитату' },
              { icon: 'bookmark', label: 'Закладка', kbd: 'B' },
            ].map((it) => (
              <button key={it.label} style={{
                display: 'flex', width: '100%', alignItems: 'center',
                gap: 8, padding: '6px 10px',
                fontSize: 13, color: 'var(--c-ink-800)',
                borderRadius: 'var(--r-sm)',
              }}
              onMouseOver={(e) => e.currentTarget.style.background = 'var(--c-ink-50)'}
              onMouseOut={(e) => e.currentTarget.style.background = 'transparent'}>
                <Icon name={it.icon} size={14} style={{ color: 'var(--c-ink-500)' }} />
                <span style={{ flex: 1, textAlign: 'start' }}>{it.label}</span>
                {it.kbd && <span className="kbd">{it.kbd}</span>}
              </button>
            ))}
            <div style={{ height: 1, background: 'var(--c-ink-150)', margin: '4px 0' }} />
            <button style={{
              display: 'flex', width: '100%', alignItems: 'center',
              gap: 8, padding: '6px 10px',
              fontSize: 13, color: 'var(--c-err-500)',
              borderRadius: 'var(--r-sm)',
            }}>
              <Icon name="x" size={14} />
              Удалить
            </button>
          </div>
        </KitSection>

        {/* Toast */}
        <KitSection title="Тосты">
          <Toast type="success" text="Тема создана · 12 узлов, 18 рёбер" />
          <Toast type="warning" text="Страница не найдена, открыта первая" />
          <Toast type="error"   text="БД недоступна. Попробуй ещё раз через минуту" />
          <Toast type="info"    text="Каталог обновлён до версии 8517" />
        </KitSection>

        {/* Empty state */}
        <KitSection title="Empty state">
          <div style={{
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-hair)',
            borderRadius: 'var(--r-lg)',
            padding: 36,
            textAlign: 'center',
          }}>
            <Icon name="graph" size={32} style={{ color: 'var(--c-ink-400)' }} />
            <h4 style={{ margin: '10px 0 4px', fontSize: 14, fontWeight: 600 }}>Пока нет тем</h4>
            <p style={{ margin: '0 0 14px', fontSize: 12, color: 'var(--c-ink-500)' }}>
              Создай первую тему дискуссии, чтобы начать строить граф аргументации.
            </p>
            <button className="btn btn-primary btn-sm"><Icon name="sparkles" size={12} /> Создать тему</button>
          </div>
        </KitSection>
      </div>
    </div>
  );
}

function KitSection({ title, hint, children }) {
  return (
    <section style={{
      background: 'var(--c-bg-elevated)',
      border: 'var(--br-hair)',
      borderRadius: 'var(--r-lg)',
      padding: 18,
      display: 'flex',
      flexDirection: 'column',
      gap: 14,
    }}>
      <header>
        <h3 style={{
          margin: 0, fontSize: 11, fontWeight: 600,
          letterSpacing: '0.1em', textTransform: 'uppercase',
          color: 'var(--c-ink-500)',
        }}>{title}</h3>
        {hint && <div style={{ fontSize: 11, color: 'var(--c-ink-400)', marginTop: 2 }}>{hint}</div>}
      </header>
      {children}
    </section>
  );
}

function KitRow({ children }) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
      {children}
    </div>
  );
}

const TYPE_TOKENS = {
  QUESTION: { bg: 'var(--c-accent-100)', fg: 'var(--c-accent-700)', icon: 'help-circle', label: 'Вопрос' },
  CLAIM:    { bg: 'var(--c-ink-100)',     fg: 'var(--c-ink-800)',     icon: 'quote',       label: 'Тезис' },
  ARGUMENT: { bg: 'var(--c-warn-100)',    fg: 'var(--c-warn-700)',    icon: 'sparkles',    label: 'Довод' },
  EVIDENCE: { bg: 'var(--c-ok-100)',      fg: 'var(--c-ok-700)',      icon: 'file-text',   label: 'Свид.' },
};
function TypeChip({ type, selected = false }) {
  const t = TYPE_TOKENS[type];
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4,
      padding: '3px 8px',
      borderRadius: 'var(--r-sm)',
      background: selected ? t.fg : t.bg,
      color: selected ? 'var(--c-ink-0)' : t.fg,
      fontSize: 11,
      fontWeight: 500,
      border: selected ? '1px solid transparent' : `1px solid color-mix(in srgb, ${t.fg} 18%, transparent)`,
    }}>
      <Icon name={t.icon} size={11} />
      {t.label}
    </span>
  );
}

const STATUS_TOKENS = {
  STANDING:   { bg: 'var(--c-ok-100)',   fg: 'var(--c-ok-700)',   label: 'Устоявшийся' },
  DISPUTED:   { bg: 'var(--c-warn-100)', fg: 'var(--c-warn-700)', label: 'Спорный' },
  REFUTED:    { bg: 'var(--c-err-100)',  fg: 'var(--c-err-700)',  label: 'Опровергнут' },
  UNVERIFIED: { bg: 'var(--c-ink-100)',  fg: 'var(--c-ink-600)',  label: 'Не оценён' },
};
function StatusBadge({ status }) {
  const s = STATUS_TOKENS[status];
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '2px 6px 2px 5px',
      borderRadius: 'var(--r-sm)',
      background: s.bg,
      color: s.fg,
      fontSize: 10, fontWeight: 600,
      textTransform: 'uppercase',
      letterSpacing: '0.04em',
    }}>
      <span style={{ width: 5, height: 5, borderRadius: '50%', background: 'currentColor' }} />
      {s.label}
    </span>
  );
}

function FieldInput({ label, value, placeholder, icon, error }) {
  return (
    <label style={{ display: 'block' }}>
      <div style={{
        fontSize: 11, fontWeight: 500,
        color: 'var(--c-ink-600)', marginBottom: 4,
      }}>{label}</div>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 6,
        height: 34, padding: '0 10px',
        background: 'var(--c-bg-elevated)',
        border: `1px solid ${error ? 'var(--c-err-500)' : 'var(--c-ink-200)'}`,
        borderRadius: 'var(--r-sm)',
      }}>
        {icon && <Icon name={icon} size={13} style={{ color: 'var(--c-ink-500)' }} />}
        <span style={{
          fontSize: 13,
          color: value ? 'var(--c-ink-900)' : 'var(--c-ink-400)',
          flex: 1,
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>
          {value || placeholder || ' '}
        </span>
      </div>
      {error && <div style={{ fontSize: 11, color: 'var(--c-err-500)', marginTop: 4 }}>{error}</div>}
    </label>
  );
}

function FieldTextarea({ label, value }) {
  return (
    <label style={{ display: 'block' }}>
      <div style={{
        fontSize: 11, fontWeight: 500,
        color: 'var(--c-ink-600)', marginBottom: 4,
      }}>{label}</div>
      <div style={{
        padding: 10,
        minHeight: 64,
        background: 'var(--c-bg-elevated)',
        border: 'var(--br-soft)',
        borderRadius: 'var(--r-sm)',
        fontSize: 13,
        color: 'var(--c-ink-900)',
        lineHeight: 1.5,
      }}>{value}</div>
    </label>
  );
}

function KitSelect({ value, small = false }) {
  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: small ? '4px 8px' : '6px 10px',
      background: 'var(--c-bg-elevated)',
      border: 'var(--br-soft)',
      borderRadius: 'var(--r-sm)',
      fontSize: small ? 12 : 13,
      cursor: 'pointer',
    }}>
      {value}
      <Icon name="chevron-down" size={12} style={{ color: 'var(--c-ink-500)' }} />
    </div>
  );
}

function DemoCard({ children, error }) {
  return (
    <div style={{
      padding: 14,
      background: error ? 'var(--c-err-100)' : 'var(--c-bg-elevated)',
      border: error ? '1px solid color-mix(in srgb, var(--c-err-500) 25%, transparent)' : 'var(--br-hair)',
      borderRadius: 'var(--r-md)',
      boxShadow: error ? 'none' : 'var(--sh-1)',
    }}>{children}</div>
  );
}

const TOAST_TOKENS = {
  success: { ico: 'sparkles', fg: 'var(--c-ok-700)',   bg: 'var(--c-ok-100)' },
  warning: { ico: 'help-circle', fg: 'var(--c-warn-700)', bg: 'var(--c-warn-100)' },
  error:   { ico: 'x',        fg: 'var(--c-err-700)',  bg: 'var(--c-err-100)' },
  info:    { ico: 'file-text',fg: 'var(--c-accent-700)', bg: 'var(--c-accent-50)' },
};
function Toast({ type, text }) {
  const t = TOAST_TOKENS[type];
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10,
      padding: '8px 12px',
      background: t.bg,
      color: t.fg,
      border: `1px solid color-mix(in srgb, ${t.fg} 25%, transparent)`,
      borderRadius: 'var(--r-md)',
      fontSize: 13,
    }}>
      <Icon name={t.ico} size={14} />
      <span style={{ flex: 1 }}>{text}</span>
      <button className="btn btn-ghost btn-sm btn-icon" aria-label="close">
        <Icon name="x" size={12} />
      </button>
    </div>
  );
}

// Export pieces for other boards
window.UIKitBoard = UIKitBoard;
window.TypeChip = TypeChip;
window.StatusBadge = StatusBadge;
window.FieldInput = FieldInput;
window.FieldTextarea = FieldTextarea;
window.KitSelect = KitSelect;
window.Toast = Toast;
window.TYPE_TOKENS = TYPE_TOKENS;
window.STATUS_TOKENS = STATUS_TOKENS;
