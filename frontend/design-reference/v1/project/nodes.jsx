// Node card component & SVG graph renderer for Argument Map.

const NodeCard = ({
  type = "CLAIM",
  status = "UNVERIFIED",
  title,
  body,
  meta,
  selected = false,
  hovered = false,
  showHandles = false,
  width = 280,
  className = "",
  compact = false,
  badge,
}) => {
  const t = NODE_TYPE[type];
  const s = STATUS[status];
  const Icon = I[t.icon];
  const StatusIcon = I[s.icon];
  return (
    <div
      style={{ width }}
      className={cx(
        "relative rounded-xl bg-white border transition-shadow",
        selected
          ? "border-indigo-500 shadow-[0_0_0_3px_rgba(99,102,241,0.18),0_8px_20px_rgba(15,23,42,0.10)]"
          : hovered
          ? "border-slate-300 shadow-[0_4px_12px_rgba(15,23,42,0.10)]"
          : "border-slate-200 shadow-[0_1px_2px_rgba(15,23,42,0.04),0_2px_6px_rgba(15,23,42,0.04)]",
        className,
      )}
    >
      {/* Status bar — left edge, 5px wide */}
      <div className={cx("absolute left-0 top-0 bottom-0 w-[5px] rounded-l-xl", s.bar)} />

      {/* Card content */}
      <div className={cx("pl-4 pr-3", compact ? "py-2.5" : "py-3")}>
        {/* Header row */}
        <div className="flex items-center gap-2 mb-1.5">
          <span className={cx("inline-flex items-center gap-1 px-1.5 h-5 rounded text-[10px] font-bold uppercase tracking-wider", t.chipBg, t.chipText)}>
            <Icon size={11} />
            {t.label}
          </span>
          <span className="flex-1" />
          <StatusBadge status={status} size="sm" />
          {!compact && (
            <button className="text-slate-400 hover:text-slate-700 transition-colors -mr-1" aria-label="Действия">
              <I.MoreHorizontal size={14} />
            </button>
          )}
        </div>

        {/* Title / body */}
        <div className={cx("text-[13px] font-semibold leading-snug text-slate-900 text-pretty", compact && "text-[12px]")}>
          {title}
        </div>
        {body && !compact && (
          <div className="mt-1 text-[12px] leading-relaxed text-slate-600 line-clamp-2 text-pretty">{body}</div>
        )}

        {/* Footer / meta */}
        {meta && !compact && (
          <div className="mt-2 flex items-center gap-2 text-[11px] text-slate-500">
            {meta}
          </div>
        )}
      </div>

      {/* Connection handles (visible on hover/selected) */}
      {(showHandles || selected || hovered) && (
        <>
          <span className="absolute left-1/2 -translate-x-1/2 -top-1.5 h-3 w-3 rounded-full bg-white border-[1.5px] border-indigo-500 shadow-sm" />
          <span className="absolute left-1/2 -translate-x-1/2 -bottom-1.5 h-3 w-3 rounded-full bg-white border-[1.5px] border-indigo-500 shadow-sm" />
          <span className="absolute -left-1.5 top-1/2 -translate-y-1/2 h-3 w-3 rounded-full bg-white border-[1.5px] border-indigo-500 shadow-sm" />
          <span className="absolute -right-1.5 top-1/2 -translate-y-1/2 h-3 w-3 rounded-full bg-white border-[1.5px] border-indigo-500 shadow-sm" />
        </>
      )}

      {badge && (
        <div className="absolute -top-2 -right-2">{badge}</div>
      )}
    </div>
  );
};

window.NodeCard = NodeCard;
