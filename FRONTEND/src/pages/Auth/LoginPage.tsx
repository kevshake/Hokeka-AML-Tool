import { useState, type FormEvent } from 'react'
import {
  ArrowRight,
  Check,
  ChevronDown,
  Eye,
  EyeOff,
  Globe2,
  Loader2,
  Lock,
  Shield,
  User,
  Users,
} from 'lucide-react'
import HokekaLogo, { HOKEKA_LOGO_SRC } from '../../components/branding/HokekaLogo'
import { useAuth } from '../../contexts/AuthContext'

export default function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setIsLoading(true)
    try {
      await login(username, password)
    } catch (err: any) {
      setError(err?.message || 'Invalid username or password')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col lg:flex-row">
      {/* LEFT — Hero image sized by aspect ratio at full viewport height */}
      <div
        className="login-hero-panel relative hidden shrink-0 lg:block lg:h-screen lg:w-auto"
        aria-hidden="true"
      >
        <img
          src="/images/login-hero-panel.png"
          alt=""
          className="login-hero-panel__img"
          draggable={false}
        />
      </div>

      {/* Mobile hero strip — cropped top of full panel */}
      <div className="login-hero-panel login-hero-panel--mobile relative overflow-hidden lg:hidden">
        <img
          src="/images/login-hero-panel.png"
          alt=""
          className="login-hero-panel__img login-hero-panel__img--mobile"
          draggable={false}
        />
      </div>

      {/* RIGHT — Login Form (fills remaining width) */}
      <div className="relative flex min-h-screen min-w-0 flex-1 flex-col bg-[#F8F9FA]">
        <button
          type="button"
          className="absolute right-6 top-6 z-10 flex items-center gap-2 rounded-xl border border-[#E5E7EB] bg-white px-4 py-2.5 text-sm text-charcoal shadow-sm transition-colors hover:bg-gray-50"
        >
          <Globe2 className="h-4 w-4 text-gray-500" />
          <span>English (US)</span>
          <ChevronDown className="h-4 w-4 text-gray-400" />
        </button>

        <div className="flex flex-1 items-center justify-center px-6 py-24 sm:px-10">
          <div className="login-card-shadow w-full max-w-[440px] rounded-3xl border border-[#ECEEF0] bg-white px-8 py-10 sm:px-10 sm:py-12">
            <HokekaLogo size="card" showWordmark={false} className="mb-6" />

            <h1 className="text-center text-[1.75rem] font-bold tracking-tight text-charcoal">
              Welcome back
            </h1>
            <p className="mt-2 text-center text-sm text-gray-500">
              Sign in to access your Hokeka dashboard
            </p>

            <form onSubmit={handleSubmit} className="mt-8">
              {error && (
                <div className="mb-5 rounded-2xl bg-red-50 px-4 py-3 text-sm text-hokeka-critical">
                  {error}
                </div>
              )}

              <div>
                <label htmlFor="username" className="mb-2 block text-sm font-semibold text-charcoal">
                  Username
                </label>
                <div className="relative">
                  <User className="absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-gray-400" />
                  <input
                    id="username"
                    type="text"
                    autoComplete="username"
                    required
                    autoFocus
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="Enter your username"
                    className="h-14 w-full rounded-2xl border-2 border-burgundy-700 bg-white pl-12 pr-4 text-sm text-charcoal placeholder:text-gray-400 transition focus:outline-none focus:ring-4 focus:ring-burgundy-700/10"
                  />
                </div>
              </div>

              <div className="mt-5">
                <label htmlFor="password" className="mb-2 block text-sm font-semibold text-charcoal">
                  Password
                </label>
                <div className="relative">
                  <Lock className="absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-gray-400" />
                  <input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your password"
                    className="h-14 w-full rounded-2xl border border-[#E5E7EB] bg-white pl-12 pr-12 text-sm text-charcoal placeholder:text-gray-400 transition focus:border-burgundy-700 focus:outline-none focus:ring-4 focus:ring-burgundy-700/10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-2 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-xl text-gray-500 transition-colors hover:bg-gray-100"
                  >
                    {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
              </div>

              <div className="mt-5 flex items-center justify-between">
                <label className="flex cursor-pointer select-none items-center gap-2.5 text-sm text-charcoal">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                    className="peer sr-only"
                  />
                  <span
                    className={`flex h-[18px] w-[18px] items-center justify-center rounded border-2 border-burgundy-700 transition-colors ${
                      rememberMe ? 'bg-burgundy-700' : 'bg-white'
                    }`}
                  >
                    {rememberMe && <Check className="h-3 w-3 text-white" strokeWidth={3} />}
                  </span>
                  Remember me
                </label>
                <a
                  href="#"
                  className="text-sm font-semibold text-burgundy-700 transition-colors hover:text-burgundy-800"
                >
                  Forgot password?
                </a>
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className="login-primary-btn mt-7 flex h-14 w-full items-center justify-center gap-3 rounded-2xl text-base font-semibold text-white disabled:opacity-60"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="h-5 w-5 animate-spin" />
                    Signing in…
                  </>
                ) : (
                  <>
                    Sign in
                    <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-white/15">
                      <ArrowRight className="h-4 w-4" />
                    </span>
                  </>
                )}
              </button>
            </form>

            <div className="relative mt-8 flex items-center">
              <div className="h-px flex-1 bg-[#E5E7EB]" />
              <div className="mx-4 flex h-9 w-9 items-center justify-center">
                <img
                  src={HOKEKA_LOGO_SRC}
                  alt=""
                  width={28}
                  height={28}
                  className="object-contain opacity-80"
                  draggable={false}
                  aria-hidden="true"
                />
              </div>
              <div className="h-px flex-1 bg-[#E5E7EB]" />
            </div>

            <div className="mt-6 flex items-start gap-3 rounded-2xl border border-gold/15 bg-[#FDF6EE] px-4 py-4">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white ring-1 ring-gold/25">
                <Users className="h-4 w-4 text-gold" />
              </div>
              <p className="text-sm leading-relaxed text-gray-600">
                <span className="font-semibold text-charcoal">
                  Hokeka access is restricted to authorized users.
                </span>{' '}
                Need access?{' '}
                <a
                  href="#"
                  className="font-semibold text-burgundy-700 hover:underline"
                >
                  Contact your system administrator.
                </a>
              </p>
            </div>
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
