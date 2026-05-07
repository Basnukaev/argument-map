// Design tokens & primitive UI components for Argument Map.

// === Status & Type tokens =====================================================

const STATUS = {
  STANDING: {
    key: "STANDING",
    label: "Устоявшийся",
    short: "Поддержан",
    border: "border-emerald-500",
    bar: "bg-emerald-500",
    bg: "bg-emerald-50",
    text: "text-emerald-700",
    accent: "text-emerald-700",
    ring: "ring-emerald-500/30",
    badgeBg: "bg-emerald-100",
    badgeText: "text-emerald-800",
    icon: "Check",
    glyph: "✓",
    description: "Поддержан, не опровергнут",
  },
  DISPUTED: {
    key: "DISPUTED",
    label: "Спорный",
    short: "Спорный",
    border: "border-amber-500",
    bar: "bg-amber-500",
    bg: "bg-amber-50",
    text: "text-amber-700",
    accent: "text-amber-700",
    ring: "ring-amber-500/30",
    badgeBg: "bg-amber-100",
    badgeText: "text-amber-900",
    icon: "AlertTriangle",
    glyph: "⚠",
    description: "Есть и за, и против",
  },
  REFUTED: {
    key: "REFUTED",
    label: "Опровергнут",
    short: "Опровергнут",
    border: "border-red-500",
    bar: "bg-red-500",
    bg: "bg-red-50",
    text: "text-red-700",
    accent: "text-red-700",
    ring: "ring-red-500/30",
    badgeBg: "bg-red-100",
    badgeText: "text-red-800",
    icon: "XCircle",
    glyph: "✗",
    description: "Опровергнут аргументацией",
  },
  UNVERIFIED: {
    key: "UNVERIFIED",
    label: "Не оценён",
    short: "Не оценён",
    border: "border-slate-300",
    bar: "bg-slate-400",
    bg: "bg-white",
    text: "text-slate-600",
    accent: "text-slate-600",
    ring: "ring-slate-400/30",
    badgeBg: "bg-slate-100",
    badgeText: "text-slate-700",
    icon: "Circle",
    glyph: "○",
    description: "Не оценён (по умолчанию)",
  },
};

const NODE_TYPE = {
  QUESTION: {
    key: "QUESTION",
    label: "Вопрос",
    full: "QUESTION",
    icon: "CircleHelp",
    chipBg: "bg-violet-100",
    chipText: "text-violet-700",
    miniDot: "bg-violet-500",
    miniMap: "fill-violet-300 stroke-violet-500",
    description: "Корневой или уточняющий вопрос дискуссии",
    example: "Дозволено ли празднование мавлида?",
  },
  CLAIM: {
    key: "CLAIM",
    label: "Тезис",
    full: "CLAIM",
    icon: "Megaphone",
    chipBg: "bg-indigo-100",
    chipText: "text-indigo-700",
    miniDot: "bg-indigo-500",
    miniMap: "fill-indigo-300 stroke-indigo-500",
    description: "Утверждение, ответ на вопрос",
    example: "Мавлид является дозволенной практикой",
  },
  ARGUMENT: {
    key: "ARGUMENT",
    label: "Аргумент",
    full: "ARGUMENT",
    icon: "MessageSquareQuote",
    chipBg: "bg-sky-100",
    chipText: "text-sky-700",
    miniDot: "bg-sky-500",
    miniMap: "fill-sky-300 stroke-sky-500",
    description: "Довод за или против тезиса",
    example: "Это выражение любви к Пророку",
  },
  EVIDENCE: {
    key: "EVIDENCE",
    label: "Свидетельство",
    full: "EVIDENCE",
    icon: "FileText",
    chipBg: "bg-teal-100",
    chipText: "text-teal-700",
    miniDot: "bg-teal-500",
    miniMap: "fill-teal-300 stroke-teal-500",
    description: "Хадис, цитата, факт, источник",
    example: "Хадис из Сахих аль-Бухари №4",
  },
};

