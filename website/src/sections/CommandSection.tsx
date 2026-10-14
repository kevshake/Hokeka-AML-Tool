import ProductVisual from '../components/ProductVisual'

/** Investigation command center with Customer 360 product mock (#platform). */
export default function CommandSection() {
  return (
    <section className="command" id="platform">
      <div className="container">
        <div className="split-head reveal">
          <div>
            <p className="eyebrow">Investigation command center</p>
            <h2 className="section-title">Context before conclusions.</h2>
          </div>
          <p className="section-copy">
            A quiet operational workspace for searching customers, linking accounts, ingesting activity and reviewing
            explainable risk signals.
          </p>
        </div>
        <ProductVisual />
      </div>
    </section>
  )
}
