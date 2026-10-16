/**
 * Playwright helper: capture marketing site screenshots at desktop and mobile widths.
 * Usage: node scripts/capture-screenshots.mjs <baseUrl> <outputDir>
 */
import { chromium, devices } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const baseUrl = process.argv[2] || 'http://127.0.0.1:4173'
const outDir = process.argv[3] || '/opt/cursor/artifacts/screenshots'

/** Scroll each in-page anchor into view and capture a named screenshot. */
async function captureSections(page, prefix, viewport) {
  const sections = [
    { id: 'home', name: 'home' },
    { id: 'solutions', name: 'solutions' },
    { id: 'platform', name: 'platform' },
    { id: 'integrations', name: 'integrations' },
    { id: 'contact', name: 'contact' },
  ]
  for (const { id, name } of sections) {
    await page.goto(`${baseUrl}/#${id}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(600)
    const file = path.join(outDir, `${prefix}-${name}-${viewport.label}.png`)
    await page.screenshot({ path: file, fullPage: false })
  }
  await page.goto(baseUrl, { waitUntil: 'networkidle' })
  await page.waitForTimeout(400)
  await page.screenshot({
    path: path.join(outDir, `${prefix}-full-${viewport.label}.png`),
    fullPage: true,
  })
}

/** Launch browser, run desktop + mobile captures, then close. */
async function main() {
  await mkdir(outDir, { recursive: true })
  const browser = await chromium.launch()
  const desktop = { label: 'desktop-1440', width: 1440, height: 900 }
  const mobile = devices['iPhone 12']

  const desktopContext = await browser.newContext({ viewport: { width: desktop.width, height: desktop.height } })
  const desktopPage = await desktopContext.newPage()
  await captureSections(desktopPage, 'site', desktop)

  const mobileContext = await browser.newContext({ ...mobile, viewport: { width: 390, height: 844 } })
  const mobilePage = await mobileContext.newPage()
  await captureSections(mobilePage, 'site', { label: 'mobile-390', width: 390, height: 844 })

  await browser.close()
  console.log(`Screenshots saved to ${outDir}`)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
