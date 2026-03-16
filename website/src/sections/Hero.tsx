import { ArrowRight, Play } from 'lucide-react'

export default function Hero() {
  return (
    <section className="relative min-h-screen gradient-burgundy overflow-hidden">
      {/* Background Pattern */}
      <div className="absolute inset-0 opacity-10">
        <div className="absolute inset-0" style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23FFD700' fill-opacity='0.4'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E")`,
        }} />
      </div>

      {/* Gradient Overlay */}
      <div className="absolute inset-0 bg-gradient-to-b from-burgundy-900/50 to-burgundy-950" />

      <div className="relative z-10 section-padding pt-32 pb-20">
        <div className="max-w-7xl mx-auto">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            {/* Left Content */}
            <div className="text-center lg:text-left">
              <div className="inline-flex items-center gap-2 px-4 py-2 bg-white/10 rounded-full mb-6 border border-gold-500/30">
                <span className="w-2 h-2 bg-green-400 rounded-full animate-pulse" />
                <span className="text-gold-300 text-sm font-medium">Next-Gen AML Compliance</span>
              </div>

              <h1 className="text-4xl sm:text-5xl lg:text-6xl xl:text-7xl font-bold text-white leading-tight mb-6">
                AI-Powered
                <span className="block text-gradient-gold">
                  Fraud Detection
                </span>
              </h1>

              <p className="text-lg sm:text-xl text-white/80 mb-8 max-w-xl mx-auto lg:mx-0">
                HOKEKA delivers intelligent Anti-Money Laundering solutions that 
                protect your business, ensure regulatory compliance, and build 
                customer trust across Africa and beyond.
              </p>

              <div className="flex flex-col sm:flex-row gap-4 justify-center lg:justify-start">
                <a href="#features" className="btn-primary flex items-center justify-center gap-2">
                  Explore Features
                  <ArrowRight className="w-5 h-5" />
                </a>
                <a href="#how-it-works" className="btn-secondary flex items-center justify-center gap-2">
                  <Play className="w-5 h-5" />
                  See How It Works
                </a>
              </div>

              {/* Trust Badges */}
              <div className="mt-12 pt-8 border-t border-white/10">
                <p className="text-white/60 text-sm mb-4">Trusted by leading financial institutions</p>
                <div className="flex flex-wrap justify-center lg:justify-start gap-6 opacity-60">
                  {['Banking', 'Fintech', 'Insurance', 'Crypto'].map((industry) => (
                    <span key={industry} className="text-white/70 font-semibold">
                      {industry}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            {/* Right Content - Dashboard Preview */}
            <div className="relative">
              <div className="relative bg-white rounded-2xl shadow-2xl overflow-hidden border-4 border-gold-500/20">
                {/* Mock Dashboard Header */}
                <div className="bg-burgundy-900 px-6 py-4 flex items-center gap-2">
                  <div className="flex gap-2">
                    <div className="w-3 h-3 rounded-full bg-red-500" />
                    <div className="w-3 h-3 rounded-full bg-yellow-500" />
                    <div className="w-3 h-3 rounded-full bg-green-500" />
                  </div>
                  <span className="ml-4 text-gold-400 font-semibold">HOKEKA Dashboard</span>
                </div>

                {/* Mock Dashboard Content */}
                <div className="p-6 bg-gray-50">
                  {/* Stats Row */}
                  <div className="grid grid-cols-3 gap-4 mb-6">
                    {[
                      { label: 'Transactions', value: '2.4M', color: 'bg-blue-500' },
                      { label: 'Alerts', value: '127', color: 'bg-red-500' },
                      { label: 'Verified', value: '98.7%', color: 'bg-green-500' },
                    ].map((stat) => (
                      <div key={stat.label} className="bg-white p-4 rounded-lg shadow">
                        <div className={`w-8 h-8 ${stat.color} rounded-lg mb-2`} />
                        <p className="text-2xl font-bold text-gray-800">{stat.value}</p>
                        <p className="text-xs text-gray-500">{stat.label}</p>
                      </div>
                    ))}
                  </div>

                  {/* Chart Placeholder */}
                  <div className="bg-white p-4 rounded-lg shadow mb-4">
                    <div className="flex items-center justify-between mb-4">
                      <h3 className="font-semibold text-gray-700">Risk Analysis</h3>
                      <span className="text-xs text-gray-500">Last 30 days</span>
                    </div>
                    <div className="h-32 flex items-end gap-2">
                      {[40, 65, 45, 80, 55, 70, 45, 60, 75, 50, 85, 60].map((h, i) => (
                        <div
                          key={i}
                          className="flex-1 bg-gradient-to-t from-burgundy-600 to-burgundy-400 rounded-t"
                          style={{ height: `${h}%` }}
                        />
                      ))}
                    </div>
                  </div>

                  {/* Alert List */}
                  <div className="bg-white p-4 rounded-lg shadow">
                    <h3 className="font-semibold text-gray-700 mb-3">Recent Alerts</h3>
                    {[
                      { risk: 'High', text: 'Unusual transaction pattern detected', time: '2m ago' },
                      { risk: 'Medium', text: 'New high-risk jurisdiction flagged', time: '15m ago' },
                      { risk: 'Low', text: 'PEP screening match requires review', time: '1h ago' },
                    ].map((alert, i) => (
                      <div key={i} className="flex items-center gap-3 py-2 border-b last:border-0">
                        <span className={`px-2 py-1 text-xs rounded ${
                          alert.risk === 'High' ? 'bg-red-100 text-red-700' :
                          alert.risk === 'Medium' ? 'bg-yellow-100 text-yellow-700' :
                          'bg-blue-100 text-blue-700'
                        }`}>
                          {alert.risk}
                        </span>
                        <span className="flex-1 text-sm text-gray-600 truncate">{alert.text}</span>
                        <span className="text-xs text-gray-400">{alert.time}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {/* Decorative Elements */}
              <div className="absolute -top-4 -right-4 w-24 h-24 bg-gold-500/20 rounded-full blur-2xl" />
              <div className="absolute -bottom-4 -left-4 w-32 h-32 bg-burgundy-400/20 rounded-full blur-3xl" />
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
