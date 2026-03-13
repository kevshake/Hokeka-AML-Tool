import { 
  Search, 
  AlertTriangle, 
  FileCheck, 
  Users, 
  Zap, 
  BarChart3,
  Lock,
  RefreshCw
} from 'lucide-react'

const features = [
  {
    icon: Search,
    title: 'Customer Screening',
    description: 'Real-time screening against global sanctions lists, PEP databases, and adverse media with intelligent fuzzy matching.',
  },
  {
    icon: AlertTriangle,
    title: 'Transaction Monitoring',
    description: 'AI-powered detection of suspicious patterns, structuring attempts, and anomalous behavior across all transactions.',
  },
  {
    icon: FileCheck,
    title: 'KYC/KYB Verification',
    description: 'Streamlined identity verification for individuals and businesses with document validation and biometric checks.',
  },
  {
    icon: Users,
    title: 'Risk Scoring',
    description: 'Dynamic risk profiles that adapt to customer behavior, geography, and transaction patterns in real-time.',
  },
  {
    icon: Zap,
    title: 'Real-Time Alerts',
    description: 'Instant notifications for high-risk activities with customizable thresholds and escalation workflows.',
  },
  {
    icon: BarChart3,
    title: 'Advanced Analytics',
    description: 'Comprehensive reporting and dashboards providing deep insights into compliance metrics and risk exposure.',
  },
  {
    icon: Lock,
    title: 'Data Security',
    description: 'Bank-grade encryption, SOC 2 compliance, and strict data privacy controls to protect sensitive information.',
  },
  {
    icon: RefreshCw,
    title: 'Continuous Monitoring',
    description: 'Ongoing surveillance of customer profiles and transactions with automatic re-screening capabilities.',
  },
]

export default function Features() {
  return (
    <section id="features" className="py-24 bg-white">
      <div className="section-padding">
        <div className="max-w-7xl mx-auto">
          {/* Section Header */}
          <div className="text-center max-w-3xl mx-auto mb-16">
            <span className="text-burgundy-800 font-semibold tracking-wider uppercase text-sm">
              Platform Features
            </span>
            <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-gray-900 mt-4 mb-6">
              Everything You Need for
              <span className="text-burgundy-800"> AML Compliance</span>
            </h2>
            <p className="text-lg text-gray-600">
              HOKEKA combines cutting-edge AI with comprehensive regulatory knowledge 
              to deliver a complete anti-money laundering solution.
            </p>
          </div>

          {/* Features Grid */}
          <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-8">
            {features.map((feature) => (
              <div
                key={feature.title}
                className="group p-6 bg-gray-50 rounded-2xl card-hover border border-gray-100"
              >
                <div className="w-12 h-12 bg-burgundy-800 rounded-xl flex items-center justify-center mb-4 group-hover:bg-gold-500 transition-colors"
                >
                  <feature.icon className="w-6 h-6 text-gold-400 group-hover:text-burgundy-900 transition-colors" />
                </div>
                <h3 className="text-lg font-bold text-gray-900 mb-2">
                  {feature.title}
                </h3>
                <p className="text-gray-600 text-sm leading-relaxed">
                  {feature.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
