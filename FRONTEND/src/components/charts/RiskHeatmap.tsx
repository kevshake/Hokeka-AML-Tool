import * as Tabs from '@radix-ui/react-tabs'
import { ArrowRight } from 'lucide-react'
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { ComposableMap, Geographies, Geography } from 'react-simple-maps'
import { useRiskHeatmap, type CountryRisk } from '../../hooks/useDashboard'

// World atlas TopoJSON — IDs are ISO-3166-1 numeric codes as strings.
const GEO_URL = 'https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json'

const COLORS = {
  LOW: '#86EFAC',
  MEDIUM: '#FCD34D',
  HIGH: '#DC2626',
  NONE: '#E5E7EB',
}

const TABS = [
  { value: 'geography', label: 'Geography' },
  { value: 'merchants', label: 'Merchants' },
  { value: 'customers', label: 'Customers' },
  { value: 'products', label: 'Products' },
]

export default function RiskHeatmap() {
  const { data, isLoading, error } = useRiskHeatmap()

  // Index by both ISO-3 alpha code and country name (lowercased) so the map
  // tile can look up its band by whichever identifier the topojson exposes.
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
    return list.slice(0, 8)
  }, [data])

  function fillForGeo(geo: { id?: string | number; properties?: { name?: string; iso_a3?: string } }) {
    const iso3 = geo?.properties?.iso_a3?.toUpperCase()
    const name = geo?.properties?.name?.toLowerCase()
    const hit = (iso3 && indexAlpha[iso3]) || (name && indexName[name])
    if (!hit) return COLORS.NONE
    if (hit.riskLevel === 'HIGH') return COLORS.HIGH
    if (hit.riskLevel === 'MEDIUM') return COLORS.MEDIUM
    return COLORS.LOW
  }

  return (
    <div className="rounded-2xl border border-hokeka-border bg-hokeka-card p-6">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-base font-semibold text-slate-900">Risk Heatmap</h3>
        <Link
          to="/risk-analytics"
          className="flex items-center gap-1 text-sm font-medium text-hokeka-secondary hover:underline"
        >
          View full map <ArrowRight size={14} />
        </Link>
      </div>

      <Tabs.Root defaultValue="geography" className="flex flex-col">
        <Tabs.List className="mb-4 flex gap-6 border-b border-hokeka-border">
          {TABS.map((t) => (
            <Tabs.Trigger
              key={t.value}
              value={t.value}
              className="-mb-px border-b-2 border-transparent pb-2 text-sm font-medium text-slate-500 outline-none transition-colors data-[state=active]:border-hokeka-secondary data-[state=active]:text-hokeka-secondary"
            >
              {t.label}
            </Tabs.Trigger>
          ))}
        </Tabs.List>

        <Tabs.Content value="geography" className="outline-none">
          {isLoading ? (
            <div className="h-[260px] w-full animate-pulse rounded bg-slate-100" />
          ) : error ? (
            <p className="text-sm text-hokeka-critical">Could not load</p>
          ) : (
            <>
              <div className="h-[260px] w-full">
                <ComposableMap
                  projectionConfig={{ scale: 140 }}
                  style={{ width: '100%', height: '100%' }}
                >
                  <Geographies geography={GEO_URL}>
                    {({ geographies }) =>
                      geographies.map((geo) => (
                        <Geography
                          key={geo.rsmKey}
                          geography={geo}
                          fill={fillForGeo(geo)}
                          stroke="#FFFFFF"
                          strokeWidth={0.5}
                          style={{
                            default: { outline: 'none' },
                            hover: { outline: 'none', fill: '#1F6FEB' },
                            pressed: { outline: 'none' },
                          }}
                        />
                      ))
                    }
                  </Geographies>
                </ComposableMap>
              </div>

              <div className="mt-4 flex items-center justify-center gap-6 text-xs text-slate-500">
                <LegendDot color={COLORS.LOW} label="Low" />
                <LegendDot color={COLORS.MEDIUM} label="Medium" />
                <LegendDot color={COLORS.HIGH} label="High" />
                <LegendDot color={COLORS.NONE} label="No data" />
              </div>

              {topCountries.length > 0 && (
                <div className="mt-4 grid grid-cols-2 gap-x-6 gap-y-1 text-xs text-slate-600">
                  {topCountries.map((c) => (
                    <div key={c.countryCode} className="flex items-center justify-between">
                      <span className="flex items-center gap-2 truncate">
                        <span
                          className="inline-block h-2 w-2 rounded-full"
                          style={{
                            backgroundColor:
                              c.riskLevel === 'HIGH'
                                ? COLORS.HIGH
                                : c.riskLevel === 'MEDIUM'
                                  ? COLORS.MEDIUM
                                  : COLORS.LOW,
                          }}
                        />
                        <span className="truncate">{c.countryName || c.countryCode}</span>
                      </span>
                      <span className="font-medium text-slate-700">
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
          <Tabs.Content key={t.value} value={t.value}>
            <div className="flex h-[260px] items-center justify-center text-sm text-slate-400">
              No {t.label.toLowerCase()} data available
            </div>
          </Tabs.Content>
        ))}
      </Tabs.Root>
    </div>
  )
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-1.5">
      <span
        className="inline-block h-2.5 w-2.5 rounded-full"
        style={{ backgroundColor: color }}
      />
      <span>{label}</span>
    </div>
  )
}
