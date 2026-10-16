import { ArrowRight, Blocks } from 'lucide-react'
import MagneticButton from '../components/MagneticButton'

/** Provider-independent integration visual (#integrations). */
export default function IntegrationsSection() {
  return (
    <section className="integrations" id="integrations">
      <div className="container integration-grid">
        <div className="integration-visual reveal" aria-label="Provider-independent integration architecture">
          <div className="core">
            <Blocks size={24} />
            <span>
              Hokeka
              <br />
              risk core
            </span>
          </div>
          {['Blockchain analytics', 'Sanctions data', 'Core banking', 'Case and filing rails'].map((value) => (
            <span className="provider" key={value}>
              {value}
            </span>
          ))}
        </div>
        <div className="reveal">
          <p className="eyebrow">API-first by design</p>
          <h2 className="section-title">Your providers can change. Your operating model stays whole.</h2>
          <p className="section-copy">
            Normalized gateways separate risk decisions from vendor-specific responses. Connect specialist intelligence,
            payment rails and reporting channels through stable contracts.
          </p>
          <MagneticButton
            href="mailto:info@hokeka.com?subject=Hokeka%20integration%20architecture"
            className="button quiet integration-cta"
          >
            Discuss your architecture <ArrowRight size={16} />
          </MagneticButton>
        </div>
      </div>
    </section>
  )
}
