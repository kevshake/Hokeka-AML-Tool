import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import HokekaSidebar from '../components/sidebar/HokekaSidebar'
import HokekaHeader from '../components/header/HokekaHeader'
import { useAuth } from '../contexts/AuthContext'
import { useNavBadges } from '../hooks/useNavBadges'
import { cn } from '../lib/utils'

interface HokekaLayoutProps {
  children: ReactNode
}

export default function HokekaLayout({ children }: HokekaLayoutProps) {
  const { user } = useAuth()
  const badges = useNavBadges()
  const { pathname } = useLocation()
  const isDashboard = pathname === '/dashboard' || pathname === '/'

  const displayName =
    user?.firstName && user?.lastName
      ? `${user.firstName} ${user.lastName}`
      : user?.username ?? 'Admin'

  return (
    <div className="hokeka-dashboard flex h-screen overflow-hidden">
      <HokekaSidebar
        alertCount={badges.alertCount}
        caseCount={badges.caseCount}
        userName={displayName}
        userEmail={user?.email}
        userRole={user?.role?.name ?? 'SUPER ADMIN'}
      />
      <div
        className={cn(
          'hokeka-dashboard-main flex min-w-0 flex-1 flex-col overflow-hidden',
          isDashboard && 'hokeka-dashboard-main--saas',
        )}
      >
        <HokekaHeader
          userName={displayName}
          notificationCount={badges.alertCount ?? 0}
        />
        <div className="hokeka-dashboard-content flex flex-1 overflow-hidden px-5 pb-6 pt-1">
          <div className="min-w-0 flex-1 overflow-auto pr-1">{children}</div>
        </div>
      </div>
    </div>
  )
}
