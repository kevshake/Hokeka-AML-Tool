import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Loader2, User } from 'lucide-react'
import {
  GENERIC_PASSWORD_RESET_REQUEST_SUCCESS,
  passwordResetRequestErrorMessage,
  requestPasswordReset,
} from '../../features/api/passwordReset'
import AuthShell from './AuthShell'

/** Collects an account identifier and requests a non-enumerating reset email. */
export default function ForgotPasswordPage() {
  const [identifier, setIdentifier] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  /** Submits the identifier while preserving an account-enumeration-safe response. */
  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setSuccess('')

    const trimmed = identifier.trim()
    if (!trimmed) {
      setError('Enter your username or email.')
      return
    }

    setIsLoading(true)
    try {
      await requestPasswordReset(trimmed)
      setSuccess(GENERIC_PASSWORD_RESET_REQUEST_SUCCESS)
    } catch (err: unknown) {
      setError(passwordResetRequestErrorMessage(err))
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthShell
      title="Forgot password"
      subtitle="Enter your username or email. We will send a reset link if an account exists."
    >
      <form onSubmit={handleSubmit} className="mt-8" noValidate>
        {error && (
          <div className="mb-5 rounded-2xl bg-red-50 px-4 py-3 text-sm text-hokeka-critical">{error}</div>
        )}
        {success && (
          <div className="mb-5 rounded-2xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{success}</div>
        )}

        <div>
          <label htmlFor="identifier" className="mb-2 block text-sm font-semibold text-charcoal">
            Username or email
          </label>
          <div className="relative">
            <User className="absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-gray-400" />
            <input
              id="identifier"
              name="identifier"
              type="text"
              autoComplete="username"
              autoFocus
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              placeholder="Enter your username or email"
              className="h-14 w-full rounded-2xl border-2 border-burgundy-700 bg-white pl-12 pr-4 text-sm text-charcoal placeholder:text-gray-400 transition focus:outline-none focus:ring-4 focus:ring-burgundy-700/10"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={isLoading}
          className="login-primary-btn mt-7 flex h-14 w-full items-center justify-center gap-3 rounded-2xl text-base font-semibold text-white disabled:opacity-60"
        >
          {isLoading ? (
            <>
              <Loader2 className="h-5 w-5 animate-spin" />
              Sending reset link…
            </>
          ) : (
            <>
              Send reset link
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-white/15">
                <ArrowRight className="h-4 w-4" />
              </span>
            </>
          )}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-500">
        <Link to="/login" className="font-semibold text-gold transition-colors hover:text-gold-bright">
          Back to sign in
        </Link>
      </p>
    </AuthShell>
  )
}
