import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2, Search } from 'lucide-react'
import { useGlobalSearch } from '../../hooks/useGlobalSearch'

interface GlobalSearchDialogProps {
  open: boolean
  onClose: () => void
}

export default function GlobalSearchDialog({ open, onClose }: GlobalSearchDialogProps) {
  const [query, setQuery] = useState('')
  const navigate = useNavigate()
  const { data, isFetching, isError } = useGlobalSearch(query, open)

  useEffect(() => {
    if (!open) {
      setQuery('')
    }
  }, [open])

  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  if (!open) {
    return null
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/60 px-4 pt-24 backdrop-blur-sm">
      <div className="w-full max-w-2xl overflow-hidden rounded-2xl border border-glass-border bg-burgundy-900 shadow-glass-glow">
        <div className="flex items-center gap-3 border-b border-glass-border px-4 py-3">
          <Search size={18} className="text-glass-muted" />
          <input
            autoFocus
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search transactions, alerts, cases, merchants…"
            className="w-full bg-transparent text-sm text-white outline-none placeholder:text-glass-muted"
          />
          {isFetching && <Loader2 size={16} className="animate-spin text-glass-muted" />}
        </div>
        <div className="max-h-96 overflow-y-auto p-2">
          {query.trim().length < 2 && (
            <p className="px-3 py-6 text-sm text-glass-muted">Type at least two characters to search.</p>
          )}
          {isError && (
            <p className="px-3 py-6 text-sm text-red-300">Search failed. Try again in a moment.</p>
          )}
          {data && data.hits.length === 0 && query.trim().length >= 2 && !isFetching && (
            <p className="px-3 py-6 text-sm text-glass-muted">No results for &quot;{data.query}&quot;.</p>
          )}
          {data?.hits.map((hit) => (
            <button
              key={`${hit.entityType}-${hit.entityId}`}
              type="button"
              onClick={() => {
                if (hit.recordPath) {
                  navigate(hit.recordPath)
                }
                onClose()
              }}
              className="flex w-full flex-col rounded-xl px-3 py-2.5 text-left transition hover:bg-burgundy-800"
            >
              <span className="text-sm font-medium text-white">{hit.title}</span>
              {hit.subtitle && (
                <span className="mt-0.5 truncate text-xs text-glass-muted">{hit.subtitle}</span>
              )}
              <span className="mt-1 text-[10px] uppercase tracking-wide text-gold/80">
                {hit.entityType}{hit.status ? ` · ${hit.status}` : ''}
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
