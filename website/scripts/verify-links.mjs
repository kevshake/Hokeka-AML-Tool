/**
 * Verify in-page anchors and mailto links resolve on the marketing site.
 */
import { chromium } from 'playwright'

const baseUrl = process.argv[2] || 'http://127.0.0.1:4174'

/** Collect hrefs and assert each anchor target exists in the DOM. */
async function main() {
  const browser = await chromium.launch()
  const page = await browser.newPage()
  await page.goto(baseUrl, { waitUntil: 'networkidle' })
  const hrefs = await page.$$eval('a[href]', (anchors) => anchors.map((a) => a.getAttribute('href')).filter(Boolean))
  const hashLinks = hrefs.filter((h) => h.startsWith('#'))
  for (const hash of hashLinks) {
    const exists = await page.locator(hash).count()
    if (!exists) throw new Error(`Missing section for ${hash}`)
  }
  const mailtos = hrefs.filter((h) => h.startsWith('mailto:'))
  if (mailtos.length < 3) throw new Error('Expected mailto contact links to remain available')
  await browser.close()
  console.log(`Verified ${hashLinks.length} in-page anchors and ${mailtos.length} mailto links`)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
