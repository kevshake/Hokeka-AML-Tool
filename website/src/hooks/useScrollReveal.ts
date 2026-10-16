import { useEffect } from 'react'
import { useReducedMotion } from './useReducedMotion'

/** IntersectionObserver reveal for `.reveal` nodes; instant when reduced motion is on. */
export function useScrollReveal(): void {
  const reduced = useReducedMotion()

  useEffect(() => {
    const els = Array.from(document.querySelectorAll<HTMLElement>('.reveal'))
    if (reduced || !('IntersectionObserver' in window)) {
      els.forEach((el) => el.classList.add('in'))
      return
    }
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('in')
            io.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.12, rootMargin: '0px 0px -6% 0px' },
    )
    els.forEach((el) => io.observe(el))
    return () => io.disconnect()
  }, [reduced])
}
