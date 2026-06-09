import { Info } from 'lucide-react'



import { PolarAngleAxis, RadialBar, RadialBarChart, ResponsiveContainer } from 'recharts'



import GlassCard from '../Common/GlassCard'



import { useRiskDistribution } from '../../hooks/useDashboard'







const TRACK_BG = 'rgba(26, 8, 12, 0.85)'







function bandColor(score: number) {



  if (score >= 70) return '#EF4444'



  if (score >= 40) return '#F59E0B'



  return '#22C55E'



}







function bandLabel(score: number) {



  if (score >= 70) return 'High Risk'



  if (score >= 40) return 'Medium Risk'



  return 'Low Risk'



}







export default function RiskGauge() {



  const { data, isLoading, error } = useRiskDistribution()







  const counts = data ?? {}



  const high = Number(counts['HIGH'] ?? 0)



  const medium = Number(counts['MEDIUM'] ?? 0)



  const low = Number(counts['LOW'] ?? 0)



  const total = high + medium + low







  const score =



    total > 0 ? Math.round((high * 100 + medium * 60 + low * 20) / total) : 0







  const pct = (n: number) => (total > 0 ? Math.round((n / total) * 100) : 0)



  const displayPct = {



    high: pct(high),



    medium: pct(medium),



    low: pct(low),



  }







  const fill = bandColor(score)



  const gaugeData = [{ name: 'risk', value: score, fill }]







  return (



    <GlassCard
      padding="sm"
      glowVariant="red"
      glowPosition="center"
      className="flex h-full min-h-0 flex-col !p-3"
    >



      <div className="mb-1.5 flex items-center gap-1.5">



        <h3 className="text-xs font-semibold text-white">Risk Score</h3>



        <Info size={12} className="text-glass-muted" />



      </div>







      <div className="grid min-h-0 flex-1 grid-cols-2 gap-2">



        <div className="relative flex min-h-0 flex-col items-center justify-end">



          {isLoading ? (



            <div className="h-full w-full animate-pulse rounded bg-glass-skeleton" />



          ) : error ? (



            <p className="text-xs text-danger">Could not load</p>



          ) : total === 0 ? (



            <div className="flex h-full w-full items-center justify-center text-[10px] text-glass-muted">



              No risk data



            </div>



          ) : (



            <>



              <div className="pointer-events-none absolute bottom-6 left-1/2 h-[90px] w-[160px] -translate-x-1/2">



                <svg viewBox="0 0 160 90" className="h-full w-full" aria-hidden>



                  <path



                    d="M 12 82 A 68 68 0 0 1 148 82"



                    fill="none"



                    stroke={TRACK_BG}



                    strokeWidth="10"



                    strokeLinecap="round"



                  />



                  <path



                    d="M 12 82 A 68 68 0 0 1 56 28"



                    fill="none"



                    stroke="#22C55E"



                    strokeWidth="8"



                    strokeLinecap="round"



                    opacity="0.55"



                  />



                  <path



                    d="M 56 28 A 68 68 0 0 1 104 28"



                    fill="none"



                    stroke="#F59E0B"



                    strokeWidth="8"



                    strokeLinecap="round"



                    opacity="0.55"



                  />



                  <path



                    d="M 104 28 A 68 68 0 0 1 148 82"



                    fill="none"



                    stroke="#EF4444"



                    strokeWidth="8"



                    strokeLinecap="round"



                    opacity="0.55"



                  />



                </svg>



              </div>







              <div className="chart-neon-red h-full max-h-[118px] w-full min-h-[90px]">



                <ResponsiveContainer width="100%" height="100%">



                  <RadialBarChart



                    cx="50%"



                    cy="85%"



                    innerRadius="70%"



                    outerRadius="130%"



                    startAngle={180}



                    endAngle={0}



                    data={gaugeData}



                  >



                    <PolarAngleAxis type="number" domain={[0, 100]} angleAxisId={0} tick={false} />



                    <RadialBar



                      background={{ fill: TRACK_BG }}



                      dataKey="value"



                      cornerRadius={6}



                      isAnimationActive={false}



                    />



                  </RadialBarChart>



                </ResponsiveContainer>



              </div>



              <div className="-mt-9 text-center">



                <p className="text-2xl font-bold leading-none text-white">{score}</p>



                <p className="text-[10px] text-glass-muted">/ 100</p>



                <p className="mt-0.5 text-[11px] font-medium" style={{ color: fill }}>



                  {bandLabel(score)}



                </p>



              </div>



            </>



          )}



        </div>







        <div className="flex min-h-0 flex-col justify-center gap-2">



          <p className="text-[9px] font-semibold uppercase tracking-widest text-gold">



            Distribution



          </p>



          {total === 0 && !isLoading ? (



            <p className="text-[10px] text-glass-muted">No entities</p>



          ) : (



            <>



              <RiskRow color="#EF4444" label="High" pct={displayPct.high} count={high} />



              <RiskRow color="#F59E0B" label="Medium" pct={displayPct.medium} count={medium} />



              <RiskRow color="#22C55E" label="Low" pct={displayPct.low} count={low} />



            </>



          )}



        </div>



      </div>



    </GlassCard>



  )



}







function RiskRow({



  color,



  label,



  pct,



  count,



}: {



  color: string



  label: string



  pct: number



  count: number



}) {



  return (



    <div>



      <div className="mb-1 flex items-center justify-between">



        <div className="flex items-center gap-1.5">



          <span className="inline-block h-1.5 w-1.5 rounded-full shadow-neon-red" style={{ backgroundColor: color }} />



          <span className="text-[10px] text-white/80">{label}</span>



        </div>



        <span className="text-[10px] font-semibold text-white">{pct}%</span>



      </div>



      <div className="h-1 overflow-hidden rounded-full bg-burgundy-950/80">



        <div className="h-full rounded-full" style={{ width: `${pct}%`, backgroundColor: color, boxShadow: `0 0 6px ${color}88` }} />



      </div>



      <p className="mt-0.5 text-[9px] text-glass-subtle">{count} entities</p>



    </div>



  )



}




