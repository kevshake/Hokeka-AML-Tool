import { useEffect } from 'react'

/** Wrap in-page hash navigation with the View Transitions API when supported. */
export function useViewTransitionNav(): void {
  useEffect(() => {
    /** Navigate to a hash target, optionally inside a view transition. */
    const navigate = (hash: string) => {
      const run = () => {
        window.location.hash = hash
        const target = document.querySelector(hash)
        target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
      if (document.startViewTransition && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        document.startViewTransition(run)
      } else {
        run()
      }
    }

    /** Intercept same-page anchor clicks for transition-friendly scrolling. */
    const onClick = (event: MouseEvent) => {
      const anchor = (event.target as HTMLElement | null)?.closest<HTMLAnchorElement>('a[href^="#"]')
      if (!anchor || anchor.getAttribute('href') === '#') return
      const href = anchor.getAttribute('href')
      if (!href?.startsWith('#')) return
      event.preventDefault()
      navigate(href)
    }

    document.addEventListener('click', onClick)
    return () => document.removeEventListener('click', onClick)
  }, [])
}
