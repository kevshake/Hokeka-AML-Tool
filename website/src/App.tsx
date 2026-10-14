import { lazy, Suspense } from 'react'
import Navbar from './components/Navbar'
import Footer from './sections/Footer'
import HeroSection from './sections/HeroSection'
import ProofSection from './sections/ProofSection'
import OfferSection from './sections/OfferSection'
import ComplianceSection from './sections/ComplianceSection'
import CtaSection from './sections/CtaSection'
import { useLenisGsap } from './hooks/useLenisGsap'
import { useViewTransitionNav } from './hooks/useViewTransitionNav'
import { useDeferredSectionBackgrounds } from './hooks/useDeferredSectionBackgrounds'

const StatsSection = lazy(() => import('./sections/StatsSection'))
const ScrollStorySection = lazy(() => import('./sections/ScrollStorySection'))
const BentoSection = lazy(() => import('./sections/BentoSection'))
const CommandSection = lazy(() => import('./sections/CommandSection'))
const OperationsSection = lazy(() => import('./sections/OperationsSection'))
const ArchitectureSection = lazy(() => import('./sections/ArchitectureSection'))
const IntegrationsSection = lazy(() => import('./sections/IntegrationsSection'))

/** Marketing site shell: preserves original IA with animated sections layered on top. */
export default function App() {
  useLenisGsap()
  useViewTransitionNav()
  useDeferredSectionBackgrounds()

  return (
    <div className="site-shell">
      <Navbar />
      <main>
        <HeroSection />
        <ProofSection />
        <Suspense fallback={null}>
          <StatsSection />
        </Suspense>
        <Suspense fallback={null}>
          <ScrollStorySection />
        </Suspense>
        <OfferSection />
        <Suspense fallback={null}>
          <BentoSection />
        </Suspense>
        <Suspense fallback={null}>
          <CommandSection />
          <OperationsSection />
          <ArchitectureSection />
          <IntegrationsSection />
        </Suspense>
        <ComplianceSection />
        <CtaSection />
      </main>
      <Footer />
    </div>
  )
}
