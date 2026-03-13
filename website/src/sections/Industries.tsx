import { Building2, Landmark, Wallet, ShoppingCart, Plane, Gamepad2 } from 'lucide-react'

const industries = [
  {
    icon: Landmark,
    name: 'Banking',
    description: 'Comprehensive AML compliance for retail and commercial banks with real-time transaction monitoring.',
  },
  {
    icon: Wallet,
    name: 'Fintech',
    description: 'Scalable solutions for digital wallets, payment processors, and neobanks.',
  },
  {
    icon: Building2,
    name: 'Insurance',
    description: 'Prevent insurance fraud and ensure compliance with industry regulations.',
  },
  {
    icon: ShoppingCart,
    name: 'Marketplaces',
    description: 'Protect your platform from illicit activity with seller and buyer verification.',
  },
  {
    icon: Gamepad2,
    name: 'iGaming',
    description: 'Meet regulatory requirements for online gaming and betting platforms.',
  },
  {
    icon: Plane,
    name: 'Remittance',
    description: 'Secure cross-border payment monitoring and compliance for money transfer services.',
  },
]

export default function Industries() {
  return (
    <section id="industries" className="py-24 bg-white">
      <div className="section-padding">
        <div className="max-w-7xl mx-auto">
          {/* Section Header */}
          <div className="text-center max-w-3xl mx-auto mb-16">
            <span className="text-burgundy-800 font-semibold tracking-wider uppercase text-sm">
              Industries We Serve
            </span>
            <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-gray-900 mt-4 mb-6">
              Trusted Across
              <span className="text-burgundy-800"> Industries</span>
            </h2>
            <p className="text-lg text-gray-600">
              HOKEKA serves diverse sectors with tailored compliance solutions 
              designed for specific regulatory requirements.
            </p>
          </div>

          {/* Industries Grid */}
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
            {industries.map((industry) => (
              <div
                key={industry.name}
                className="group p-8 bg-gradient-to-br from-gray-50 to-white rounded-2xl border border-gray-100 card-hover"
              >
                <div className="w-14 h-14 bg-burgundy-800 rounded-xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform"
                >
                  <industry.icon className="w-7 h-7 text-gold-400" />
                </div>
                <h3 className="text-xl font-bold text-gray-900 mb-3">
                  {industry.name}
                </h3>
                <p className="text-gray-600 leading-relaxed">
                  {industry.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
