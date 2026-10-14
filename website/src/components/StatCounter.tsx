import { useEffect, useRef } from 'react'
import { useReducedMotion } from '../hooks/useReducedMotion'

type Props = {
  value: number
  suffix?: string
  label: string
  decimals?: number
}

/** Scroll-triggered stat counter with Intl formatting and reduced-motion jump. */
export default function StatCounter({ value, suffix = '', label, decimals = 0 }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const reduced = useReducedMotion()

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const output = el.querySelector<HTMLElement>('[data-count]')
    if (!output) return

    const formatter = new Intl.NumberFormat(undefined, {
      maximumFractionDigits: decimals,
      minimumFractionDigits: decimals,
    })

    if (reduced) {
      output.textContent = `${formatter.format(value)}${suffix}`
      return
    }

    /** Load GSAP only when the stat enters view. */
    const run = async () => {
      const [{ default: gsap }, { ScrollTrigger }] = await Promise.all([
        import('gsap'),
        import('gsap/ScrollTrigger'),
      ])
      gsap.registerPlugin(ScrollTrigger)
      const proxy = { v: 0 }
      gsap.to(proxy, {
        v: value,
        duration: 1.4,
        ease: 'power2.out',
        scrollTrigger: { trigger: el, start: 'top 85%', once: true },
        onUpdate: () => {
          output.textContent = `${formatter.format(proxy.v)}${suffix}`
        },
      })
    }

    run()
  }, [value, suffix, decimals, reduced])

  return (
    <div ref={ref} className="stat-counter reveal">
      <strong data-count>
        {value}
        {suffix}
      </strong>
      <span>{label}</span>
    </div>
  )
}
