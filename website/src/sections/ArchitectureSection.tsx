import ArchitectureDiagram from '../components/ArchitectureDiagram'

/** Edge + cloud dual-post architecture diagram section. */
export default function ArchitectureSection() {
  return (
    <section className="architecture-section">
      <div className="container">
        <div className="split-head reveal">
          <div>
            <p className="eyebrow">Edge + cloud</p>
            <h2 className="section-title">Decisions at the edge. Truth in the cloud.</h2>
          </div>
          <p className="section-copy">
            PSP edge nodes evaluate pre-auth with local feature stores; the control plane ingests, investigates and
            files with the same rule bundles and metrics contract.
          </p>
        </div>
        <ArchitectureDiagram />
      </div>
    </section>
  )
}
