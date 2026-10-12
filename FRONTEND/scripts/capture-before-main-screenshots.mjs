#!/usr/bin/env node
/**
 * Captures baseline Console screenshots from main (run against a worktree checked out at f9706c62+).
 * Output: /opt/cursor/artifacts/console-screenshots/before-main-*
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const outDir = '/opt/cursor/artifacts/console-screenshots/before-main'
mkdirSync(outDir, { recursive: true })
const base = process.env.VITE_DEV_URL || 'http://127.0.0.1:5173'

const routes = ['/dashboard', '/screening', '/alerts', '/network']

const browser = await chromium.launch()
for (const viewport of [
  { w: 1440, h: 900, tag: '1440' },
  { w: 390, h: 844, tag: '390' },
]) {
  const ctx = await browser.newContext({ viewport: { width: viewport.w, height: viewport.h } })
  const page = await ctx.newPage()
  for (const path of routes) {
    await page.goto(`${base}${path}`, { waitUntil: 'networkidle', timeout: 60_000 })
    await page.waitForTimeout(1000)
    const slug = path.slice(1) || 'home'
    await page.screenshot({ path: `${outDir}/${slug}-${viewport.tag}.png` })
  }
  await ctx.close()
}
await browser.close()
console.log('Before-main baselines →', outDir)
