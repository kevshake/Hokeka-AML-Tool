import { useEffect, useMemo, useState } from 'react'

import { NavLink, useLocation } from 'react-router-dom'

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

  ChevronRight,

  Menu,

  type LucideIcon,

} from 'lucide-react'

import HokekaLogo from '../branding/HokekaLogo'

import { cn } from '../../lib/utils'



const BADGE_RED = '#b31b24'



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

  userName = 'Super Admin',

  userEmail,

  userRole = 'SUPER ADMIN',

}: HokekaSidebarProps) {

  const location = useLocation()

  const [collapsed, setCollapsed] = useState(false)



  const groups: NavGroup[] = useMemo(

    () => [

      {

        label: 'INTELLIGENCE',

        items: [

          { label: 'Dashboard', icon: LayoutDashboard, to: '/dashboard' },

          { label: 'Live Monitoring', icon: Activity, to: '/transaction-monitoring' },

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

        ],

      },

      {

        label: 'COMPLIANCE',

        items: [

          { label: 'KYC', icon: UserCheck, to: '/kyc-documents' },

          { label: 'Screening', icon: ShieldCheck, to: '/screening' },

          { label: 'Risk Rules', icon: FileSliders, to: '/rules-generation' },

          { label: 'Transaction Limits', icon: Gauge, to: '/limits-aml' },

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

        items: [

          { label: 'Merchants', icon: Store, to: '/merchants' },

          { label: 'PSPs', icon: Building2, to: '/psps' },

          { label: 'Users', icon: Users, to: '/users' },

          { label: 'Settings', icon: Settings, to: '/settings' },

        ],

      },

    ],

    [alertCount, caseCount],

  )



  const [expandedSection, setExpandedSection] = useState<string>(() =>

    findActiveSection(location.pathname, groups),

  )



  useEffect(() => {

    setExpandedSection(findActiveSection(location.pathname, groups))

  }, [location.pathname, groups])



  const handleSectionToggle = (label: string) => {

    setExpandedSection(label)

  }



  return (

    <aside

      className={cn(

        'relative z-10 flex h-full flex-shrink-0 flex-col border-r border-glass-border bg-glass-panel backdrop-blur-glass transition-[width] duration-200',

        collapsed ? 'w-[72px] min-w-[72px]' : 'w-[280px] min-w-[280px]',

      )}

    >

      <div

        className={cn(

          'flex h-[72px] flex-shrink-0 border-b border-glass-border bg-[#0e0606]',

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

            'flex flex-shrink-0 items-center justify-center rounded-lg bg-[#1a1212] text-white/65 transition-colors hover:bg-[#251818] hover:text-white',

            collapsed ? 'h-8 w-8' : 'h-9 w-9',

          )}

        >

          <Menu size={18} strokeWidth={2} />

        </button>

      </div>



      <nav className="flex-1 overflow-y-auto py-3">

        {groups.map((group) => {

          const isExpanded = expandedSection === group.label

          const badgeTotal = sectionBadgeTotal(group)



          return (

            <div key={group.label} className="mb-1">

              {!collapsed && (

                <button

                  type="button"

                  onClick={() => handleSectionToggle(group.label)}

                  aria-expanded={isExpanded}

                  className={cn(

                    'flex w-full items-center gap-2 px-5 py-2 text-left transition-colors',

                    'text-[10px] font-semibold uppercase tracking-widest text-gold',

                    'hover:text-gold/90',

                  )}

                >

                  {isExpanded ? (

                    <ChevronDown size={12} className="flex-shrink-0 text-gold/60" />

                  ) : (

                    <ChevronRight size={12} className="flex-shrink-0 text-gold/60" />

                  )}

                  <span className="flex-1">{group.label}</span>

                  {!isExpanded && (

                    <span className="flex items-center gap-1.5">

                      {badgeTotal > 0 && (

                        <span

                          className="flex items-center rounded-full px-1.5 py-0.5 text-[9px] font-semibold leading-none text-white"

                          style={{ backgroundColor: BADGE_RED }}

                        >

                          {badgeTotal}

                        </span>

                      )}

                      <span className="rounded-full bg-burgundy-850/70 px-1.5 py-0.5 text-[9px] font-medium leading-none text-white/45">

                        {group.items.length}

                      </span>

                    </span>

                  )}

                </button>

              )}



              <div

                className={cn(

                  'grid transition-all duration-200 ease-in-out',

                  collapsed || isExpanded

                    ? 'grid-rows-[1fr] opacity-100'

                    : 'grid-rows-[0fr] opacity-0',

                )}

              >

                <div className="overflow-hidden">

                  <div className={cn('flex flex-col pb-1', collapsed ? 'px-1.5' : 'px-2')}>

                    {group.items.map((item) => {

                      const Icon = item.icon

                      return (

                        <NavLink

                          key={item.to}

                          to={item.to}

                          title={collapsed ? item.label : undefined}

                          className={({ isActive }) =>

                            cn(

                              'relative my-0.5 flex h-10 items-center rounded-xl text-sm transition-all',

                              collapsed ? 'justify-center px-0' : 'gap-3 px-3',

                              isActive

                                ? 'hokeka-nav-active text-white'

                                : 'text-white/70 hover:bg-burgundy-850/65 hover:text-white',

                            )

                          }

                        >

                          <Icon size={18} className="flex-shrink-0" />

                          {!collapsed && (

                            <>

                              <span className="flex-1 truncate">{item.label}</span>

                              {typeof item.badge === 'number' && item.badge > 0 && (

                                <span

                                  className="flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold leading-none text-white"

                                  style={{ backgroundColor: BADGE_RED }}

                                >

                                  {item.badge}

                                </span>

                              )}

                            </>

                          )}

                          {collapsed && typeof item.badge === 'number' && item.badge > 0 && (

                            <span

                              className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[9px] font-semibold leading-none text-white"

                              style={{ backgroundColor: BADGE_RED }}

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



      <div className="border-t border-glass-border px-3 py-3">

        <button

          type="button"

          title={collapsed ? userName : undefined}

          className={cn(

            'flex w-full items-center rounded-xl py-2 text-left transition-colors hover:bg-burgundy-850/65',

            collapsed ? 'justify-center px-0' : 'gap-3 px-3',

          )}

        >

          <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-burgundy-700 to-gold text-sm font-semibold text-white">

            {userName.charAt(0).toUpperCase()}

          </div>

          {!collapsed && (

            <>

              <div className="min-w-0 flex-1">

                <div className="truncate text-sm font-medium leading-tight text-white">

                  {userName}

                </div>

                <div className="truncate text-[10px] leading-tight text-white/45">

                  {userEmail ?? userRole}

                </div>

              </div>

              <ChevronDown size={16} className="flex-shrink-0 text-white/50" />

            </>

          )}

        </button>

      </div>

    </aside>

  )

}

