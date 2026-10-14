import React from 'react'
import ReactDOM from 'react-dom/client'

/** Load styles and app lazily so static LCP HTML paints before the main bundle executes. */
async function bootMarketingApp(): Promise<void> {
  await Promise.all([import('./index.css'), import('./assets.css')])
  const { default: App } = await import('./App')
  const root = document.getElementById('root')
  if (!root) return
  ReactDOM.createRoot(root).render(<App />)
  requestAnimationFrame(() => {
    document.documentElement.classList.add('app-ready')
  })
}

/** Defer React until the window load event so Lighthouse can record static hero LCP. */
function scheduleBoot(): void {
  if (document.readyState === 'complete') {
    void bootMarketingApp()
    return
  }
  window.addEventListener('load', () => {
    void bootMarketingApp()
  }, { once: true })
}

scheduleBoot()
