// =============================================================================
// PLATFORM — sections 31 Add book · 32 Image regions · 33 Shamela admin
//                    34 Q&A · 35 Platform home
// =============================================================================

// ===== 31 — Add book flows ====================================================

const StepDots = ({ steps, current }) => (
  <div className="flex items-center gap-1.5 text-[10.5px] font-mono">
    {steps.map((s, i) => (
      <React.Fragment key={i}>
        <span className={cx("inline-flex items-center gap-1.5",
          i < current ? "text-emerald-700" : i === current ? "text-indigo-700" : "text-slate-400"
        )}>
          <span className={cx("h-5 w-5 rounded-full grid place-items-center text-[10px] font-bold",
            i < current ? "bg-emerald-100 text-emerald-700" :
            i === current ? "bg-indigo-600 text-white ring-2 ring-indigo-200" : "bg-slate-100 text-slate-400"
          )}>{i < current ? "✓" : i + 1}</span>
          <span className="uppercase tracking-wider">{s}</span>
        </span>
        {i < steps.length - 1 && <span className={cx("h-px w-6", i < current ? "bg-emerald-300" : "bg-slate-200")} />}
      </React.Fragment>
    ))}
  </div>
);

const ShamelaWizard = () => (
  <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
    <div className="px-5 h-12 border-b border-slate-200 flex items-center gap-3">
      <div className="h-7 w-7 rounded bg-indigo-100 grid place-items-center text-indigo-700"><I.Library size={13} /></div>
      <div className="flex-1">
        <div className="text-[13px] font-bold text-slate-900 leading-none">Импорт из Shamela</div>
        <div className="text-[10.5px] text-slate-500 mt-0.5">admin only · в три шага</div>
      </div>
      <StepDots steps={["sync", "поиск", "preview", "импорт", "готово"]} current={2} />
    </div>
    <div className="p-5 grid grid-cols-12 gap-5">
      <div className="col-span-5 space-y-3">
        <div className="rounded-md border border-emerald-200 bg-emerald-50/40 p-3">
          <div className="text-[10.5px] font-mono uppercase tracking-wider text-emerald-700 mb-1.5 flex items-center gap-1.5"><I.CheckCircle size={11} />шаг 1 · sync каталога</div>
          <div className="text-[11.5px] text-slate-700 leading-relaxed">обновлено <strong>+ 47 книг</strong> · master-version <code className="font-mono text-[10.5px]">2026.05.07</code></div>
        </div>
        <div>
          <label className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500">шаг 2 · поиск</label>
          <div className="relative mt-1">
            <I.Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input defaultValue="فتح الباري" dir="rtl" className="h-8 w-full pl-8 pr-3 text-[13px] font-naskh rounded-md border border-indigo-300 ring-2 ring-indigo-100 outline-none" />
          </div>
          <div className="mt-2 max-h-32 overflow-auto border border-slate-200 rounded-md">
            {[
              { id: 1672, ar: "فَتْحُ الْبَارِي", au: "ابْنُ حَجَرٍ", sel: true },
              { id: 1673, ar: "فَتْحُ الْبَارِي · ط أخرى", au: "ابْنُ حَجَرٍ" },
              { id: 8214, ar: "فَتْحُ الْقَدِيرِ", au: "الشَّوْكَانِيُّ" },
            ].map((r) => (
              <div key={r.id} className={cx("px-2.5 py-1.5 flex items-baseline justify-between gap-2 text-[12px]", r.sel && "bg-indigo-50")}>
                <span dir="rtl" className={cx("font-naskh truncate", r.sel ? "text-indigo-900 font-semibold" : "text-slate-800")}>{r.ar}</span>
                <span className="text-[10px] font-mono text-slate-500 shrink-0">#{r.id}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
      <div className="col-span-7">
        <div className="rounded-md border border-indigo-200 bg-indigo-50/40 p-4">
          <div className="text-[10.5px] font-mono uppercase tracking-wider text-indigo-700 mb-2">шаг 3 · preview · перед импортом</div>
          <div className="flex items-baseline justify-between gap-3 mb-2">
            <div dir="rtl" className="font-naskh text-[20px] font-bold text-slate-900">فَتْحُ الْبَارِي بِشَرْحِ صَحِيحِ الْبُخَارِيِّ</div>
            <div className="text-[10px] font-mono text-slate-500 shrink-0">shamela #1672</div>
          </div>
          <div className="text-[11.5px] italic text-slate-600">Ибн Хаджар аль-‘Аскаляни · 852 г.х.</div>
          <div className="mt-3 grid grid-cols-3 gap-2 text-[11px]">
            {[
              { l: "глав", v: "13" },
              { l: "страниц", v: "7 124" },
              { l: "размер", v: "~ 84 МБ" },
            ].map((x) => (
              <div key={x.l} className="rounded border border-indigo-100 bg-white px-2 py-1.5">
                <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500">{x.l}</div>
                <div className="text-[14px] font-bold tabular text-slate-900">{x.v}</div>
              </div>
            ))}
          </div>
          <div className="mt-3 pt-3 border-t border-indigo-100 flex items-center gap-2">
            <button className="h-8 px-3.5 rounded-md text-[12px] font-semibold bg-indigo-600 text-white inline-flex items-center gap-1.5"><I.Download size={11} />Импортировать</button>
            <label className="ml-2 text-[11px] text-slate-600 inline-flex items-center gap-1.5">
              <input type="checkbox" defaultChecked className="rounded border-slate-300" />
              skip-cleanup · сохранить staging XML
            </label>
            <span className="ml-auto text-[10.5px] font-mono text-slate-500">≈ 90 сек</span>
          </div>
        </div>
        <div className="mt-3 rounded-md border border-amber-200 bg-amber-50/60 p-3 flex items-start gap-2.5">
          <I.AlertTriangle size={13} className="text-amber-700 mt-0.5" />
          <div className="text-[11.5px] text-amber-900 leading-relaxed flex-1">
            <strong>Возможные ошибки:</strong> Cloudflare 403, прокси не найден, размер архива &gt; 200 МБ. При сбое импорт можно повторить — staging переживает retry.
          </div>
        </div>
      </div>
    </div>
  </div>
);

const PdfUpload = () => (
  <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
    <div className="px-5 h-12 border-b border-slate-200 flex items-center gap-3">
      <div className="h-7 w-7 rounded bg-amber-100 grid place-items-center text-amber-700"><I.FileText size={13} /></div>
      <div className="text-[13px] font-bold text-slate-900">Загрузить PDF / EPUB</div>
      <span className="text-[10.5px] text-slate-500 font-mono ml-2">drag&drop · до 200 МБ</span>
      <div className="ml-auto text-[10px] font-mono text-slate-400">2 / 4 · форма метаданных</div>
    </div>
    <div className="p-5 grid grid-cols-12 gap-5">
      <div className="col-span-5">
        <div className="aspect-[3/4] rounded-md border-2 border-dashed border-slate-300 bg-slate-50/60 flex flex-col items-center justify-center text-center px-6 relative overflow-hidden">
          <div className="absolute inset-x-3 inset-y-3 bg-white border border-amber-300 rounded shadow-[0_4px_14px_-4px_rgba(15,23,42,0.18)] flex flex-col">
            <div className="h-8 px-3 flex items-center gap-2 border-b border-slate-100 text-[11px]">
              <I.FileText size={11} className="text-amber-600" />
              <span className="font-medium text-slate-800 truncate">manuscript-1287.pdf</span>
              <span className="ml-auto font-mono text-slate-400">142 стр</span>
            </div>
            <div className="flex-1 p-3 space-y-1.5">
              {Array.from({length:14}).map((_,i)=>(
                <div key={i} className="h-1.5 bg-slate-200 rounded-full" style={{width: `${60+Math.random()*35}%`}}/>
              ))}
            </div>
          </div>
          <div className="absolute bottom-2.5 left-2.5 right-2.5 bg-emerald-50 border border-emerald-200 rounded p-1.5 text-[10.5px] text-emerald-800 flex items-center gap-1.5"><I.CheckCircle size={10} />файл принят · 12.4 МБ</div>
        </div>
      </div>
      <div className="col-span-7 space-y-2.5">
        <div>
          <label className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500">title (распознано)</label>
          <input defaultValue="Хукмы Аллаха · конспект лекций" className="mt-1 h-8 w-full px-2.5 text-[12.5px] rounded-md border border-slate-200 bg-white outline-none focus:border-indigo-300" />
        </div>
        <div className="grid grid-cols-2 gap-2.5">
          <div>
            <label className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500">автор · поиск Authority</label>
            <div className="mt-1 h-8 px-2.5 rounded-md border border-indigo-300 ring-2 ring-indigo-100 bg-white flex items-center gap-2 text-[12.5px]">
              <I.User size={11} className="text-indigo-600" />
              <span className="text-slate-900">А. Г. Тагирьянов</span>
              <span className="ml-auto text-[10px] font-mono text-emerald-700">найден · #314</span>
            </div>
          </div>
          <div>
            <label className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500">тип</label>
            <select className="mt-1 h-8 w-full px-2.5 text-[12.5px] rounded-md border border-slate-200 bg-white outline-none">
              <option>PDF — конспект лекций</option>
            </select>
          </div>
          <div>
            <label className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500">язык</label>
            <select className="mt-1 h-8 w-full px-2.5 text-[12.5px] rounded-md border border-slate-200 bg-white outline-none">
              <option>русский (ru)</option><option>арабский (ar)</option><option>английский (en)</option>
            </select>
          </div>
          <div>
            <label className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500">видимость</label>
            <div className="mt-1 inline-flex h-8 rounded-md border border-slate-200 bg-white p-0.5 text-[11px] w-full">
              {[{k:"private",l:"приватно",ic:"Lock"},{k:"shared",l:"группа",ic:"Users"},{k:"public",l:"всем",ic:"Eye"}].map((v,i)=>{
                const Ic = I[v.ic];
                return (
                  <button key={v.k} className={cx("flex-1 rounded font-medium inline-flex items-center justify-center gap-1", i===0 ? "bg-slate-900 text-white" : "text-slate-500")}>
                    <Ic size={10} />{v.l}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
        <div className="pt-2 mt-1 border-t border-slate-100">
          <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500 mb-1">прогресс загрузки</div>
          <div className="h-1.5 rounded-full bg-slate-100 overflow-hidden"><div className="h-full bg-gradient-to-r from-indigo-500 to-indigo-600 rounded-full" style={{width:'72%'}}/></div>
          <div className="mt-1 flex items-center justify-between text-[10.5px] font-mono text-slate-500">
            <span>9.0 / 12.4 МБ · 72%</span>
            <span>≈ 4 сек</span>
          </div>
        </div>
        <div className="flex items-center gap-2 pt-2">
          <button className="h-8 px-3 rounded-md text-[12px] font-medium text-slate-600 hover:bg-slate-100">Назад</button>
          <button className="ml-auto h-8 px-3.5 rounded-md text-[12px] font-semibold bg-slate-900 text-white inline-flex items-center gap-1.5">Загрузить и продолжить<I.ArrowRight size={11} /></button>
        </div>
      </div>
    </div>
  </div>
);

const ScansUpload = () => (
  <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
    <div className="px-5 h-12 border-b border-slate-200 flex items-center gap-3">
      <div className="h-7 w-7 rounded bg-rose-100 grid place-items-center text-rose-700"><I.Layers size={13} /></div>
      <div className="text-[13px] font-bold text-slate-900">Загрузить сканы страниц</div>
      <span className="text-[10.5px] text-slate-500 font-mono ml-2">JPG/PNG · drag-reorder</span>
      <div className="ml-auto inline-flex items-center gap-1.5 text-[11px]">
        <input type="checkbox" defaultChecked className="rounded border-slate-300" />
        <I.Sparkles size={11} className="text-indigo-600" />
        запустить OCR после загрузки
      </div>
    </div>
    <div className="p-5">
      <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500 mb-2">218 страниц · drag для пересортировки · #1 → #218</div>
      <div className="grid grid-cols-12 gap-2">
        {Array.from({length:24}).map((_,i)=>{
          const isUploading = i === 7;
          const isDone = i < 7;
          const isQueued = i > 7;
          const isDragging = i === 3;
          return (
            <div key={i} className={cx(
              "aspect-[3/4] rounded border bg-[#f4ecd8] relative overflow-hidden",
              isDragging ? "border-indigo-500 ring-2 ring-indigo-200 -rotate-2 scale-105 z-10 shadow-lg" : "border-slate-300"
            )}>
              <div className="absolute inset-1.5 flex flex-col gap-0.5" dir="rtl">
                {Array.from({length:6}).map((_,j)=>(
                  <div key={j} className="h-0.5 bg-slate-700/50 rounded-full" style={{width: `${60+Math.random()*30}%`}}/>
                ))}
              </div>
              <div className="absolute top-0.5 left-0.5 text-[8.5px] font-mono bg-white/80 px-0.5 rounded text-slate-700 tabular">{i+1}</div>
              {isDone && <div className="absolute bottom-0.5 right-0.5 h-3 w-3 rounded-full bg-emerald-500 grid place-items-center text-white"><I.Check size={7} strokeWidth={3} /></div>}
              {isUploading && <>
                <div className="absolute inset-x-0 bottom-0 h-0.5 bg-indigo-200"><div className="h-full bg-indigo-600" style={{width:'40%'}}/></div>
                <div className="absolute top-0.5 right-0.5 h-3 w-3 rounded-full bg-white grid place-items-center"><I.Loader size={7} className="text-indigo-600 animate-spin" /></div>
              </>}
              {isQueued && <div className="absolute inset-0 bg-white/60" />}
            </div>
          );
        })}
        <div className="aspect-[3/4] col-span-2 row-span-2 rounded border-2 border-dashed border-slate-300 grid place-items-center text-center text-[10.5px] text-slate-500 hover:bg-slate-50 cursor-pointer">
          <div>
            <I.Plus size={18} className="mx-auto text-slate-400 mb-1" />
            +194<br/>остальные
          </div>
        </div>
      </div>
      <div className="mt-4 grid grid-cols-12 gap-3">
        <div className="col-span-7 grid grid-cols-2 gap-2.5">
          <input placeholder="Title книги" defaultValue="Манускрипт · Мадина 1287 г.х." className="h-8 px-2.5 text-[12.5px] rounded-md border border-slate-200 bg-white outline-none focus:border-indigo-300" />
          <input placeholder="Автор / неустановлен" defaultValue="неустановлен" className="h-8 px-2.5 text-[12.5px] italic text-slate-500 rounded-md border border-slate-200 bg-white outline-none focus:border-indigo-300" />
          <select className="h-8 px-2.5 text-[12.5px] rounded-md border border-slate-200 bg-white"><option>арабский (ar)</option></select>
          <select className="h-8 px-2.5 text-[12.5px] rounded-md border border-slate-200 bg-white"><option>Скан / рукопись</option></select>
        </div>
        <div className="col-span-5 rounded-md border border-slate-200 bg-slate-50/60 p-3">
          <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500 mb-1.5">очередь OCR</div>
          <div className="space-y-1 text-[11px]">
            <div className="flex items-center justify-between"><span className="text-emerald-700 inline-flex items-center gap-1.5"><I.CheckCircle size={11} />готово</span><span className="font-mono tabular text-slate-700">7</span></div>
            <div className="flex items-center justify-between"><span className="text-indigo-700 inline-flex items-center gap-1.5"><I.Loader size={11} className="animate-spin" />в работе</span><span className="font-mono tabular text-slate-700">1</span></div>
            <div className="flex items-center justify-between"><span className="text-slate-500 inline-flex items-center gap-1.5"><I.Circle size={11} />ожидают</span><span className="font-mono tabular text-slate-700">210</span></div>
          </div>
          <div className="mt-2 text-[10.5px] font-mono text-slate-500">≈ 18 мин до полной обработки</div>
        </div>
      </div>
    </div>
  </div>
);

const AddBookSection = () => (
  <Section
    id="add-book"
    title="Add book · 3 flow'а"
    kicker="31 — add book"
    hint="Из dropdown «+ Добавить книгу» в BookListPage. Каждый — свой визард: Shamela (admin), PDF/EPUB upload, image scans с OCR-очередью."
  >
    <div className="space-y-6">
      <div>
        <div className="flex items-baseline gap-3 mb-3"><span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">A</span><h3 className="text-[14px] font-semibold text-slate-900">Shamela import wizard · admin</h3><span className="text-[11px] text-slate-500">5 шагов · sync → search → preview → import → done</span></div>
        <ShamelaWizard />
      </div>
      <div>
        <div className="flex items-baseline gap-3 mb-3"><span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">B</span><h3 className="text-[14px] font-semibold text-slate-900">PDF / EPUB upload</h3><span className="text-[11px] text-slate-500">drag-drop + автодетект title + Authority lookup + видимость</span></div>
        <PdfUpload />
      </div>
      <div>
        <div className="flex items-baseline gap-3 mb-3"><span className="text-[10px] font-mono uppercase tracking-wider text-slate-500">C</span><h3 className="text-[14px] font-semibold text-slate-900">Image scans · постранично</h3><span className="text-[11px] text-slate-500">сетка thumbnails · drag-reorder · OCR в очереди</span></div>
        <ScansUpload />
      </div>
    </div>
  </Section>
);

// ===== 32 — Image regions + OCR overlay =======================================

const ImageRegionsSection = () => (
  <Section
    id="image-regions"
    title="Image regions + OCR overlay"
    kicker="32 — image regions"
    hint="Расширение reader'а для сканов: rect-выделение → popover «процитировать?» → сохранённые регионы подсвечены с cross-ref count'ом. Bilingual side-by-side (скан + перевод)."
  >
    <div className="grid grid-cols-12 gap-5">
      {/* Bilingual side-by-side */}
      <div className="col-span-12">
        <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500 mb-2">bilingual · скан + русский перевод</div>
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <div className="px-4 h-10 border-b border-slate-200 flex items-center gap-2 text-[11.5px] bg-slate-50/60">
            <I.Layers size={12} className="text-slate-500" />
            <span className="font-medium text-slate-800">Side-by-side · стр. 31 · ar/ru</span>
            <span className="ml-auto inline-flex items-center gap-1 text-[10.5px] font-mono text-slate-500">
              <I.Eye size={11} /> OCR overlay <span className="text-emerald-700">on</span>
            </span>
          </div>
          <div className="grid grid-cols-2 divide-x divide-slate-200">
            {/* arabic scan with regions and OCR overlay */}
            <div className="bg-slate-100 p-5 grid place-items-center min-h-[360px]">
              <div className="bg-[#f4ecd8] w-full max-w-[400px] aspect-[3/4] relative rounded-sm shadow-[0_4px_16px_-4px_rgba(15,23,42,0.2)] border border-slate-300 overflow-hidden">
                {/* fake page lines */}
                <div className="absolute inset-0 p-4 flex flex-col gap-2" dir="rtl">
                  {[88,92,76,84,95,70,88,80,92,78,85,90,65,72].map((w,i)=>(
                    <div key={i} className="h-1.5 bg-slate-700/70 rounded-full" style={{width:`${w}%`}}/>
                  ))}
                </div>
                {/* OCR text translucent overlay */}
                <div className="absolute inset-0 p-4 flex flex-col gap-2 font-naskh text-[10px] text-slate-900/85 leading-tight" dir="rtl">
                  <span className="bg-white/40 backdrop-blur-[1px] px-0.5 rounded">قَالَ الْمُؤَلِّفُ:</span>
                  <span className="bg-white/40 backdrop-blur-[1px] px-0.5 rounded">الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ</span>
                  <span className="bg-white/40 backdrop-blur-[1px] px-0.5 rounded">وَأَمَّا مَسْأَلَةُ الِاحْتِفَالِ بِمَوْلِدِ النَّبِيِّ ﷺ</span>
                </div>
                {/* saved regions with numbers */}
                <div className="absolute border-2 border-emerald-500 bg-emerald-500/10 rounded-sm" style={{ top: '14%', right: '6%', width: '85%', height: '6%' }}>
                  <div className="absolute -left-6 top-0 h-5 w-5 rounded-full bg-emerald-500 text-white text-[10px] font-mono grid place-items-center font-bold">1</div>
                </div>
                <div className="absolute border-2 border-indigo-500 bg-indigo-500/15 rounded-sm" style={{ top: '24%', right: '8%', width: '78%', height: '14%' }}>
                  <div className="absolute -left-6 top-0 h-5 w-5 rounded-full bg-indigo-500 text-white text-[10px] font-mono grid place-items-center font-bold">2</div>
                  {/* selected popover */}
                  <div className="absolute -bottom-1 left-1/2 -translate-x-1/2 translate-y-full mt-2 z-10">
                    <div className="bg-white border border-slate-200 rounded-md shadow-[0_8px_20px_-4px_rgba(15,23,42,0.25)] p-2.5 w-60" dir="ltr">
                      <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 mb-1">region #2 · уже процитирован</div>
                      <div className="text-[11px] text-slate-700 leading-relaxed">
                        <strong>3 ссылки</strong> · «Дозволенность мавлида» · «Бид‘а ляйса минха» · Q&A #1
                      </div>
                      <div className="flex items-center gap-1.5 mt-2">
                        <button className="h-6 px-2 text-[10.5px] font-medium rounded bg-indigo-600 text-white inline-flex items-center gap-1"><I.Quote size={9} />Процитировать ещё</button>
                        <button className="h-6 px-2 text-[10.5px] font-medium rounded text-slate-600 hover:bg-slate-100">Открыть ссылки</button>
                      </div>
                    </div>
                  </div>
                </div>
                <div className="absolute border-2 border-amber-500 bg-amber-500/10 rounded-sm" style={{ top: '52%', right: '10%', width: '60%', height: '8%' }}>
                  <div className="absolute -left-6 top-0 h-5 w-5 rounded-full bg-amber-500 text-white text-[10px] font-mono grid place-items-center font-bold">5</div>
                </div>
                <div className="absolute top-2 right-3 font-mono text-[10px] text-slate-700/60" dir="rtl">٣١</div>
              </div>
            </div>
            {/* russian translation panel */}
            <div className="p-5 min-h-[360px]">
              <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400 mb-2">русский перевод · параллельно</div>
              <div className="space-y-3 text-[12.5px] leading-[1.7] text-slate-800">
                <p className="bg-emerald-50/60 border-l-2 border-emerald-500 pl-2.5 py-1.5 rounded-r">
                  <span className="text-[10px] font-mono text-emerald-700 mr-2">[1]</span>
                  «Сказал автор: хвала Аллаху, Господу миров…»
                </p>
                <p className="bg-indigo-50/60 border-l-2 border-indigo-500 pl-2.5 py-1.5 rounded-r ring-1 ring-indigo-200">
                  <span className="text-[10px] font-mono text-indigo-700 mr-2">[2]</span>
                  «Что же касается празднования мавлида Пророка ﷺ, то учёные разошлись о нём на несколько мнений; самые известные — два…»
                  <span className="block text-[10px] font-mono text-indigo-700 mt-1.5">в фокусе — 3 цитаты ссылаются на этот регион</span>
                </p>
                <p className="bg-amber-50/60 border-l-2 border-amber-500 pl-2.5 py-1.5 rounded-r">
                  <span className="text-[10px] font-mono text-amber-700 mr-2">[5]</span>
                  «Первое — мнение запрещающих абсолютно; это мазхаб большинства ханбалитов…»
                </p>
              </div>
              <div className="mt-4 pt-3 border-t border-slate-100 flex items-center gap-2 text-[10.5px] font-mono text-slate-500">
                <I.Sparkles size={11} className="text-indigo-600" />
                перевод сгенерирован OCR + LLM · отредактирован вручную
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* OCR overlay toggle states */}
      <div className="col-span-6">
        <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500 mb-2">OCR overlay · toggle on</div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <div className="flex items-center gap-2 mb-3">
            <span className="text-[11px] text-slate-700">Показывать OCR поверх скана</span>
            <span className="ml-auto inline-flex items-center h-5 w-8 rounded-full bg-emerald-500 px-0.5 justify-end"><span className="h-4 w-4 rounded-full bg-white shadow"/></span>
          </div>
          <div className="bg-[#f4ecd8] aspect-[5/3] rounded border border-slate-300 relative overflow-hidden">
            <div className="absolute inset-0 p-3 space-y-1.5" dir="rtl">
              {[85,92,78,68].map((w,i)=>(<div key={i} className="h-2 bg-slate-700/70 rounded-full" style={{width:`${w}%`}}/>))}
            </div>
            <div className="absolute inset-0 p-3 space-y-1 font-naskh text-[12px] text-slate-900/85" dir="rtl">
              <div className="bg-white/50 backdrop-blur-[1px] px-1 rounded">إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ</div>
              <div className="bg-white/50 backdrop-blur-[1px] px-1 rounded">وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى</div>
            </div>
          </div>
        </div>
      </div>
      <div className="col-span-6">
        <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500 mb-2">OCR overlay · off</div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <div className="flex items-center gap-2 mb-3">
            <span className="text-[11px] text-slate-700">Показывать OCR поверх скана</span>
            <span className="ml-auto inline-flex items-center h-5 w-8 rounded-full bg-slate-300 px-0.5"><span className="h-4 w-4 rounded-full bg-white shadow"/></span>
          </div>
          <div className="bg-[#f4ecd8] aspect-[5/3] rounded border border-slate-300 relative overflow-hidden">
            <div className="absolute inset-0 p-3 space-y-1.5" dir="rtl">
              {[85,92,78,68].map((w,i)=>(<div key={i} className="h-2 bg-slate-700/70 rounded-full" style={{width:`${w}%`}}/>))}
            </div>
          </div>
        </div>
      </div>
    </div>
  </Section>
);

// ===== 33 — Shamela admin dashboard ===========================================

const ShamelaAdminSection = () => (
  <Section
    id="admin-shamela"
    title="Shamela admin · /admin/library/shamela"
    kicker="33 — admin / library"
    hint="Оператор платформы видит каталог shamela, статус каждой книги (imported / not_imported / mapping_failed) и точечно импортирует. Bulk-import добавится после решения о размере БД."
  >
    <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
      {/* header stats */}
      <div className="grid grid-cols-4 divide-x divide-slate-200 border-b border-slate-200">
        {[
          { l: "master-version", v: "2026.05.07", sub: "обновлён 4 ч назад", tone: "indigo" },
          { l: "в shamela", v: "8 471", sub: "книг в каталоге", tone: "slate" },
          { l: "в staging", v: "642", sub: "не материализованы", tone: "amber" },
          { l: "в lib_books", v: "47", sub: "доступны юзерам", tone: "emerald" },
        ].map((s) => (
          <div key={s.l} className="px-5 py-4">
            <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500">{s.l}</div>
            <div className={cx("text-[24px] font-bold tabular leading-none mt-1",
              s.tone === "emerald" && "text-emerald-700",
              s.tone === "indigo" && "text-indigo-700",
              s.tone === "amber" && "text-amber-700",
              s.tone === "slate" && "text-slate-900"
            )}>{s.v}</div>
            <div className="text-[10.5px] text-slate-500 mt-0.5">{s.sub}</div>
          </div>
        ))}
      </div>
      {/* toolbar */}
      <div className="px-5 h-12 border-b border-slate-200 flex items-center gap-2">
        <button className="h-8 px-3 rounded-md text-[12px] font-semibold bg-indigo-600 text-white inline-flex items-center gap-1.5"><I.Refresh size={11} />Синхронизировать каталог</button>
        <button className="h-8 px-3 rounded-md text-[12px] font-medium border border-slate-300 text-slate-700 hover:bg-slate-50 inline-flex items-center gap-1.5"><I.Boxes size={11} />Bulk-import</button>
        <span className="text-[10.5px] font-mono text-amber-700 bg-amber-100 px-1.5 rounded">⚠ ~ 1.5 ГБ</span>
        <div className="ml-auto flex items-center gap-2">
          <div className="inline-flex h-8 rounded-md bg-slate-100 p-0.5 text-[11px]">
            {[
              { k: "all", l: "все", n: 8471, sel: true },
              { k: "imp", l: "imported", n: 47 },
              { k: "ni", l: "not_imp", n: 8332 },
              { k: "fail", l: "mapping_failed", n: 92 },
            ].map((f) => (
              <button key={f.k} className={cx("h-7 px-2 rounded font-medium inline-flex items-center gap-1",
                f.sel ? "bg-white text-slate-900 shadow-[0_1px_2px_rgba(15,23,42,0.06)]" : "text-slate-600",
                f.k === "fail" && !f.sel && "text-rose-600"
              )}>{f.l}<span className="text-[9.5px] font-mono opacity-60">{f.n}</span></button>
            ))}
          </div>
          <div className="relative w-56">
            <I.Search size={11} className="absolute left-2 top-1/2 -translate-y-1/2 text-slate-400" />
            <input className="h-8 w-full pl-7 pr-2 text-[11.5px] rounded-md border border-slate-200 bg-slate-50 outline-none placeholder:text-slate-400" placeholder="ID или название…" />
          </div>
        </div>
      </div>
      {/* table */}
      <div>
        <div className="grid grid-cols-[60px_1fr_180px_80px_80px_180px] gap-3 px-5 h-9 items-center text-[10px] font-mono uppercase tracking-wider text-slate-500 border-b border-slate-100 bg-slate-50/50">
          <div>id</div><div>title</div><div>автор</div><div className="text-right">стр.</div><div className="text-center">статус</div><div className="text-right">действия</div>
        </div>
        {[
          { id: 1681, ar: "صَحِيحُ الْبُخَارِيِّ",  au: "البُخَارِي", pages: 4127, st: "imp" },
          { id: 1727, ar: "صَحِيحُ مُسْلِمٍ",       au: "مُسْلِم",     pages: 1893, st: "imp" },
          { id: 1672, ar: "فَتْحُ الْبَارِي",        au: "ابْنُ حَجَر", pages: 7124, st: "imp" },
          { id: 6481, ar: "تَفْسِيرُ ابْنِ كَثِيرٍ", au: "ابْنُ كَثِير", pages: 4416, st: "imp" },
          { id: 22799, ar: "الْمُوَطَّأُ",          au: "مَالِك",      pages: 1024, st: "ni",   loading: true, prog: 32 },
          { id: 1031, ar: "رِيَاضُ الصَّالِحِينَ",   au: "النَّوَوِي",  pages: 632,  st: "ni" },
          { id: 8214, ar: "فَتْحُ الْقَدِيرِ",       au: "الشَّوْكَانِيُّ", pages: 5840, st: "fail" },
          { id: 1158, ar: "الْأُمُّ",               au: "الشَّافِعِي",   pages: 4280, st: "ni" },
        ].map((r,i)=>(
          <div key={r.id} className="grid grid-cols-[60px_1fr_180px_80px_80px_180px] gap-3 px-5 h-12 items-center text-[12px] border-b border-slate-100 hover:bg-slate-50/60 last:border-b-0">
            <code className="font-mono text-[11px] text-slate-500 tabular">#{r.id}</code>
            <div dir="rtl" className="font-naskh text-[15px] text-slate-900 truncate">{r.ar}</div>
            <div dir="rtl" className="font-naskh text-[13px] text-slate-600 truncate">{r.au}</div>
            <div className="text-right font-mono tabular text-slate-500 text-[11px]">{r.pages.toLocaleString("ru-RU")}</div>
            <div className="text-center">
              {r.st === "imp"  && <span className="inline-flex items-center gap-1 h-5 px-1.5 rounded bg-emerald-100 text-emerald-700 text-[10px] font-medium"><I.CheckCircle size={9}/>imported</span>}
              {r.st === "ni"   && (r.loading
                ? <span className="inline-flex items-center gap-1 h-5 px-1.5 rounded bg-indigo-100 text-indigo-700 text-[10px] font-medium"><I.Loader size={9} className="animate-spin"/>{r.prog}%</span>
                : <span className="inline-flex items-center gap-1 h-5 px-1.5 rounded bg-slate-100 text-slate-600 text-[10px] font-medium"><I.Circle size={9}/>not_imported</span>)}
              {r.st === "fail" && <span className="inline-flex items-center gap-1 h-5 px-1.5 rounded bg-rose-100 text-rose-700 text-[10px] font-medium"><I.AlertTriangle size={9}/>mapping</span>}
            </div>
            <div className="flex items-center justify-end gap-1">
              {r.st === "imp" && <>
                <button className="h-6 px-2 rounded text-[10.5px] font-medium text-slate-600 hover:bg-slate-100 inline-flex items-center gap-1"><I.Refresh size={9}/>sync</button>
                <button className="h-6 px-2 rounded text-[10.5px] font-medium text-slate-600 hover:bg-slate-100 inline-flex items-center gap-1"><I.Eye size={9}/>open</button>
              </>}
              {r.st === "ni" && !r.loading && <>
                <button className="h-6 px-2 rounded text-[10.5px] font-medium bg-slate-900 text-white inline-flex items-center gap-1"><I.Download size={9}/>import</button>
                <button className="h-6 w-6 rounded grid place-items-center text-slate-500 hover:bg-slate-100"><I.MoreHorizontal size={11}/></button>
              </>}
              {r.st === "ni" && r.loading && <>
                <div className="flex-1 h-1 rounded-full bg-slate-100 max-w-20 overflow-hidden"><div className="h-full bg-indigo-500" style={{width:`${r.prog}%`}}/></div>
                <button className="h-6 px-2 rounded text-[10.5px] text-rose-600 hover:bg-rose-50">отмена</button>
              </>}
              {r.st === "fail" && <>
                <button className="h-6 px-2 rounded text-[10.5px] font-medium text-rose-700 hover:bg-rose-50 inline-flex items-center gap-1"><I.Refresh size={9}/>retry map</button>
                <button className="h-6 px-2 rounded text-[10.5px] font-medium text-slate-600 hover:bg-slate-100 inline-flex items-center gap-1"><I.Info size={9}/>log</button>
              </>}
            </div>
          </div>
        ))}
      </div>
      <div className="px-5 h-10 flex items-center text-[11px] text-slate-500 border-t border-slate-200 bg-slate-50/60">
        страница 1 из 706 · 12 строк на странице
        <div className="ml-auto flex items-center gap-1">
          <button className="h-6 w-6 rounded grid place-items-center text-slate-500 hover:bg-white"><I.ArrowLeft size={11}/></button>
          <button className="h-6 w-6 rounded grid place-items-center text-slate-500 hover:bg-white"><I.ArrowRight size={11}/></button>
        </div>
      </div>
    </div>
  </Section>
);

// ===== 34 — Q&A app ============================================================

const QA_TAGS = {
  фикх: "bg-indigo-100 text-indigo-700",
  акыда: "bg-emerald-100 text-emerald-700",
  хадис: "bg-amber-100 text-amber-700",
  тафсир: "bg-rose-100 text-rose-700",
  ибада: "bg-violet-100 text-violet-700",
};

const QA_ITEMS = [
  { id: 1, q: "Можно ли праздновать день рождения ребёнка?", a: "Дозволенность практики зависит от намерения и формы. Если торжество не содержит запретного, большинство современных учёных считают его дозволенным…", tags: ["фикх", "ибада"], cites: 6, scholar_ar: "ابْنُ بَازٍ", scholar_ru: "Ибн Баз", date: "2 дня назад" },
  { id: 2, q: "Что является условием действительности тахарата?", a: "Условия очищения делятся на условия для самой воды и для совершающего омовение. К первым относятся чистота воды и её достаточное количество…", tags: ["фикх"], cites: 11, scholar_ar: "النَّوَوِيُّ", scholar_ru: "ан-Навави · по фатвам", date: "неделю назад" },
  { id: 3, q: "Является ли возвышенный над троном — атрибут Аллаха?", a: "Аят «Милостивый над Троном вознёсся» (20:5) — один из ключевых текстов в обсуждении вопроса. Саляфы единогласно утверждали…", tags: ["акыда", "тафсир"], cites: 14, scholar_ar: "ابْنُ تَيْمِيَّةَ", scholar_ru: "Ибн Таймиййа · собр. фатв", date: "3 нед" },
  { id: 4, q: "Дозволено ли праздновать мавлид Пророка ﷺ?", a: "Учёные разошлись на два главных мнения. Сторонники дозволенности (ас-Суюти, аль-Хаджар, аль-Газзи) опираются на общие тексты о любви к Пророку ﷺ…", tags: ["фикх", "ибада", "хадис"], cites: 22, scholar_ar: "السُّيُوطِيُّ", scholar_ru: "ас-Суюти · аль-Хави ли-ль-фатави", date: "недавно", featured: true },
];

const QASection = () => (
  <Section
    id="qa"
    title="Q&A · вопросы со ссылками на источники"
    kicker="34 — qa"
    hint="Стартовый дизайн: список вопросов с цитатами + страница вопроса с inline-сносками. Тот же визуальный язык что и argument-map — единая платформа."
  >
    <div className="space-y-6">
      {/* /qa list */}
      <div>
        <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500 mb-3">/qa · список</div>
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <div className="px-5 h-13 py-3 border-b border-slate-200 flex items-center gap-3">
            <h2 className="text-[15px] font-bold text-slate-900">Вопросы и ответы</h2>
            <span className="text-[11px] text-slate-500">{QA_ITEMS.length} вопросов · 53 цитаты привязано</span>
            <div className="ml-auto flex items-center gap-1.5">
              <div className="inline-flex h-7 rounded bg-slate-100 p-0.5 text-[11px]">
                {Object.keys(QA_TAGS).map((t,i) => (
                  <button key={t} className={cx("h-6 px-2 rounded font-medium",
                    i === 0 ? "bg-white text-slate-900 shadow-sm" : "text-slate-600"
                  )}>{t}</button>
                ))}
              </div>
              <div className="relative w-56">
                <I.Search size={11} className="absolute left-2 top-1/2 -translate-y-1/2 text-slate-400" />
                <input className="h-7 w-full pl-7 pr-2 text-[11.5px] rounded border border-slate-200 outline-none placeholder:text-slate-400" placeholder="Поиск по вопросам…" />
              </div>
            </div>
          </div>
          <div className="divide-y divide-slate-100">
            {QA_ITEMS.map((it) => (
              <div key={it.id} className="px-5 py-4 hover:bg-slate-50/60 cursor-pointer relative">
                {it.featured && <span className="absolute left-0 top-4 bottom-4 w-0.5 bg-indigo-500 rounded-r" />}
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <h3 className="text-[14.5px] font-semibold text-slate-900 leading-snug">{it.q}</h3>
                    <p className="mt-1.5 text-[12.5px] text-slate-600 leading-relaxed line-clamp-2 max-w-[78ch]">{it.a}</p>
                    <div className="mt-2.5 flex items-center gap-2 text-[11px]">
                      {it.tags.map(t => <span key={t} className={cx("h-5 px-1.5 inline-flex items-center rounded text-[10.5px] font-medium", QA_TAGS[t])}>{t}</span>)}
                      <span className="text-slate-300">·</span>
                      <span className="inline-flex items-center gap-1 text-slate-600"><I.Quote size={11} strokeWidth={1.75} />{it.cites} цитат</span>
                      <span className="text-slate-300">·</span>
                      <span className="text-slate-500">{it.date}</span>
                    </div>
                  </div>
                  <div className="text-right shrink-0 max-w-[160px]">
                    <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400">учёный</div>
                    <div dir="rtl" className="font-naskh text-[15px] text-slate-800 leading-tight">{it.scholar_ar}</div>
                    <div className="text-[10.5px] text-slate-500 italic truncate">{it.scholar_ru}</div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* /qa/{id} detail */}
      <div>
        <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500 mb-3">/qa/4 · страница вопроса</div>
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <div className="grid grid-cols-12 divide-x divide-slate-200">
            <div className="col-span-8 p-6">
              <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400 mb-1">фикх · ибада · хадис</div>
              <h1 className="text-[24px] font-bold text-slate-900 leading-tight mb-4">Дозволено ли праздновать мавлид Пророка ﷺ?</h1>
              <div className="space-y-3.5 text-[13.5px] leading-[1.75] text-slate-800">
                <p>Учёные разошлись на два главных мнения. Сторонники дозволенности — ас-Суюти, Ибн Хаджар аль-‘Аскаляни, аль-Газзи — опираются на общие тексты о любви к Пророку ﷺ и общую дозволенность благодеяния <a className="text-indigo-700 font-mono text-[11px] hover:underline">[1]</a>. Они приводят хадис «Поистине, дела — по намерениям» <a className="text-indigo-700 font-mono text-[11px] hover:underline">[2]</a> как основание оценки практики.</p>
                <p>Противники — большинство ханбалитов, аш-Шатиби в «аль-И‘тисам», шейх Ибн Баз — указывают на отсутствие практики у саляфов и считают торжество бид‘ой <a className="text-indigo-700 font-mono text-[11px] hover:underline">[3]</a>. Их главный аргумент — хадис «Кто внесёт в дело наше то, что не из него, — отвергнуто» <a className="text-indigo-700 font-mono text-[11px] hover:underline">[4]</a>.</p>
                <p>Современные муфтии — в частности, Совет старших учёных Саудовской Аравии и Дар аль-ифта Египта — выносят разные фатвы в зависимости от формы практики. Если торжество не содержит запретного и проводится без убеждения о его обязательности, ряд учёных допускают его <a className="text-indigo-700 font-mono text-[11px] hover:underline">[5]</a>.</p>
              </div>
              {/* inline citation popover example */}
              <div className="mt-4 inline-block relative">
                <div className="bg-white border border-slate-200 rounded-md shadow-[0_8px_20px_-4px_rgba(15,23,42,0.18)] p-3 w-80">
                  <div className="text-[10px] font-mono uppercase tracking-wider text-slate-400 mb-1.5 flex items-center gap-1.5"><I.Quote size={11} className="text-indigo-600" />цитата [2] · popover</div>
                  <div dir="rtl" className="font-naskh text-[14px] text-slate-900 leading-[1.85] mb-1.5">«إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ»</div>
                  <div className="text-[11px] italic text-slate-600">«Поистине, дела — по намерениям»</div>
                  <div className="text-[10.5px] font-mono text-slate-500 mt-2 pt-2 border-t border-slate-100">صَحِيحُ الْبُخَارِيِّ · стр. 41 · хадис №1</div>
                  <div className="mt-2 flex items-center gap-1.5">
                    <button className="h-6 px-2 rounded text-[10.5px] font-medium bg-indigo-600 text-white inline-flex items-center gap-1"><I.Network size={9}/>Процитировать в графе</button>
                    <button className="h-6 px-2 rounded text-[10.5px] text-slate-600 hover:bg-slate-100">Открыть книгу</button>
                  </div>
                </div>
              </div>
            </div>
            <div className="col-span-4 p-5 bg-slate-50/40">
              <div className="text-[10.5px] font-mono uppercase tracking-wider text-slate-500 mb-3">упомянутые источники · 22</div>
              {[
                { tone: "indigo", label: "Хадисы", n: 8, src: ["Бухари №1", "Муслим №1718", "Тирмизи №2676"] },
                { tone: "emerald", label: "Книги учёных", n: 11, src: ["аль-Хави ли-ль-фатави · ас-Суюти", "аль-И‘тисам · аш-Шатиби", "Маджму‘ фатава · Ибн Баз"] },
                { tone: "amber", label: "Тафсиры", n: 3, src: ["Тафсир Ибн Касира · 5:3", "Тафсир ат-Табари · 16:124"] },
              ].map((g) => (
                <div key={g.label} className="mb-3">
                  <div className="flex items-center justify-between mb-1.5">
                    <div className={cx("text-[11px] font-medium",
                      g.tone === "indigo" && "text-indigo-700",
                      g.tone === "emerald" && "text-emerald-700",
                      g.tone === "amber" && "text-amber-700"
                    )}>{g.label}</div>
                    <span className="text-[10px] font-mono text-slate-400">{g.n}</span>
                  </div>
                  <div className="space-y-0.5">
                    {g.src.map((s,i) => (
                      <div key={i} className="flex items-baseline gap-1.5 text-[11.5px] text-slate-700">
                        <span className={cx("h-1 w-1 rounded-full mt-1.5 shrink-0",
                          g.tone === "indigo" && "bg-indigo-500",
                          g.tone === "emerald" && "bg-emerald-500",
                          g.tone === "amber" && "bg-amber-500"
                        )} />
                        <span className="truncate">{s}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
              <div className="mt-4 pt-3 border-t border-slate-200">
                <button className="w-full h-8 rounded-md text-[12px] font-medium bg-indigo-600 text-white inline-flex items-center justify-center gap-1.5">
                  <I.Network size={11}/>
                  Открыть в argument-map
                </button>
                <div className="text-[10px] text-slate-500 mt-1.5 text-center italic">этот вопрос → тема в графе</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Section>
);

// ===== 35 — Platform home ====================================================

const PlatformHomeSection = () => (
  <Section
    id="platform-home"
    title="Platform home · /"
    kicker="35 — platform home"
    hint="Стартовый dashboard платформы. Hero + 3 cards (Темы / Библиотека / Q&A) + последняя активность + footer."
  >
    <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
      {/* hero */}
      <div className="relative bg-gradient-to-br from-indigo-50/80 via-white to-emerald-50/40 px-12 py-14 border-b border-slate-200 overflow-hidden">
        <svg className="absolute -right-10 -top-10 opacity-[0.07]" width="380" height="380" viewBox="0 0 100 100">
          <g fill="none" stroke="#4f46e5" strokeWidth="0.5">
            <circle cx="50" cy="50" r="48"/><circle cx="50" cy="50" r="36"/><circle cx="50" cy="50" r="24"/>
            <polygon points="50,8 91,76 9,76"/><polygon points="50,92 9,24 91,24"/>
          </g>
        </svg>
        <div className="max-w-[640px] relative">
          <div className="text-[10.5px] font-mono uppercase tracking-[0.2em] text-indigo-700 mb-3">منصة الأدوات الرقمية</div>
          <h1 className="text-[40px] font-bold tracking-tight text-slate-900 leading-[1.05] text-balance">
            Цифровые инструменты для исламской науки.
          </h1>
          <p className="mt-4 text-[14.5px] text-slate-600 leading-[1.7] text-pretty max-w-[58ch]">
            Аргументационный граф, библиотека первоисточников, вопросы со ссылками — всё с точной
            атрибуцией каждой цитаты к месту в книге.
          </p>
          <div className="mt-6 flex items-center gap-2.5">
            <button className="h-9 px-4 rounded-md text-[13px] font-semibold bg-slate-900 text-white inline-flex items-center gap-1.5 shadow-sm">Начать новую тему<I.ArrowRight size={13}/></button>
            <button className="h-9 px-4 rounded-md text-[13px] font-medium text-slate-700 border border-slate-300 hover:bg-white inline-flex items-center gap-1.5"><I.Library size={13}/>Открыть библиотеку</button>
          </div>
        </div>
      </div>
      {/* 3 app cards */}
      <div className="grid grid-cols-3 divide-x divide-slate-200 border-b border-slate-200">
        {[
          { ic: "Network", title: "Темы · argument-map", desc: "Графы аргументации с типизированными узлами и связями. Каждая позиция подкреплена цитатой.", c: 12, cl: "тем", tone: "indigo", live: true },
          { ic: "Library", title: "Библиотека", desc: "Книги, тафсиры, своды хадисов и пользовательские сканы — фундамент всего цитирования.", c: 47, cl: "книг", tone: "emerald", live: true, anchor: true },
          { ic: "MessageSquareQuote", title: "Q&A", desc: "Вопросы по фикху и акыде с inline-цитатами и переходом в граф для разбора.", c: 4, cl: "вопроса", tone: "slate", soon: true },
        ].map((c) => {
          const Ic = I[c.ic];
          return (
            <div key={c.title} className={cx("p-6 group cursor-pointer transition-colors", c.soon ? "bg-slate-50/60" : "hover:bg-slate-50/60")}>
              <div className="flex items-start justify-between mb-3">
                <div className={cx("h-10 w-10 rounded-lg grid place-items-center",
                  c.tone === "indigo" && "bg-indigo-100 text-indigo-700",
                  c.tone === "emerald" && "bg-emerald-100 text-emerald-700",
                  c.tone === "slate" && "bg-slate-200 text-slate-500"
                )}>
                  <Ic size={18} strokeWidth={1.5}/>
                </div>
                {c.anchor && <span className="text-[9.5px] font-mono uppercase tracking-wider px-1.5 h-[18px] inline-flex items-center rounded bg-emerald-100 text-emerald-700">фундамент</span>}
                {c.soon && <span className="text-[9.5px] font-mono uppercase tracking-wider px-1.5 h-[18px] inline-flex items-center rounded bg-slate-200 text-slate-500">скоро</span>}
              </div>
              <h3 className="text-[15px] font-bold text-slate-900 mb-1.5">{c.title}</h3>
              <p className="text-[12px] text-slate-600 leading-relaxed mb-4">{c.desc}</p>
              <div className="flex items-baseline gap-2">
                <span className={cx("text-[28px] font-bold tabular leading-none",
                  c.tone === "indigo" && "text-indigo-700",
                  c.tone === "emerald" && "text-emerald-700",
                  c.tone === "slate" && "text-slate-400"
                )}>{c.c}</span>
                <span className="text-[11px] text-slate-500">{c.cl}</span>
                <span className="ml-auto text-[11px] font-medium inline-flex items-center gap-1 text-slate-700 group-hover:text-indigo-700">открыть <I.ArrowRight size={11}/></span>
              </div>
            </div>
          );
        })}
      </div>
      {/* recent activity */}
      <div className="grid grid-cols-12 divide-x divide-slate-200">
        <div className="col-span-7 p-5">
          <div className="flex items-baseline justify-between mb-3">
            <h3 className="text-[13px] font-bold text-slate-900">Последняя активность</h3>
            <a className="text-[11px] text-slate-500 hover:text-slate-900">всё →</a>
          </div>
          <div className="space-y-2.5">
            {[
              { ic: "Quote", tone: "indigo", t: "Цитата привязана к узлу «Тезис · мавлид дозволен»", b: "صَحِيحُ الْبُخَارِيِّ · стр. 41 · хадис №1", time: "5 мин" },
              { ic: "BookOpen", tone: "emerald", t: "Импортирована книга", b: "فَتْحُ الْبَارِي بِشَرْحِ صَحِيحِ الْبُخَارِيِّ · 7 124 стр.", time: "1 ч" },
              { ic: "Network", tone: "amber", t: "Создана новая тема", b: "«Кунут в фаджр-намазе» · 3 узла, 2 связи", time: "вчера" },
              { ic: "MessageSquareQuote", tone: "violet", t: "Опубликован вопрос Q&A", b: "«Дозволено ли праздновать мавлид?» · 22 цитаты", time: "вчера" },
            ].map((a, i) => {
              const Ic = I[a.ic];
              return (
                <div key={i} className="flex items-start gap-3 py-2 border-b border-slate-100 last:border-0">
                  <div className={cx("h-7 w-7 rounded grid place-items-center shrink-0",
                    a.tone === "indigo" && "bg-indigo-50 text-indigo-700",
                    a.tone === "emerald" && "bg-emerald-50 text-emerald-700",
                    a.tone === "amber" && "bg-amber-50 text-amber-700",
                    a.tone === "violet" && "bg-violet-50 text-violet-700"
                  )}><Ic size={12} strokeWidth={1.75}/></div>
                  <div className="flex-1 min-w-0">
                    <div className="text-[12.5px] font-medium text-slate-900">{a.t}</div>
                    <div className="text-[11px] text-slate-500 mt-0.5 truncate">{a.b}</div>
                  </div>
                  <span className="text-[10.5px] font-mono text-slate-400 shrink-0 mt-1.5">{a.time}</span>
                </div>
              );
            })}
          </div>
        </div>
        <div className="col-span-5 p-5">
          <h3 className="text-[13px] font-bold text-slate-900 mb-3">Быстрые ссылки</h3>
          <div className="grid grid-cols-2 gap-2">
            {[
              { ic: "Plus", l: "Новая тема", d: "argument-map" },
              { ic: "Download", l: "Импорт книги", d: "shamela / PDF" },
              { ic: "MessageSquareQuote", l: "Задать вопрос", d: "Q&A · soon", soon: true },
              { ic: "Settings", l: "Настройки", d: "профиль / язык" },
              { ic: "ShieldCheck", l: "Admin", d: "library / users", admin: true },
              { ic: "Info", l: "Помощь", d: "FAQ + контакты" },
            ].map((q) => {
              const Ic = I[q.ic];
              return (
                <button key={q.l} className={cx("p-2.5 rounded-md border border-slate-200 bg-white hover:bg-slate-50 text-left flex items-start gap-2", q.soon && "opacity-60")}>
                  <Ic size={13} strokeWidth={1.5} className="text-indigo-600 mt-0.5 shrink-0"/>
                  <div className="flex-1 min-w-0">
                    <div className="text-[11.5px] font-medium text-slate-900 flex items-center gap-1">{q.l}{q.admin && <span className="text-[8.5px] font-mono px-1 rounded bg-amber-100 text-amber-700">admin</span>}</div>
                    <div className="text-[10.5px] text-slate-500">{q.d}</div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  </Section>
);

window.AddBookSection = AddBookSection;
window.ImageRegionsSection = ImageRegionsSection;
window.ShamelaAdminSection = ShamelaAdminSection;
window.QASection = QASection;
window.PlatformHomeSection = PlatformHomeSection;
