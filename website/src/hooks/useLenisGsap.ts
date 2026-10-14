import { useEffect } from 'react'
import { useReducedMotion } from './useReducedMotion'

/** Wire Lenis smooth scroll to GSAP's ticker after idle (keeps first paint light). */
export function useLenisGsap(): void {
  const reduced = useReducedMotion()

  useEffect(() => {
    if (reduced) return

    let cancelled = false
    /** Dynamically load scroll libraries so LCP stays text-first. */
    const boot = async () => {
      const [{ default: gsap }, { ScrollTrigger }, { default: Lenis }] = await Promise.all([
        import('gsap'),
        import('gsap/ScrollTrigger'),
        import('lenis'),
      ])
      if (cancelled) return
      gsap.registerPlugin(ScrollTrigger)

      const lenis = new Lenis({ lerp: 0.1, smoothWheel: true })
      lenis.on('scroll', ScrollTrigger.update)

      const tick = (time: number) => {
        lenis.raf(time * 1000)
      }
      gsap.ticker.add(tick)
      gsap.ticker.lagSmoothing(0)

      const onResize = () => ScrollTrigger.refresh()
      window.addEventListener('resize', onResize)

      return () => {
        window.removeEventListener('resize', onResize)
        gsap.ticker.remove(tick)
        lenis.destroy()
        ScrollTrigger.getAll().forEach((t) => t.kill())
      }
    }

    const idle = window.requestIdleCallback ?? ((cb: () => void) => window.setTimeout(cb, 1))
    let cleanup: (() => void) | undefined
    const idleId = idle(() => {
      boot().then((fn) => {
        cleanup = fn
      })
    })

    return () => {
      cancelled = true
      if (typeof idleId === 'number' && window.cancelIdleCallback) {
        window.cancelIdleCallback(idleId)
      }
      cleanup?.()
    }
  }, [reduced])
}
