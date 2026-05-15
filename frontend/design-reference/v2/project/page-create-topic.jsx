// Redesigned CreateTopicPage — a focused two-column form.
// Left: form fields. Right: explanation of what a "topic" is + best practices.

function CreateTopicBoard() {
  return (
    <div style={{
      width: '100%', height: '100%',
      display: 'flex', flexDirection: 'column',
      background: 'var(--c-bg)',
      fontFamily: 'var(--font-ui)',
      color: 'var(--c-ink-900)',
      overflow: 'hidden',
    }}>
      <AppHeader currentPath="/topics" />

      <main style={{ flex: 1, overflow: 'auto', padding: '32px 32px 80px' }}>
        <div style={{ maxWidth: 920, margin: '0 auto' }}>
          {/* Breadcrumb */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 6,
            fontSize: 12, color: 'var(--c-ink-500)', marginBottom: 14,
          }}>
            <a style={{ cursor: 'pointer' }}>Темы</a>
            <Icon name="chevron-right" size={12} />
            <span style={{ color: 'var(--c-ink-900)' }}>Новая тема</span>
          </div>

          <h1 style={{
            margin: 0,
            fontFamily: 'var(--font-serif)',
            fontWeight: 600, fontSize: 30,
            letterSpacing: '-0.01em',
          }}>Новая тема дискуссии</h1>
          <p style={{
            margin: '6px 0 28px',
            fontSize: 14, color: 'var(--c-ink-600)',
          }}>
            Тема — это вопрос, вокруг которого строится граф аргументации.
            Тщательно сформулированный корневой вопрос задаёт всю структуру.
          </p>

          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 320px',
            gap: 32,
            alignItems: 'flex-start',
          }}>
            {/* Form */}
            <form style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              <Field
                label="Название"
                hint="Краткая формулировка для списка тем"
                required
              >
                <input
                  type="text"
                  defaultValue="Дозволенность пятничного гусля для участвующих в джум‘а"
                  style={inputStyle()}
                />
                <FieldMeta left="60 / 500" />
              </Field>

              <Field
                label="Описание"
                hint="Необязательно. Контекст темы, чтобы участники быстрее ориентировались."
              >
                <textarea
                  rows={3}
                  defaultValue="Разбор позиций мазхабов: фард по захиритам vs сильно желательное по большинству. Опора на хадис «الغسل يوم الجمعة واجب»."
                  style={{ ...inputStyle(), minHeight: 72, resize: 'vertical', fontFamily: 'inherit', lineHeight: 1.5 }}
                />
              </Field>

              <Field
                label="Корневой вопрос"
                hint="Станет первым QUESTION-узлом графа. На него отвечают тезисы (CLAIM)."
                required
              >
                <textarea
                  rows={2}
                  defaultValue="Является ли гусль пятницы фардом для каждого участвующего в джум‘а, или это сильно желательная сунна?"
                  style={{ ...inputStyle(), minHeight: 64, resize: 'vertical', fontFamily: 'inherit', lineHeight: 1.5 }}
                />
                <FieldMeta left="QUESTION · корневой узел" right="142 / 1000" />
              </Field>

              <Field
                label="Дисциплина"
                hint="Метка для группировки в списке тем"
              >
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  {['Фикх', 'Усуль аль-фикх', 'Акыда', 'Тафсир', 'Хадис', 'Сира'].map((d, i) => (
                    <button key={d} type="button" style={{
                      padding: '5px 10px',
                      fontSize: 12, fontWeight: 500,
                      border: `1px solid ${i === 0 ? 'var(--c-accent-600)' : 'var(--c-ink-200)'}`,
                      color: i === 0 ? 'var(--c-accent-700)' : 'var(--c-ink-700)',
                      background: i === 0 ? 'var(--c-accent-50)' : 'var(--c-bg-elevated)',
                      borderRadius: 'var(--r-sm)',
                    }}>{d}</button>
                  ))}
                  <button type="button" style={{
                    padding: '5px 10px',
                    fontSize: 12, color: 'var(--c-ink-500)',
                    border: '1px dashed var(--c-ink-300)',
                    borderRadius: 'var(--r-sm)',
                  }}>+ свой</button>
                </div>
              </Field>

              <Field
                label="Видимость"
              >
                <div style={{ display: 'grid', gap: 6 }}>
                  {[
                    { val: 'private', label: 'Приватная', sub: 'Видна только тебе. Можно изменить позже.', icon: 'bookmark', sel: true },
                    { val: 'team',    label: 'Команда',    sub: 'Видна участникам твоей рабочей группы.',    icon: 'sparkles' },
                    { val: 'public',  label: 'Публичная',  sub: 'Видна всем зарегистрированным пользователям.', icon: 'eye' },
                  ].map((opt) => (
                    <label key={opt.val} style={{
                      display: 'flex', alignItems: 'flex-start', gap: 10,
                      padding: 12,
                      border: `1px solid ${opt.sel ? 'var(--c-accent-600)' : 'var(--c-ink-150)'}`,
                      background: opt.sel ? 'var(--c-accent-50)' : 'var(--c-bg-elevated)',
                      borderRadius: 'var(--r-md)',
                      cursor: 'pointer',
                    }}>
                      <span style={{
                        width: 16, height: 16, borderRadius: '50%',
                        border: `2px solid ${opt.sel ? 'var(--c-accent-600)' : 'var(--c-ink-300)'}`,
                        background: opt.sel ? 'var(--c-accent-600)' : 'var(--c-bg-elevated)',
                        position: 'relative', flex: 'none',
                        marginTop: 2,
                      }}>
                        {opt.sel && <span style={{
                          position: 'absolute', inset: 3,
                          background: 'var(--c-ink-0)',
                          borderRadius: '50%',
                        }} />}
                      </span>
                      <div style={{ flex: 1 }}>
                        <div style={{
                          fontSize: 13, fontWeight: 500,
                          display: 'flex', alignItems: 'center', gap: 6,
                          color: 'var(--c-ink-900)',
                        }}>
                          <Icon name={opt.icon} size={13} />
                          {opt.label}
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--c-ink-500)', marginTop: 2 }}>{opt.sub}</div>
                      </div>
                    </label>
                  ))}
                </div>
              </Field>

              {/* Buttons */}
              <div style={{
                display: 'flex', gap: 10, justifyContent: 'flex-end',
                paddingTop: 8, borderTop: 'var(--br-hair)',
              }}>
                <button className="btn btn-ghost" type="button">Отмена</button>
                <button className="btn btn-secondary" type="button">Сохранить черновик</button>
                <button className="btn btn-primary" type="button">
                  Создать
                  <span className="kbd">⌘↵</span>
                </button>
              </div>
            </form>

            {/* Sidebar: explanation */}
            <aside style={{
              position: 'sticky', top: 0,
              padding: 16,
              background: 'var(--c-paper)',
              border: 'var(--br-hair)',
              borderRadius: 'var(--r-lg)',
              fontSize: 13,
              color: 'var(--c-ink-700)',
              lineHeight: 1.55,
            }}>
              <h3 style={{
                margin: '0 0 10px',
                fontFamily: 'var(--font-serif)',
                fontSize: 16, fontWeight: 600,
                color: 'var(--c-ink-900)',
              }}>Как сформулировать вопрос?</h3>
              <p style={{ margin: '0 0 12px' }}>
                Хороший корневой вопрос — это <strong>дихотомия с явными альтернативами</strong>:
                «Х фард или сунна?», «Допустимо ли Y по А, по Б, по обоим?»
              </p>
              <p style={{ margin: '0 0 14px' }}>
                Избегай вопросов, требующих развёрнутого описания —
                их сложнее представить как граф.
              </p>

              <div style={{ height: 1, background: 'var(--c-ink-150)', margin: '14px 0' }} />

              <div style={{
                fontSize: 11, fontWeight: 600, letterSpacing: '0.08em',
                textTransform: 'uppercase', color: 'var(--c-ink-500)',
                marginBottom: 8,
              }}>Дальше</div>
              <ol style={{ margin: 0, paddingInlineStart: 20, fontSize: 12, lineHeight: 1.7, color: 'var(--c-ink-700)' }}>
                <li>Корневой вопрос станет первым узлом графа.</li>
                <li>К нему добавляются <strong>тезисы-ответы</strong> (CLAIM).</li>
                <li>На каждый тезис — доводы (ARGUMENT) и свидетельства (EVIDENCE).</li>
                <li>Связи между ними соблюдают{' '}
                  <a style={{ color: 'var(--c-accent-600)', textDecoration: 'underline' }}>матрицу ADR-010</a>.
                </li>
              </ol>
            </aside>
          </div>
        </div>
      </main>
    </div>
  );
}

