import type { ReactNode } from 'react'
import {
  Building2,
  ChevronDown,
  FileSearch,
  Globe2,
  Shield,
  ShieldCheck,
  UserSearch,
} from 'lucide-react'
import HokekaLogo, { HOKEKA_LOGO_SRC } from '../../components/branding/HokekaLogo'

const HERO_FEATURES = [
  {
    icon: ShieldCheck,
    tone: 'gold' as const,
    title: 'AI Risk Detection',
    description: 'Identify suspicious behaviour in real time',
  },
  {
    icon: FileSearch,
    tone: 'rose' as const,
    title: 'Case Management',
    description: 'Investigate and resolve alerts efficiently',
  },
  {
    icon: Building2,
    tone: 'gold' as const,
    title: 'Regulatory Compliance',
    description: 'Stay aligned with AML and KYC requirements',
  },
  {
    icon: UserSearch,
    tone: 'rose' as const,
    title: 'Screening Intelligence',
    description: 'Sanctions, PEP and adverse media monitoring',
  },
] as const

/** Renders the shared security-product hero for desktop or compact mobile layouts. */
function LoginHeroPanel({ mobile = false }: { mobile?: boolean }) {
  return (
    <aside
      className={
        mobile
          ? 'login-hero-panel login-hero-panel--mobile flex lg:hidden'
          : 'login-hero-panel relative hidden shrink-0 lg:flex lg:h-screen lg:w-[42%]'
      }
      aria-hidden={mobile ? true : undefined}
    >
      <div className="login-hero-panel__glow" aria-hidden="true" />
      <div className="login-hero-panel__map" aria-hidden="true" />
      <div className="login-hero-panel__circuits" aria-hidden="true" />

      <div className="login-hero-panel__inner">
        <div className="login-hero-panel__brand">
          <img
            src={HOKEKA_LOGO_SRC}
            alt=""
            width={44}
            height={44}
            className="login-hero-panel__brand-logo"
            draggable={false}
          />
          <div className="login-hero-panel__brand-text">
            <span className="login-hero-panel__brand-name">HOKEKA</span>
            <span className="login-hero-panel__brand-tag">AML INTELLIGENCE</span>
          </div>
        </div>

        <div className="login-hero-panel__copy">
          <h2 className="login-hero-panel__headline">
            Protecting Financial Systems Through Intelligence
          </h2>
          {!mobile && (
            <p className="login-hero-panel__subtext">
              Monitor transactions, investigate alerts, manage risk and maintain compliance from a
              single platform.
            </p>
          )}
        </div>

        {!mobile && (
          <>
            <div className="login-hero-panel__divider" aria-hidden="true" />

            <ul className="login-hero-panel__features">
              {HERO_FEATURES.map(({ icon: Icon, tone, title, description }) => (
                <li key={title} className="login-hero-panel__feature">
                  <span className={`login-hero-panel__feature-icon login-hero-panel__feature-icon--${tone}`}>
                    <Icon size={18} strokeWidth={1.75} aria-hidden="true" />
                  </span>
                  <span className="login-hero-panel__feature-copy">
                    <span className="login-hero-panel__feature-title">{title}</span>
                    <span className="login-hero-panel__feature-desc">{description}</span>
                  </span>
                </li>
              ))}
            </ul>

            <div className="login-hero-panel__graphic">
              <img
                src="/images/login-hero-shield.png"
                alt=""
                className="login-hero-panel__shield"
                draggable={false}
              />
            </div>
          </>
        )}
      </div>
    </aside>
  )
}

/** Wraps public authentication forms in the common branded, responsive shell. */
export default function AuthShell({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle: string
  children: ReactNode
}) {
  return (
    <div className="flex min-h-screen flex-col lg:flex-row">
      <LoginHeroPanel />
      <LoginHeroPanel mobile />

      <div className="relative flex min-h-screen min-w-0 flex-1 flex-col bg-[var(--ink)]">
        <button
          type="button"
          className="absolute right-6 top-6 z-10 flex items-center gap-2 rounded-xl border border-[var(--line)] bg-white px-4 py-2.5 text-sm text-charcoal shadow-sm transition-colors hover:bg-gray-50"
        >
          <Globe2 className="h-4 w-4 text-gray-500" />
          <span>English (US)</span>
          <ChevronDown className="h-4 w-4 text-gray-400" />
        </button>

        <div className="flex flex-1 items-center justify-center px-6 py-24 sm:px-10">
          <div className="login-card-shadow w-full max-w-[440px] rounded-3xl border border-[var(--line)] bg-white px-8 py-10 sm:px-10 sm:py-12">
            <HokekaLogo size="card" showWordmark={false} className="mb-6" />

            <h1 className="text-center text-[1.75rem] font-bold tracking-tight text-charcoal">
              {title}
            </h1>
            <p className="mt-2 text-center text-sm text-gray-500">{subtitle}</p>

            {children}
          </div>
        </div>

        <footer className="pb-8 pt-4">
          <div className="flex items-center justify-center gap-2 text-sm text-gray-500">
            <Shield className="h-4 w-4 text-gold" strokeWidth={1.75} />
            <span className="font-medium text-gray-600">Secure</span>
            <span className="text-gray-300">•</span>
            <span className="font-medium text-gray-600">Compliant</span>
            <span className="text-gray-300">•</span>
            <span className="font-medium text-gray-600">Trusted</span>
          </div>
          <p className="mt-2 text-center text-xs text-gray-400">
            © 2026 Hokeka. All rights reserved.
          </p>
        </footer>
      </div>
    </div>
  )
}
