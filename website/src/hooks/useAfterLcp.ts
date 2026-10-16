import { useEffect, useState } from 'react'

/** Flip true after the first LCP (or a short fallback) so heavy hero work can start. */
export function useAfterLcp(fallbackMs = 2500): boolean {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    /** Mark ready once so canvas / secondary effects can mount. */
    const markReady = () => setReady(true)

    if (typeof PerformanceObserver === 'undefined') {
      markReady()
      return
    }

    let observer: PerformanceObserver | undefined
    try {
      observer = new PerformanceObserver(() => {
        markReady()
        observer?.disconnect()
      })
      observer.observe({ type: 'largest-contentful-paint', buffered: true })
    } catch {
      markReady()
    }

    const fallback = window.setTimeout(markReady, fallbackMs)
    return () => {
      observer?.disconnect()
      window.clearTimeout(fallback)
    }
  }, [fallbackMs])

  return ready
}
