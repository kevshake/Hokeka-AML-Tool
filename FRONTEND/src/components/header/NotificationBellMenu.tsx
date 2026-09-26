import { useEffect, useRef, useState } from 'react'
import { Bell, CheckCheck, ExternalLink, Loader2, Mail } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../lib/apiClient'
import TwBadge from '../Common/TwBadge'

interface MessageRow {
  id: string
  subject?: string
  title?: string
  body?: string
  content?: string
  read?: boolean
  createdAt?: string
  sentAt?: string
}

interface NotificationBellMenuProps {
  unreadCount?: number
}

export default function NotificationBellMenu({ unreadCount }: NotificationBellMenuProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const queryClient = useQueryClient()

  const { data: messages, isLoading, isError } = useQuery<MessageRow[]>({
    queryKey: ['messages', 'bell-preview'],
    queryFn: () => apiClient.get<MessageRow[]>('messages?unreadOnly=false'),
    enabled: open,
    staleTime: 20_000,
  })

  const markRead = useMutation({
    mutationFn: (id: string) => apiClient.put(`messages/${id}/read`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['messages'] })
    },
  })

  const markAllRead = useMutation({
    mutationFn: () => apiClient.put('messages/read-all', {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['messages'] })
    },
  })

  useEffect(() => {
    const onDoc = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  const preview = (messages ?? []).slice(0, 8)
  const badge = unreadCount ?? 0

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="relative flex h-10 w-10 items-center justify-center rounded-full border border-glass-border bg-glass-panel text-white/85 transition-all hover:border-glass-border-hover hover:bg-burgundy-800 hover:text-white hover:shadow-glass-glow"
        aria-label="Notifications"
        aria-expanded={open}
      >
        <Bell size={18} />
        {badge > 0 && (
          <span className="absolute -top-1 -right-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold leading-none text-charcoal">
            {badge > 99 ? '99+' : badge}
          </span>
        )}
      </button>

      {open && (
        <div
          className="absolute right-0 top-12 z-40 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-glass-border bg-burgundy-850/98 shadow-glass-glow backdrop-blur-glass"
          role="dialog"
          aria-label="Notification center"
        >
          <div className="flex items-center justify-between border-b border-glass-border px-4 py-3">
            <div>
              <p className="text-sm font-semibold text-white">Notifications</p>
              <p className="text-[11px] text-glass-muted">System messages for your account</p>
            </div>
            <button
              type="button"
              disabled={markAllRead.isPending || badge === 0}
              onClick={() => markAllRead.mutate()}
              className="inline-flex items-center gap-1 rounded-lg border border-glass-border px-2 py-1 text-[11px] font-medium text-glass-muted transition-colors hover:border-glass-border-hover hover:text-white disabled:opacity-40"
            >
              <CheckCheck size={14} />
              Mark all read
            </button>
          </div>

          <div className="max-h-80 overflow-y-auto">
            {isLoading && (
              <div className="flex items-center gap-2 px-4 py-6 text-sm text-glass-muted">
                <Loader2 size={18} className="animate-spin" /> Loading…
              </div>
            )}
            {isError && (
              <div className="mx-3 my-3 rounded-lg border border-red-700/30 bg-red-900/30 px-3 py-2 text-sm text-red-200">
                Could not load notifications.
              </div>
            )}
            {!isLoading && !isError && preview.length === 0 && (
              <div className="flex flex-col items-center gap-2 px-6 py-10 text-center">
                <div className="flex h-10 w-10 items-center justify-center rounded-full border border-glass-border bg-surface-2 text-gold">
                  <Mail size={18} />
                </div>
                <p className="text-sm font-medium text-white">You&apos;re all caught up</p>
                <p className="text-xs text-glass-muted">New system messages will appear here.</p>
              </div>
            )}
            {preview.map((message) => {
              const title = message.subject || message.title || '(no subject)'
              const body = message.body || message.content || ''
              const ts = message.createdAt || message.sentAt
              return (
                <button
                  key={message.id}
                  type="button"
                  onClick={() => {
                    if (!message.read) markRead.mutate(message.id)
                  }}
                  className={`w-full border-0 border-b border-white/5 px-4 py-3 text-left transition-colors last:border-b-0 hover:bg-white/[0.03] ${
                    message.read ? '' : 'bg-burgundy-700/10'
                  }`}
                >
                  <div className="flex items-start gap-2">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <p className={`truncate text-sm ${message.read ? 'text-white/80' : 'font-semibold text-white'}`}>
                          {title}
                        </p>
                        {!message.read && <TwBadge variant="info">New</TwBadge>}
                      </div>
                      {body ? (
                        <p className="mt-0.5 line-clamp-2 text-xs text-glass-muted">{body}</p>
                      ) : null}
                      {ts ? (
                        <p className="mt-1 text-[10px] text-glass-muted/70">
                          {new Date(ts).toLocaleString()}
                        </p>
                      ) : null}
                    </div>
                  </div>
                </button>
              )
            })}
          </div>

          <div className="border-t border-glass-border px-4 py-2.5">
            <Link
              to="/messages"
              onClick={() => setOpen(false)}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-gold hover:text-gold-bright"
            >
              View all messages
              <ExternalLink size={13} />
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