const EDGE_TYPE = {
  SUPPORTS: {
    key: "SUPPORTS",
    label: "Поддерживает",
    altLabels: ["поддерживает", "доказывает", "согласуется с"],
    color: "#10b981",     // emerald-500
    badgeBg: "bg-emerald-50",
    badgeText: "text-emerald-700",
    badgeBorder: "border-emerald-200",
    style: "solid",
    width: 2,
    icon: "PlusCircle",
    description: "Поддерживает / усиливает родительский узел",
  },
  REFUTES: {
    key: "REFUTES",
    label: "Опровергает",
    altLabels: ["опровергает", "противоречит"],
    color: "#ef4444",     // red-500
    badgeBg: "bg-red-50",
    badgeText: "text-red-700",
    badgeBorder: "border-red-200",
    style: "solid",
    width: 2,
    icon: "XCircle",
    description: "Опровергает родительский узел",
  },
  INVALIDATES: {
    key: "INVALIDATES",
    label: "Аннулирует",
    altLabels: ["аннулирует"],
    color: "#b91c1c",     // red-700
    badgeBg: "bg-red-50",
    badgeText: "text-red-800",
    badgeBorder: "border-red-300",
    style: "dashed",
    width: 3,
    icon: "Slash",
    description: "Жёсткое мета-опровержение (kill-switch)",
  },
  QUALIFIES: {
    key: "QUALIFIES",
    label: "Уточняет",
    altLabels: ["уточняет", "сужает"],
    color: "#3b82f6",     // blue-500
    badgeBg: "bg-blue-50",
    badgeText: "text-blue-700",
    badgeBorder: "border-blue-200",
    style: "solid",
    width: 2,
    icon: "Crosshair",
    description: "Уточняет применимость или сужает область",
  },
  RESPONDS_TO: {
    key: "RESPONDS_TO",
    label: "Отвечает на",
    altLabels: ["отвечает на"],
    color: "#94a3b8",     // slate-400
    badgeBg: "bg-slate-50",
    badgeText: "text-slate-600",
    badgeBorder: "border-slate-200",
    style: "solid",
    width: 1.5,
    opacity: 0.7,
    icon: "CornerDownRight",
    description: "Организационная связь «ответ на вопрос»",
  },
};

// === Primitive components ====================================================

function cx(...xs) { return xs.filter(Boolean).join(" "); }

const Button = ({ variant = "primary", size = "md", icon, iconRight, children, className = "", disabled, full, ...rest }) => {
  const sizes = {
    xs: "h-7 px-2.5 text-[12px] gap-1 rounded",
    sm: "h-8 px-3 text-[13px] gap-1.5 rounded-md",
    md: "h-9 px-3.5 text-[13px] gap-2 rounded-md",
    lg: "h-11 px-5 text-[14px] gap-2 rounded-md",
  };
  const variants = {
    primary: "bg-indigo-600 text-white hover:bg-indigo-700 active:bg-indigo-800 border border-indigo-700/40 shadow-sm",
    secondary: "bg-white text-slate-800 hover:bg-slate-50 active:bg-slate-100 border border-slate-300 shadow-[0_1px_0_rgba(15,23,42,0.04)]",
    ghost: "text-slate-700 hover:bg-slate-100 active:bg-slate-200 border border-transparent",
    danger: "bg-red-600 text-white hover:bg-red-700 active:bg-red-800 border border-red-700/40 shadow-sm",
    "danger-ghost": "text-red-700 hover:bg-red-50 active:bg-red-100 border border-transparent",
    link: "text-indigo-700 hover:text-indigo-800 hover:underline underline-offset-4 border border-transparent px-1",
  };
  const Icon = icon ? I[icon] : null;
  const IconR = iconRight ? I[iconRight] : null;
  return (
    <button
      className={cx(
        "inline-flex items-center justify-center font-medium select-none",
        "transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2",
        sizes[size],
        variants[variant],
        full && "w-full",
        disabled && "opacity-50 cursor-not-allowed pointer-events-none",
        className,
      )}
      disabled={disabled}
      {...rest}
    >
      {Icon && <Icon size={size === "lg" ? 18 : size === "xs" ? 13 : 15} />}
      {children}
      {IconR && <IconR size={size === "lg" ? 18 : size === "xs" ? 13 : 15} />}
    </button>
  );
};

const IconButton = ({ icon, label, active, size = "md", variant = "ghost", className = "", ...rest }) => {
  const Icon = I[icon];
  const sizes = { sm: "h-7 w-7", md: "h-9 w-9", lg: "h-10 w-10" };
  const variants = {
    ghost: active
      ? "bg-indigo-50 text-indigo-700 border-indigo-200"
      : "text-slate-600 hover:bg-slate-100 hover:text-slate-900 border-transparent",
    solid: "bg-white border-slate-300 text-slate-700 hover:bg-slate-50",
  };
  return (
    <button
      title={label}
      aria-label={label}
      className={cx(
        "inline-flex items-center justify-center rounded-md border transition-colors",
        "focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1",
        sizes[size],
        variants[variant],
        className,
      )}
      {...rest}
    >
      <Icon size={size === "lg" ? 20 : 18} />
    </button>
  );
};

