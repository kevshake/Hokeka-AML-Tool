import { Activity, FileCheck2, Network, ShieldCheck, UserRoundSearch } from 'lucide-react'

/** Static product mock preserved from the original marketing site. */
export default function ProductVisual() {
  const rows = [
    ['Securities · Sell', 'Rapid liquidation', 'Funding proximity', 'Review'],
    ['E-money · Top up', 'Device velocity', 'Known device', 'Allow'],
    ['Crypto · Transfer', 'Wallet exposure', 'Travel Rule check', 'Review'],
    ['Banking · Deposit', 'Third-party funding', 'Counterparty link', 'Alert'],
  ]

  return (
    <div className="product-frame reveal" aria-label="Hokeka Customer 360 product interface">
      <aside className="product-nav">
        <div className="product-brand">
          <img src="/hokeka-logo.png" alt="" /> Hokeka
        </div>
        <p>Intelligence</p>
        <span>
          <Activity size={14} /> Live monitoring
        </span>
        <span className="active">
          <UserRoundSearch size={14} /> Customer 360
        </span>
        <span>
          <ShieldCheck size={14} /> Screening
        </span>
        <p>Investigations</p>
        <span>
          <Network size={14} /> Network view
        </span>
        <span>
          <FileCheck2 size={14} /> Reports
        </span>
      </aside>
      <div className="product-main">
        <div className="product-top">
          <div>
            <h3>Customer 360</h3>
            <p>Cross-asset identity and activity intelligence</p>
          </div>
          <div className="live-status">
            <i /> Provider gateways connected
          </div>
        </div>
        <div className="customer-line">
          <div>
            <strong>Customer investigation</strong>
            <span>ENTITY · PSP-SCOPED RECORD · KYC REVIEW</span>
          </div>
          <div className="risk-readout">
            <div>
              <span>Composite risk</span>
              <b>Evidence-led</b>
            </div>
            <div>
              <span>Decision</span>
              <b>Review</b>
            </div>
          </div>
        </div>
        <div className="exposure-row">
          {['Banking', 'Securities', 'E-money', 'Crypto'].map((label) => (
            <div key={label}>
              <span>{label}</span>
              <strong>Linked</strong>
            </div>
          ))}
        </div>
        <div className="activity-table">
          <div className="activity-head">
            <span>Asset / activity</span>
            <span>Risk context</span>
            <span>Evidence</span>
            <span>Decision</span>
          </div>
          {rows.map((row, index) => (
            <div className="activity-row" key={row[0]}>
              <span className="asset-label">
                <i style={{ background: ['#d3b371', '#75b7ab', '#c27463', '#8796d8'][index] }} />
                {row[0]}
              </span>
              <span>{row[1]}</span>
              <span>{row[2]}</span>
              <span className={row[3] === 'Allow' ? 'clear' : 'review'}>{row[3]}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
