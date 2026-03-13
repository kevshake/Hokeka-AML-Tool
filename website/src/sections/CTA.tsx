import { ArrowRight, Mail, Phone } from 'lucide-react'

export default function CTA() {
  return (
    <section className="py-24 bg-white">
      <div className="section-padding">
        <div className="max-w-5xl mx-auto">
          <div className="relative bg-gradient-to-br from-burgundy-800 to-burgundy-900 rounded-3xl p-8 sm:p-12 lg:p-16 overflow-hidden">
            {/* Background Pattern */}
            <div className="absolute inset-0 opacity-10">
              <div className="absolute -top-24 -right-24 w-64 h-64 bg-gold-500 rounded-full blur-3xl" />
              <div className="absolute -bottom-24 -left-24 w-64 h-64 bg-burgundy-400 rounded-full blur-3xl" />
            </div>

            <div className="relative z-10 text-center">
              <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-white mb-6">
                Ready to Strengthen Your
                <span className="text-gold-400"> Compliance</span>?
              </h2>
              
              <p className="text-lg text-white/80 mb-8 max-w-2xl mx-auto">
                Join leading financial institutions that trust HOKEKA for their 
                AML compliance needs. Get started with a personalized demo today.
              </p>

              <div className="flex flex-col sm:flex-row gap-4 justify-center mb-8">
                <a
                  href="mailto:contact@hokeka.com"
                  className="btn-primary flex items-center justify-center gap-2"
                >
                  Request a Demo
                  <ArrowRight className="w-5 h-5" />
                </a>
                <a
                  href="https://aml.hokeka.com"
                  className="btn-secondary flex items-center justify-center gap-2"
                >
                  Access Dashboard
                </a>
              </div>

              {/* Contact Info */}
              <div className="flex flex-col sm:flex-row gap-6 justify-center text-white/70">
                <a href="mailto:contact@hokeka.com" className="flex items-center gap-2 hover:text-gold-400 transition-colors">
                  <Mail className="w-5 h-5" />
                  contact@hokeka.com
                </a>
                <a href="tel:+27XXXXXXXXX" className="flex items-center gap-2 hover:text-gold-400 transition-colors">
                  <Phone className="w-5 h-5" />
                  +27 XX XXX XXXX
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
