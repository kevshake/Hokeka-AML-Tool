import { lazy, Suspense } from 'react'
import Navbar from './components/Navbar'
import Footer from './sections/Footer'
import HeroSection from './sections/HeroSection'
import ComplianceSection from './sections/ComplianceSection'
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
const ProofSection = lazy(() => import('./sections/ProofSection'))
const OfferSection = lazy(() => import('./sections/OfferSection'))
const CtaSection = lazy(() => import('./sections/CtaSection'))

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
        <Suspense fallback={null}>
          <ProofSection />
          <StatsSection />
        </Suspense>
        <Suspense fallback={null}>
          <ScrollStorySection />
        </Suspense>
        <Suspense fallback={null}>
          <OfferSection />
          <BentoSection />
        </Suspense>
        <Suspense fallback={null}>
          <CommandSection />
          <OperationsSection />
          <ArchitectureSection />
          <IntegrationsSection />
        </Suspense>
        <ComplianceSection />
        <Suspense fallback={null}>
          <CtaSection />
        </Suspense>
      </main>
      <Footer />
    </div>
  )
}
