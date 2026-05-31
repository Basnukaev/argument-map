/* ================================================================
   ARGUMENT MAP — MAIN ARTBOARDS WRAPPER
   Mounts all redesigned screens into DesignCanvas sections.
   ================================================================ */

const App = () => (
  <DesignCanvas>
    {/* ============= 1. FOUNDATIONS ============= */}
    <DCSection id="foundations" title="Foundations" subtitle="Дизайн-токены и базовые элементы. Single source of truth — все экраны используют эти переменные.">
      <DCArtboard id="found-light" label="Light · tokens preview" width={1280} height={760}>
        <Foundations dark={false}/>
      </DCArtboard>
      <DCArtboard id="found-dark" label="Dark · tokens preview" width={1280} height={760}>
        <Foundations dark={true}/>
      </DCArtboard>
    </DCSection>

    {/* ============= 2. AUTH ============= */}
    <DCSection id="auth" title="Аутентификация" subtitle="Login с фиксом основной проблемы — primary button в dark больше не выглядит disabled.">
      <DCArtboard id="login-light" label="Login · light" width={720} height={760}>
        <Login dark={false}/>
      </DCArtboard>
      <DCArtboard id="login-dark" label="Login · dark · FIXED primary button" width={720} height={760}>
        <Login dark={true}/>
      </DCArtboard>
    </DCSection>

    {/* ============= 3. NAVIGATION & TOPICS ============= */}
    <DCSection id="topics" title="Темы — список" subtitle="Главный экран. Hash-id скрыт по умолчанию (на hover); сегментированные контролы вместо native &lt;select&gt;; underline-active вместо filled pill; group separators в header.">
      <DCArtboard id="topics-light" label="Topics · light · RU" width={1280} height={760}>
        <TopicsList dark={false} lang="ru"/>
      </DCArtboard>
      <DCArtboard id="topics-dark" label="Topics · dark · RU" width={1280} height={760}>
        <TopicsList dark={true} lang="ru"/>
      </DCArtboard>
      <DCArtboard id="topics-ar" label="Topics · light · AR — RTL-audited" width={1280} height={760}>
        <TopicsList dark={false} lang="ar"/>
      </DCArtboard>
    </DCSection>

    {/* ============= 4. GRAPH (главная фича) ============= */}
    <DCSection id="graph" title="Граф аргументов — главная фича" subtitle="Tooltips на toolbar; vote-counter с явными иконками + labels; edge-icons; high-contrast edges в dark; minimap c явным viewport-indicator.">
      <DCArtboard id="graph-light" label="Graph · light · без selection" width={1440} height={900}>
        <Graph dark={false} selected={false}/>
      </DCArtboard>
      <DCArtboard id="graph-light-sel" label="Graph · light · selection + detail panel" width={1440} height={900}>
        <Graph dark={false} selected={true}/>
      </DCArtboard>
      <DCArtboard id="graph-dark" label="Graph · dark · FIXED edge contrast" width={1440} height={900}>
        <Graph dark={true} selected={false}/>
      </DCArtboard>
      <DCArtboard id="graph-dark-sel" label="Graph · dark · selection state" width={1440} height={900}>
        <Graph dark={true} selected={true}/>
      </DCArtboard>
    </DCSection>

    {/* ============= 5. LIBRARY & READER ============= */}
    <DCSection id="library" title="Библиотека и Reader" subtitle="Каталог книг + reader с TOC. Замена native dropdown'ов, кнопки favorite/edit floating, segmented filter-pills.">
      <DCArtboard id="lib-light" label="Library · light" width={1280} height={800}>
        <Library dark={false}/>
      </DCArtboard>
      <DCArtboard id="lib-dark" label="Library · dark" width={1280} height={800}>
        <Library dark={true}/>
      </DCArtboard>
      <DCArtboard id="reader-light" label="Reader · text mode · light" width={1280} height={900}>
        <Reader dark={false}/>
      </DCArtboard>
      <DCArtboard id="reader-dark" label="Reader · text mode · dark" width={1280} height={900}>
        <Reader dark={true}/>
      </DCArtboard>
    </DCSection>

    {/* ============= 6. Q&A ============= */}
    <DCSection id="qa" title="Q&A" subtitle="Список вопросов + детальная страница с привязанным источником. Empty state с центральным CTA; counter символов мелкий и неназойливый.">
      <DCArtboard id="qa-list-light" label="Q&A list · light" width={1280} height={760}>
        <QAList dark={false}/>
      </DCArtboard>
      <DCArtboard id="qa-detail-light" label="Q&A detail · light · с источником" width={1280} height={960}>
        <QADetail dark={false}/>
      </DCArtboard>
      <DCArtboard id="qa-detail-dark" label="Q&A detail · dark" width={1280} height={960}>
        <QADetail dark={true}/>
      </DCArtboard>
    </DCSection>

    {/* ============= 7. HADITH ============= */}
    <DCSection id="hadith" title="Хадисы" subtitle="Детальная страница с иснад-цепочкой. Hash-id передатчиков теперь с tooltip (русская подсказка); подсказки о хороших/слабых иснадах через статус-пилл.">
      <DCArtboard id="hadith-light" label="Hadith detail · light" width={1280} height={1100}>
        <HadithDetail dark={false}/>
      </DCArtboard>
      <DCArtboard id="hadith-dark" label="Hadith detail · dark" width={1280} height={1100}>
        <HadithDetail dark={true}/>
      </DCArtboard>
    </DCSection>

    {/* ============= 8. COLLECTIONS ============= */}
    <DCSection id="collections" title="Коллекции" subtitle="Слева — список коллекций со счётчиками; справа — содержимое. Empty state с центральным CTA «Перейти в библиотеку».">
      <DCArtboard id="coll-empty" label="Collections · empty state" width={1280} height={680}>
        <Collections dark={false} empty={true}/>
      </DCArtboard>
      <DCArtboard id="coll-filled" label="Collections · с книгой" width={1280} height={680}>
        <Collections dark={false} empty={false}/>
      </DCArtboard>
    </DCSection>

    {/* ============= 9. SETTINGS & ADMIN ============= */}
    <DCSection id="settings" title="Настройки и Админ" subtitle="Strana настроек — лучшая часть приложения, оставлена как есть с минорными улучшениями. Admin dashboard со счётчиками-cards (теперь явно кликабельные).">
      <DCArtboard id="settings-light" label="Settings · light" width={1280} height={1700}>
        <Settings dark={false}/>
      </DCArtboard>
      <DCArtboard id="settings-dark" label="Settings · dark" width={1280} height={1700}>
        <Settings dark={true}/>
      </DCArtboard>
      <DCArtboard id="admin-light" label="Admin · Shamela import · light" width={1280} height={900}>
        <Admin dark={false}/>
      </DCArtboard>
      <DCArtboard id="admin-dark" label="Admin · Shamela import · dark" width={1280} height={900}>
        <Admin dark={true}/>
      </DCArtboard>
    </DCSection>

    {/* ============= 10. STATES (новое!) ============= */}
    <DCSection id="states" title="Loading / Empty / Error states (отсутствовали)" subtitle="Skeleton-loader с shimmer-анимацией; empty states с иллюстрациями + основной CTA; error pages для 404, network, permission-denied.">
      <DCArtboard id="state-loading" label="Loading · skeleton-grid" width={1280} height={760}>
        <StateLoading dark={false}/>
      </DCArtboard>
      <DCArtboard id="state-loading-dark" label="Loading · dark" width={1280} height={760}>
        <StateLoading dark={true}/>
      </DCArtboard>
      <DCArtboard id="state-empty" label="Empty · нет тем" width={1280} height={760}>
        <StateEmpty dark={false}/>
      </DCArtboard>
      <DCArtboard id="state-404" label="Error · 404 Not found" width={1280} height={600}>
        <StateError kind="404" dark={false}/>
      </DCArtboard>
      <DCArtboard id="state-network" label="Error · Network — авто-retry" width={1280} height={600}>
        <StateError kind="network" dark={false}/>
      </DCArtboard>
      <DCArtboard id="state-permission" label="Error · Permission denied" width={1280} height={600}>
        <StateError kind="permission" dark={false}/>
      </DCArtboard>
    </DCSection>

    {/* ============= 11. MOBILE ============= */}
    <DCSection id="mobile" title="Мобильная версия" subtitle="Hit-targets ≥44pt; FAB вместо header-button для primary action; vertical-only layout для графа; bottom thumb-zone action-bar для reader.">
      <DCArtboard id="mob-topics-light" label="Mobile · Topics · light" width={460} height={800}>
        <MobTopics dark={false}/>
      </DCArtboard>
      <DCArtboard id="mob-topics-dark" label="Mobile · Topics · dark" width={460} height={800}>
        <MobTopics dark={true}/>
      </DCArtboard>
      <DCArtboard id="mob-graph" label="Mobile · Graph · vertical layout" width={460} height={800}>
        <MobGraph dark={false}/>
      </DCArtboard>
      <DCArtboard id="mob-reader" label="Mobile · Reader · thumb-zone controls" width={460} height={800}>
        <MobReader dark={false}/>
      </DCArtboard>
      <DCArtboard id="mob-reader-dark" label="Mobile · Reader · dark" width={460} height={800}>
        <MobReader dark={true}/>
      </DCArtboard>
    </DCSection>
  </DesignCanvas>
);

const root = ReactDOM.createRoot(document.querySelector("design-canvas"));
root.render(<App/>);
