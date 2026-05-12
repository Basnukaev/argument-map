// Dropdown / Select / Menu primitives — matches the slate/indigo design system.
// Three shapes:
//   <Dropdown>   — generic trigger + menu (most flexible, accepts custom trigger)
//   <Select>     — form-style select with a value (replaces <select>)
//   <Menu>       — context/overflow menu opened by an IconButton
//
// All three share <DropdownItem>, <DropdownSeparator>, <DropdownLabel>.

const { useState: useDDState, useRef: useDDRef, useEffect: useDDEffect } = React;

// === Hook: click-outside + Esc =============================================
function useDismiss(open, setOpen) {
  const ref = useDDRef(null);
  useDDEffect(() => {
    if (!open) return;
    const onDown = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const onKey  = (e) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => { document.removeEventListener("mousedown", onDown); document.removeEventListener("keydown", onKey); };
  }, [open]);
  return ref;
}

// === <DropdownMenu> — the panel itself =====================================
const DropdownMenu = ({ children, align = "left", width = 220, className = "" }) => (
  <div
    role="menu"
    className={cx(
      "absolute z-40 mt-1.5 rounded-md border border-slate-200 bg-white shadow-lg ring-1 ring-black/[0.04] py-1",
      align === "right" && "right-0",
      align === "left"  && "left-0",
      className,
    )}
    style={{ minWidth: width }}
  >
    {children}
  </div>
);

// === <DropdownItem> — single menu row ======================================
const DropdownItem = ({
  children,
  icon,
  iconRight,
  shortcut,
  selected,
  disabled,
  danger,
  description,
  onClick,
  className = "",
}) => {
  const Icon = icon ? I[icon] : null;
  const IconR = iconRight ? I[iconRight] : null;
  return (
    <button
      role="menuitem"
      onClick={disabled ? undefined : onClick}
      disabled={disabled}
      className={cx(
        "w-full text-left flex items-center gap-2.5 px-2.5 py-1.5 text-[13px]",
        "transition-colors",
        disabled
          ? "text-slate-400 cursor-not-allowed"
          : danger
            ? "text-red-700 hover:bg-red-50 active:bg-red-100"
            : "text-slate-700 hover:bg-slate-100 active:bg-slate-200",
        selected && !disabled && "bg-indigo-50 text-indigo-800",
        className,
      )}
    >
      {Icon && <Icon size={15} className={cx("shrink-0", danger ? "text-red-500" : selected ? "text-indigo-600" : "text-slate-500")} />}
      <span className="flex-1 min-w-0">
        <span className="block truncate">{children}</span>
        {description && <span className="block text-[11px] text-slate-500 mt-0.5 truncate">{description}</span>}
      </span>
      {selected && !IconR && <I.Check size={14} className="text-indigo-600" />}
      {IconR && <IconR size={14} className="text-slate-400" />}
      {shortcut && (
        <span className="font-mono text-[10.5px] text-slate-400 tracking-wider ml-1">{shortcut}</span>
      )}
    </button>
  );
};

const DropdownLabel = ({ children, className = "" }) => (
  <div className={cx("px-2.5 pt-2 pb-1 text-[10px] font-semibold uppercase tracking-wider text-slate-400", className)}>
    {children}
  </div>
);

const DropdownSeparator = ({ className = "" }) => (
  <div className={cx("my-1 h-px bg-slate-100", className)} />
);

// === <Dropdown> — generic: render-prop trigger =============================
// Usage:
//   <Dropdown trigger={(open, toggle) => <button onClick={toggle}>...</button>}>
//     <DropdownItem ...>Item</DropdownItem>
//   </Dropdown>
const Dropdown = ({
  trigger,
  children,
  align = "left",
  width = 220,
  defaultOpen = false,
  open: controlledOpen,
  onOpenChange,
  className = "",
}) => {
  const [innerOpen, setInnerOpen] = useDDState(defaultOpen);
  const open = controlledOpen !== undefined ? controlledOpen : innerOpen;
  const setOpen = (v) => {
    if (controlledOpen === undefined) setInnerOpen(v);
    onOpenChange?.(v);
  };
  const ref = useDismiss(open, setOpen);
  return (
    <div ref={ref} className={cx("relative inline-block", className)}>
      {trigger(open, () => setOpen(!open))}
      {open && (
        <DropdownMenu align={align} width={width}>
          {children}
        </DropdownMenu>
      )}
    </div>
  );
};

