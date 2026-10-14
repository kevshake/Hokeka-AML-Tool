import { Bitcoin, Building2, Landmark, Smartphone } from 'lucide-react'

const assets = [
  {
    icon: Landmark,
    title: 'Banking',
    copy: 'Monitor accounts, transfers, counterparties and payment behavior within the same risk story.',
    items: ['Transaction monitoring', 'Sanctions and PEP screening', 'KYC and source of funds'],
  },
  {
    icon: Building2,
    title: 'Securities',
    copy: 'Surface financial crime and market-abuse patterns across brokerage activity and funding rails.',
    items: ['Rapid in-and-out trading', 'Wash and matched-order signals', 'Penny stock and UBO risk'],
  },
  {
    icon: Smartphone,
    title: 'E-money',
    copy: 'Join identity, device, location and merchant context for mobile money and stored value.',
    items: ['Velocity and smurfing', 'Tiered KYC and CDD', 'Device and geolocation risk'],
  },
  {
    icon: Bitcoin,
    title: 'Crypto',
    copy: 'Assess wallet and transaction exposure without locking the platform to one intelligence provider.',
    items: ['Wallet exposure screening', 'Cross-chain and mixer signals', 'Travel Rule completeness'],
  },
]

/** Multi-asset solutions grid (#solutions). */
export default function OfferSection() {
  return (
    <section className="offer" id="solutions">
      <div className="container">
        <div className="split-head reveal">
          <div>
            <p className="eyebrow">Multi-asset coverage</p>
            <h2 className="section-title">Financial crime rarely stays in one rail.</h2>
          </div>
          <p className="section-copy">
            Hokeka follows behavior from fiat funding to brokerage activity, mobile value and on-chain movement,
            preserving the evidence behind every signal.
          </p>
        </div>
        <div className="asset-grid">
          {assets.map(({ icon: Icon, title, copy, items }, i) => (
            <article className="asset-card reveal" style={{ ['--i' as string]: i }} key={title}>
              <div className="asset-icon">
                <Icon size={20} />
              </div>
              <h3>{title}</h3>
              <p>{copy}</p>
              <ul>
                {items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
