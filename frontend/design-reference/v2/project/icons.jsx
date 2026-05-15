// Inline SVG icon set with three stylistic flavors:
// lucide (current — 2px, square caps), tabler (1.5px, hairline), phosphor (1.75px, rounded).
// Pick the style on a root element via [data-icon-style="..."]; <Icon> picks paths accordingly.
//
// Defined paths cover only what the Reader screens need.

const ICON_PATHS = {
  lucide: {
    'chevron-left':  'M15 18l-6-6 6-6',
    'chevron-right': 'M9 18l6-6-6-6',
    'chevron-down':  'M6 9l6 6 6-6',
    'chevron-up':    'M18 15l-6-6-6 6',
    'arrow-left':    'M19 12H5 M12 19l-7-7 7-7',
    'menu':          'M4 6h16 M4 12h16 M4 18h16',
    'x':             'M6 6l12 12 M6 18L18 6',
    'search':        'M11 11m-7 0a7 7 0 1014 0a7 7 0 10-14 0 M21 21l-4.3-4.3',
    'book':          'M4 4.5A2.5 2.5 0 016.5 2H20v18H6.5A2.5 2.5 0 014 17.5v-13z M20 18H6.5a2.5 2.5 0 000 5H20',
    'list':          'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
    'panel-left':    'M3 3h18v18H3z M9 3v18',
    'panel-right':   'M3 3h18v18H3z M15 3v18',
    'file-text':     'M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8',
    'eye':           'M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z M12 12m-3 0a3 3 0 106 0a3 3 0 10-6 0',
    'bookmark':      'M19 21l-7-5-7 5V5a2 2 0 012-2h10a2 2 0 012 2z',
    'maximize':      'M3 9V3h6 M21 9V3h-6 M3 15v6h6 M21 15v6h-6',
    'minimize':      'M9 3v6H3 M15 3v6h6 M9 21v-6H3 M15 21v-6h6',
    'columns':       'M3 3h7v18H3z M14 3h7v18h-7z',
    'sparkles':      'M12 3l1.5 4.5L18 9l-4.5 1.5L12 15l-1.5-4.5L6 9l4.5-1.5z M19 14l.7 2.3L22 17l-2.3.7L19 20l-.7-2.3L16 17l2.3-.7z',
    'graph':         'M5 5m-2 0a2 2 0 104 0a2 2 0 10-4 0 M19 5m-2 0a2 2 0 104 0a2 2 0 10-4 0 M12 19m-2 0a2 2 0 104 0a2 2 0 10-4 0 M6.5 6.5l4 11 M17.5 6.5l-4 11',
    'quote':         'M3 21c3 0 7-1 7-8V5c0-1.1-.9-2-2-2H4a2 2 0 00-2 2v6c0 1.1.9 2 2 2h3 M14 21c3 0 7-1 7-8V5c0-1.1-.9-2-2-2h-4a2 2 0 00-2 2v6c0 1.1.9 2 2 2h3',
    'help-circle':   'M12 12m-10 0a10 10 0 1020 0a10 10 0 10-20 0 M9.1 9a3 3 0 015.8 1c0 2-3 3-3 3 M12 17h.01',
    'pin':           'M12 17v5 M9 10.76a2 2 0 01-1.11 1.79l-1.78.9A2 2 0 005 15.24V16a1 1 0 001 1h12a1 1 0 001-1v-.76a2 2 0 00-1.11-1.79l-1.78-.9A2 2 0 0115 10.76V7a1 1 0 011-1 2 2 0 000-4H8a2 2 0 000 4 1 1 0 011 1z',
    'external-link': 'M15 3h6v6 M10 14L21 3 M21 14v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h7',
  },
  tabler: {
    'chevron-left':  'M15 6l-6 6l6 6',
    'chevron-right': 'M9 6l6 6l-6 6',
    'chevron-down':  'M6 9l6 6l6 -6',
    'chevron-up':    'M6 15l6 -6l6 6',
    'arrow-left':    'M5 12l14 0 M5 12l4 4 M5 12l4 -4',
    'menu':          'M4 6l16 0 M4 12l16 0 M4 18l16 0',
    'x':             'M18 6l-12 12 M6 6l12 12',
    'search':        'M10 10m-7 0a7 7 0 1014 0a7 7 0 10-14 0 M21 21l-6 -6',
    'book':          'M3 19a9 9 0 0 1 9 0a9 9 0 0 1 9 0 M3 6a9 9 0 0 1 9 0a9 9 0 0 1 9 0 M3 6l0 13 M12 6l0 13 M21 6l0 13',
    'list':          'M9 6l11 0 M9 12l11 0 M9 18l11 0 M5 6l0 .01 M5 12l0 .01 M5 18l0 .01',
    'panel-left':    'M4 4m0 2a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z M9 4l0 16',
    'panel-right':   'M4 4m0 2a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z M15 4l0 16',
    'file-text':     'M14 3v4a1 1 0 0 0 1 1h4 M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2z M9 9l1 0 M9 13l6 0 M9 17l6 0',
    'eye':           'M10 12a2 2 0 1 0 4 0a2 2 0 0 0 -4 0 M22 12c-2.667 4.667 -6 7 -10 7s-7.333 -2.333 -10 -7c2.667 -4.667 6 -7 10 -7s7.333 2.333 10 7',
    'bookmark':      'M9 4h6a2 2 0 0 1 2 2v14l-5 -3l-5 3v-14a2 2 0 0 1 2 -2',
    'maximize':      'M4 8v-2a2 2 0 0 1 2 -2h2 M4 16v2a2 2 0 0 0 2 2h2 M16 4h2a2 2 0 0 1 2 2v2 M16 20h2a2 2 0 0 0 2 -2v-2',
    'minimize':      'M8 4v2a2 2 0 0 1 -2 2h-2 M16 4v2a2 2 0 0 0 2 2h2 M8 20v-2a2 2 0 0 0 -2 -2h-2 M16 20v-2a2 2 0 0 1 2 -2h2',
    'columns':       'M4 6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-2a2 2 0 0 1 -2 -2z M14 6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-2a2 2 0 0 1 -2 -2z',
    'sparkles':      'M16 18a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2zm0 -12a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2zm-7 12a6 6 0 0 1 6 -6a6 6 0 0 1 -6 -6a6 6 0 0 1 -6 6a6 6 0 0 1 6 6z',
    'graph':         'M5 7m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M19 7m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M12 19m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M7 7l10 0 M7.5 8.5l3 8.5 M16.5 8.5l-3 8.5',
    'quote':         'M10 11h-4a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1h3a1 1 0 0 1 1 1v6c0 2.667 -1.333 4.333 -4 5 M19 11h-4a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1h3a1 1 0 0 1 1 1v6c0 2.667 -1.333 4.333 -4 5',
    'help-circle':   'M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0 M12 16v.01 M12 13a2 2 0 0 0 .914 -3.782a1.98 1.98 0 0 0 -2.414 .483',
    'pin':           'M9 4v6l-2 4v2h10v-2l-2 -4v-6 M12 16l0 5 M8 4l8 0',
    'external-link': 'M12 6l-8 0a2 2 0 0 0 -2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-8 M11 13l9 -9 M15 4l5 0l0 5',
  },
  phosphor: {
    'chevron-left':  'M15.5 19L8.5 12l7-7',
    'chevron-right': 'M8.5 19l7-7-7-7',
    'chevron-down':  'M5 9l7 7 7-7',
    'chevron-up':    'M5 15l7-7 7 7',
    'arrow-left':    'M21 12H4 M11 5l-7 7 7 7',
    'menu':          'M3.5 7h17 M3.5 12h17 M3.5 17h17',
    'x':             'M6 6l12 12 M6 18L18 6',
    'search':        'M11 11m-7.5 0a7.5 7.5 0 1015 0a7.5 7.5 0 10-15 0 M21 21l-4.5-4.5',
    'book':          'M5 4h14v15.5a1.5 1.5 0 01-1.5 1.5H6a1 1 0 01-1-1V4z M19 18H6a1 1 0 000 2h13',
    'list':          'M3.5 6h17 M3.5 12h17 M3.5 18h17',
    'panel-left':    'M3.5 5a1.5 1.5 0 011.5-1.5h14A1.5 1.5 0 0120.5 5v14a1.5 1.5 0 01-1.5 1.5H5A1.5 1.5 0 013.5 19zM10 4v16',
    'panel-right':   'M3.5 5a1.5 1.5 0 011.5-1.5h14A1.5 1.5 0 0120.5 5v14a1.5 1.5 0 01-1.5 1.5H5A1.5 1.5 0 013.5 19zM14 4v16',
    'file-text':     'M14 3h-7A1.5 1.5 0 005.5 4.5v15A1.5 1.5 0 007 21h10a1.5 1.5 0 001.5-1.5V8L14 3z M14 3v5h4.5 M9 13h6 M9 17h6 M9 9h2',
    'eye':           'M2 12c2.5-5.5 5.5-7 10-7s7.5 1.5 10 7c-2.5 5.5-5.5 7-10 7s-7.5-1.5-10-7z M12 12m-3 0a3 3 0 106 0a3 3 0 10-6 0',
    'bookmark':      'M6 4h12v17l-6-4-6 4z',
    'maximize':      'M4 9V5a1 1 0 011-1h4 M20 9V5a1 1 0 00-1-1h-4 M4 15v4a1 1 0 001 1h4 M20 15v4a1 1 0 01-1 1h-4',
    'minimize':      'M9 4v4a1 1 0 01-1 1H4 M15 4v4a1 1 0 001 1h4 M9 20v-4a1 1 0 00-1-1H4 M15 20v-4a1 1 0 011-1h4',
    'columns':       'M4 4h7v16H4z M13 4h7v16h-7z',
    'sparkles':      'M12 3c.6 4.8 2.2 6.4 7 7-4.8.6-6.4 2.2-7 7-.6-4.8-2.2-6.4-7-7 4.8-.6 6.4-2.2 7-7zM19 13c.3 2.4 1.1 3.2 3.5 3.5-2.4.3-3.2 1.1-3.5 3.5-.3-2.4-1.1-3.2-3.5-3.5 2.4-.3 3.2-1.1 3.5-3.5z',
    'graph':         'M5 5m-2.2 0a2.2 2.2 0 104.4 0a2.2 2.2 0 10-4.4 0 M19 5m-2.2 0a2.2 2.2 0 104.4 0a2.2 2.2 0 10-4.4 0 M12 19m-2.2 0a2.2 2.2 0 104.4 0a2.2 2.2 0 10-4.4 0 M6.4 6.8l4.2 10.4 M17.6 6.8l-4.2 10.4',
    'quote':         'M4 17V8a2 2 0 012-2h3v4H7v3h2v4H6a2 2 0 01-2-2z M15 17V8a2 2 0 012-2h3v4h-2v3h2v4h-3a2 2 0 01-2-2z',
    'help-circle':   'M12 12m-9.5 0a9.5 9.5 0 1019 0a9.5 9.5 0 10-19 0 M9.5 9.5a2.5 2.5 0 015 0c0 1.5-2.5 2-2.5 3.5 M12 17h.01',
    'pin':           'M14 3l7 7-3.5 1L14 14.5l-3 3-1.5-1.5 3-3L9 9.5 10 6z M9 15l-5 5',
    'external-link': 'M14 3.5h6.5v6.5 M21 3.5L11 13.5 M19.5 13v6a1.5 1.5 0 01-1.5 1.5H5A1.5 1.5 0 013.5 19V6A1.5 1.5 0 015 4.5h6',
  },
};

