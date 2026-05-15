// VARIANT 2 — Balanced workspace (Obsidian / GitHub feel)
// Three columns: chapters (L) · reader (C) · context panel (R).
// The context panel surfaces what makes THIS app special:
//   - related argument-map topics that cite this page
//   - inline footnotes / sources
//   - a peek at the PDF original on demand
// Density: medium-high. Used as the recommended default.

// UI strings — Russian by default, Arabic when direction='rtl' or via prop.
const UI_RU = {
  back: 'Библиотека',
  findChapter: 'Найти главу',
  chapters: 'Главы',
  text: 'Текст', pdf: 'PDF',
  page: 'стр.', volume: 'Том', printed: 'orig',
  original: 'Оригинал', open: 'Открыть',
  relatedTopics: 'Темы дискуссии',
  relatedHint: 'Эта страница цитируется в темах графа аргументации',
  actions: 'Действия',
  actionCite: 'Цитировать выделенное',
  actionNode: 'Создать узел из цитаты',
  actionBookmark: 'Закладка на эту страницу',
  statusStanding: 'Устоявшийся',
  statusDisputed: 'Спорный',
  statusRefuted: 'Опровергнут',
  noTopics: 'Эта страница пока не упомянута ни в одной теме.',
  origPageTitle: 'Номер на оригинальной странице',
};

const UI_AR = {
  back: 'المكتبة',
  findChapter: 'بحث في الأبواب',
  chapters: 'الفهرس',
  text: 'نص', pdf: 'PDF',
  page: 'ص', volume: 'جزء', printed: 'أصلي',
  original: 'الأصل المطبوع', open: 'فتح',
  relatedTopics: 'مواضيع النقاش',
  relatedHint: 'تستشهد بهذه الصفحة في مواضيع خريطة الحجاج',
  actions: 'إجراءات',
  actionCite: 'اقتباس المحدد',
  actionNode: 'إنشاء عقدة من الاقتباس',
  actionBookmark: 'إشارة مرجعية',
  statusStanding: 'ثابت',
  statusDisputed: 'متنازع',
  statusRefuted: 'مردود',
  noTopics: 'لم تُذكر هذه الصفحة بعد في أيّ موضوع.',
  origPageTitle: 'رقم الصفحة في الطبعة الأصلية',
};

// Walk chapter tree, return current chapter title (deepest match)
function findCurrentChapter(chapters) {
  for (const c of chapters || []) {
    if (c.children) {
      const childMatch = findCurrentChapter(c.children);
      if (childMatch) return childMatch;
    }
    if (c.current) return c.title;
  }
  return null;
}

