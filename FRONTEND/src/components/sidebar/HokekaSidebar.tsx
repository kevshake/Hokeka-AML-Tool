import { useEffect, useMemo, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
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
  Server,
  Building2,
  Users,
  Settings,
  ChevronDown,
  ChevronRight,
  Menu,
  ContactRound,
  CandlestickChart,
  Smartphone,
  WalletCards,
  LogOut,
  UserRound,
  Landmark,
  Mail,
  type LucideIcon,
} from 'lucide-react'
import HokekaLogo from '../branding/HokekaLogo'
import { cn } from '../../lib/utils'
import { useAuth } from '../../contexts/AuthContext'

const BADGE_ALERT = 'var(--danger)'

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
  messageUnreadCount?: number
  userName?: string
  userEmail?: string
  userRole?: string
}

function isRouteActive(pathname: string, to: string): boolean {
  return pathname === to || pathname.startsWith(`${to}/`)
}

function findActiveSection(pathname: string, groups: NavGroup[]): string {
  for (const group of groups) {
    if (group.items.some((item) => isRouteActive(pathname, item.to))) {
      return group.label
    }
  }
  return groups[0]?.label ?? 'INTELLIGENCE'
}

function sectionBadgeTotal(group: NavGroup): number {
  return group.items.reduce((sum, item) => sum + (item.badge ?? 0), 0)
}

