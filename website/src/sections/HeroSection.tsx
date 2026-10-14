import { lazy, Suspense } from 'react'
import IconArrowRight from '../components/IconArrowRight'
import IconShieldCheck from '../components/IconShieldCheck'
import MagneticButton from '../components/MagneticButton'

const NetworkGraphHero = lazy(() => import('../components/NetworkGraphHero'))

/** Hero with deferred network graph canvas, headline, and primary CTAs. */
export default function HeroSection() {
  return (
    <section className="hero hero-animated" id="home">
      <Suspense fallback={null}>
        <NetworkGraphHero />
      </Suspense>
      <div className="container hero-content">
        <p className="eyebrow">Unified financial crime intelligence</p>
        <h1>Hokeka</h1>
        <p className="hero-statement">See the whole financial story, across every asset.</p>
        <p className="hero-copy">
          One operational platform for banking, securities, e-money and crypto AML. Link identity, movement and risk
          into decisions investigators can explain.
        </p>
        <div className="hero-actions">
          <MagneticButton href="mailto:info@hokeka.com?subject=Hokeka%20platform%20demo">
            Request a private demo <IconArrowRight />
          </MagneticButton>
          <MagneticButton href="#platform" className="button quiet">
            Explore the platform
          </MagneticButton>
        </div>
      </div>
      <div className="hero-footnote">
        <IconShieldCheck /> Built for regulated financial institutions
      </div>
      <div className="hero-scroll" aria-hidden="true" />
    </section>
  )
}
