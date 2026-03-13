import { Check, Shield, FileText, Scale } from 'lucide-react'

const compliances = [
  {
    region: 'Global',
    standards: [
      'FATF Recommendations',
      'Wolfsberg Group Standards',
      'IOSCO Principles',
    ],
  },
  {
    region: 'Africa',
    standards: [
      'South Africa FICA',
      'Nigeria CBN Guidelines',
      'Kenya POCAMLA',
      'Ghana BoG Directives',
    ],
  },
  {
    region: 'Europe',
    standards: [
      'EU 6AMLD',
      'GDPR Compliant',
      'FCA Regulations',
      'CySEC Requirements',
    ],
  },
  {
    region: 'Americas',
    standards: [
      'FinCEN BSA',
      'OFAC Sanctions',
      'FINRA Rule 3310',
      'FINTRAC PCMLTFA',
    ],
  },
]

const certifications = [
  { icon: Shield, label: 'SOC 2 Type II' },
  { icon: FileText, label: 'ISO 27001' },
  { icon: Scale, label: 'GDPR Ready' },
]

export default function Compliance() {
  return (
    <section id="compliance" className="py-24 bg-burgundy-900">
      <div className="section-padding">
        <div className="max-w-7xl mx-auto">
          <div className="grid lg:grid-cols-2 gap-16 items-center">
            {/* Left Content */}
            <div>
              <span className="text-gold-400 font-semibold tracking-wider uppercase text-sm">
                Regulatory Compliance
              </span>
              <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-white mt-4 mb-6">
                Stay Compliant with
                <span className="text-gold-400"> Global Regulations</span>
              </h2>
              <p className="text-lg text-white/80 mb-8">
                HOKEKA is designed to help you meet regulatory requirements across 
                multiple jurisdictions. Our platform is regularly updated to reflect 
                the latest AML/CFT standards.
              </p>

              {/* Certifications */}
              <div className="flex flex-wrap gap-4">
                {certifications.map((cert) => (
                  <div
                    key={cert.label}
                    className="flex items-center gap-2 px-4 py-2 bg-white/10 rounded-lg"
                  >
                    <cert.icon className="w-5 h-5 text-gold-400" />
                    <span className="text-white font-medium">{cert.label}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Right Content - Compliance Cards */}
            <div className="grid sm:grid-cols-2 gap-6">
              {compliances.map((compliance) => (
                <div
                  key={compliance.region}
                  className="bg-white/5 backdrop-blur-sm rounded-2xl p-6 border border-white/10"
                >
                  <h3 className="text-xl font-bold text-gold-400 mb-4">
                    {compliance.region}
                  </h3>
                  <ul className="space-y-3">
                    {compliance.standards.map((standard) => (
                      <li key={standard} className="flex items-start gap-2">
                        <Check className="w-5 h-5 text-green-400 flex-shrink-0 mt-0.5" />
                        <span className="text-white/80 text-sm">{standard}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
