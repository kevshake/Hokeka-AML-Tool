import { useEffect } from 'react'

/** Apply photographic hero/CTA backgrounds after first paint to protect LCP. */
export function useDeferredSectionBackgrounds(): void {
  useEffect(() => {
    /** Attach the loaded class when each background image finishes decoding. */
    const preload = (selector: string, src: string, readyClass: string) => {
      const img = new Image()
      img.decoding = 'async'
      img.src = src
      img.onload = () => {
        document.querySelector(selector)?.classList.add(readyClass)
      }
    }
    preload('.hero', '/hero-financial-district.webp', 'hero-bg-ready')
    preload('.cta', '/cta-architecture.jpg', 'cta-bg-ready')
  }, [])
}
