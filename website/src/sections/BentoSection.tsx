import BentoFeatureGrid from '../components/BentoFeatureGrid'

/** Bento feature grid with micro-interactions (platform capabilities). */
export default function BentoSection() {
  return (
    <section className="bento-section">
      <div className="container">
        <div className="split-head reveal">
          <div>
            <p className="eyebrow">Platform depth</p>
            <h2 className="section-title">Built for investigators, not dashboards.</h2>
          </div>
          <p className="section-copy section-copy-dark">
            Every surface is evidence-led: screening outcomes, monitoring scenarios and regulatory exports share the
            same provenance model.
          </p>
        </div>
        <BentoFeatureGrid />
      </div>
    </section>
  )
}
