import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        burgundy: {
          950: '#1A0508',
          900: '#4A0F1C',
          850: '#3D0B16',
          800: '#5A1823',
          700: '#7B2332',
        },
        gold: '#C9A96E',
        charcoal: '#0a0a0a',
        'charcoal-alt': '#111827',
        success: '#22C55E',
        warning: '#F59E0B',
        danger: '#DC2626',
        risk: {
          critical: '#EF4444',
          high: '#F97316',
          medium: '#F59E0B',
          low: '#22C55E',
        },
        glass: 'rgba(22, 8, 12, 0.78)',
        'glass-surface': 'rgba(18, 6, 10, 0.85)',
        'glass-panel': 'rgba(10, 8, 10, 0.92)',
        'glass-border': 'rgba(123, 35, 50, 0.32)',
        'glass-border-hover': 'rgba(220, 38, 38, 0.45)',
        'glass-skeleton': 'rgba(74, 15, 28, 0.38)',
        hokeka: {
          primary:       '#7B2332',
          primaryLight:  '#8D3042',
          wine:          '#5A1823',
          charcoal:      '#0a0a0a',
          gold:          '#C9A96E',
          background:    '#0a0a0a',
          card:          'rgba(18, 6, 10, 0.85)',
          border:        'rgba(123, 35, 50, 0.32)',
          sidebar:       '#1A0508',
          sidebarHover:  '#3D0B16',
          sidebarActive: '#5A1823',
          success:       '#22C55E',
          warning:       '#F59E0B',
          critical:      '#EF4444',
          secondary:     '#7B2332',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        xl: '12px',
        '2xl': '18px',
      },
      boxShadow: {
        glass: '0 8px 32px rgba(0, 0, 0, 0.48), inset 0 1px 0 rgba(220, 38, 38, 0.08), inset 0 0 28px rgba(74, 15, 28, 0.22)',
        'glass-glow': '0 0 24px rgba(220, 38, 38, 0.14), 0 8px 32px rgba(0, 0, 0, 0.42)',
        'nav-active': '0 0 16px rgba(220, 38, 38, 0.25), inset 0 1px 0 rgba(255, 255, 255, 0.06)',
        'neon-red': '0 0 8px rgba(239, 68, 68, 0.6)',
        'neon-green': '0 0 8px rgba(34, 197, 94, 0.65)',
      },
      dropShadow: {
        'neon-red': '0 0 6px rgba(239, 68, 68, 0.75)',
        'neon-gold': '0 0 6px rgba(201, 169, 110, 0.6)',
        'neon-green': '0 0 6px rgba(34, 197, 94, 0.6)',
      },
      backdropBlur: {
        glass: '12px',
      },
    },
  },
  plugins: [require('tailwindcss-animate')],
}

export default config
