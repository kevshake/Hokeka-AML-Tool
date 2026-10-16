/**
 * Record a short scroll-through of the hero and pinned story for artifact upload.
 * Usage: node scripts/record-hero.mjs <baseUrl> <outputMp4>
 */
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const baseUrl = process.argv[2] || 'http://127.0.0.1:4173'
const outFile = process.argv[3] || '/opt/cursor/artifacts/hero-scroll-story.mp4'

/** Scroll the page slowly while Playwright captures video frames. */
async function main() {
  await mkdir(path.dirname(outFile), { recursive: true })
  const browser = await chromium.launch()
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    recordVideo: { dir: path.dirname(outFile), size: { width: 1440, height: 900 } },
  })
  const page = await context.newPage()
  await page.goto(baseUrl, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  for (let i = 0; i < 8; i++) {
    await page.mouse.wheel(0, 900)
    await page.waitForTimeout(900)
  }
  const video = page.video()
  await context.close()
  await browser.close()
  if (video) {
    const tempPath = await video.path()
    const { rename } = await import('node:fs/promises')
    await rename(tempPath, outFile)
    console.log(`Saved recording to ${outFile}`)
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
