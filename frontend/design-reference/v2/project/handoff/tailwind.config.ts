import type { Config } from 'tailwindcss';

// Merge this into your existing tailwind config.
// The key parts to copy: `theme.extend.colors`, `fontFamily`, `fontSize`,
// `borderRadius`, `boxShadow`, and `content`.
//
// `content` should already include your project's src/** glob; add it here
// only if you're starting fresh.

const config: Config = {
  content: ['./src/**/*.{ts,tsx,js,jsx,html}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        // Semantic — prefer these in components.
        bg:       'var(--c-bg)',
        elevated: 'var(--c-bg-elevated)',
        sunken:   'var(--c-bg-sunken)',
        paper:    'var(--c-paper)',
        border:   'var(--c-border)',
        'border-strong': 'var(--c-border-strong)',

        // Ink scale.
        ink: {
          0:   'var(--c-ink-0)',
          50:  'var(--c-ink-50)',
          100: 'var(--c-ink-100)',
          150: 'var(--c-ink-150)',
          200: 'var(--c-ink-200)',
          300: 'var(--c-ink-300)',
          400: 'var(--c-ink-400)',
          500: 'var(--c-ink-500)',
          600: 'var(--c-ink-600)',
          700: 'var(--c-ink-700)',
          800: 'var(--c-ink-800)',
          900: 'var(--c-ink-900)',
        },

        // Accent — single source of truth.
        accent: {
          50:  'var(--c-accent-50)',
          100: 'var(--c-accent-100)',
          500: 'var(--c-accent-500)',
          600: 'var(--c-accent-600)',
          700: 'var(--c-accent-700)',
        },

        // Status.
        ok:   { 100: 'var(--c-ok-100)',   500: 'var(--c-ok-500)',   700: 'var(--c-ok-700)' },
        warn: { 100: 'var(--c-warn-100)', 500: 'var(--c-warn-500)', 700: 'var(--c-warn-700)' },
        err:  { 100: 'var(--c-err-100)',  500: 'var(--c-err-500)',  700: 'var(--c-err-700)' },

        // Graph-specific node-type tokens.
        'type-abstract':  { bg: 'var(--c-type-abstract-bg)',  fg: 'var(--c-type-abstract-fg)' },
        'type-empirical': { bg: 'var(--c-type-empirical-bg)', fg: 'var(--c-type-empirical-fg)' },

        // Graph-specific edge tokens.
        edge: {
          supports:    'var(--c-edge-supports)',
          'supports-bg':    'var(--c-edge-supports-bg)',
          refutes:     'var(--c-edge-refutes)',
          'refutes-bg':     'var(--c-edge-refutes-bg)',
          qualifies:   'var(--c-edge-qualifies)',
          'qualifies-bg':   'var(--c-edge-qualifies-bg)',
          responds:    'var(--c-edge-responds)',
          'responds-bg':    'var(--c-edge-responds-bg)',
        },
      },
      fontFamily: {
        ui:     ['var(--font-ui)',     'system-ui', 'sans-serif'],
        serif:  ['var(--font-serif)',  'Georgia', 'serif'],
        mono:   ['var(--font-mono)',   'ui-monospace', 'monospace'],
        arabic: ['var(--font-arabic)', 'serif'],
      },
      fontSize: {
        xs:   ['var(--t-xs)',   { lineHeight: '1.4' }],
        sm:   ['var(--t-sm)',   { lineHeight: '1.45' }],
        base: ['var(--t-base)', { lineHeight: '1.55' }],
        md:   ['var(--t-md)',   { lineHeight: '1.55' }],
        lg:   ['var(--t-lg)',   { lineHeight: '1.3' }],
        xl:   ['var(--t-xl)',   { lineHeight: '1.2' }],
      },
      spacing: {
        // Override Tailwind defaults to a curated subset.
        // Use these and only these.
        '1':  '4px',
        '2':  '8px',
        '3':  '12px',
        '4':  '16px',
        '5':  '20px',
        '6':  '24px',
        '10': '40px',
        '16': '64px',
      },
      borderRadius: {
        sm: 'var(--r-sm)', // 4
        DEFAULT: 'var(--r-sm)',
        md: 'var(--r-md)', // 8
        lg: 'var(--r-lg)', // 12
      },
      boxShadow: {
        sh1: 'var(--sh-1)',
        sh2: 'var(--sh-2)',
        sh3: 'var(--sh-3)',
        sh4: 'var(--sh-4)',
      },
    },
  },
};

export default config;
