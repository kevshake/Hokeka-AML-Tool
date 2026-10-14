import StatCounter from '../components/StatCounter'

/** Animated operational stats (scroll-triggered counters). */
export default function StatsSection() {
  return (
    <section className="stats-strip" aria-label="Platform scale indicators">
      <div className="container stats-grid">
        <StatCounter value={4} label="Asset classes unified" />
        <StatCounter value={99.95} suffix="%" label="Screening path availability target" decimals={2} />
        <StatCounter value={120} suffix="+" label="Investigation-ready data points per entity" />
        <StatCounter value={24} suffix="/7" label="Monitoring & alert pipeline" />
      </div>
    </section>
  )
}
