import type { ReactNode } from 'react'
import HokekaSidebar from '../components/sidebar/HokekaSidebar'
import HokekaHeader from '../components/header/HokekaHeader'
import { useAuth } from '../contexts/AuthContext'
import { useNavBadges } from '../hooks/useNavBadges'

interface HokekaLayoutProps {
  children: ReactNode
}

export default function HokekaLayout({ children }: HokekaLayoutProps) {
  const { user } = useAuth()
  const badges = useNavBadges()

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
      <div className="hokeka-dashboard-main flex min-w-0 flex-1 flex-col overflow-hidden">
        <HokekaHeader
          userName={displayName}
          notificationCount={badges.alertCount ?? 0}
        />
        <div className="flex flex-1 overflow-hidden px-5 pb-5">
          <div className="min-w-0 flex-1 overflow-auto">{children}</div>
        </div>
      </div>
    </div>
  )
}
