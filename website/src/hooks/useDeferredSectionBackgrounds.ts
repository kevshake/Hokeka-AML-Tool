import { useEffect } from 'react'
import { useAfterLcp } from './useAfterLcp'

/** Apply photographic hero/CTA backgrounds only after LCP (CSS poster until then). */
export function useDeferredSectionBackgrounds(): void {
  const afterLcp = useAfterLcp(4000)

  useEffect(() => {
    if (!afterLcp) return

    /** Attach the loaded class when each background image finishes decoding. */
    const preload = (selector: string, src: string, readyClass: string) => {
      const img = new Image()
      img.decoding = 'async'
      img.src = src
      img.onload = () => {
        document.querySelector(selector)?.classList.add(readyClass)
      }
    }

    const isMobile = window.matchMedia('(max-width: 960px)').matches
    preload('.hero', isMobile ? '/hero-financial-district-mobile.webp' : '/hero-financial-district.webp', 'hero-bg-ready')
    preload('.cta', '/cta-architecture.jpg', 'cta-bg-ready')
  }, [afterLcp])
}