function ReaderVariantBalanced({ data, direction = 'ltr', ui }) {
  const book = data;
  const page = book.pageContent;
  const isRTL = direction === 'rtl';
  const t = ui || (isRTL ? UI_AR : UI_RU);
  const isArabic = book.language === 'ar';
  const arabicFont = isArabic ? 'var(--font-arabic)' : undefined;
  const currentChapter = findCurrentChapter(book.chapters) || t.chapters;

  return (
    <div dir={direction} style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      background: 'var(--c-surface-cool)',
      color: 'var(--c-ink-900)',
      overflow: 'hidden',
    }}>
      {/* ── Slim top bar ─────────────────────────────────────── */}
      <header style={{
        flex: 'none',
        height: 44,
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '0 12px',
        background: 'var(--c-ink-0)',
        borderBottom: 'var(--br-hair)',
      }}>
        <button className="btn btn-ghost btn-sm">
          <Icon name="arrow-left" size={14} />
          <span style={{ fontSize: 12, fontFamily: isRTL ? arabicFont : undefined }}>{t.back}</span>
        </button>
        <span style={{ width: 1, height: 16, background: 'var(--c-ink-150)' }} />
        <Icon name="book" size={14} style={{ color: 'var(--c-ink-500)' }} />
        <span dir="auto" style={{
          fontSize: isArabic ? 14 : 13,
          fontWeight: 600,
          fontFamily: isArabic ? arabicFont : undefined,
        }}>{book.title}</span>
        {book.titleTransliteration && (
          <span style={{ fontSize: 11, color: 'var(--c-ink-500)' }}>· {book.titleTransliteration}</span>
        )}
        {book.author && (
          <>
            <span style={{ width: 1, height: 14, background: 'var(--c-ink-150)', marginInline: 4 }} />
            <span dir="auto" style={{
              fontSize: 12,
              color: 'var(--c-ink-500)',
              fontFamily: isArabic ? arabicFont : undefined,
            }}>{book.author}</span>
          </>
        )}
        <span style={{ flex: 1 }} />

        {/* Center toolbar group: prev / page / next */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          padding: 2,
          background: 'var(--c-ink-50)',
          borderRadius: 'var(--r-sm)',
          border: 'var(--br-hair)',
        }}>
          <button className="btn btn-ghost btn-sm btn-icon" aria-label="prev">
            <Icon name={isRTL ? 'chevron-right' : 'chevron-left'} size={14} />
          </button>
          <div style={{
            padding: '2px 8px',
            fontSize: 12,
            fontFamily: 'var(--font-mono)',
            minWidth: 96,
            textAlign: 'center',
            fontVariantNumeric: 'tabular-nums',
          }}>
            {book.currentPage} <span style={{ color: 'var(--c-ink-400)' }}>/ {book.pagesCount}</span>
          </div>
          <button className="btn btn-ghost btn-sm btn-icon" aria-label="next">
            <Icon name={isRTL ? 'chevron-left' : 'chevron-right'} size={14} />
          </button>
        </div>

        <span style={{ flex: 1 }} />

        <button className="btn btn-ghost btn-sm">
          <Icon name="search" size={13} />
          <span className="kbd">/</span>
        </button>
        {/* Reader mode segmented */}
        <div style={{
          display: 'inline-flex',
          background: 'var(--c-ink-50)',
          border: 'var(--br-hair)',
          borderRadius: 'var(--r-sm)',
          padding: 2,
        }}>
          <button className="btn btn-ghost btn-sm is-active" style={{
            padding: '3px 10px',
            fontFamily: isRTL ? arabicFont : undefined,
          }}>
            <Icon name="file-text" size={12} /> {t.text}
          </button>
          <button className="btn btn-ghost btn-sm" style={{ padding: '3px 10px', color: 'var(--c-ink-500)' }}>
            <Icon name="book" size={12} /> {t.pdf}
          </button>
        </div>
        <button className="btn btn-ghost btn-sm btn-icon" aria-label="bookmark"><Icon name="bookmark" size={14} /></button>
        <button className="btn btn-ghost btn-sm btn-icon" aria-label="menu"><Icon name="menu" size={14} /></button>
      </header>

      {/* ── 3-column body ────────────────────────────────────── */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', minHeight: 0 }}>

        {/* LEFT: chapter tree */}
        <aside style={{
          flex: 'none',
          width: 248,
          background: 'var(--c-ink-0)',
          borderInlineEnd: 'var(--br-hair)',
          display: 'flex',
          flexDirection: 'column',
          minHeight: 0,
        }}>
          <div style={{
            padding: '12px 12px 8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}>
            <div style={{
              fontSize: isRTL ? 12 : 10,
              fontWeight: 600,
              letterSpacing: isRTL ? '0.02em' : '0.08em',
              textTransform: isRTL ? 'none' : 'uppercase',
              color: 'var(--c-ink-500)',
              fontFamily: isRTL ? arabicFont : undefined,
            }}>
              {t.chapters}
            </div>
            <button className="btn btn-ghost btn-sm btn-icon" aria-label="collapse">
              <Icon name="panel-left" size={12} />
            </button>
          </div>

          {/* tiny search in sidebar */}
          <div style={{ padding: '0 12px 12px' }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '5px 8px',
              fontSize: isRTL ? 13 : 12,
              background: 'var(--c-ink-50)',
              borderRadius: 'var(--r-sm)',
              border: '1px solid transparent',
              color: 'var(--c-ink-500)',
              fontFamily: isRTL ? arabicFont : undefined,
            }}>
              <Icon name="search" size={12} />
              {t.findChapter}
            </div>
          </div>

          <div style={{ flex: 1, overflow: 'auto', paddingBottom: 12 }}>
            <ChapterTree
              chapters={book.chapters}
              activeClass="is-current"
              arabicFont={isArabic}
            />
          </div>

          <div style={{
            flex: 'none',
            padding: '8px 12px',
            borderTop: 'var(--br-hair)',
            fontSize: 11,
            color: 'var(--c-ink-500)',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}>
            <div style={{
              flex: 1,
              height: 3,
              background: 'var(--c-ink-100)',
              borderRadius: 99,
              overflow: 'hidden',
            }}>
              <div style={{
                width: `${(book.currentPage / book.pagesCount) * 100}%`,
                height: '100%',
                background: 'var(--c-accent-600)',
              }} />
            </div>
            <span className="mono">{Math.round((book.currentPage / book.pagesCount) * 100)}%</span>
          </div>
        </aside>

        {/* CENTER: reader */}
        <main style={{
          flex: 1,
          minWidth: 0,
          background: 'var(--c-paper)',
          overflow: 'auto',
          padding: '28px 28px 60px',
        }}>
          <article style={{
            maxWidth: 720,
            margin: '0 auto',
            background: 'var(--c-bg-elevated)',
            border: 'var(--br-hair)',
            borderRadius: 'var(--r-lg)',
            padding: isArabic ? '40px 48px' : '40px 56px',
            boxShadow: 'var(--sh-2)',
          }}>
            {/* Pill / meta row */}
            <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginBottom: 18, flexWrap: 'wrap' }}>
              <span className="chip chip-accent" style={{
                fontFamily: isArabic ? arabicFont : undefined,
                fontSize: isArabic ? 13 : 11,
              }}>
                <Icon name="book" size={11} /> <span dir="auto">{currentChapter}</span>
              </span>
              {book.currentPart && (
                <span className="chip" style={{
                  fontFamily: isArabic ? arabicFont : undefined,
                  fontSize: isArabic ? 12 : 11,
                }}>
                  <span dir="auto">{t.volume} · {book.currentPart}</span>
                </span>
              )}
              <span style={{ flex: 1 }} />
              {book.currentPrintedPage && (
                <span className="mono" title={t.origPageTitle} style={{
                  fontSize: 11,
                  color: 'var(--c-ink-600)',
                  background: 'var(--c-ink-50)',
                  padding: '2px 7px',
                  borderRadius: 'var(--r-sm)',
                  border: 'var(--br-hair)',
                }}>
                  {t.printed} · {book.currentPrintedPage}
                </span>
              )}
              <span className="mono" style={{
                fontSize: 11,
                color: 'var(--c-ink-500)',
                fontVariantNumeric: 'tabular-nums',
              }}>
                {t.page} {book.currentPage}/{book.pagesCount}
              </span>
            </div>

            <h1 dir="auto" style={{
              fontFamily: isArabic ? arabicFont : 'var(--font-serif)',
              fontWeight: 600,
              fontSize: isArabic ? 32 : 28,
              lineHeight: 1.25,
              margin: '0 0 28px',
              letterSpacing: isArabic ? 0 : '-0.01em',
            }}>
              {page.heading}
            </h1>

            {isArabic ? (
              <div className="prose prose-arabic">
                {page.body.map((p, i) => <p key={i} dir="rtl">{p}</p>)}
              </div>
            ) : (
              <div className="prose">
                <ProseBody paragraphs={page.body} />
              </div>
            )}

            {page.footnotes && (
              <>
                <hr style={{ margin: '32px 0 14px', border: 0, borderTop: 'var(--br-hair)' }} />
                <ol style={{
                  margin: 0,
                  padding: 0,
                  listStyle: 'none',
                  fontSize: 12,
                  color: 'var(--c-ink-700)',
                  lineHeight: 1.55,
                  display: 'grid',
                  gap: 4,
                }}>
                  {page.footnotes.map((fn) => (
                    <li key={fn.n} style={{ display: 'flex', gap: 8 }}>
                      <span className="mono" style={{ color: 'var(--c-accent-600)', fontWeight: 600 }}>
                        [{fn.n}]
                      </span>
                      <span>{fn.text}</span>
                    </li>
                  ))}
                </ol>
              </>
            )}
          </article>
        </main>

        {/* RIGHT: context panel */}
        <aside style={{
          flex: 'none',
          width: 288,
          background: 'var(--c-ink-0)',
          borderInlineStart: 'var(--br-hair)',
          overflow: 'auto',
          padding: '16px 16px 40px',
        }}>
          {/* PDF preview teaser */}
          <SidePanel
            title={t.original}
            arabicFont={isRTL}
            extra={<button className="btn btn-ghost btn-sm" style={{
              padding: '2px 8px',
              fontFamily: isRTL ? arabicFont : undefined,
            }}>{t.open} <Icon name="maximize" size={11} /></button>}
          >
            <div style={{
              aspectRatio: '3 / 4',
              background: '#f4f1e8',
              borderRadius: 'var(--r-sm)',
              border: 'var(--br-hair)',
              padding: 14,
              display: 'flex',
              flexDirection: 'column',
              gap: 4,
              position: 'relative',
              overflow: 'hidden',
            }}>
              {Array.from({ length: 14 }).map((_, i) => (
                <div key={i} style={{
                  height: 4,
                  borderRadius: 1,
                  background: `rgba(60,50,30,${0.25 - (i % 4) * 0.04})`,
                  width: `${75 + ((i * 13) % 20)}%`,
                  marginInlineStart: i % 2 ? 0 : 'auto',
                }} />
              ))}
              <div style={{
                position: 'absolute',
                bottom: 8,
                insetInline: 14,
                fontSize: 10,
                color: 'var(--c-ink-500)',
                fontFamily: 'var(--font-mono)',
                display: 'flex',
                justifyContent: 'space-between',
              }}>
                <span>{t.volume} · {book.currentPart ?? book.currentVolume ?? 1}</span>
                <span>{t.page} {book.currentPrintedPage ?? book.currentPage}</span>
              </div>
            </div>
          </SidePanel>

          {/* Related topics — argument map */}
          <SidePanel
            title={t.relatedTopics}
            hint={t.relatedHint}
            arabicFont={isRTL}
          >
            <div style={{ display: 'grid', gap: 8 }}>
              {(book.relatedTopics ?? []).map((topic) => (
                <a key={topic.id} style={{
                  display: 'block',
                  padding: 10,
                  border: 'var(--br-hair)',
                  borderRadius: 'var(--r-sm)',
                  cursor: 'pointer',
                  background: 'var(--c-ink-0)',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                    <Icon name="graph" size={12} style={{ color: 'var(--c-accent-600)' }} />
                    <span className="mono" style={{ fontSize: 10, color: 'var(--c-ink-500)' }}>
                      {topic.nodes} · {topic.edges}
                    </span>
                    <span style={{ flex: 1 }} />
                    <StatusDot status={topic.status} ui={t} />
                  </div>
                  <div dir="auto" style={{
                    fontSize: isArabic ? 14 : 12,
                    fontWeight: 500,
                    lineHeight: 1.4,
                    fontFamily: isArabic ? arabicFont : undefined,
                  }}>{topic.title}</div>
                </a>
              ))}
              {(!book.relatedTopics || book.relatedTopics.length === 0) && (
                <div style={{
                  fontSize: 12,
                  color: 'var(--c-ink-500)',
                  fontFamily: isRTL ? arabicFont : undefined,
                }}>
                  {t.noTopics}
                </div>
              )}
            </div>
          </SidePanel>

          {/* Excerpt / cite */}
          <SidePanel title={t.actions} arabicFont={isRTL}>
            <div style={{ display: 'grid', gap: 6 }}>
              <button className="btn btn-secondary btn-sm" style={{
                justifyContent: 'flex-start',
                fontFamily: isRTL ? arabicFont : undefined,
                fontSize: isRTL ? 13 : undefined,
              }}>
                <Icon name="quote" size={13} /> {t.actionCite}
              </button>
              <button className="btn btn-secondary btn-sm" style={{
                justifyContent: 'flex-start',
                fontFamily: isRTL ? arabicFont : undefined,
                fontSize: isRTL ? 13 : undefined,
              }}>
                <Icon name="graph" size={13} /> {t.actionNode}
              </button>
              <button className="btn btn-ghost btn-sm" style={{
                justifyContent: 'flex-start',
                fontFamily: isRTL ? arabicFont : undefined,
                fontSize: isRTL ? 13 : undefined,
              }}>
                <Icon name="bookmark" size={13} /> {t.actionBookmark}
              </button>
            </div>
          </SidePanel>
        </aside>
      </div>
    </div>
  );
}

function SidePanel({ title, hint, extra, children, arabicFont }) {
  return (
    <section style={{ marginBottom: 24 }}>
      <header style={{
        display: 'flex',
        alignItems: 'center',
        marginBottom: 8,
        gap: 8,
      }}>
        <h3 style={{
          margin: 0,
          fontSize: arabicFont ? 13 : 10,
          fontWeight: 600,
          letterSpacing: arabicFont ? '0.01em' : '0.08em',
          textTransform: arabicFont ? 'none' : 'uppercase',
          color: 'var(--c-ink-500)',
          fontFamily: arabicFont ? 'var(--font-arabic)' : undefined,
        }}>{title}</h3>
        <span style={{ flex: 1 }} />
        {extra}
      </header>
      {hint && (
        <div style={{
          fontSize: arabicFont ? 13 : 11,
          color: 'var(--c-ink-500)',
          marginBottom: 8,
          lineHeight: arabicFont ? 1.65 : 1.45,
          fontFamily: arabicFont ? 'var(--font-arabic)' : undefined,
        }}>
          {hint}
        </div>
      )}
      {children}
    </section>
  );
}

function StatusDot({ status, ui }) {
  const map = {
    standing: { bg: 'var(--c-ok-500)', label: ui?.statusStanding || 'Standing' },
    disputed: { bg: 'var(--c-warn-500)', label: ui?.statusDisputed || 'Disputed' },
    refuted:  { bg: 'var(--c-err-500)', label: ui?.statusRefuted || 'Refuted' },
  };
  const s = map[status] || { bg: 'var(--c-ink-300)', label: '—' };
  return (
    <span title={s.label} style={{
      width: 7, height: 7, borderRadius: 99, background: s.bg, flex: 'none',
    }} />
  );
}

window.ReaderVariantBalanced = ReaderVariantBalanced;
