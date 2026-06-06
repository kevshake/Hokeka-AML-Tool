import { Search, Bell, Calendar, ChevronDown } from 'lucide-react'

interface HokekaHeaderProps {
  userName?: string
  notificationCount?: number
}

export default function HokekaHeader({
  userName = 'Admin',
  notificationCount = 8,
}: HokekaHeaderProps) {
  return (
    <header className="sticky top-0 z-10 h-20 flex items-center justify-between px-6 bg-hokeka-background">
      {/* Left: welcome */}
      <div className="flex flex-col">
        <h1 className="text-2xl font-semibold text-slate-900 leading-tight">
          Welcome back, {userName} <span aria-hidden>👋</span>
        </h1>
        <p className="text-sm text-slate-500 mt-0.5">
          Here&apos;s what&apos;s happening in your compliance environment today.
        </p>
      </div>

      {/* Right: actions */}
      <div className="flex items-center gap-3">
        {/* Search */}
        <div className="relative w-80">
          <Search
            size={16}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none"
          />
          <input
            type="text"
            placeholder="Search anything..."
            className="w-full h-10 pl-9 pr-14 rounded-xl border border-slate-200 bg-white text-sm text-slate-700 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-hokeka-secondary/30 focus:border-hokeka-secondary transition"
          />
          <kbd className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] font-medium text-slate-500 bg-slate-100 border border-slate-200 rounded px-1.5 py-0.5 leading-none">
            ⌘K
          </kbd>
        </div>

        {/* Notifications */}
        <button
          type="button"
          className="relative h-10 w-10 rounded-xl border border-slate-200 bg-white flex items-center justify-center text-slate-600 hover:text-slate-900 hover:bg-slate-50 transition-colors"
          aria-label="Notifications"
        >
          <Bell size={18} />
          {notificationCount > 0 && (
            <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-rose-500 text-white text-[10px] font-semibold flex items-center justify-center leading-none">
              {notificationCount}
            </span>
          )}
        </button>

        {/* Date picker */}
        <button
          type="button"
          className="h-10 inline-flex items-center gap-2 px-3 rounded-xl border border-slate-200 bg-white text-sm text-slate-700 hover:bg-slate-50 transition-colors"
        >
          <Calendar size={16} className="text-slate-500" />
          <span>Today</span>
          <ChevronDown size={14} className="text-slate-500" />
        </button>
      </div>
    </header>
  )
}