function Icon({ name, size = 16, strokeWidth, style, className = '', title }) {
  // Resolve current icon style from nearest ancestor with data-icon-style.
  // Fallback to "lucide" if not set.
  const [iconStyle, setIconStyle] = React.useState('lucide');
  const ref = React.useRef(null);

  React.useEffect(() => {
    if (!ref.current) return;
    const root = ref.current.closest('[data-icon-style]');
    if (root) setIconStyle(root.getAttribute('data-icon-style') || 'lucide');
  });

  const lib = ICON_PATHS[iconStyle] || ICON_PATHS.lucide;
  const d = lib[name] || ICON_PATHS.lucide[name];
  if (!d) return null;

  const paths = d.split(/\s+(?=M)/);
  const sw =
    strokeWidth ??
    (iconStyle === 'tabler' ? 1.6 :
     iconStyle === 'phosphor' ? 1.8 : 2);

  return (
    <svg
      ref={ref}
      className={`ico ${className}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={sw}
      strokeLinecap={iconStyle === 'phosphor' ? 'round' : iconStyle === 'tabler' ? 'round' : 'round'}
      strokeLinejoin="round"
      style={style}
      aria-hidden={!title}
      role={title ? 'img' : undefined}
    >
      {title && <title>{title}</title>}
      {paths.map((p, i) => <path key={i} d={p} />)}
    </svg>
  );
}

window.Icon = Icon;
