import type { Config } from 'tailwindcss'

/**
 * Colour keys are intentionally kept stable (burgundy-*, gold, glass-*,
 * hokeka.*) so existing markup keeps compiling — but every one of them now
 * resolves through the Hokeka design tokens in `src/theme/tokens.ts`
 * (emitted as CSS variables by `src/theme/globals.css` + ThemeContext).
 *
 * `burgundy-950 … burgundy-700` is the dark elevation ramp (deepest → raised);
 * `burgundy-600 … burgundy-200` is the hairline/ink ramp. That mapping keeps
 * the ~200 legacy class usages meaningful without touching the pages.
 *
 * Tailwind's stock `red / emerald / amber / sky / …` ramps are re-anchored on
 * the Hokeka signal hues so ~250 existing utility usages stop shipping
 * off-brand neon and land inside the editorial palette instead.
 */

const NEAR_BLACK = '#080909'

function blend(a: string, b: string, t: number): string {
  const parse = (h: string) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16))
  const [ar, ag, ab] = parse(a)
  const [br, bg, bb] = parse(b)
  const ch = (x: number, y: number) =>
    Math.round(x + (y - x) * t)
      .toString(16)
      .padStart(2, '0')
  return `#${ch(ar, br)}${ch(ag, bg)}${ch(ab, bb)}`
}

/** 50 → 950 ramp anchored on `base` at the 400 step (the on-dark text weight). */
function ramp(base: string): Record<string, string> {
  const light = (t: number) => blend(base, '#ffffff', t)
  const dark = (t: number) => blend(base, NEAR_BLACK, t)
  return {
    50: light(0.9),
    100: light(0.8),
    200: light(0.62),
    300: light(0.3),
    400: base,
    500: dark(0.14),
    600: dark(0.32),
    700: dark(0.5),
    800: dark(0.66),
    900: dark(0.8),
    950: dark(0.9),
  }
}

const CORAL = ramp('#e8776b') // danger / critical
const TEAL = ramp('#75b7ab') // positive / cleared
const AMBER = ramp('#e2b25d') // caution
const STEEL = ramp('#84a9c4') // neutral information
const RUST = ramp('#e08a4f') // elevated risk
const PLUM = ramp('#b094c2') // rare categorical accent
const STONE = ramp('#a8aba8') // neutral text / chrome

