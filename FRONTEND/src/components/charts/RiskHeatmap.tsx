import * as Tabs from '@radix-ui/react-tabs'
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { ComposableMap, Geographies, Geography } from 'react-simple-maps'
import DashboardPanel from '../dashboard/DashboardPanel'
import DashboardWidgetHeader from '../dashboard/DashboardWidgetHeader'
import DashboardRiskBadge from '../dashboard/DashboardRiskBadge'
import { DashboardEmpty, DashboardError, DashboardLoading } from '../dashboard/DashboardState'
import { useRiskHeatmap, useTopRiskMerchants, type CountryRisk } from '../../hooks/useDashboard'

const GEO_URL = 'https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json'

const COLORS = {
  LOW: '#079455',
  MEDIUM: '#b54708',
  HIGH: '#d92d20',
  NONE: '#e4e7ec',
}

/** Answers: Where is geographic and merchant risk concentrated? */
export default function RiskHeatmap() {
  const { data, isLoading, error } = useRiskHeatmap()
  const topRisk = useTopRiskMerchants(8)

  const indexAlpha: Record<string, CountryRisk> = useMemo(() => {
    const out: Record<string, CountryRisk> = {}
    for (const c of data ?? []) {
      if (c.countryCode) out[c.countryCode.toUpperCase()] = c
    }
    return out
  }, [data])

  const indexName: Record<string, CountryRisk> = useMemo(() => {
    const out: Record<string, CountryRisk> = {}
    for (const c of data ?? []) {
      if (c.countryName) out[c.countryName.toLowerCase()] = c
    }
    return out
  }, [data])

  const topCountries = useMemo(() => {
    const list = [...(data ?? [])]
    list.sort((a, b) => b.transactionCount - a.transactionCount)
    return list.slice(0, 5)
  }, [data])

  function fillForGeo(geo: {
    properties?: { name?: string; iso_a3?: string }
  }) {
    const iso3 = geo?.properties?.iso_a3?.toUpperCase()
    const name = geo?.properties?.name?.toLowerCase()
    const hit = (iso3 && indexAlpha[iso3]) || (name && indexName[name])
    if (!hit) return COLORS.NONE
    if (hit.riskLevel === 'HIGH') return COLORS.HIGH
    if (hit.riskLevel === 'MEDIUM') return COLORS.MEDIUM
    return COLORS.LOW
  }

  return (
    <DashboardPanel aria-labelledby="db-risk-heatmap" className="h-full">
      <DashboardWidgetHeader
        id="db-risk-heatmap"
        title="Risk concentration"
        description="Where transaction volume meets elevated alert rates"
        actionLabel="Risk analytics"
        actionTo="/risk-analytics"
      />

      <Tabs.Root defaultValue="geography" className="flex min-h-0 flex-1 flex-col">
        <Tabs.List className="db-tabs mb-3" aria-label="Risk concentration views">
          <Tabs.Trigger value="geography" className="db-tab">
            Geography
          </Tabs.Trigger>
          <Tabs.Trigger value="merchants" className="db-tab">
            Merchants
          </Tabs.Trigger>
        </Tabs.List>

        <Tabs.Content value="geography" className="flex min-h-0 flex-1 flex-col outline-none">
          {isLoading && !data ? (
            <DashboardLoading rows={6} />
          ) : error ? (
            <DashboardError message="Geographic risk data unavailable." />
          ) : !(data ?? []).length ? (
            <DashboardEmpty message="No country-level risk aggregates yet." />
          ) : (
            <>
              <div className="min-h-[160px] flex-1 rounded-[var(--db-radius-sm)] border border-[var(--db-border)] bg-[var(--db-surface-muted)]">
                <ComposableMap
                  projectionConfig={{ scale: 120 }}
                  style={{ width: '100%', height: '100%' }}
                  aria-label="World map of country risk"
                >
                  <Geographies geography={GEO_URL}>
                    {({ geographies }) =>
                      geographies.map((geo) => (
                        <Geography
                          key={geo.rsmKey}
                          geography={geo}
                          fill={fillForGeo(geo)}
                          stroke="#ffffff"
                          strokeWidth={0.4}
                          style={{
                            default: { outline: 'none' },
                            hover: { outline: 'none', fill: 'var(--db-accent)' },
                            pressed: { outline: 'none' },
                          }}
                        />
                      ))
                    }
                  </Geographies>
                </ComposableMap>
              </div>

              <div className="mt-2 flex flex-wrap items-center justify-center gap-4 text-[11px] text-[var(--db-text-muted)]">
                <LegendDot color={COLORS.LOW} label="Low" />
                <LegendDot color={COLORS.MEDIUM} label="Medium" />
                <LegendDot color={COLORS.HIGH} label="High" />
              </div>

              {topCountries.length > 0 && (
                <ul className="mt-2 grid grid-cols-1 gap-1 sm:grid-cols-2">
                  {topCountries.map((c) => (
                    <li
                      key={c.countryCode}
                      className="flex items-center justify-between gap-2 rounded-[var(--db-radius-sm)] px-1 py-0.5 text-xs"
                    >
                      <span className="flex min-w-0 items-center gap-1.5 truncate text-[var(--db-text-secondary)]">
                        <span
                          className="inline-block h-1.5 w-1.5 rounded-full"
                          style={{
                            backgroundColor:
                              c.riskLevel === 'HIGH'
                                ? COLORS.HIGH
                                : c.riskLevel === 'MEDIUM'
                                  ? COLORS.MEDIUM
                                  : COLORS.LOW,
                          }}
                          aria-hidden
                        />
                        {c.countryName || c.countryCode}
                      </span>
                      <span className="tabular-nums font-medium text-[var(--db-text)]">
                        {c.transactionCount.toLocaleString()}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </Tabs.Content>

        <Tabs.Content value="merchants" className="flex min-h-0 flex-1 flex-col outline-none">
          {topRisk.isLoading && !topRisk.data ? (
            <DashboardLoading rows={6} />
          ) : topRisk.error ? (
            <DashboardError message="Top-risk merchants unavailable." />
          ) : (topRisk.data ?? []).length === 0 ? (
            <DashboardEmpty message="No high-risk merchants to rank." />
          ) : (
            <ol className="space-y-1.5">
              {(topRisk.data ?? []).map((m) => (
                <li
                  key={m.merchantId ?? m.rank}
                  className="flex items-center justify-between gap-2 rounded-[var(--db-radius-sm)] border border-transparent px-1 py-1.5 text-xs hover:border-[var(--db-border)] hover:bg-[var(--db-surface-muted)]"
                >
                  <div className="flex min-w-0 items-center gap-2">
                    <span className="w-4 tabular-nums text-[var(--db-text-muted)]">{m.rank}.</span>
                    <Link to="/merchants" className="truncate font-medium text-[var(--db-text)] hover:text-[var(--db-accent)]">
                      {m.name ?? `Merchant #${m.merchantId ?? '—'}`}
                    </Link>
                  </div>
                  <div className="flex flex-shrink-0 items-center gap-2">
                    {m.riskScore != null && (
                      <span className="tabular-nums font-semibold text-[var(--db-text)]">
                        {Math.round(m.riskScore)}
                      </span>
                    )}
                    <DashboardRiskBadge level={m.riskLevel} />
                  </div>
                </li>
              ))}
            </ol>
          )}
        </Tabs.Content>
      </Tabs.Root>
    </DashboardPanel>
  )
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-1.5">
      <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: color }} aria-hidden />
      <span>{label}</span>
    </div>
  )
}
