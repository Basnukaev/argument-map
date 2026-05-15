// Main app: design canvas with reader variants + tweaks panel.

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "iconStyle": "lucide",
  "density": 6,
  "theme": "light",
  "fontPair": "default",
  "showGrid": true,
  "rtlOnVariant3": false
}/*EDITMODE-END*/;

const FONT_PRESETS = {
  default: { ui: 'Inter',          serif: 'Source Serif 4', label: 'Inter + Source Serif (current)' },
  plex:    { ui: 'IBM Plex Sans',  serif: 'IBM Plex Serif', label: 'IBM Plex (academic)' },
  geist:   { ui: 'Geist',          serif: 'Lora',           label: 'Geist + Lora (modern)' },
  manrope: { ui: 'Manrope',        serif: 'Source Serif 4', label: 'Manrope + Source Serif (friendly)' },
  system:  { ui: 'system-ui',      serif: 'Charter',        label: 'System fonts (no download)' },
};

function App() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);

  // Density slider scales padding inside reader content. 1=loose, 10=dense.
  // Effective range: 0.7×–1.15× base spacing.
  const densityScale = 1.2 - t.density * 0.05;

  React.useEffect(() => {
    const root = document.documentElement;
    root.style.setProperty('--density-scale', densityScale);
    root.setAttribute('data-icon-style', t.iconStyle);
    root.setAttribute('data-theme', t.theme);
    const preset = FONT_PRESETS[t.fontPair] || FONT_PRESETS.default;
    root.style.setProperty('--font-ui',    `"${preset.ui}", system-ui, -apple-system, sans-serif`);
    root.style.setProperty('--font-serif', `"${preset.serif}", Charter, Georgia, serif`);
  }, [densityScale, t.iconStyle, t.theme, t.fontPair]);

  const ru = window.READER_DATA.russianBook;
  const ar = window.READER_DATA.arabicBook;

  return (
    <div data-icon-style={t.iconStyle} style={{ width: '100%', height: '100%' }}>
      <DesignCanvas>

        {/* ───────────────────────────────────────────────────────────── */}
        <DCSection
          id="overview"
          title="С чего начать"
          subtitle="Чтение: проблема, система, три направления. Перетаскивай артборды, открывай в полный экран двойным кликом."
        >
          <DCArtboard id="intro" label="Контекст · читай первым" width={760} height={780}>
            <IntroBoard />
          </DCArtboard>
          <DCArtboard id="tokens" label="Дизайн-токены" width={920} height={780}>
            <TokensBoard />
          </DCArtboard>
        </DCSection>

        {/* ───────────────────────────────────────────────────────────── */}
        <DCSection
          id="variants"
          title="Три направления для BookReaderPage"
          subtitle="Каждый артборд — рабочий мок Reader'а в одном из стилей. Открой в полный экран, чтобы посмотреть детально."
        >
          <DCArtboard id="v1" label="A · Editorial — консервативный (Stripe Docs / academic press)" width={1280} height={920}>
            <ReaderVariantConservative data={ru} />
          </DCArtboard>
          <DCArtboard id="v2" label="B · Workspace — сбалансированный (Obsidian / GitHub) ★ рекомендация" width={1280} height={920}>
            <ReaderVariantBalanced data={ru} />
          </DCArtboard>
          <DCArtboard id="v3" label="C · Focus — экспериментальный (iA Writer / Readwise)" width={1280} height={920}>
            <ReaderVariantFocus data={ru} />
          </DCArtboard>
        </DCSection>

        {/* ───────────────────────────────────────────────────────────── */}
        <DCSection
          id="rtl"
          title="RTL · реальные данные из бэка"
          subtitle="Workspace-вариант на твоём настоящем JSON: تفسير ابن كثير (издание Дар ибн аль-Джаузи). UI тоже на арабском, layout зеркалится через CSS-логические свойства."
        >
          <DCArtboard id="rtl-tafsir" label="Реальная книга — тафсир Ибн Касира (стр. 1)" width={1280} height={920}>
            <ReaderVariantBalanced data={window.READER_DATA.tafsirIbnKathir} direction="rtl" />
          </DCArtboard>
        </DCSection>

        {/* ───────────────────────────────────────────────────────────── */}
        <DCSection
          id="pages"
          title="Остальные страницы в той же системе"
          subtitle="BookList · TopicList · TopicGraph · CreateTopic · AdminShamela. Один словарь компонентов, один акцент, одна шкала."
        >
          <DCArtboard id="topic-list" label="TopicListPage — список тем со статус-распределением" width={1280} height={920}>
            <TopicListBoard />
          </DCArtboard>
          <DCArtboard id="topic-graph" label="TopicGraphPage v1 — мой первый заход" width={1280} height={920}>
            <TopicGraphBoard />
          </DCArtboard>
          <DCArtboard id="topic-graph-v2" label="TopicGraphPage v2 — мердж с твоим стилем узлов и связей" width={1280} height={920}>
            <TopicGraphV2Board />
          </DCArtboard>
          <DCArtboard id="topic-graph-v3" label="TopicGraphPage v3 ★ — приглушённый дарк + без статус-легенды" width={1280} height={920}>
            <TopicGraphV3Board />
          </DCArtboard>
          <DCArtboard id="book-list" label="BookListPage — библиотека" width={1280} height={920}>
            <BookListBoard />
          </DCArtboard>
          <DCArtboard id="create-topic" label="CreateTopicPage — форма с пояснением" width={1280} height={920}>
            <CreateTopicBoard />
          </DCArtboard>
          <DCArtboard id="admin-shamela" label="AdminShamelaPage — импорт книг" width={1280} height={920}>
            <AdminShamelaBoard />
          </DCArtboard>
        </DCSection>

        {/* ───────────────────────────────────────────────────────────── */}
        <DCSection
          id="kit"
          title="UI Kit · все примитивы"
          subtitle="Кнопки · чипы · поля · модалки · контекстное меню · тосты · пустые состояния. Переключи тему в Tweaks справа."
        >
          <DCArtboard id="ui-kit" label="Все примитивы — переключи light/dark в Tweaks" width={1100} height={1300}>
            <UIKitBoard />
          </DCArtboard>
        </DCSection>

        {/* ───────────────────────────────────────────────────────────── */}
        <DCSection
          id="next"
          title="Дальше"
          subtitle="Когда выберешь направление — следующие шаги по спецификации, токенам и переносу в код."
        >
          <DCArtboard id="next-steps" label="Что делать после выбора" width={760} height={620}>
            <NextStepsBoard />
          </DCArtboard>
        </DCSection>

      </DesignCanvas>

      <TweaksPanel title="Tweaks">
        <TweakSection label="Тема">
          <TweakRadio
            label="Theme"
            value={t.theme}
            options={[
              { value: 'light', label: 'Light' },
              { value: 'dark',  label: 'Dark' },
            ]}
            onChange={(v) => setTweak('theme', v)}
          />
        </TweakSection>

        <TweakSection label="Шрифты">
          <TweakSelect
            label="Font pair"
            value={t.fontPair}
            options={Object.entries(FONT_PRESETS).map(([k, p]) => ({ value: k, label: p.label }))}
            onChange={(v) => setTweak('fontPair', v)}
          />
        </TweakSection>

        <TweakSection label="Иконки">
          <TweakRadio
            label="Стиль"
            value={t.iconStyle}
            options={[
              { value: 'lucide', label: 'Lucide' },
              { value: 'tabler', label: 'Tabler' },
              { value: 'phosphor', label: 'Phosphor' },
            ]}
            onChange={(v) => setTweak('iconStyle', v)}
          />
        </TweakSection>

        <TweakSection label="Плотность">
          <TweakSlider
            label="Density"
            value={t.density}
            min={1}
            max={10}
            step={1}
            onChange={(v) => setTweak('density', v)}
          />
        </TweakSection>

        <TweakSection label="Канвас">
          <TweakToggle
            label="Сетка"
            value={t.showGrid}
            onChange={(v) => setTweak('showGrid', v)}
          />
        </TweakSection>
      </TweaksPanel>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────
// Intro board — first thing the user sees. Plain text "card" explaining
// what's on the canvas and the design system choices.
// ─────────────────────────────────────────────────────────────────────
function IntroBoard() {
  return (
    <div style={{
      width: '100%',
      height: '100%',
      background: 'var(--c-bg-elevated)',
      padding: 40,
      overflow: 'auto',
      fontFamily: 'var(--font-ui)',
      color: 'var(--c-ink-900)',
    }}>
      <div style={{
        fontSize: 11,
        fontWeight: 600,
        letterSpacing: '0.1em',
        textTransform: 'uppercase',
        color: 'var(--c-accent-600)',
        marginBottom: 12,
      }}>
        Reader Redesign · итерация 1
      </div>

      <h1 style={{
        fontFamily: 'var(--font-serif)',
        fontSize: 32,
        fontWeight: 600,
        lineHeight: 1.15,
        margin: '0 0 14px',
        letterSpacing: '-0.01em',
      }}>
        Один эталонный экран, из которого вырастет всё остальное.
      </h1>

      <p className="prose" style={{ fontSize: 16, color: 'var(--c-ink-700)', marginBottom: 24 }}>
        Это <strong>BookReaderPage</strong> в трёх стилистических направлениях. Цель — выбрать одно и
        зафиксировать дизайн-систему: типографику, шкалу, плотность, акцент.
        После этого тот же словарь распространится на TopicList, TopicGraph и AdminShamela.
      </p>

      <Section heading="Что я зафиксировал">
        <ul style={{ margin: 0, paddingInlineStart: 18, fontSize: 14, lineHeight: 1.7, color: 'var(--c-ink-800)' }}>
          <li><strong>Палитра:</strong> Navy (#1e3a8a) — один акцент, тёплый slate для нейтралей вместо холодного синевато-серого.</li>
          <li><strong>Типографика:</strong> Inter для UI, <strong>Source Serif 4</strong> для тела Reader, Amiri для арабского, JetBrains Mono для чисел и id.</li>
          <li><strong>Шкала текста:</strong> 6 размеров. <span className="mono">12 / 14 / 16 / 18 / 22 / 28</span>. Никаких <span className="mono">[10.5px]</span>.</li>
          <li><strong>Spacing:</strong> 4 значения. <span className="mono">4 / 8 / 16 / 24 / 40 / 64</span>.</li>
          <li><strong>Border-radius:</strong> 3 значения с семантикой. sm=button, md=card, lg=panel.</li>
        </ul>
      </Section>

      <Section heading="Что пробовать в Tweaks (справа внизу)">
        <ul style={{ margin: 0, paddingInlineStart: 18, fontSize: 14, lineHeight: 1.7, color: 'var(--c-ink-800)' }}>
          <li>Переключить стиль иконок — <em>Lucide / Tabler / Phosphor</em>. Меняется в реальном времени во всех артбордах.</li>
          <li>Плотность интерфейса — 1 (Apple-вакуум) до 10 (Bloomberg-плотно).</li>
        </ul>
      </Section>

      <Section heading="Как смотреть">
        <ul style={{ margin: 0, paddingInlineStart: 18, fontSize: 14, lineHeight: 1.7, color: 'var(--c-ink-800)' }}>
          <li>Двойной клик по артборду — полноэкранный режим. <span className="kbd">Esc</span> назад.</li>
          <li>Стрелки <span className="kbd">←</span><span className="kbd">→</span> — между артбордами в фокус-режиме.</li>
          <li>Колесо мыши + <span className="kbd">⌘</span> или жест pinch — зум канваса.</li>
        </ul>
      </Section>
    </div>
  );
}

function Section({ heading, children }) {
  return (
    <section style={{ marginBottom: 22 }}>
      <h3 style={{
        margin: '0 0 8px',
        fontSize: 11,
        fontWeight: 600,
        letterSpacing: '0.1em',
        textTransform: 'uppercase',
        color: 'var(--c-ink-500)',
      }}>
        {heading}
      </h3>
      {children}
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────
// Tokens board — visual cheat-sheet of the design system.
// ─────────────────────────────────────────────────────────────────────
function TokensBoard() {
  return (
    <div style={{
      width: '100%', height: '100%', background: 'var(--c-bg-elevated)',
      padding: 28, overflow: 'auto', fontFamily: 'var(--font-ui)', color: 'var(--c-ink-900)',
    }}>
      {/* Type scale */}
      <TokensRow heading="Типографика — 6 размеров и две гарнитуры">
        {[
          { s: 28, w: 600, l: 'Раудат ат-талибин', meta: '28 · book title', f: 'serif' },
          { s: 22, w: 600, l: 'Книга очищения', meta: '22 · chapter', f: 'serif' },
          { s: 18, w: 400, l: 'Тело Reader: сказал автор, да помилует его Аллах…', meta: '18 · reader body', f: 'serif' },
          { s: 16, w: 400, l: 'UI body — обычный текст интерфейса.', meta: '16 · ui body', f: 'ui' },
          { s: 14, w: 500, l: 'Кнопки, ярлыки, навигация.', meta: '14 · ui small', f: 'ui' },
          { s: 12, w: 500, l: 'META · id · 671h · стр. 74', meta: '12 · meta', f: 'mono' },
        ].map((row) => (
          <div key={row.s} style={{ display: 'grid', gridTemplateColumns: '1fr 140px', alignItems: 'baseline', gap: 12, padding: '6px 0', borderBottom: '1px solid var(--c-ink-100)' }}>
            <div style={{
              fontFamily: row.f === 'serif' ? 'var(--font-serif)' : row.f === 'mono' ? 'var(--font-mono)' : 'var(--font-ui)',
              fontSize: row.s, fontWeight: row.w, letterSpacing: row.s >= 22 ? '-0.01em' : 0,
            }}>
              {row.l}
            </div>
            <div className="mono" style={{ fontSize: 11, color: 'var(--c-ink-500)' }}>{row.meta}</div>
          </div>
        ))}
      </TokensRow>

      {/* Colors */}
      <TokensRow heading="Палитра — один акцент, тёплые нейтрали">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(10, 1fr)', gap: 6 }}>
          {[
            ['ink-900', '#1a1814'], ['ink-700', '#3a3631'], ['ink-500', '#6b6660'], ['ink-300', '#b8b2a8'],
            ['ink-150', '#e6e1d6'], ['ink-50', '#f7f3ea'], ['paper', '#fbfaf5'], ['white', '#ffffff'],
            ['accent', '#1e3a8a'], ['accent-100', '#e0e7f5'],
          ].map(([n, c]) => (
            <div key={n}>
              <div style={{ aspectRatio: '1 / 1', background: c, border: '1px solid rgba(0,0,0,0.06)', borderRadius: 4 }} />
              <div className="mono" style={{ fontSize: 9, marginTop: 4, color: 'var(--c-ink-500)' }}>{n}</div>
              <div className="mono" style={{ fontSize: 9, color: 'var(--c-ink-400)' }}>{c}</div>
            </div>
          ))}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 6, marginTop: 12 }}>
          {[
            ['ok-500', '#16a34a', 'STANDING'], ['warn-500', '#d97706', 'DISPUTED'], ['err-500', '#dc2626', 'REFUTED'],
            ['ok-100', '#dcfce7', 'STANDING bg'], ['warn-100', '#fef3c7', 'DISPUTED bg'], ['err-100', '#fee2e2', 'REFUTED bg'],
          ].map(([n, c, role]) => (
            <div key={n} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{ width: 22, height: 22, background: c, border: '1px solid rgba(0,0,0,0.06)', borderRadius: 4 }} />
              <div>
                <div style={{ fontSize: 10, fontWeight: 500 }}>{role}</div>
                <div className="mono" style={{ fontSize: 9, color: 'var(--c-ink-500)' }}>{c}</div>
              </div>
            </div>
          ))}
        </div>
      </TokensRow>

      {/* Spacing */}
      <TokensRow heading="Spacing — 6 stop'ов, всё кратно 4">
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 20 }}>
          {[['s-1', 4], ['s-2', 8], ['s-3', 16], ['s-4', 24], ['s-5', 40], ['s-6', 64]].map(([n, v]) => (
            <div key={n} style={{ textAlign: 'center' }}>
              <div style={{ width: v, height: v, background: 'var(--c-accent-600)', borderRadius: 2, margin: '0 auto 6px' }} />
              <div className="mono" style={{ fontSize: 10, color: 'var(--c-ink-700)' }}>{n}</div>
              <div className="mono" style={{ fontSize: 9, color: 'var(--c-ink-400)' }}>{v}px</div>
            </div>
          ))}
        </div>
      </TokensRow>

      {/* Components */}
      <TokensRow heading="Базовые примитивы">
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <button className="btn btn-primary">Primary</button>
          <button className="btn btn-secondary">Secondary</button>
          <button className="btn btn-ghost">Ghost</button>
          <button className="btn btn-secondary btn-sm"><Icon name="search" size={12} /> Sm с иконкой</button>
          <span className="chip"><Icon name="book" size={11} /> chip</span>
          <span className="chip chip-accent">chip accent</span>
          <span className="chip chip-ok">STANDING</span>
          <span className="kbd">⌘K</span>
          <span className="kbd">/</span>
          <span className="kbd">J</span>
        </div>
      </TokensRow>
    </div>
  );
}

