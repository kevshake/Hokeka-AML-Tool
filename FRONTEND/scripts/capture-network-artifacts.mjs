import { chromium } from 'playwright'
import { copyFileSync, mkdirSync } from 'node:fs'

const outDir = '/opt/cursor/artifacts'
mkdirSync(outDir, { recursive: true })
const base = 'http://127.0.0.1:5173'

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })

await page.goto(`${base}/network`, { waitUntil: 'networkidle' })
await page.getByText('Select Case').waitFor({ timeout: 15_000 })
await page.waitForSelector('.graph-node', { timeout: 15_000 })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${outDir}/final-network.png`, fullPage: false })

const pspPage = await browser.newPage({ viewport: { width: 1280, height: 800 } })
await pspPage.addInitScript(() => {
  sessionStorage.setItem('devMockGraphAnalysis', 'false')
  sessionStorage.setItem('devMockUserRole', 'PSP_USER')
})
await pspPage.goto(`${base}/network`, { waitUntil: 'networkidle' })
await pspPage.getByRole('heading', { name: /Graph analysis is turned off/i }).waitFor({ timeout: 15_000 })
await pspPage.getByText(/Relationship graphs are not available/i).waitFor({ timeout: 15_000 })
await pspPage.waitForTimeout(600)
await pspPage.screenshot({ path: `${outDir}/final-network-disabled-psp.png`, fullPage: false })

await browser.close()
console.log('Saved', `${outDir}/final-network.png`, `${outDir}/final-network-disabled-psp.png`)