const config: Config = {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        /* Shadcn-compatible aliases, mapped onto the Hokeka tokens. */
        border: 'rgba(255, 255, 255, 0.12)',
        input: 'rgba(255, 255, 255, 0.34)',
        ring: 'rgb(var(--brand-accent-rgb, 211 179 113) / <alpha-value>)',
        background: 'rgb(var(--surface-0-rgb, 8 9 9) / <alpha-value>)',
        foreground: 'rgb(var(--ink-rgb, 245 242 235) / <alpha-value>)',
        primary: {
          DEFAULT: 'rgb(var(--brand-accent-rgb, 211 179 113) / <alpha-value>)',
          foreground: 'rgb(var(--surface-0-rgb, 8 9 9) / <alpha-value>)',
        },
        secondary: {
          DEFAULT: 'rgb(var(--brand-secondary-rgb, 117 183 171) / <alpha-value>)',
          foreground: 'rgb(var(--surface-0-rgb, 8 9 9) / <alpha-value>)',
        },
        destructive: {
          DEFAULT: 'rgb(var(--danger-rgb, 232 119 107) / <alpha-value>)',
          foreground: 'rgb(var(--surface-0-rgb, 8 9 9) / <alpha-value>)',
        },
        popover: {
          DEFAULT: 'rgb(var(--surface-2-rgb, 18 21 20) / <alpha-value>)',
          foreground: 'rgb(var(--ink-rgb, 245 242 235) / <alpha-value>)',
        },
        card: {
          DEFAULT: 'rgb(var(--surface-2-rgb, 18 21 20) / <alpha-value>)',
          foreground: 'rgb(var(--ink-rgb, 245 242 235) / <alpha-value>)',
        },

        /* Elevation ramp (950 deepest → 700 raised) + ink ramp (600 → 200). */
        burgundy: {
          950: 'rgb(var(--surface-0-rgb, 8 9 9) / <alpha-value>)',
          900: 'rgb(var(--surface-1-rgb, 12 14 13) / <alpha-value>)',
          850: 'rgb(var(--surface-2-rgb, 18 21 20) / <alpha-value>)',
          800: 'rgb(var(--surface-3-rgb, 25 28 27) / <alpha-value>)',
          700: 'rgb(var(--surface-4-rgb, 35 38 36) / <alpha-value>)',
          600: 'rgba(255, 255, 255, 0.22)',
          500: 'rgb(var(--muted-2-rgb, 133 137 134) / <alpha-value>)',
          400: 'rgb(var(--muted-rgb, 168 171 168) / <alpha-value>)',
          300: 'rgb(var(--brand-accent-rgb, 211 179 113) / <alpha-value>)',
          200: 'rgb(var(--ink-rgb, 245 242 235) / <alpha-value>)',
        },

        /* Stock ramps, re-anchored on the Hokeka signal hues. */
        red: CORAL,
        rose: CORAL,
        emerald: TEAL,
        green: TEAL,
        amber: AMBER,
        yellow: AMBER,
        orange: RUST,
        sky: STEEL,
        blue: STEEL,
        cyan: STEEL,
        indigo: STEEL,
        purple: PLUM,
        violet: PLUM,
        gray: STONE,
        slate: STONE,
        zinc: STONE,
        neutral: STONE,
        stone: STONE,

        /* Brand */
        gold: 'rgb(var(--brand-accent-rgb, 211 179 113) / <alpha-value>)',
        'gold-bright': '#e1c684',
        'gold-deep': '#74511e',
        teal: { ...TEAL, DEFAULT: 'rgb(var(--brand-secondary-rgb, 117 183 171) / <alpha-value>)' },
        ink: 'rgb(var(--ink-rgb, 245 242 235) / <alpha-value>)',
        'ink-muted': 'rgb(var(--muted-rgb, 168 171 168) / <alpha-value>)',
        'ink-subtle': 'rgb(var(--muted-2-rgb, 133 137 134) / <alpha-value>)',
        hairline: 'rgba(255, 255, 255, 0.12)',
        'hairline-strong': 'rgba(255, 255, 255, 0.22)',

        charcoal: 'rgb(var(--surface-0-rgb, 8 9 9) / <alpha-value>)',
        'charcoal-alt': 'rgb(var(--surface-1-rgb, 12 14 13) / <alpha-value>)',

        /* Semantic */
        success: 'rgb(var(--success-rgb, 117 183 171) / <alpha-value>)',
        warning: 'rgb(var(--warning-rgb, 226 178 93) / <alpha-value>)',
        danger: 'rgb(var(--danger-rgb, 232 119 107) / <alpha-value>)',
        info: 'rgb(var(--info-rgb, 132 169 196) / <alpha-value>)',
        'success-soft': 'var(--success-soft)',
        'warning-soft': 'var(--warning-soft)',
        'danger-soft': 'var(--danger-soft)',
        'info-soft': 'var(--info-soft)',
        'neutral-soft': 'var(--neutral-soft)',
        risk: {
          critical: 'rgb(var(--risk-critical-rgb, 232 119 107) / <alpha-value>)',
          high: 'rgb(var(--risk-high-rgb, 224 138 79) / <alpha-value>)',
          medium: 'rgb(var(--risk-medium-rgb, 226 178 93) / <alpha-value>)',
          low: 'rgb(var(--risk-low-rgb, 117 183 171) / <alpha-value>)',
          unknown: 'var(--risk-unknown)',
          /* Opaque badge grounds — see SOFT_TINT in src/theme/tokens.ts. */
          'critical-soft': 'var(--risk-critical-soft)',
          'high-soft': 'var(--risk-high-soft)',
          'medium-soft': 'var(--risk-medium-soft)',
          'low-soft': 'var(--risk-low-soft)',
          'unknown-soft': 'var(--risk-unknown-soft)',
        },

        /* Panels */
        glass: 'rgb(var(--surface-1-rgb, 12 14 13) / 0.86)',
        'glass-surface': 'rgb(var(--surface-2-rgb, 18 21 20) / 0.9)',
        'glass-panel': 'rgb(var(--surface-1-rgb, 12 14 13) / 0.94)',
        'glass-border': 'rgba(255, 255, 255, 0.12)',
        'glass-border-hover': 'rgba(255, 255, 255, 0.28)',
        'glass-skeleton': 'rgb(var(--surface-4-rgb, 35 38 36) / 0.7)',

        hokeka: {
          primary: 'rgb(var(--brand-accent-rgb, 211 179 113) / <alpha-value>)',
          primaryLight: '#e1c684',
          wine: 'rgb(var(--surface-3-rgb, 25 28 27) / <alpha-value>)',
          charcoal: 'rgb(var(--surface-0-rgb, 8 9 9) / <alpha-value>)',
          gold: 'rgb(var(--brand-accent-rgb, 211 179 113) / <alpha-value>)',
          background: 'rgb(var(--surface-0-rgb, 8 9 9) / <alpha-value>)',
          card: 'rgb(var(--surface-2-rgb, 18 21 20) / 0.9)',
          border: 'rgba(255, 255, 255, 0.12)',
          sidebar: 'rgb(var(--surface-1-rgb, 12 14 13) / <alpha-value>)',
          sidebarHover: 'rgb(var(--surface-3-rgb, 25 28 27) / <alpha-value>)',
          sidebarActive: 'rgb(var(--surface-4-rgb, 35 38 36) / <alpha-value>)',
          success: 'rgb(var(--success-rgb, 117 183 171) / <alpha-value>)',
          warning: 'rgb(var(--warning-rgb, 226 178 93) / <alpha-value>)',
          critical: 'rgb(var(--danger-rgb, 232 119 107) / <alpha-value>)',
          secondary: 'rgb(var(--brand-secondary-rgb, 117 183 171) / <alpha-value>)',
        },
      },
      fontFamily: {
        sans: ['DM Sans', 'Inter', 'system-ui', 'sans-serif'],
        display: ['Manrope', 'Inter', 'system-ui', 'sans-serif'],
      },
      /* Editorial 3px shape language (the marketing site's --radius). */
      borderRadius: {
        none: '0px',
        sm: '2px',
        DEFAULT: '3px',
        md: '3px',
        lg: '4px',
        xl: '5px',
        '2xl': '6px',
        '3xl': '8px',
        full: '9999px',
      },
      letterSpacing: {
        eyebrow: '0.16em',
      },
      transitionTimingFunction: {
        editorial: 'cubic-bezier(.22, 1, .36, 1)',
      },
      boxShadow: {
        glass: '0 10px 30px -18px rgba(0, 0, 0, 0.9)',
        'glass-glow': '0 0 0 1px rgb(var(--brand-accent-rgb, 211 179 113) / 0.18), 0 26px 50px -30px rgba(0, 0, 0, 0.75)',
        'nav-active': 'inset 2px 0 0 0 rgb(var(--brand-accent-rgb, 211 179 113)), 0 8px 22px -16px rgba(0, 0, 0, 0.9)',
        'neon-red': '0 0 0 1px rgb(var(--danger-rgb, 232 119 107) / 0.35)',
        'neon-green': '0 0 0 1px rgb(var(--success-rgb, 117 183 171) / 0.35)',
        cta: '0 10px 26px -16px rgb(var(--brand-accent-rgb, 211 179 113) / 0.65)',
        'cta-hover': '0 14px 34px -12px rgb(var(--brand-accent-rgb, 211 179 113) / 0.6)',
        editorial: '0 26px 50px -30px rgba(0, 0, 0, 0.75)',
      },
      dropShadow: {
        'neon-red': '0 0 6px rgb(var(--danger-rgb, 232 119 107) / 0.5)',
        'neon-gold': '0 0 6px rgb(var(--brand-accent-rgb, 211 179 113) / 0.5)',
        'neon-green': '0 0 6px rgb(var(--success-rgb, 117 183 171) / 0.5)',
      },
      backdropBlur: {
        glass: '14px',
      },
    },
  },
  plugins: [require('tailwindcss-animate')],
}

export default config