export default function HokekaSidebar({
  alertCount,
  caseCount,
  messageUnreadCount,
  userName = 'Super Admin',
  userEmail,
  userRole = 'SUPER ADMIN',
}: HokekaSidebarProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const { logout } = useAuth()

  const [collapsed, setCollapsed] = useState(false)
  const [accountMenuOpen, setAccountMenuOpen] = useState(false)
  const normalizedRole = userRole.replaceAll(' ', '_').toUpperCase()
  const isPspUser = normalizedRole.startsWith('PSP_')
  const canManagePspUsers = normalizedRole === 'PSP_ADMIN'

  const groups: NavGroup[] = useMemo(
    () => [
      {
        label: 'INTELLIGENCE',
        items: [
          { label: 'Dashboard', icon: LayoutDashboard, to: '/dashboard' },
          { label: 'Live Monitoring', icon: Activity, to: '/transaction-monitoring' },
          { label: 'Customer 360', icon: ContactRound, to: '/customer-360' },
          { label: 'Market Surveillance', icon: CandlestickChart, to: '/market-surveillance' },
          { label: 'Mobile Money', icon: Smartphone, to: '/mobile-money' },
          { label: 'Wallet Intelligence', icon: WalletCards, to: '/wallet-intelligence' },
          {
            label: 'Alerts',
            icon: Bell,
            to: '/alerts',
            badge: alertCount !== undefined && alertCount > 0 ? alertCount : undefined,
          },
          {
            label: 'Cases',
            icon: Briefcase,
            to: '/cases',
            badge: caseCount !== undefined && caseCount > 0 ? caseCount : undefined,
          },
          {
            label: 'Messages',
            icon: Mail,
            to: '/messages',
            badge:
              messageUnreadCount !== undefined && messageUnreadCount > 0
                ? messageUnreadCount
                : undefined,
          },
        ],
      },
      {
        label: 'COMPLIANCE',
        items: [
          { label: 'KYC', icon: UserCheck, to: '/kyc-documents' },
          { label: 'Screening', icon: ShieldCheck, to: '/screening' },
          { label: 'Risk Rules', icon: FileSliders, to: '/rules-generation' },
          { label: 'Transaction Limits', icon: Gauge, to: '/limits-aml' },
          { label: 'Regulatory Reports', icon: Landmark, to: '/regulatory-reports' },
        ],
      },
      {
        label: 'ANALYTICS',
        items: [
          { label: 'Risk Analytics', icon: BarChart3, to: '/risk-analytics' },
          { label: 'Reports', icon: FileText, to: '/reports' },
          { label: 'Audit Logs', icon: ClipboardList, to: '/audit' },
        ],
      },
      {
        label: 'ADMINISTRATION',
        items: isPspUser
          ? [
              { label: 'My Organization', icon: Building2, to: '/organization' },
              ...(canManagePspUsers ? [{ label: 'Users', icon: Users, to: '/users' }] : []),
              ...(canManagePspUsers ? [{ label: 'Edge Nodes', icon: Server, to: '/edge-nodes' }] : []),
              { label: 'Settings', icon: Settings, to: '/settings' },
            ]
          : [
              { label: 'Merchants', icon: Store, to: '/merchants' },
              { label: 'PSPs', icon: Building2, to: '/psps' },
              { label: 'Edge Nodes', icon: Server, to: '/edge-nodes' },
              { label: 'Users', icon: Users, to: '/users' },
              { label: 'Runtime Errors', icon: ClipboardList, to: '/runtime-errors' },
              { label: 'Settings', icon: Settings, to: '/settings' },
            ],
      },
    ],
    [alertCount, caseCount, messageUnreadCount, canManagePspUsers, isPspUser],
  )

  const [expandedSections, setExpandedSections] = useState<Set<string>>(() => {
    const active = findActiveSection(location.pathname, groups)
    return new Set([active])
  })

  useEffect(() => {
    const active = findActiveSection(location.pathname, groups)
    setExpandedSections((prev) => {
      if (prev.has(active)) return prev
      const next = new Set(prev)
      next.add(active)
      return next
    })
  }, [location.pathname, groups])

  const toggleSection = (label: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev)
      if (next.has(label)) next.delete(label)
      else next.add(label)
      return next
    })
  }

  return (
    <aside
      className={cn(
        'relative z-10 flex h-full flex-shrink-0 flex-col border-r border-glass-border bg-glass-panel/98 shadow-[8px_0_28px_rgba(0,0,0,0.35)] backdrop-blur-glass transition-[width] duration-200',
        collapsed ? 'w-[72px] min-w-[72px]' : 'w-[280px] min-w-[280px]',
      )}
    >
      <div
        className={cn(
          'flex h-[72px] flex-shrink-0 border-b border-glass-border bg-charcoal-alt',
          collapsed
            ? 'flex-col items-center justify-center gap-1 px-2'
            : 'items-center justify-between gap-3 px-3',
        )}
      >
        <HokekaLogo variant="header" collapsed={collapsed} />
        <button
          type="button"
          onClick={() => setCollapsed((value) => !value)}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          className={cn(
            'flex flex-shrink-0 items-center justify-center rounded-lg border border-glass-border bg-burgundy-850 text-ink-muted transition-all hover:border-gold/50 hover:bg-burgundy-800 hover:text-ink',
            collapsed ? 'h-8 w-8' : 'h-9 w-9',
          )}
        >
          <Menu size={18} strokeWidth={2} />
        </button>
      </div>

      <nav className="flex-1 overflow-y-auto py-3" aria-label="Main navigation">
        {groups.map((group) => {
          const isExpanded = collapsed || expandedSections.has(group.label)
          const badgeTotal = sectionBadgeTotal(group)
          const sectionHasActive = group.items.some((item) =>
            isRouteActive(location.pathname, item.to),
          )

          return (
            <div key={group.label} className="mb-0.5">
              {!collapsed && (
                <button
                  type="button"
                  onClick={() => toggleSection(group.label)}
                  aria-expanded={isExpanded}
                  className={cn(
                    'hokeka-nav-section-trigger',
                    sectionHasActive && 'text-gold',
                  )}
                >
                  {isExpanded ? (
                    <ChevronDown size={12} className="flex-shrink-0 opacity-80" aria-hidden />
                  ) : (
                    <ChevronRight size={12} className="flex-shrink-0 opacity-80" aria-hidden />
                  )}
                  <span className="flex-1">{group.label}</span>
                  {!isExpanded && (
                    <span className="flex items-center gap-1.5">
                      {badgeTotal > 0 && (
                        <span
                          className="flex items-center rounded-full px-1.5 py-0.5 text-[9px] font-semibold leading-none text-charcoal"
                          style={{ backgroundColor: BADGE_ALERT }}
                        >
                          {badgeTotal}
                        </span>
                      )}
                      <span className="rounded-full border border-hairline bg-burgundy-850/70 px-1.5 py-0.5 text-[9px] font-medium leading-none text-ink-subtle">
                        {group.items.length}
                      </span>
                    </span>
                  )}
                </button>
              )}

              <div
                className={cn(
                  'grid transition-all duration-200 ease-editorial',
                  isExpanded ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0',
                )}
              >
                <div className="overflow-hidden">
                  <div
                    className={cn(
                      collapsed ? 'flex flex-col px-1.5 pb-1' : 'hokeka-nav-tree pb-1',
                    )}
                  >
                    {group.items.map((item) => {
                      const Icon = item.icon
                      return (
                        <NavLink
                          key={item.to}
                          to={item.to}
                          title={collapsed ? item.label : undefined}
                          className={({ isActive }) =>
                            cn(
                              collapsed
                                ? 'relative my-0.5 flex h-10 items-center justify-center rounded-xl px-0 text-sm transition-all'
                                : 'hokeka-nav-item',
                              isActive
                                ? 'hokeka-nav-active font-medium'
                                : collapsed
                                  ? 'text-ink-muted hover:bg-burgundy-850/65 hover:text-ink'
                                  : undefined,
                            )
                          }
                        >
                          <Icon size={18} className="flex-shrink-0" aria-hidden />
                          {!collapsed && (
                            <>
                              <span className="flex-1 truncate">{item.label}</span>
                              {typeof item.badge === 'number' && item.badge > 0 && (
                                <span
                                  className="flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold leading-none text-charcoal"
                                  style={{ backgroundColor: BADGE_ALERT }}
                                >
                                  {item.badge}
                                </span>
                              )}
                            </>
                          )}
                          {collapsed && typeof item.badge === 'number' && item.badge > 0 && (
                            <span
                              className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[9px] font-semibold leading-none text-charcoal"
                              style={{ backgroundColor: BADGE_ALERT }}
                            >
                              {item.badge > 9 ? '9+' : item.badge}
                            </span>
                          )}
                        </NavLink>
                      )
                    })}
                  </div>
                </div>
              </div>
            </div>
          )
        })}
      </nav>

      <div className="relative border-t border-glass-border px-3 py-3">
        {accountMenuOpen && (
          <div
            className={cn(
              'absolute bottom-[68px] z-30 rounded-lg border border-glass-border bg-burgundy-850 p-1 shadow-editorial',
              collapsed ? 'left-2 w-48' : 'left-3 right-3',
            )}
          >
            <button
              type="button"
              onClick={() => {
                setAccountMenuOpen(false)
                navigate('/profile')
              }}
              className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm text-ink-muted hover:bg-burgundy-800 hover:text-ink"
            >
              <UserRound size={16} /> My Profile
            </button>
            <button
              type="button"
              onClick={() => {
                setAccountMenuOpen(false)
                void logout()
              }}
              className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm text-danger hover:bg-danger-soft hover:text-ink"
            >
              <LogOut size={16} /> Log out
            </button>
          </div>
        )}

        <button
          type="button"
          aria-label="Open account menu"
          aria-expanded={accountMenuOpen}
          onClick={() => setAccountMenuOpen((open) => !open)}
          title={collapsed ? userName : undefined}
          className={cn(
            'flex w-full items-center rounded-xl py-2 text-left transition-colors hover:bg-burgundy-850/65',
            collapsed ? 'justify-center px-0' : 'gap-3 px-3',
          )}
        >
          <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full border border-gold/40 bg-burgundy-800 text-sm font-semibold text-gold">
            {userName.charAt(0).toUpperCase()}
          </div>
          {!collapsed && (
            <>
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium leading-tight text-ink">{userName}</div>
                <div className="truncate text-[10px] leading-tight text-ink-subtle">
                  {userEmail ?? userRole}
                </div>
              </div>
              <ChevronDown size={16} className="flex-shrink-0 text-ink-subtle" aria-hidden />
            </>
          )}
        </button>
      </div>
    </aside>
  )
}
