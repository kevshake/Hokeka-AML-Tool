const capabilities = [
  ['Customer 360', 'Link banking, brokerage, e-money and crypto identities into one PSP-scoped customer view.'],
  ['Explainable risk decisions', 'Every alert carries the scenario, score impact and evidence that caused the decision.'],
  [
    'Unified investigation',
    'Move from signal to alert, case timeline, network context and regulatory reporting without losing provenance.',
  ],
  [
    'Operational resilience',
    'Fail-closed screening paths, idempotent ingestion and explicit provider-unavailable states protect decisions under pressure.',
  ],
] as const

/** End-to-end operations capability list. */
export default function OperationsSection() {
  return (
    <section className="operations">
      <div className="container operations-grid">
        <div className="operations-copy reveal">
          <p className="eyebrow">End-to-end operations</p>
          <h2 className="section-title">From first signal to defensible filing.</h2>
          <p className="section-copy">
            Hokeka brings monitoring, screening, investigations and reporting into one evidence chain while keeping
            specialist systems replaceable.
          </p>
        </div>
        <div className="capability-list">
          {capabilities.map(([title, copy], index) => (
            <article className="capability reveal" style={{ ['--i' as string]: index }} key={title}>
              <span>0{index + 1}</span>
              <div>
                <h3>{title}</h3>
                <p>{copy}</p>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