// === <Select> — form-style with value + label-style trigger ================
// Usage:
//   <Select value={v} onChange={setV} options={[{value, label, icon?, description?}]} placeholder="..."/>
const Select = ({
  value,
  onChange,
  options = [],
  placeholder = "Выберите...",
  label,
  hint,
  error,
  size = "md",
  width = 220,
  icon,
  disabled,
  className = "",
}) => {
  const [open, setOpen] = useDDState(false);
  const ref = useDismiss(open, setOpen);
  const selected = options.find((o) => o.value === value);
  const sizes = {
    sm: "h-7 px-2 text-[12px] gap-1.5 rounded",
    md: "h-9 px-3 text-[13px] gap-2 rounded-md",
    lg: "h-10 px-3.5 text-[14px] gap-2 rounded-md",
  };
  const Icon = icon ? I[icon] : null;
  const SelIcon = selected?.icon ? I[selected.icon] : null;
  return (
    <div ref={ref} className={cx("flex flex-col gap-1.5", className)} style={{ width }}>
      {label && <label className="text-[12px] font-medium text-slate-700">{label}</label>}
      <div className="relative">
        <button
          type="button"
          disabled={disabled}
          onClick={() => setOpen(!open)}
          aria-haspopup="listbox"
          aria-expanded={open}
          className={cx(
            "w-full inline-flex items-center justify-between bg-white border transition-colors",
            "focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500/30",
            sizes[size],
            error
              ? "border-red-400 ring-2 ring-red-100"
              : open
                ? "border-indigo-500 ring-2 ring-indigo-500/20"
                : "border-slate-300 hover:border-slate-400",
            disabled && "opacity-50 cursor-not-allowed bg-slate-50",
          )}
        >
          <span className="flex items-center gap-2 min-w-0 flex-1">
            {SelIcon ? <SelIcon size={14} className="text-slate-500 shrink-0" /> :
             Icon ? <Icon size={14} className="text-slate-400 shrink-0" /> : null}
            <span className={cx("truncate text-left", !selected && "text-slate-400")}>
              {selected ? selected.label : placeholder}
            </span>
          </span>
          <I.ChevronDown size={14} className={cx("text-slate-400 shrink-0 transition-transform", open && "rotate-180 text-slate-600")} />
        </button>
        {open && (
          <DropdownMenu width={width} className="left-0 right-0" align="left">
            {options.map((o, i) =>
              o.separator ? (
                <DropdownSeparator key={i} />
              ) : o.label_group ? (
                <DropdownLabel key={i}>{o.label_group}</DropdownLabel>
              ) : (
                <DropdownItem
                  key={o.value}
                  icon={o.icon}
                  description={o.description}
                  selected={o.value === value}
                  disabled={o.disabled}
                  onClick={() => { onChange?.(o.value); setOpen(false); }}
                >
                  {o.label}
                </DropdownItem>
              )
            )}
          </DropdownMenu>
        )}
      </div>
      {hint && !error && <span className="text-[11px] text-slate-500">{hint}</span>}
      {error && <span className="text-[11px] text-red-600 flex items-center gap-1"><I.AlertCircle size={12} />{error}</span>}
    </div>
  );
};

// === <Menu> — IconButton-triggered overflow menu ===========================
// Usage:
//   <Menu icon="MoreHorizontal" label="Действия" align="right">
//     <DropdownItem ...>Item</DropdownItem>
//   </Menu>
const Menu = ({ icon = "MoreHorizontal", label = "Меню", align = "right", width = 200, children, size = "md" }) => (
  <Dropdown
    align={align}
    width={width}
    trigger={(open, toggle) => (
      <IconButton
        icon={icon}
        label={label}
        size={size}
        onClick={toggle}
        active={open}
      />
    )}
  >
    {children}
  </Dropdown>
);

