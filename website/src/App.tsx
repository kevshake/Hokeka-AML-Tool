import { useEffect } from 'react'
import { Activity, ArrowRight, Bitcoin, Blocks, Building2, FileCheck2, Landmark, MousePointerClick, Network, ShieldCheck, Smartphone, UserRoundSearch } from 'lucide-react'
import Navbar from './components/Navbar'
import Footer from './sections/Footer'

const assets = [
  { icon: Landmark, title: 'Banking', copy: 'Monitor accounts, transfers, counterparties and payment behavior within the same risk story.', items: ['Transaction monitoring', 'Sanctions and PEP screening', 'KYC and source of funds'] },
  { icon: Building2, title: 'Securities', copy: 'Surface financial crime and market-abuse patterns across brokerage activity and funding rails.', items: ['Rapid in-and-out trading', 'Wash and matched-order signals', 'Penny stock and UBO risk'] },
  { icon: Smartphone, title: 'E-money', copy: 'Join identity, device, location and merchant context for mobile money and stored value.', items: ['Velocity and smurfing', 'Tiered KYC and CDD', 'Device and geolocation risk'] },
  { icon: Bitcoin, title: 'Crypto', copy: 'Assess wallet and transaction exposure without locking the platform to one intelligence provider.', items: ['Wallet exposure screening', 'Cross-chain and mixer signals', 'Travel Rule completeness'] },
]

const capabilities = [
  ['Customer 360', 'Link banking, brokerage, e-money and crypto identities into one PSP-scoped customer view.'],
  ['Explainable risk decisions', 'Every alert carries the scenario, score impact and evidence that caused the decision.'],
  ['Unified investigation', 'Move from signal to alert, case timeline, network context and regulatory reporting without losing provenance.'],
  ['Operational resilience', 'Fail-closed screening paths, idempotent ingestion and explicit provider-unavailable states protect decisions under pressure.'],
]

/** Scroll-reveal: fade/slide elements in as they enter the viewport (once). */
function useScrollReveal() {
  useEffect(() => {
    const els = Array.from(document.querySelectorAll<HTMLElement>('.reveal'))
    if (!('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      els.forEach(el => el.classList.add('in'))
      return
    }
    const io = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('in')
          io.unobserve(entry.target)
        }
      })
    }, { threshold: 0.15, rootMargin: '0px 0px -8% 0px' })
    els.forEach(el => io.observe(el))
    return () => io.disconnect()
  }, [])
}

function ProductVisual() {
  const rows = [
    ['Securities · Sell', 'Rapid liquidation', 'Funding proximity', 'Review'],
    ['E-money · Top up', 'Device velocity', 'Known device', 'Allow'],
    ['Crypto · Transfer', 'Wallet exposure', 'Travel Rule check', 'Review'],
    ['Banking · Deposit', 'Third-party funding', 'Counterparty link', 'Alert'],
  ]
  return <div className="product-frame reveal" aria-label="Hokeka Customer 360 product interface">
    <aside className="product-nav"><div className="product-brand"><img src="/hokeka-logo.png" alt="" /> Hokeka</div><p>Intelligence</p><span><Activity size={14}/> Live monitoring</span><span className="active"><UserRoundSearch size={14}/> Customer 360</span><span><ShieldCheck size={14}/> Screening</span><p>Investigations</p><span><Network size={14}/> Network view</span><span><FileCheck2 size={14}/> Reports</span></aside>
    <div className="product-main"><div className="product-top"><div><h3>Customer 360</h3><p>Cross-asset identity and activity intelligence</p></div><div className="live-status"><i/> Provider gateways connected</div></div><div className="customer-line"><div><strong>Customer investigation</strong><span>ENTITY · PSP-SCOPED RECORD · KYC REVIEW</span></div><div className="risk-readout"><div><span>Composite risk</span><b>Evidence-led</b></div><div><span>Decision</span><b>Review</b></div></div></div><div className="exposure-row">{['Banking', 'Securities', 'E-money', 'Crypto'].map(label => <div key={label}><span>{label}</span><strong>Linked</strong></div>)}</div><div className="activity-table"><div className="activity-head"><span>Asset / activity</span><span>Risk context</span><span>Evidence</span><span>Decision</span></div>{rows.map((row, index) => <div className="activity-row" key={row[0]}><span className="asset-label"><i style={{background: ['#d3b371','#75b7ab','#c27463','#8796d8'][index]}}/>{row[0]}</span><span>{row[1]}</span><span>{row[2]}</span><span className={row[3] === 'Allow' ? 'clear' : 'review'}>{row[3]}</span></div>)}</div></div>
  </div>
}

