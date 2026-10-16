const proofItems = [
  ['One customer record', 'Cross-asset identities and accounts'],
  ['One investigation trail', 'Signals, alerts, cases and reports'],
  ['Provider independent', 'Switchable intelligence gateways'],
  ['PSP isolated', 'Tenant-scoped data and decisions'],
] as const

/** Proof strip beneath the hero (original four-up grid). */
export default function ProofSection() {
  return (
    <section className="proof">
      <div className="container proof-grid">
        {proofItems.map((item, i) => (
          <div className="proof-item reveal" style={{ ['--i' as string]: i }} key={item[0]}>
            <strong>{item[0]}</strong>
            <span>{item[1]}</span>
          </div>
        ))}
      </div>
    </section>
  )
}
