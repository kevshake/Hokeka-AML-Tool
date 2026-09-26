import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const outDir = '/opt/cursor/artifacts'
mkdirSync(outDir, { recursive: true })

const base = 'http://127.0.0.1:5173'

const desktopRoutes = [
  { path: '/dashboard', name: 'wave2-dashboard-desktop' },
  { path: '/settings?tab=platform-admin', name: 'wave2-platform-admin-desktop' },
  { path: '/billing', name: 'wave2-billing-desktop' },
  { path: '/settings?tab=webhooks', name: 'wave2-webhooks-desktop' },
  { path: '/edge-nodes', name: 'wave2-edge-nodes-desktop' },
  { path: '/network', name: 'wave2-network-desktop' },
  { path: '/screening', name: 'wave2-screening-ai-desktop', screening: true },
]

const browser = await chromium.launch()
const desktop = await browser.newContext({ viewport: { width: 1280, height: 800 } })
const page = await desktop.newPage()

await page.goto(`${base}/dashboard`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)

for (const { path, name, screening } of desktopRoutes) {
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

await page.goto(`${base}/dashboard`, { waitUntil: 'networkidle' })
await page.getByLabel('Notifications').click()
await page.waitForTimeout(600)
await page.screenshot({ path: `${outDir}/wave2-notifications-desktop.png`, fullPage: false })

const mobileViews = [
  { path: '/dashboard', name: 'wave2-dashboard-mobile' },
  { path: '/screening', name: 'wave2-screening-mobile', screening: true },
  { path: '/billing', name: 'wave2-billing-mobile' },
]

for (const { path, name, screening } of mobileViews) {
  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const mpage = await mobile.newPage()
  await mpage.goto(`${base}${path}`, { waitUntil: 'networkidle' })
  if (screening) {
    await mpage.getByPlaceholder(/enter a name/i).fill('Acme')
    await mpage.getByRole('button', { name: /^screen$/i }).click()
    await mpage.waitForTimeout(1200)
  } else {
    await mpage.waitForTimeout(1000)
  }
  await mpage.screenshot({ path: `${outDir}/${name}.png`, fullPage: false })
  await mobile.close()
}

await browser.close()
console.log('Screenshots saved to', outDir)