export default function App() {
  useScrollReveal()
  return <div className="site-shell"><Navbar/><main>
    <section className="hero" id="home"><div className="container hero-content"><p className="eyebrow">Unified financial crime intelligence</p><h1>Hokeka</h1><p className="hero-statement">See the whole financial story, across every asset.</p><p className="hero-copy">One operational platform for banking, securities, e-money and crypto AML. Link identity, movement and risk into decisions investigators can explain.</p><div className="hero-actions"><a className="button" href="mailto:info@hokeka.com?subject=Hokeka%20platform%20demo">Request a private demo <ArrowRight size={16}/></a><a className="button quiet" href="#platform">Explore the platform</a></div></div><div className="hero-footnote"><ShieldCheck size={15}/> Built for regulated financial institutions</div><div className="hero-scroll" aria-hidden="true"/></section>
    <section className="proof"><div className="container proof-grid">{[['One customer record','Cross-asset identities and accounts'],['One investigation trail','Signals, alerts, cases and reports'],['Provider independent','Switchable intelligence gateways'],['PSP isolated','Tenant-scoped data and decisions']].map((item, i) => <div className="proof-item reveal" style={{['--i' as string]: i}} key={item[0]}><strong>{item[0]}</strong><span>{item[1]}</span></div>)}</div></section>
    <section className="offer" id="solutions"><div className="container"><div className="split-head reveal"><div><p className="eyebrow">Multi-asset coverage</p><h2 className="section-title">Financial crime rarely stays in one rail.</h2></div><p className="section-copy">Hokeka follows behavior from fiat funding to brokerage activity, mobile value and on-chain movement, preserving the evidence behind every signal.</p></div><div className="asset-grid">{assets.map(({icon: Icon, title, copy, items}, i) => <article className="asset-card reveal" style={{['--i' as string]: i}} key={title}><div className="asset-icon"><Icon size={20}/></div><h3>{title}</h3><p>{copy}</p><ul>{items.map(item => <li key={item}>{item}</li>)}</ul></article>)}</div></div></section>
    <section className="command" id="platform"><div className="container"><div className="split-head reveal"><div><p className="eyebrow">Investigation command center</p><h2 className="section-title">Context before conclusions.</h2></div><p className="section-copy">A quiet operational workspace for searching customers, linking accounts, ingesting activity and reviewing explainable risk signals.</p></div><ProductVisual/></div></section>
    <section className="operations"><div className="container operations-grid"><div className="operations-copy reveal"><p className="eyebrow">End-to-end operations</p><h2 className="section-title">From first signal to defensible filing.</h2><p className="section-copy">Hokeka brings monitoring, screening, investigations and reporting into one evidence chain while keeping specialist systems replaceable.</p></div><div className="capability-list">{capabilities.map(([title, copy], index) => <article className="capability reveal" style={{['--i' as string]: index}} key={title}><span>0{index + 1}</span><div><h3>{title}</h3><p>{copy}</p></div></article>)}</div></div></section>
    <section className="integrations" id="integrations"><div className="container integration-grid"><div className="integration-visual reveal" aria-label="Provider-independent integration architecture"><div className="core"><Blocks size={24}/><span>Hokeka<br/>risk core</span></div>{['Blockchain analytics','Sanctions data','Core banking','Case and filing rails'].map(value => <span className="provider" key={value}>{value}</span>)}</div><div className="reveal"><p className="eyebrow">API-first by design</p><h2 className="section-title">Your providers can change. Your operating model stays whole.</h2><p className="section-copy">Normalized gateways separate risk decisions from vendor-specific responses. Connect specialist intelligence, payment rails and reporting channels through stable contracts.</p><a className="button quiet" href="mailto:info@hokeka.com?subject=Hokeka%20integration%20architecture" style={{marginTop: 28}}>Discuss your architecture <ArrowRight size={16}/></a></div></div></section>
    <section className="cta" id="contact"><div className="container cta-inner reveal"><p className="eyebrow">A broader view of risk</p><h2 className="section-title">Bring the hidden connections into focus.</h2><p>Talk with the Hokeka team about your asset mix, investigative workflow and regulatory reporting environment.</p><div className="hero-actions"><a className="button" href="mailto:info@hokeka.com?subject=Hokeka%20private%20demo">Request a private demo <ArrowRight size={16}/></a><a className="button quiet" href="mailto:info@hokeka.com"><MousePointerClick size={16}/> info@hokeka.com</a></div></div></section>
  </main><Footer/></div>
}
