import { useEffect, useState } from 'react'
import { Bell, ChevronDown, LogOut, UserRound } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import GlassInput from '../Common/GlassInput'
import { useAuth } from '../../contexts/AuthContext'

interface HokekaHeaderProps {
  userName?: string
  notificationCount?: number
}

function formatTime(d: Date) {
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${hh}:${mm}:${ss}`
}

export default function HokekaHeader({
  userName = 'Admin',
  notificationCount,
}: HokekaHeaderProps) {
  const [now, setNow] = useState(() => formatTime(new Date()))
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()
  const { logout } = useAuth()

  useEffect(() => {
    const id = setInterval(() => setNow(formatTime(new Date())), 1000)
    return () => clearInterval(id)
  }, [])

  return (
    <header className="sticky top-0 z-10 flex h-[72px] flex-shrink-0 items-center justify-between gap-4 border-b border-glass-border bg-glass-panel/92 px-5 shadow-[0_8px_24px_rgba(0,0,0,0.28)] backdrop-blur-glass">
      <div className="min-w-0 flex-shrink">
        <h1 className="truncate text-[1.2rem] font-semibold tracking-tight text-white leading-tight">
          Welcome back, {userName}
        </h1>
        <p className="mt-0.5 truncate text-xs leading-relaxed text-glass-muted">
          Here&apos;s what&apos;s happening in your compliance environment today.
        </p>
      </div>

      <div className="flex flex-shrink-0 items-center gap-2.5">
        <GlassInput
          placeholder="Search anything..."
          shortcut="⌘K"
          showSearchIcon
          className="w-72 hidden lg:block"
        />

        <div className="hidden h-10 flex-col justify-center rounded-full border border-glass-border bg-glass-panel px-3.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.04)] sm:flex">
          <div className="flex items-center gap-2">
            <span className="relative inline-flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-60" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-success live-dot-glow" />
            </span>
            <span className="text-[11px] font-bold tracking-wide leading-none text-success">LIVE</span>
          </div>
          <span className="mt-0.5 text-[10px] leading-none text-glass-muted">
            Last sync: {now}
          </span>
        </div>

        <button
          type="button"
          className="relative flex h-10 w-10 items-center justify-center rounded-full border border-glass-border bg-glass-panel text-white/85 transition-all hover:border-glass-border-hover hover:bg-burgundy-800 hover:text-white hover:shadow-glass-glow"
          aria-label="Notifications"
        >
          <Bell size={18} />
          {notificationCount !== undefined && notificationCount > 0 && (
            <span className="absolute -top-1 -right-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold leading-none text-charcoal">
              {notificationCount}
            </span>
          )}
        </button>

        <div className="relative">
          <button
            type="button"
            aria-label="Open account menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
            className="flex h-10 items-center gap-2 rounded-full border border-glass-border bg-glass-panel px-2 transition-all hover:border-glass-border-hover hover:bg-burgundy-800"
          >
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-burgundy-800 text-xs font-semibold text-gold ring-1 ring-gold/30">
              {userName.charAt(0).toUpperCase()}
            </div>
            <ChevronDown size={14} className="hidden text-white/60 sm:block" />
          </button>
          {menuOpen && (
            <div className="absolute right-0 top-12 z-30 w-48 rounded-xl border border-glass-border bg-burgundy-850 p-1.5 shadow-glass-glow">
              <button type="button" onClick={() => { setMenuOpen(false); navigate('/profile') }}
                className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-white/80 hover:bg-burgundy-800 hover:text-white">
                <UserRound size={16} /> My Profile
              </button>
              <button type="button" onClick={() => { setMenuOpen(false); void logout() }}
                className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-red-300 hover:bg-red-950/40 hover:text-red-200">
                <LogOut size={16} /> Log out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
