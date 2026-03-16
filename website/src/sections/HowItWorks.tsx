import { Upload, Cog, Bell, CheckCircle } from 'lucide-react'

const steps = [
  {
    number: '01',
    icon: Upload,
    title: 'Integration',
    description: 'Connect HOKEKA to your systems via our RESTful API or use our pre-built SDKs. Integration takes hours, not months.',
    color: 'bg-blue-500',
  },
  {
    number: '02',
    icon: Cog,
    title: 'Configuration',
    description: 'Customize risk rules, screening lists, and workflows to match your compliance requirements and risk appetite.',
    color: 'bg-burgundy-600',
  },
  {
    number: '03',
    icon: Bell,
    title: 'Monitor',
    description: 'Our AI continuously screens transactions and customers, alerting you to suspicious activities in real-time.',
    color: 'bg-gold-500',
  },
  {
    number: '04',
    icon: CheckCircle,
    title: 'Report',
    description: 'Generate regulatory reports with one click. Stay audit-ready with comprehensive case management and documentation.',
    color: 'bg-green-500',
  },
]

export default function HowItWorks() {
  return (
    <section id="how-it-works" className="py-24 bg-gray-50">
      <div className="section-padding">
        <div className="max-w-7xl mx-auto">
          {/* Section Header */}
          <div className="text-center max-w-3xl mx-auto mb-16">
            <span className="text-burgundy-800 font-semibold tracking-wider uppercase text-sm">
              Implementation
            </span>
            <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-gray-900 mt-4 mb-6">
              Simple Integration,
              <span className="text-burgundy-800"> Powerful Results</span>
            </h2>
            <p className="text-lg text-gray-600">
              Get up and running with HOKEKA in four simple steps. Our team provides 
              full support throughout the implementation process.
            </p>
          </div>

          {/* Steps */}
          <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-8">
            {steps.map((step, index) => (
              <div key={step.number} className="relative">
                {/* Connector Line */}
                {index < steps.length - 1 && (
                  <div className="hidden lg:block absolute top-12 left-full w-full h-0.5 bg-gray-200 -translate-y-1/2 z-0" />
                )}
                
                <div className="relative z-10 bg-white rounded-2xl p-8 shadow-lg border border-gray-100 card-hover">
                  <div className="flex items-center justify-between mb-6">
                    <span className="text-4xl font-bold text-gray-200">{step.number}</span>
                    <div className={`w-12 h-12 ${step.color} rounded-xl flex items-center justify-center`}>
                      <step.icon className="w-6 h-6 text-white" />
                    </div>
                  </div>
                  
                  <h3 className="text-xl font-bold text-gray-900 mb-3">
                    {step.title}
                  </h3>
                  
                  <p className="text-gray-600 leading-relaxed">
                    {step.description}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
