import { ArrowRight } from 'lucide-react'



import { Link } from 'react-router-dom'



import {



  CartesianGrid,



  Line,



  LineChart,



  ResponsiveContainer,



  Tooltip,



  XAxis,



  YAxis,



} from 'recharts'



import GlassCard from '../Common/GlassCard'



import { useAlertTrends } from '../../hooks/useDashboard'







const GRID_STROKE = 'rgba(123, 35, 50, 0.22)'



const TICK_FILL = 'rgba(255, 255, 255, 0.42)'



const LINE_COLOR = '#EF4444'







export default function AlertTrends() {



  const { data, isLoading, error } = useAlertTrends(7)







  const labels = data?.labels ?? []



  const series = data?.data ?? []



  const chartData = labels.map((l, i) => ({ label: l, value: series[i] ?? 0 }))



  const hasData = chartData.some((d) => d.value > 0)







  return (



    <GlassCard padding="sm" glowVariant="red" className="flex h-full min-h-0 flex-col !p-3">



      <div className="mb-1.5 flex items-start justify-between">



        <div>



          <h3 className="text-xs font-semibold text-white">Alert Trends</h3>



          <p className="text-[9px] text-glass-muted">Last 7 days</p>



        </div>



        <Link



          to="/analytics"



          className="flex items-center gap-1 text-[10px] font-medium text-gold hover:underline"



        >



          View report <ArrowRight size={12} />



        </Link>



      </div>







      {isLoading ? (



        <div className="min-h-0 flex-1 animate-pulse rounded bg-glass-skeleton" />



      ) : error ? (



        <p className="text-xs text-danger">Could not load</p>



      ) : !hasData ? (



        <div className="flex min-h-0 flex-1 items-center justify-center text-[10px] text-glass-muted">



          No trend data



        </div>



      ) : (



        <div className="chart-neon-red min-h-0 w-full flex-1">



          <ResponsiveContainer width="100%" height="100%">



            <LineChart data={chartData} margin={{ top: 4, right: 4, bottom: 0, left: -22 }}>



              <CartesianGrid strokeDasharray="3 3" stroke={GRID_STROKE} vertical={false} />



              <XAxis



                dataKey="label"



                tick={{ fontSize: 10, fill: TICK_FILL }}



                axisLine={false}



                tickLine={false}



              />



              <YAxis



                tick={{ fontSize: 10, fill: TICK_FILL }}



                axisLine={false}



                tickLine={false}



              />



              <Tooltip



                contentStyle={{



                  borderRadius: 8,



                  border: '1px solid rgba(123, 35, 50, 0.45)',



                  background: 'rgba(10, 8, 10, 0.95)',



                  fontSize: 11,



                  color: '#fff',



                  boxShadow: '0 0 16px rgba(220, 38, 38, 0.15)',



                }}



              />



              <Line



                type="monotone"



                dataKey="value"



                stroke={LINE_COLOR}



                strokeWidth={2}



                dot={{ r: 3, fill: LINE_COLOR, strokeWidth: 0 }}



                activeDot={{ r: 5, fill: LINE_COLOR, stroke: '#fff', strokeWidth: 1 }}



                isAnimationActive={false}



              />



            </LineChart>



          </ResponsiveContainer>



        </div>



      )}



    </GlassCard>



  )



}