const Badge = ({ children, tone = "slate", size = "md", icon, className = "", style }) => {
  const sizes = { sm: "h-5 px-1.5 text-[11px] gap-1 rounded", md: "h-[22px] px-2 text-[11px] gap-1 rounded", lg: "h-7 px-2.5 text-[12px] gap-1.5 rounded-md" };
  const tones = {
    slate: "bg-slate-100 text-slate-700 border-slate-200",
    indigo: "bg-indigo-50 text-indigo-700 border-indigo-200",
    emerald: "bg-emerald-50 text-emerald-700 border-emerald-200",
    amber: "bg-amber-50 text-amber-800 border-amber-200",
    red: "bg-red-50 text-red-700 border-red-200",
    blue: "bg-blue-50 text-blue-700 border-blue-200",
    violet: "bg-violet-50 text-violet-700 border-violet-200",
    sky: "bg-sky-50 text-sky-700 border-sky-200",
    teal: "bg-teal-50 text-teal-700 border-teal-200",
  };
  const Icon = icon ? I[icon] : null;
  return (
    <span style={style} className={cx("inline-flex items-center font-medium border whitespace-nowrap", sizes[size], tones[tone], className)}>
      {Icon && <Icon size={size === "lg" ? 14 : 12} />}
      {children}
    </span>
  );
};

const StatusBadge = ({ status, size = "md", showIcon = true }) => {
  const s = STATUS[status];
  const Icon = I[s.icon];
  const sizes = { sm: "h-5 px-1.5 text-[11px] gap-1 rounded", md: "h-6 px-2 text-[11px] gap-1 rounded-md", lg: "h-7 px-2.5 text-[12px] gap-1.5 rounded-md" };
  return (
    <span className={cx("inline-flex items-center font-medium border whitespace-nowrap", sizes[size], s.badgeBg, s.badgeText, "border-" + s.border.split("-")[1] + "-200")}>
      {showIcon && <Icon size={size === "lg" ? 14 : 12} />}
      {s.label}
    </span>
  );
};

const TypeChip = ({ type, size = "md" }) => {
  const t = NODE_TYPE[type];
  const Icon = I[t.icon];
  const sizes = { sm: "h-5 px-1.5 text-[11px] gap-1 rounded", md: "h-6 px-2 text-[11px] gap-1 rounded" };
  return (
    <span className={cx("inline-flex items-center font-semibold uppercase tracking-wide", sizes[size], t.chipBg, t.chipText)}>
      <Icon size={12} />
      {t.label}
    </span>
  );
};

const Kbd = ({ children, className = "" }) => (
  <kbd className={cx("inline-flex items-center justify-center min-w-[20px] h-[20px] px-1.5 rounded border border-slate-300 bg-white text-[11px] font-mono text-slate-600 shadow-[0_1px_0_rgba(15,23,42,0.04)]", className)}>
    {children}
  </kbd>
);

const Input = ({ icon, error, label, hint, suffix, className = "", ...rest }) => {
  const Icon = icon ? I[icon] : null;
  return (
    <div className={cx("flex flex-col gap-1.5", className)}>
      {label && <label className="text-[12px] font-medium text-slate-700">{label}</label>}
      <div className={cx(
        "flex items-center h-9 rounded-md border bg-white transition-colors",
        error ? "border-red-400 ring-2 ring-red-100" : "border-slate-300 focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-500/20",
      )}>
        {Icon && <Icon size={16} className="ml-3 text-slate-400" />}
        <input
          className={cx("flex-1 px-3 bg-transparent text-[13px] text-slate-900 placeholder:text-slate-400 outline-none", Icon && "pl-2")}
          {...rest}
        />
        {suffix && <div className="pr-3 text-[12px] text-slate-400">{suffix}</div>}
      </div>
      {hint && !error && <span className="text-[11px] text-slate-500">{hint}</span>}
      {error && <span className="text-[11px] text-red-600 flex items-center gap-1"><I.AlertCircle size={12} />{error}</span>}
    </div>
  );
};

const Textarea = ({ label, hint, error, rows = 4, className = "", ...rest }) => (
  <div className={cx("flex flex-col gap-1.5", className)}>
    {label && <label className="text-[12px] font-medium text-slate-700">{label}</label>}
    <textarea
      rows={rows}
      className={cx(
        "block w-full rounded-md border bg-white px-3 py-2 text-[13px] text-slate-900 placeholder:text-slate-400",
        "outline-none transition-colors resize-y leading-relaxed",
        error ? "border-red-400 ring-2 ring-red-100" : "border-slate-300 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20",
      )}
      {...rest}
    />
    {hint && !error && <span className="text-[11px] text-slate-500">{hint}</span>}
    {error && <span className="text-[11px] text-red-600 flex items-center gap-1"><I.AlertCircle size={12} />{error}</span>}
  </div>
);

// Roman numeral converter (1..30 needed)
const ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
  "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
  "XXI", "XXII", "XXIII", "XXIV", "XXV", "XXVI", "XXVII", "XXVIII", "XXIX", "XXX"];