// === <SplitButton> — primary action + dropdown of alternatives =============
const SplitButton = ({ children, onPrimary, icon = "Plus", size = "md", variant = "primary", menuItems = [] }) => {
  const [open, setOpen] = useDDState(false);
  const ref = useDismiss(open, setOpen);
  const sizes = {
    sm: { btn: "h-7 px-2.5 text-[12px] gap-1.5", split: "h-7 w-6", iconN: 13 },
    md: { btn: "h-9 px-3.5 text-[13px] gap-1.5", split: "h-9 w-7", iconN: 15 },
    lg: { btn: "h-11 px-5 text-[14px] gap-2",   split: "h-11 w-8", iconN: 18 },
  };
  const s = sizes[size];
  const Icon = icon ? I[icon] : null;
  const colors = variant === "primary"
    ? { main: "bg-indigo-600 text-white hover:bg-indigo-700 active:bg-indigo-800 border-indigo-700/40", divider: "border-indigo-500/40" }
    : { main: "bg-white text-slate-800 hover:bg-slate-50 active:bg-slate-100 border-slate-300", divider: "border-slate-300" };
  return (
    <div ref={ref} className="relative inline-flex">
      <button
        onClick={onPrimary}
        className={cx("inline-flex items-center justify-center font-medium border rounded-l-md transition-colors", s.btn, colors.main)}
      >
        {Icon && <Icon size={s.iconN} />}
        {children}
      </button>
      <button
        onClick={() => setOpen(!open)}
        aria-label="Больше действий"
        aria-haspopup="menu"
        aria-expanded={open}
        className={cx(
          "inline-flex items-center justify-center font-medium border rounded-r-md border-l-0 transition-colors",
          s.split,
          colors.main,
        )}
      >
        <I.ChevronDown size={s.iconN} className={cx("transition-transform", open && "rotate-180")} />
      </button>
      {open && (
        <DropdownMenu align="right" width={220} className="top-full">
          {menuItems.map((m, i) =>
            m.separator ? <DropdownSeparator key={i} /> :
            m.label_group ? <DropdownLabel key={i}>{m.label_group}</DropdownLabel> :
            <DropdownItem key={i} icon={m.icon} shortcut={m.shortcut} onClick={() => { setOpen(false); m.onClick?.(); }}>{m.label}</DropdownItem>
          )}
        </DropdownMenu>
      )}
    </div>
  );
};

// === <ComboBox> — Select with search (compact, for long lists) ============
const ComboBox = ({ value, onChange, options = [], placeholder = "Выберите...", searchPlaceholder = "Найти...", width = 260, label, icon }) => {
  const [open, setOpen] = useDDState(false);
  const [q, setQ] = useDDState("");
  const ref = useDismiss(open, setOpen);
  const selected = options.find((o) => o.value === value);
  const filtered = options.filter((o) => !q || (o.label || "").toLowerCase().includes(q.toLowerCase()));
  const Icon = icon ? I[icon] : null;
  const SelIcon = selected?.icon ? I[selected.icon] : null;
  return (
    <div ref={ref} className="flex flex-col gap-1.5" style={{ width }}>
      {label && <label className="text-[12px] font-medium text-slate-700">{label}</label>}
      <div className="relative">
        <button
          type="button"
          onClick={() => { setOpen(!open); setQ(""); }}
          className={cx(
            "w-full inline-flex items-center justify-between gap-2 bg-white border rounded-md h-9 px-3 text-[13px] transition-colors",
            "focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500/30",
            open ? "border-indigo-500 ring-2 ring-indigo-500/20" : "border-slate-300 hover:border-slate-400",
          )}
        >
          <span className="flex items-center gap-2 min-w-0 flex-1">
            {SelIcon ? <SelIcon size={14} className="text-slate-500 shrink-0" /> :
             Icon ? <Icon size={14} className="text-slate-400 shrink-0" /> : null}
            <span className={cx("truncate text-left", !selected && "text-slate-400")}>{selected ? selected.label : placeholder}</span>
          </span>
          <I.ChevronDown size={14} className={cx("text-slate-400 shrink-0 transition-transform", open && "rotate-180")} />
        </button>
        {open && (
          <div className="absolute z-40 mt-1.5 left-0 right-0 rounded-md border border-slate-200 bg-white shadow-lg ring-1 ring-black/[0.04]">
            <div className="flex items-center gap-2 px-2.5 h-9 border-b border-slate-100">
              <I.Search size={14} className="text-slate-400" />
              <input
                autoFocus
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={searchPlaceholder}
                className="flex-1 bg-transparent text-[13px] outline-none placeholder:text-slate-400"
              />
              {q && <button onClick={() => setQ("")} className="text-slate-400 hover:text-slate-600"><I.X size={14} /></button>}
            </div>
            <div className="max-h-64 overflow-y-auto py-1">
              {filtered.length === 0 ? (
                <div className="px-3 py-6 text-center text-[12px] text-slate-400">Ничего не найдено</div>
              ) : (
                filtered.map((o) => (
                  <DropdownItem
                    key={o.value}
                    icon={o.icon}
                    description={o.description}
                    selected={o.value === value}
                    onClick={() => { onChange?.(o.value); setOpen(false); }}
                  >{o.label}</DropdownItem>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

Object.assign(window, { Dropdown, DropdownMenu, DropdownItem, DropdownLabel, DropdownSeparator, Select, Menu, SplitButton, ComboBox });
