import { TrendingUp, Shield, Clock, Globe } from 'lucide-react'

const stats = [
  {
    icon: TrendingUp,
    value: '99.9%',
    label: 'Uptime SLA',
    description: 'Enterprise-grade reliability',
  },
  {
    icon: Shield,
    value: '50M+',
    label: 'Transactions Monitored',
    description: 'Daily fraud detection',
  },
  {
    icon: Clock,
    value: '<50ms',
    label: 'Response Time',
    description: 'Real-time screening',
  },
  {
    icon: Globe,
    value: '195+',
    label: 'Countries Covered',
    description: 'Global compliance',
  },
]

export default function Stats() {
  return (
    <section className="py-16 bg-burgundy-950">
      <div className="section-padding">
        <div className="max-w-7xl mx-auto">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-8">
            {stats.map((stat) => (
              <div key={stat.label} className="text-center">
                <div className="inline-flex items-center justify-center w-12 h-12 bg-gold-500/10 rounded-xl mb-4">
                  <stat.icon className="w-6 h-6 text-gold-500" />
                </div>
                <p className="text-3xl sm:text-4xl font-bold text-white mb-1">
                  {stat.value}
                </p>
                <p className="text-gold-400 font-semibold mb-1">{stat.label}</p>
                <p className="text-white/60 text-sm">{stat.description}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
