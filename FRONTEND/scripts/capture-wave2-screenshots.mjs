import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const outDir = '/opt/cursor/artifacts'
mkdirSync(outDir, { recursive: true })

const routes = [
  { path: '/dashboard', name: 'wave2-dashboard-desktop' },
  { path: '/settings', name: 'wave2-settings-desktop' },
  { path: '/billing', name: 'wave2-billing-desktop' },
  { path: '/edge-nodes', name: 'wave2-edge-nodes-desktop' },
  { path: '/network', name: 'wave2-network-desktop' },
  { path: '/screening', name: 'wave2-screening-ai-desktop', screening: true },
]

const base = 'http://127.0.0.1:5173'

const browser = await chromium.launch()
const desktop = await browser.newContext({ viewport: { width: 1280, height: 800 } })
const page = await desktop.newPage()

await page.goto(`${base}/login`, { waitUntil: 'networkidle' })
if (page.url().includes('/login')) {
  await page.goto(`${base}/dashboard`, { waitUntil: 'networkidle' })
}
await page.waitForTimeout(1500)

for (const { path, name, screening } of routes) {
  await page.goto(`${base}${path}`, { waitUntil: 'networkidle' })
  if (screening) {
    await page.getByPlaceholder(/enter a name/i).fill('Acme Holdings')
    await page.getByRole('button', { name: /^screen$/i }).click()
    await page.waitForTimeout(1500)
  } else {
    await page.waitForTimeout(1200)
  }
  await page.screenshot({ path: `${outDir}/${name}.png`, fullPage: false })
}

await page.goto(`${base}/settings`, { waitUntil: 'networkidle' })
await page.getByRole('tab', { name: /webhook/i }).click().catch(() => {})
await page.waitForTimeout(800)
await page.screenshot({ path: `${outDir}/wave2-webhooks-desktop.png`, fullPage: false })

await page.goto(`${base}/dashboard`, { waitUntil: 'networkidle' })
await page.getByLabel('Notifications').click()
await page.waitForTimeout(600)
await page.screenshot({ path: `${outDir}/wave2-notifications-desktop.png`, fullPage: false })

const mobile = await browser.newContext({ viewport: { width: 390, height: 844 } })
const mpage = await mobile.newPage()
await mpage.goto(`${base}/dashboard`, { waitUntil: 'networkidle' })
await mpage.waitForTimeout(1200)
await mpage.screenshot({ path: `${outDir}/wave2-dashboard-mobile.png`, fullPage: false })

await browser.close()
console.log('Screenshots saved to', outDir)