function Field({ label, hint, required, children }) {
  return (
    <div>
      <label style={{
        display: 'flex', alignItems: 'baseline', gap: 8,
        marginBottom: 6,
      }}>
        <span style={{
          fontSize: 12, fontWeight: 600,
          color: 'var(--c-ink-900)',
        }}>{label}</span>
        {required && <span style={{ fontSize: 10, color: 'var(--c-err-500)', fontWeight: 600 }}>required</span>}
      </label>
      {hint && (
        <div style={{ fontSize: 12, color: 'var(--c-ink-500)', marginBottom: 6, lineHeight: 1.45 }}>
          {hint}
        </div>
      )}
      {children}
    </div>
  );
}

function FieldMeta({ left, right }) {
  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between',
      marginTop: 4,
      fontSize: 11,
      color: 'var(--c-ink-500)',
      fontFamily: 'var(--font-mono)',
    }}>
      <span>{left || ' '}</span>
      <span>{right || ' '}</span>
    </div>
  );
}

function inputStyle() {
  return {
    width: '100%',
    height: 38,
    padding: '0 12px',
    fontSize: 13.5,
    color: 'var(--c-ink-900)',
    background: 'var(--c-bg-elevated)',
    border: 'var(--br-soft)',
    borderRadius: 'var(--r-sm)',
    outline: 'none',
  };
}

window.CreateTopicBoard = CreateTopicBoard;
