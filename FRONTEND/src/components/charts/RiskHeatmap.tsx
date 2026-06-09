import * as Tabs from '@radix-ui/react-tabs'
import { ArrowRight } from 'lucide-react'
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { ComposableMap, Geographies, Geography } from 'react-simple-maps'
import GlassCard from '../Common/GlassCard'
import { useRiskHeatmap, type CountryRisk } from '../../hooks/useDashboard'

const GEO_URL = 'https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json'

const COLORS = {
  LOW: '#22C55E',
  MEDIUM: '#F59E0B',
  HIGH: '#EF4444',
  NONE: 'rgba(18, 6, 10, 0.92)',
}

const TABS = [
  { value: 'geography', label: 'Geography' },
  { value: 'merchants', label: 'Merchants' },
  { value: 'customers', label: 'Customers' },
  { value: 'products', label: 'Products' },
]

export default function RiskHeatmap() {
  const { data, isLoading, error } = useRiskHeatmap()

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
    return list.slice(0, 6)
  }, [data])

  function fillForGeo(geo: {
    id?: string | number
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
    <GlassCard padding="sm" glowVariant="red" className="flex h-full min-h-0 flex-col !p-3">
      <div className="mb-1.5 flex items-center justify-between">
        <h3 className="text-xs font-semibold text-white">Risk Heatmap</h3>
        <Link
          to="/risk-analytics"
          className="flex items-center gap-1 text-[10px] font-medium text-gold hover:underline"
        >
          View full map <ArrowRight size={12} />
        </Link>
      </div>

      <Tabs.Root defaultValue="geography" className="flex min-h-0 flex-1 flex-col">
        <Tabs.List className="mb-1.5 flex gap-3 border-b border-glass-border">
          {TABS.map((t) => (
            <Tabs.Trigger
              key={t.value}
              value={t.value}
              className="-mb-px border-b-2 border-transparent pb-1 text-[10px] font-medium text-glass-muted outline-none transition-colors data-[state=active]:border-gold data-[state=active]:text-gold"
            >
              {t.label}
            </Tabs.Trigger>
          ))}
        </Tabs.List>

        <Tabs.Content value="geography" className="flex min-h-0 flex-1 flex-col outline-none">
          {isLoading ? (
            <div className="h-full w-full animate-pulse rounded bg-glass-skeleton" />
          ) : error ? (
            <p className="text-xs text-danger">Could not load</p>
          ) : (
            <>
              <div className="min-h-0 flex-1">
                <ComposableMap
                  projectionConfig={{ scale: 115 }}
                  style={{ width: '100%', height: '100%' }}
                >
                  <Geographies geography={GEO_URL}>
                    {({ geographies }) =>
                      geographies.map((geo) => (
                        <Geography
                          key={geo.rsmKey}
                          geography={geo}
                          fill={fillForGeo(geo)}
                          stroke="rgba(123, 35, 50, 0.35)"
                          strokeWidth={0.4}
                          style={{
                            default: { outline: 'none' },
                            hover: { outline: 'none', fill: '#C9A96E' },
                            pressed: { outline: 'none' },
                          }}
                        />
                      ))
                    }
                  </Geographies>
                </ComposableMap>
              </div>

              <div className="mt-1 flex items-center justify-center gap-4 text-[9px] text-glass-muted">
                <LegendDot color={COLORS.LOW} label="Low" />
                <LegendDot color={COLORS.MEDIUM} label="Medium" />
                <LegendDot color={COLORS.HIGH} label="High" />
              </div>

              {topCountries.length > 0 && (
                <div className="mt-1 grid grid-cols-2 gap-x-3 gap-y-0.5 text-[9px]">
                  {topCountries.map((c) => (
                    <div key={c.countryCode} className="flex items-center justify-between">
                      <span className="flex items-center gap-1.5 truncate text-white/70">
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
                        />
                        {c.countryName || c.countryCode}
                      </span>
                      <span className="font-medium text-white/90">
                        {c.transactionCount.toLocaleString()}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </Tabs.Content>

        {TABS.slice(1).map((t) => (
          <Tabs.Content key={t.value} value={t.value} className="flex flex-1 items-center justify-center">
            <p className="text-[10px] text-glass-muted">No {t.label.toLowerCase()} data available</p>
          </Tabs.Content>
        ))}
      </Tabs.Root>
    </GlassCard>
  )
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-1.5">
      <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: color }} />
      <span>{label}</span>
    </div>
  )
}
