import { ExternalLink, Share2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import { operatorDocUrl } from '../../lib/operatorDocs'
import { isPlatformAdmin } from '../../lib/userAccess'
import type { GraphAnalysisStatus } from '../../features/api/queries'
import { graphAnalysisDisabledCopy } from './graphAnalysisDisabledCopy'

interface NetworkGraphAnalysisStateProps {
  status: GraphAnalysisStatus
  variant?: 'disabled' | 'unavailable'
}

export function NetworkGraphAnalysisDisabledState({
  status,
  variant = 'disabled',
}: NetworkGraphAnalysisStateProps) {
  const { user } = useAuth()
  const operator = isPlatformAdmin(user)
  const controlPlaneDoc = operator ? operatorDocUrl('controlPlane') : null

  const { title, body } = graphAnalysisDisabledCopy(status, operator, variant)

  return (
    <div className="flex min-h-[420px] flex-col items-center justify-center rounded-2xl border border-glass-border bg-glass-panel/60 px-8 py-12 text-center shadow-[inset_0_1px_0_rgba(255,255,255,0.04)]">
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full border border-glass-border bg-burgundy-850/80 text-gold">
        <Share2 size={26} aria-hidden />
      </div>
      <h2 className="text-lg font-semibold text-ink">{title}</h2>
      <p className="mt-2 max-w-lg text-sm leading-relaxed text-ink-muted">{body}</p>
      {operator && controlPlaneDoc && variant === 'disabled' ? (
        <a
          href={controlPlaneDoc}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-6 inline-flex items-center gap-2 rounded-full border border-gold/40 bg-burgundy-850 px-4 py-2 text-sm font-medium text-gold transition-colors hover:border-gold/70 hover:bg-burgundy-800"
        >
          Enable graph analysis (Control Plane stack)
          <ExternalLink size={14} aria-hidden />
        </a>
      ) : !operator ? (
        <p className="mt-6 text-xs text-ink-subtle">
          Contact your platform operator if you need graph analysis for investigations.
        </p>
      ) : null}
    </div>
  )
}

export function NetworkGraphAnalysisEmptyState() {
  return (
    <div className="flex min-h-[420px] flex-col items-center justify-center rounded-2xl border border-glass-border bg-glass-panel/60 px-8 py-12 text-center">
      <Share2 size={40} className="mb-3 text-ink-subtle" aria-hidden />
      <h2 className="text-lg font-semibold text-ink">No cases to graph yet</h2>
      <p className="mt-2 max-w-md text-sm text-ink-muted">
        Graph analysis is enabled, but there are no compliance cases in scope. Open or assign a case
        with linked transactions to see its relationship graph here.
      </p>
      <Link
        to="/cases"
        className="mt-6 text-sm font-medium text-gold underline-offset-4 hover:underline"
      >
        Go to Cases
      </Link>
    </div>
  )
}