function TokensRow({ heading, children }) {
  return (
    <section style={{ marginBottom: 28 }}>
      <h3 style={{
        margin: '0 0 12px',
        fontSize: 10, fontWeight: 600,
        letterSpacing: '0.1em', textTransform: 'uppercase',
        color: 'var(--c-ink-500)',
      }}>{heading}</h3>
      {children}
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────
// Next steps board
// ─────────────────────────────────────────────────────────────────────
function NextStepsBoard() {
  return (
    <div style={{ width: '100%', height: '100%', background: 'var(--c-bg-elevated)', padding: 40, overflow: 'auto', fontFamily: 'var(--font-ui)', color: 'var(--c-ink-900)' }}>
      <h2 style={{ fontFamily: 'var(--font-serif)', fontSize: 26, fontWeight: 600, margin: '0 0 8px' }}>
        Шаги после выбора направления
      </h2>
      <p style={{ fontSize: 14, color: 'var(--c-ink-700)', marginTop: 0, marginBottom: 28 }}>
        Когда определишься с A / B / C, я сделаю следующее:
      </p>

      {[
        ['1', 'Спецификация для Claude Code',
          'Сжатый MD-документ: токены (CSS-переменные), правила (border всегда hair-line, кнопки только трёх типов), вокабуляр (когда card, когда panel, когда раскрывашка). Этот документ передаётся в Claude Code как контекст.'],
        ['2', 'Реальные TSX-компоненты',
          'Адаптирую выбранный артборд под твой стек: Tailwind classes вместо inline-стилей, lucide-react (или тот, что выберешь), интеграция с твоими Card / Button / ChapterList. Целью будет drop-in замена существующего BookReaderPage.'],
        ['3', 'Прогон оставшихся экранов',
          'TopicListPage, TopicGraphPage и BookListPage по тому же визуальному словарю. Каждый — мок-первый, потом код. Чтобы граф ощущался как "тот же продукт", его productivity-плотность будет жить внутри общей системы.'],
        ['4', 'Чистка хвостов',
          'Замена text-[28px] / text-[10.5px] / p-2.5 на токены. Свести типы border-radius к трём. Удалить ad-hoc gradient backgrounds на BookCard и BookHeader.'],
      ].map(([n, h, body]) => (
        <div key={n} style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
          <div style={{
            flex: 'none',
            width: 28, height: 28, borderRadius: 99,
            background: 'var(--c-accent-100)', color: 'var(--c-accent-700)',
            display: 'grid', placeItems: 'center',
            fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 600,
          }}>{n}</div>
          <div>
            <h3 style={{ margin: '4px 0 4px', fontSize: 14, fontWeight: 600 }}>{h}</h3>
            <p style={{ margin: 0, fontSize: 13, lineHeight: 1.55, color: 'var(--c-ink-700)' }}>{body}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
