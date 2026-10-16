import { ArrowRight, MousePointerClick } from 'lucide-react'
import ContactForm from '../components/ContactForm'
import MagneticButton from '../components/MagneticButton'

/** Contact CTA with working form plus preserved mailto actions (#contact). */
export default function CtaSection() {
  return (
    <section className="cta" id="contact">
      <div className="container cta-inner reveal">
        <p className="eyebrow">A broader view of risk</p>
        <h2 className="section-title">Bring the hidden connections into focus.</h2>
        <p>
          Talk with the Hokeka team about your asset mix, investigative workflow and regulatory reporting environment.
        </p>
        <ContactForm />
        <div className="hero-actions cta-mail-actions">
          <MagneticButton href="mailto:info@hokeka.com?subject=Hokeka%20private%20demo">
            Request a private demo <ArrowRight size={16} />
          </MagneticButton>
          <MagneticButton href="mailto:info@hokeka.com" className="button quiet">
            <MousePointerClick size={16} /> info@hokeka.com
          </MagneticButton>
        </div>
      </div>
    </section>
  )
}