// Parse a kicker like "08 — screen · main" → { num: "VIII", rest: "screen · main" }
function parseKicker(k) {
  if (!k) return null;
  const m = String(k).match(/^\s*(\d+)\s*[—–-]\s*(.+)$/);
  if (!m) return { num: null, rest: String(k) };
  const n = parseInt(m[1], 10);
  return { num: ROMAN[n] || String(n), rest: m[2] };
}

const Section = ({ title, kicker, hint, children, className = "", id }) => (
  <section id={id} className={cx("py-16", className)}>
    <div className="max-w-[1380px] mx-auto px-10">
      {kicker && (
        <div className="text-[11px] font-mono font-semibold tracking-wider uppercase text-indigo-600 mb-2">{kicker}</div>
      )}
      <div className="flex items-end justify-between mb-8 gap-8">
        <h2 className="text-[28px] font-bold tracking-tight text-slate-900 leading-tight">{title}</h2>
        {hint && <p className="text-[13px] text-slate-500 max-w-md text-right leading-relaxed">{hint}</p>}
      </div>
      {children}
    </div>
  </section>
);

const SubSection = ({ title, hint, children, className = "" }) => (
  <div className={cx("mb-10", className)}>
    <div className="flex items-baseline justify-between mb-4 gap-6">
      <h3 className="text-[15px] font-semibold text-slate-900">{title}</h3>
      {hint && <span className="text-[12px] text-slate-500 leading-snug max-w-sm text-right">{hint}</span>}
    </div>
    {children}
  </div>
);

// Girih-star section divider (decorative, single instance)
const GirihDivider = ({ className = "" }) => (
  <div className={cx("girih-divider", className)} aria-hidden="true">
    <svg width="42" height="42" viewBox="0 0 42 42" fill="none" stroke="currentColor" strokeWidth="0.9" strokeLinecap="round" strokeLinejoin="round">
      <g transform="translate(21 21)">
        {[0,45,90,135].map((a) => (
          <g key={a} transform={`rotate(${a})`}>
            <line x1="-15" y1="0" x2="15" y2="0" />
          </g>
        ))}
        {[22.5, 67.5, 112.5, 157.5].map((a) => (
          <g key={a} transform={`rotate(${a})`}>
            <line x1="-11" y1="0" x2="11" y2="0" opacity="0.55" />
          </g>
        ))}
        <circle r="6" fill="none" />
        <circle r="2" fill="currentColor" stroke="none" />
      </g>
    </svg>
  </div>
);

const Tooltip = ({ children, label, side = "top" }) => (
  <span className="relative group inline-flex">
    {children}
    <span className={cx(
      "pointer-events-none absolute z-30 whitespace-nowrap rounded-md bg-slate-900 px-2 py-1 text-[11px] font-medium text-white opacity-0 group-hover:opacity-100 transition-opacity",
      side === "top" && "bottom-full left-1/2 -translate-x-1/2 mb-1.5",
      side === "right" && "left-full top-1/2 -translate-y-1/2 ml-1.5",
      side === "bottom" && "top-full left-1/2 -translate-x-1/2 mt-1.5",
      side === "left" && "right-full top-1/2 -translate-y-1/2 mr-1.5",
    )}>
      {label}
    </span>
  </span>
);

const Avatar = ({ name = "?", color = "indigo", size = "md" }) => {
  const initials = name.split(" ").map((s) => s[0]).slice(0, 2).join("").toUpperCase();
  const colors = {
    indigo: "bg-indigo-100 text-indigo-700",
    emerald: "bg-emerald-100 text-emerald-700",
    amber: "bg-amber-100 text-amber-800",
    rose: "bg-rose-100 text-rose-700",
    sky: "bg-sky-100 text-sky-700",
    violet: "bg-violet-100 text-violet-700",
    teal: "bg-teal-100 text-teal-700",
  };
  const sizes = { sm: "h-6 w-6 text-[10px]", md: "h-7 w-7 text-[11px]", lg: "h-9 w-9 text-[13px]" };
  return (
    <span className={cx("inline-flex items-center justify-center rounded-full font-semibold ring-2 ring-white", colors[color], sizes[size])}>
      {initials}
    </span>
  );
};

const Card = ({ children, className = "", as = "div", ...rest }) => {
  const Tag = as;
  return (
    <Tag className={cx("rounded-xl border border-slate-200 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.04)]", className)} {...rest}>
      {children}
    </Tag>
  );
};

const Divider = ({ className = "" }) => <div className={cx("h-px bg-slate-200", className)} />;

window.STATUS = STATUS;
window.NODE_TYPE = NODE_TYPE;
window.EDGE_TYPE = EDGE_TYPE;
window.cx = cx;
Object.assign(window, {
  Button, IconButton, Badge, StatusBadge, TypeChip, Kbd, Input, Textarea,
  Section, SubSection, GirihDivider, Tooltip, Avatar, Card, Divider,
});
