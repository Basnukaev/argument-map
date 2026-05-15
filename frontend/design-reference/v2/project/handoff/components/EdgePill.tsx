/**
 * EdgePill — SVG-rendered edge label for TopicGraph.
 *
 * Used inside the graph's edge layer:
 *   <svg ...>
 *     <path d="..." stroke="..." />
 *     <EdgePill x={midX} y={midY} kind="SUPPORTS" />
 *   </svg>
 *
 * Picks color, glyph, and label text from the EDGE token registry.
 */

export type EdgeKind = 'SUPPORTS' | 'REFUTES' | 'INVALIDATES' | 'QUALIFIES' | 'PROVES' | 'RESPONDS_TO';

interface EdgeMeta {
  /** CSS var name (without var()) for stroke / text color */
  colorVar: string;
  /** CSS var name (without var()) for fill */
  bgVar: string;
  label: string;
  mark: string;
}

const EDGES: Record<EdgeKind, EdgeMeta> = {
  SUPPORTS:    { colorVar: '--c-edge-supports',  bgVar: '--c-edge-supports-bg',  label: 'поддерживает', mark: '✓' },
  REFUTES:     { colorVar: '--c-edge-refutes',   bgVar: '--c-edge-refutes-bg',   label: 'опровергает',  mark: '✗' },
  INVALIDATES: { colorVar: '--c-edge-refutes',   bgVar: '--c-edge-refutes-bg',   label: 'аннулирует',   mark: '⊘' },
  QUALIFIES:   { colorVar: '--c-edge-qualifies', bgVar: '--c-edge-qualifies-bg', label: 'уточняет',     mark: '»' },
  PROVES:      { colorVar: '--c-edge-supports',  bgVar: '--c-edge-supports-bg',  label: 'доказывает',   mark: '✓' },
  RESPONDS_TO: { colorVar: '--c-edge-responds',  bgVar: '--c-edge-responds-bg',  label: 'отвечает',     mark: '↩' },
};

export function EdgePill({ x, y, kind }: { x: number; y: number; kind: EdgeKind }) {
  const t = EDGES[kind];
  const w = t.label.length * 6.5 + 32;
  const h = 22;
  const color = `var(${t.colorVar})`;
  const bg = `var(${t.bgVar})`;

  return (
    <g transform={`translate(${x - w / 2} ${y - h / 2})`}>
      <rect x="0" y="0" width={w} height={h} rx={h / 2}
            fill={bg} stroke={color} strokeWidth={1} />
      <text x={w / 2} y={h / 2 + 3.5}
            textAnchor="middle"
            fill={color}
            fontSize={10.5}
            fontWeight={600}
            style={{ fontFamily: 'var(--font-ui)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        {t.mark} {t.label}
      </text>
    </g>
  );
}

/**
 * Edge marker definitions — drop once into the <defs> of your graph svg.
 *
 *   <svg>
 *     <defs>
 *       <EdgeMarkerDefs />
 *     </defs>
 *     <path ... markerEnd="url(#edge-arrow-SUPPORTS)" />
 *   </svg>
 */
export function EdgeMarkerDefs() {
  return (
    <>
      {(Object.keys(EDGES) as EdgeKind[]).map((k) => (
        <marker key={k}
          id={`edge-arrow-${k}`}
          viewBox="0 0 10 10"
          refX={9} refY={5}
          markerWidth={6} markerHeight={6}
          orient="auto-start-reverse">
          <path d="M 0 0 L 10 5 L 0 10 z" fill={`var(${EDGES[k].colorVar})`} />
        </marker>
      ))}
    </>
  );
}
