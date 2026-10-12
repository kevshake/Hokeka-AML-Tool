#!/usr/bin/env node
/**
 * Baseline captures from main @ f9706c62+ using the same route sets as after shots.
 * Run against a worktree dev server (default port 5174).
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const outDir = '/opt/cursor/artifacts/console-screenshots/before-main'
mkdirSync(outDir, { recursive: true })
const base = process.env.VITE_DEV_URL || 'http://127.0.0.1:5174'

const routesByRole = {
  SUPER_ADMIN: ['/dashboard', '/psps', '/billing', '/edge-nodes', '/users/list', '/screening', '/alerts'],
  PSP_USER: ['/dashboard', '/messages', '/screening', '/cases/all'],
}

async function pageHasContent(page) {
  const text = await page.evaluate(() => {
    const main = document.querySelector('.hokeka-dashboard-content') || document.body
    return (main.innerText || '').replace(/\s+/g, ' ').trim()
  })
  return text.length > 40
}

const browser = await chromium.launch()

for (const [role, paths] of Object.entries(routesByRole)) {
  for (const viewport of [
    { w: 1440, h: 900, tag: '1440' },
    { w: 390, h: 844, tag: '390' },
  ]) {
    const context = await browser.newContext({ viewport: { width: viewport.w, height: viewport.h } })
    const page = await context.newPage()
    await page.addInitScript((r) => {
      sessionStorage.setItem('devMockUserRole', r)
    }, role)

    for (const path of paths) {
      await page.goto(`${base}${path}`, { waitUntil: 'networkidle', timeout: 60_000 })
      await page.waitForTimeout(1200)
      const slug = path.replace(/[^\w]+/g, '-').replace(/^-|-$/g, '') || 'root'
      const file = `${outDir}/${role.toLowerCase()}-${slug}-${viewport.tag}.png`
      if (!(await pageHasContent(page))) {
        throw new Error(`Blank before capture for ${role} ${path} @ ${viewport.tag}`)
      }
      await page.screenshot({ path: file, fullPage: false })
      console.log('saved', file)
    }
    await context.close()
  }
}

await browser.close()
console.log('Before-main role baselines →', outDir)
