import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  Activity,
  Bell,
  Briefcase,
  UserCheck,
  ShieldCheck,
  FileSliders,
  Gauge,
  BarChart3,
  FileText,
  ClipboardList,
  Store,
  Building2,
  Users,
  Settings,
  ChevronDown,
  type LucideIcon,
} from 'lucide-react'
import HokekaLogo from '../branding/HokekaLogo'
import { cn } from '../../lib/utils'

interface NavItem {
  label: string
  icon: LucideIcon
  to: string
  badge?: number
}

interface NavGroup {
  label: string
  items: NavItem[]
}

interface HokekaSidebarProps {
  alertCount?: number
  caseCount?: number
}

export default function HokekaSidebar({
  alertCount = 12,
  caseCount = 32,
}: HokekaSidebarProps) {
  const groups: NavGroup[] = [
    {
      label: 'INTELLIGENCE',
      items: [
        { label: 'Dashboard', icon: LayoutDashboard, to: '/dashboard' },
        { label: 'Live Monitoring', icon: Activity, to: '/transaction-monitoring' },
        { label: 'Alerts', icon: Bell, to: '/alerts', badge: alertCount },
        { label: 'Cases', icon: Briefcase, to: '/cases', badge: caseCount },
      ],
    },
    {
      label: 'COMPLIANCE',
      items: [
        { label: 'KYC', icon: UserCheck, to: '/kyc-documents' },
        { label: 'Screening', icon: ShieldCheck, to: '/screening' },
        { label: 'Risk Rules', icon: FileSliders, to: '/rules' },
        { label: 'Transaction Limits', icon: Gauge, to: '/limits-aml' },
      ],
    },
    {
      label: 'ANALYTICS',
      items: [
        { label: 'Risk Analytics', icon: BarChart3, to: '/risk-analytics' },
        { label: 'Reports', icon: FileText, to: '/reports' },
        { label: 'Audit Logs', icon: ClipboardList, to: '/audit-logs' },
      ],
    },
    {
      label: 'ADMINISTRATION',
      items: [
        { label: 'Merchants', icon: Store, to: '/merchants' },
        { label: 'PSPs', icon: Building2, to: '/psps' },
        { label: 'Users', icon: Users, to: '/users' },
        { label: 'Settings', icon: Settings, to: '/settings' },
      ],
    },
  ]

  return (
    <aside
      className="flex flex-col h-screen bg-hokeka-sidebar text-slate-300"
      style={{ width: 280, minWidth: 280 }}
    >
      {/* Logo */}
      <div className="py-6 border-b border-white/5 flex items-center justify-center">
        <HokekaLogo />
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto py-4">
        {groups.map((group) => (
          <div key={group.label} className="mb-2">
            <div className="text-[10px] tracking-widest text-slate-500 uppercase px-6 py-2 font-semibold">
              {group.label}
            </div>
            <div className="flex flex-col">
              {group.items.map((item) => {
                const Icon = item.icon
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) =>
                      cn(
                        'mx-3 my-0.5 px-3 rounded-xl flex items-center gap-3 text-sm transition-colors',
                        'h-10',
                        isActive
                          ? 'bg-hokeka-secondary text-white shadow-md shadow-hokeka-secondary/20'
                          : 'text-slate-300 hover:bg-hokeka-sidebarHover hover:text-white',
                      )
                    }
                  >
                    <Icon size={18} className="flex-shrink-0" />
                    <span className="flex-1 truncate">{item.label}</span>
                    {typeof item.badge === 'number' && item.badge > 0 && (
                      <span className="bg-rose-500 text-white text-[10px] px-2 py-0.5 rounded-full font-medium leading-none flex items-center">
                        {item.badge}
                      </span>
                    )}
                  </NavLink>
                )
              })}
            </div>
          </div>
        ))}
      </nav>

      {/* User card */}
      <div className="px-3 pb-4 pt-2 border-t border-white/5">
        <button
          type="button"
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-hokeka-sidebarHover transition-colors text-left"
        >
          <div className="w-8 h-8 rounded-full bg-slate-700 flex items-center justify-center text-white text-sm font-semibold flex-shrink-0">
            S
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-sm text-white font-medium leading-tight truncate">
              super.admin
            </div>
            <div className="text-[11px] text-slate-400 leading-tight truncate">
              Super Admin
            </div>
          </div>
          <ChevronDown size={16} className="text-slate-400 flex-shrink-0" />
        </button>
      </div>
    </aside>
  )
}
