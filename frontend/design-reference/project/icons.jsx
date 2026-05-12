// Hand-rolled lucide-style icons. Stroke 1.75, rounded joins, 24x24 viewBox.
// We pull just what we need; keeps the page self-contained.

const Ico = ({ children, size = 20, className = "", strokeWidth = 1.75, ...rest }) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={strokeWidth}
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
    {...rest}
  >
    {children}
  </svg>
);

const I = {
  CircleHelp: (p) => (
    <Ico {...p}>
      <circle cx="12" cy="12" r="10" />
      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
      <path d="M12 17h.01" />
    </Ico>
  ),
  Megaphone: (p) => (
    <Ico {...p}>
      <path d="m3 11 18-5v12L3 14v-3z" />
      <path d="M11.6 16.8a3 3 0 1 1-5.8-1.6" />
    </Ico>
  ),
  MessageSquareQuote: (p) => (
    <Ico {...p}>
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      <path d="M8 12a2 2 0 0 0 2-2V8H7" />
      <path d="M14 12a2 2 0 0 0 2-2V8h-3" />
    </Ico>
  ),
  FileText: (p) => (
    <Ico {...p}>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
      <path d="M8 13h8" />
      <path d="M8 17h6" />
    </Ico>
  ),
  Plus: (p) => <Ico {...p}><path d="M12 5v14" /><path d="M5 12h14" /></Ico>,
  PlusCircle: (p) => <Ico {...p}><circle cx="12" cy="12" r="10" /><path d="M12 8v8" /><path d="M8 12h8" /></Ico>,
  Search: (p) => <Ico {...p}><circle cx="11" cy="11" r="7" /><path d="m21 21-4.35-4.35" /></Ico>,
  ArrowLeft: (p) => <Ico {...p}><path d="m12 19-7-7 7-7" /><path d="M19 12H5" /></Ico>,
  ArrowRight: (p) => <Ico {...p}><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></Ico>,
  X: (p) => <Ico {...p}><path d="M18 6 6 18" /><path d="m6 6 12 12" /></Ico>,
  Check: (p) => <Ico {...p}><path d="M20 6 9 17l-5-5" /></Ico>,
  CheckCircle: (p) => <Ico {...p}><circle cx="12" cy="12" r="10" /><path d="m9 12 2 2 4-4" /></Ico>,
  AlertTriangle: (p) => <Ico {...p}><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3" /><path d="M12 9v4" /><path d="M12 17h.01" /></Ico>,
  AlertCircle: (p) => <Ico {...p}><circle cx="12" cy="12" r="10" /><path d="M12 8v4" /><path d="M12 16h.01" /></Ico>,
  XCircle: (p) => <Ico {...p}><circle cx="12" cy="12" r="10" /><path d="m15 9-6 6" /><path d="m9 9 6 6" /></Ico>,
  Info: (p) => <Ico {...p}><circle cx="12" cy="12" r="10" /><path d="M12 16v-4" /><path d="M12 8h.01" /></Ico>,
  Circle: (p) => <Ico {...p}><circle cx="12" cy="12" r="10" /></Ico>,
  Trash: (p) => <Ico {...p}><path d="M3 6h18" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" /><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /><path d="M10 11v6" /><path d="M14 11v6" /></Ico>,
  Trash2: (p) => <Ico {...p}><path d="M3 6h18" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" /><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /><path d="M10 11v6" /><path d="M14 11v6" /></Ico>,
  Edit: (p) => <Ico {...p}><path d="M12 20h9" /><path d="M16.5 3.5a2.121 2.121 0 1 1 3 3L7 19l-4 1 1-4z" /></Ico>,
  Pencil: (p) => <Ico {...p}><path d="M12 20h9" /><path d="M16.5 3.5a2.121 2.121 0 1 1 3 3L7 19l-4 1 1-4z" /></Ico>,
  LayoutGrid: (p) => <Ico {...p}><rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /></Ico>,
  AlignJustify: (p) => <Ico {...p}><path d="M3 6h18" /><path d="M3 12h18" /><path d="M3 18h18" /></Ico>,
  Library: (p) => <Ico {...p}><path d="M3 3v18" /><path d="M7 3v18" /><rect x="11" y="3" width="4" height="18" /><path d="m18 3 3 18" /></Ico>,
  Eye: (p) => <Ico {...p}><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z" /><circle cx="12" cy="12" r="3" /></Ico>,
  EyeOff: (p) => <Ico {...p}><path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" /><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" /><path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61" /><path d="M2 2l20 20" /></Ico>,
  Link: (p) => <Ico {...p}><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" /><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" /></Ico>,
  Unlink: (p) => <Ico {...p}><path d="m18.84 12.25 1.72-1.71a5 5 0 0 0-7.07-7.07L11.78 5.17"/><path d="m5.16 11.75-1.72 1.71a5 5 0 0 0 7.07 7.07l1.71-1.71"/><path d="M2 2 22 22"/></Ico>,
  ZoomIn: (p) => <Ico {...p}><circle cx="11" cy="11" r="7" /><path d="m21 21-4.35-4.35" /><path d="M11 8v6" /><path d="M8 11h6" /></Ico>,
  ZoomOut: (p) => <Ico {...p}><circle cx="11" cy="11" r="7" /><path d="m21 21-4.35-4.35" /><path d="M8 11h6" /></Ico>,
  Maximize: (p) => <Ico {...p}><path d="M3 3h7v7H3z" /><path d="M14 3h7v7h-7z" /><path d="M14 14h7v7h-7z" /><path d="M3 14h7v7H3z" /></Ico>,
  Map: (p) => <Ico {...p}><path d="M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z" /><path d="M9 3v15" /><path d="M15 6v15" /></Ico>,
  Settings: (p) => <Ico {...p}><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.36.16.66.42.86.74.2.32.31.69.31 1.07v0c0 .38-.11.75-.31 1.07-.2.32-.5.58-.86.74z" /></Ico>,
  ChevronDown: (p) => <Ico {...p}><path d="m6 9 6 6 6-6" /></Ico>,
  ChevronRight: (p) => <Ico {...p}><path d="m9 18 6-6-6-6" /></Ico>,
  ChevronUp: (p) => <Ico {...p}><path d="m18 15-6-6-6 6" /></Ico>,
  ChevronsUpDown: (p) => <Ico {...p}><path d="m7 15 5 5 5-5" /><path d="m7 9 5-5 5 5" /></Ico>,
  MoreHorizontal: (p) => <Ico {...p}><circle cx="12" cy="12" r="1.4" /><circle cx="19" cy="12" r="1.4" /><circle cx="5" cy="12" r="1.4" /></Ico>,
  Layers: (p) => <Ico {...p}><path d="m12 2 10 6-10 6L2 8z" /><path d="m2 14 10 6 10-6" /><path d="m2 11 10 6 10-6" /></Ico>,
  GripVertical: (p) => <Ico {...p}><circle cx="9" cy="6" r="1" /><circle cx="9" cy="12" r="1" /><circle cx="9" cy="18" r="1" /><circle cx="15" cy="6" r="1" /><circle cx="15" cy="12" r="1" /><circle cx="15" cy="18" r="1" /></Ico>,
  History: (p) => <Ico {...p}><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" /><path d="M3 3v5h5" /><path d="M12 7v5l4 2" /></Ico>,
  BookOpen: (p) => <Ico {...p}><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" /><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" /></Ico>,
  Quote: (p) => <Ico {...p}><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h2c0 4-2 4-3 4z" /><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h2c0 4-2 4-3 4z" /></Ico>,
  Users: (p) => <Ico {...p}><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></Ico>,
  User: (p) => <Ico {...p}><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></Ico>,
  Save: (p) => <Ico {...p}><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" /><path d="M17 21v-8H7v8" /><path d="M7 3v5h8" /></Ico>,
  Filter: (p) => <Ico {...p}><path d="M22 3H2l8 9.46V19l4 2v-8.54z" /></Ico>,
  Loader: (p) => <Ico {...p}><path d="M12 2v4" /><path d="m16.24 7.76 2.83-2.83" /><path d="M18 12h4" /><path d="m16.24 16.24 2.83 2.83" /><path d="M12 18v4" /><path d="m4.93 19.07 2.83-2.83" /><path d="M2 12h4" /><path d="m4.93 4.93 2.83 2.83" /></Ico>,
  Sparkles: (p) => <Ico {...p}><path d="m12 3-1.9 5.7a2 2 0 0 1-1.4 1.4L3 12l5.7 1.9a2 2 0 0 1 1.4 1.4L12 21l1.9-5.7a2 2 0 0 1 1.4-1.4L21 12l-5.7-1.9a2 2 0 0 1-1.4-1.4z" /><path d="M5 3v4" /><path d="M19 17v4" /><path d="M3 5h4" /><path d="M17 19h4" /></Ico>,
  Hash: (p) => <Ico {...p}><path d="M4 9h16" /><path d="M4 15h16" /><path d="M10 3 8 21" /><path d="m16 3-2 18" /></Ico>,
  Calendar: (p) => <Ico {...p}><rect x="3" y="4" width="18" height="18" rx="2" /><path d="M16 2v4" /><path d="M8 2v4" /><path d="M3 10h18" /></Ico>,
  Copy: (p) => <Ico {...p}><rect x="9" y="9" width="13" height="13" rx="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" /></Ico>,
  Mouse: (p) => <Ico {...p}><rect x="6" y="3" width="12" height="18" rx="6" /><path d="M12 7v4" /></Ico>,
  Command: (p) => <Ico {...p}><path d="M18 3a3 3 0 0 0-3 3v12a3 3 0 0 0 3 3 3 3 0 0 0 3-3 3 3 0 0 0-3-3H6a3 3 0 0 0-3 3 3 3 0 0 0 3 3 3 3 0 0 0 3-3V6a3 3 0 0 0-3-3 3 3 0 0 0-3 3 3 3 0 0 0 3 3h12a3 3 0 0 0 3-3 3 3 0 0 0-3-3z" /></Ico>,
  CornerDownRight: (p) => <Ico {...p}><path d="m15 10 5 5-5 5" /><path d="M4 4v7a4 4 0 0 0 4 4h12" /></Ico>,
  Move: (p) => <Ico {...p}><path d="M5 9 2 12l3 3" /><path d="M9 5l3-3 3 3" /><path d="M15 19l-3 3-3-3" /><path d="M19 9l3 3-3 3" /><path d="M2 12h20" /><path d="M12 2v20" /></Ico>,
  Lasso: (p) => <Ico {...p}><path d="M7 22a5 5 0 0 1-2-4" /><path d="M7 16.93c.96.43 1.96.74 2.99.91" /><path d="M3.34 14A6.8 6.8 0 0 1 2 10c0-4.42 4.48-8 10-8s10 3.58 10 8a7.19 7.19 0 0 1-.33 2" /><path d="M5 18a2 2 0 1 0 0-4 2 2 0 0 0 0 4z" /><path d="M14.33 22h-.09a.35.35 0 0 1-.24-.32v-1.7c0-.18.16-.35.34-.34.05 0 .1 0 .15.04.5.27 1.07.4 1.6.4.97 0 1.91-.4 2.6-1.1.7-.7 1.1-1.6 1.1-2.5 0-.9-.4-1.8-1.1-2.5-.7-.7-1.6-1.1-2.6-1.1-.97 0-1.91.4-2.6 1.1-.7.7-1.1 1.6-1.1 2.5 0 .9.4 1.8 1.1 2.5.05.05.1.1.16.14a.35.35 0 0 1-.16.71h-1.4c-.18 0-.34-.16-.34-.34" /></Ico>,
  Tag: (p) => <Ico {...p}><path d="M20.59 13.41 13.41 20.6a2 2 0 0 1-2.82 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" /><path d="M7 7h.01" /></Ico>,
  ListTree: (p) => <Ico {...p}><path d="M21 12h-8" /><path d="M21 6H8" /><path d="M21 18h-8" /><path d="M3 6v4c0 1.1.9 2 2 2h3" /><path d="M3 10v6c0 1.1.9 2 2 2h3" /></Ico>,
  Network: (p) => <Ico {...p}><rect x="16" y="16" width="6" height="6" rx="1" /><rect x="2" y="16" width="6" height="6" rx="1" /><rect x="9" y="2" width="6" height="6" rx="1" /><path d="M5 16v-3a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v3" /><path d="M12 12V8" /></Ico>,
  Diamond: (p) => <Ico {...p}><path d="M2.7 10.3a2.41 2.41 0 0 0 0 3.41l7.59 7.59a2.41 2.41 0 0 0 3.41 0l7.59-7.59a2.41 2.41 0 0 0 0-3.41L13.7 2.71a2.41 2.41 0 0 0-3.41 0z" /></Ico>,
  Slash: (p) => <Ico {...p}><circle cx="12" cy="12" r="10" /><path d="m4.93 4.93 14.14 14.14" /></Ico>,
  Crosshair: (p) => <Ico {...p}><circle cx="12" cy="12" r="10" /><path d="M22 12h-4" /><path d="M6 12H2" /><path d="M12 6V2" /><path d="M12 22v-4" /></Ico>,
  Pin: (p) => <Ico {...p}><path d="M12 17v5" /><path d="M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z" /></Ico>,
  Refresh: (p) => <Ico {...p}><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" /><path d="M21 3v5h-5" /><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" /><path d="M3 21v-5h5" /></Ico>,
  Lock: (p) => <Ico {...p}><rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></Ico>,
  Mail: (p) => <Ico {...p}><rect x="2" y="4" width="20" height="16" rx="2" /><path d="m22 7-10 6L2 7" /></Ico>,
  MousePointer2: (p) => <Ico {...p}><path d="m4 4 7.07 17 2.51-7.39L21 11.07z" /></Ico>,
  Boxes: (p) => <Ico {...p}><path d="M2.97 12.92A2 2 0 0 0 2 14.63v3.24a2 2 0 0 0 .97 1.71l3 1.8a2 2 0 0 0 2.06 0L12 19v-5.5l-5-3z" /><path d="m7 16.5-4.74-2.85" /><path d="m7 16.5 5-3" /><path d="M7 16.5v5.17" /><path d="M12 13.5V19l3.97 2.38a2 2 0 0 0 2.06 0l3-1.8a2 2 0 0 0 .97-1.71v-3.24a2 2 0 0 0-.97-1.71L17 10.5l-5 3z" /></Ico>,
  ShieldCheck: (p) => <Ico {...p}><path d="M20 13c0 5-3.5 7.5-8 9-4.5-1.5-8-4-8-9V5l8-3 8 3z" /><path d="m9 12 2 2 4-4" /></Ico>,
  GitBranch: (p) => <Ico {...p}><line x1="6" y1="3" x2="6" y2="15" /><circle cx="18" cy="6" r="3" /><circle cx="6" cy="18" r="3" /><path d="M18 9a9 9 0 0 1-9 9" /></Ico>,
  GitCompareArrows: (p) => <Ico {...p}><circle cx="5" cy="6" r="3" /><circle cx="19" cy="18" r="3" /><path d="M12 6h5a2 2 0 0 1 2 2v7" /><path d="m15 9-3-3 3-3" /><path d="M12 18H7a2 2 0 0 1-2-2V9" /><path d="m9 15 3 3-3 3" /></Ico>,
  Download: (p) => <Ico {...p}><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" /></Ico>,
  ScrollText: (p) => <Ico {...p}><path d="M15 12h-5"/><path d="M15 8h-5"/><path d="M19 17V5a2 2 0 0 0-2-2H4"/><path d="M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3"/></Ico>,
  Star: (p) => <Ico {...p}><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" /></Ico>,
  Sidebar: (p) => <Ico {...p}><rect x="3" y="3" width="18" height="18" rx="2" /><line x1="9" y1="3" x2="9" y2="21" /></Ico>,
  MoreVertical: (p) => <Ico {...p}><circle cx="12" cy="5" r="1" /><circle cx="12" cy="12" r="1" /><circle cx="12" cy="19" r="1" /></Ico>,
  ArrowDown: (p) => <Ico {...p}><line x1="12" y1="5" x2="12" y2="19" /><polyline points="19 12 12 19 5 12" /></Ico>,
  ArrowUp: (p) => <Ico {...p}><line x1="12" y1="19" x2="12" y2="5" /><polyline points="5 12 12 5 19 12" /></Ico>,
  Share2: (p) => <Ico {...p}><circle cx="18" cy="5" r="3" /><circle cx="6" cy="12" r="3" /><circle cx="18" cy="19" r="3" /><line x1="8.59" y1="13.51" x2="15.42" y2="17.49" /><line x1="15.41" y1="6.51" x2="8.59" y2="10.49" /></Ico>,
  Upload: (p) => <Ico {...p}><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" /></Ico>,
  MessageSquare: (p) => <Ico {...p}><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></Ico>,
  LogOut: (p) => <Ico {...p}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" /></Ico>,
  ExternalLink: (p) => <Ico {...p}><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /><polyline points="15 3 21 3 21 9" /><line x1="10" y1="14" x2="21" y2="3" /></Ico>,
  GraduationCap: (p) => <Ico {...p}><path d="M22 10v6" /><path d="M2 10l10-5 10 5-10 5z" /><path d="M6 12v5c0 2 3 3 6 3s6-1 6-3v-5" /></Ico>,
  Languages: (p) => <Ico {...p}><path d="m5 8 6 6" /><path d="m4 14 6-6 2-3" /><path d="M2 5h12" /><path d="M7 2h1" /><path d="m22 22-5-10-5 10" /><path d="M14 18h6" /></Ico>,
  BookText: (p) => <Ico {...p}><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" /><path d="M9 7h6" /><path d="M9 11h4" /></Ico>,
};

window.I = I;
