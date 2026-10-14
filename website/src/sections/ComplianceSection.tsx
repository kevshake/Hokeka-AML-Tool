const logos = ['ISO 27001 aligned', 'GDPR-ready processing', 'Audit trail by default', 'PSP tenant isolation']

/** Compliance and trust strip (static labels, no third-party marks). */
export default function ComplianceSection() {
  return (
    <section className="compliance-strip" aria-label="Compliance posture">
      <div className="container compliance-grid">
        {logos.map((label, i) => (
          <div className="compliance-item reveal" style={{ ['--i' as string]: i }} key={label}>
            {label}
          </div>
        ))}
      </div>
    </section>
  )
}
