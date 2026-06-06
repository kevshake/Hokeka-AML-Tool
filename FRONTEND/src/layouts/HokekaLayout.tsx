import type { ReactNode } from 'react'
import HokekaSidebar from '../components/sidebar/HokekaSidebar'
import HokekaHeader from '../components/header/HokekaHeader'

interface HokekaLayoutProps {
  children: ReactNode
}

export default function HokekaLayout({ children }: HokekaLayoutProps) {
  return (
    <div className="flex h-screen bg-hokeka-background">
      <HokekaSidebar />
      <main className="flex-1 overflow-auto">
        <HokekaHeader />
        <div className="px-6 pb-6">{children}</div>
      </main>
    </div>
  )
}
