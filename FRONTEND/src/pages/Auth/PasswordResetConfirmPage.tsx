import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import {
  confirmPasswordReset,
  confirmPasswordResetErrorMessage,
  PASSWORD_RESET_CONFIRM_SUCCESS,
  PASSWORD_RESET_MISSING_TOKEN,
} from '../../features/api/passwordReset'
import AuthShell from './AuthShell'

export default function PasswordResetConfirmPage() {
  const [searchParams] = useSearchParams()
  const token = (searchParams.get('token') ?? '').trim()
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>(token ? 'loading' : 'error')
  const [message, setMessage] = useState(token ? '' : PASSWORD_RESET_MISSING_TOKEN)

  useEffect(() => {
    if (!token) {
      setStatus('error')
      setMessage(PASSWORD_RESET_MISSING_TOKEN)
      return
    }

    let cancelled = false
    setStatus('loading')
    setMessage('')

    confirmPasswordReset(token)
      .then(() => {
        if (cancelled) return
        setStatus('success')
        setMessage(PASSWORD_RESET_CONFIRM_SUCCESS)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setStatus('error')
        setMessage(confirmPasswordResetErrorMessage(err))
      })

    return () => {
      cancelled = true
    }
  }, [token])

  return (
    <AuthShell title="Reset password" subtitle="Confirm your reset link to restore the default password.">
      <div className="mt-8">
        {status === 'loading' && (
          <div className="flex items-center justify-center gap-3 rounded-2xl bg-[var(--line)] px-4 py-5 text-sm text-gray-600">
            <Loader2 className="h-5 w-5 animate-spin text-gold" />
            Confirming your reset link…
          </div>
        )}
        {status === 'success' && (
          <div className="rounded-2xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{message}</div>
        )}
        {status === 'error' && (
          <div className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-hokeka-critical">{message}</div>
        )}
      </div>

      <div className="mt-6 flex flex-col items-center gap-3 text-sm">
        <Link to="/login" className="font-semibold text-gold transition-colors hover:text-gold-bright">
          Back to sign in
        </Link>
        {status === 'error' && (
          <Link to="/forgot-password" className="text-gray-500 transition-colors hover:text-charcoal">
            Request a new reset link
          </Link>
        )}
      </div>
    </AuthShell>
  )
}
