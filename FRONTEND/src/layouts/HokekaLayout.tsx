import type { ReactNode } from 'react'
import { useState } from 'react'
import HokekaSidebar from '../components/sidebar/HokekaSidebar'
import HokekaHeader from '../components/header/HokekaHeader'
import { useAuth } from '../contexts/AuthContext'
import { useNavBadges } from '../hooks/useNavBadges'
import { useMediaQuery } from '../hooks/useMediaQuery'

interface HokekaLayoutProps {
  children: ReactNode
}

export default function HokekaLayout({ children }: HokekaLayoutProps) {
  const { user } = useAuth()
  const badges = useNavBadges()
  const isMobileNav = useMediaQuery('(max-width: 767px)')
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  const displayName =
    user?.firstName && user?.lastName
      ? `${user.firstName} ${user.lastName}`
      : user?.username ?? 'Admin'

  return (
    <div className="hokeka-dashboard flex h-screen overflow-hidden">
      <HokekaSidebar
        alertCount={badges.alertCount}
        caseCount={badges.caseCount}
        messageUnreadCount={badges.messageUnreadCount}
        userName={displayName}
        userEmail={user?.email}
        userRole={user?.role?.name ?? 'SUPER ADMIN'}
        isMobile={isMobileNav}
        mobileOpen={mobileNavOpen}
        onMobileClose={() => setMobileNavOpen(false)}
      />
      {isMobileNav && mobileNavOpen ? (
        <button
          type="button"
          aria-label="Close navigation menu"
          className="fixed inset-0 z-40 bg-black/55 backdrop-blur-[2px]"
          onClick={() => setMobileNavOpen(false)}
        />
      ) : null}
      <div className="hokeka-dashboard-main flex min-w-0 flex-1 flex-col overflow-hidden">
        <HokekaHeader
          userName={displayName}
          notificationCount={badges.messageUnreadCount}
          showMobileMenu={isMobileNav}
          onOpenMobileMenu={() => setMobileNavOpen(true)}
        />
        <div className="hokeka-dashboard-content flex flex-1 overflow-hidden px-5 pb-6 pt-1">
          <div className="min-w-0 flex-1 overflow-auto pr-1">{children}</div>
        </div>
      </div>
    </div>
  )
}
