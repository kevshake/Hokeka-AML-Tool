#!/usr/bin/env node
/**
 * Captures non-empty Console screenshots per role at 1440 and 390 viewports.
 * Requires: VITE_DEV_MOCK_API=true dev server on :5173
 */
import { chromium } from 'playwright'
import { mkdirSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const roleSource = readFileSync(resolve(root, 'src/lib/consoleRbac.ts'), 'utf8')
const roleMatch = roleSource.match(/CONSOLE_TEST_ROLES = \[([\s\S]*?)\] as const/)
const ROLES = roleMatch
  ? roleMatch[1].split(',').map((s) => s.replace(/['"\s]/g, '')).filter(Boolean)
  : []

const outDir = process.env.SCREENSHOT_OUT || '/opt/cursor/artifacts/console-screenshots'
mkdirSync(outDir, { recursive: true })

const base = process.env.VITE_DEV_URL || 'http://127.0.0.1:5173'

const routesByRole = {
  SUPER_ADMIN: ['/dashboard', '/psps', '/billing', '/edge-nodes', '/users/list', '/screening', '/alerts'],
  PLATFORM_ADMIN: ['/dashboard', '/psps', '/edge-nodes', '/settings', '/screening'],
  PSP_ADMIN: ['/dashboard', '/organization', '/users/list', '/edge-nodes', '/settings', '/alerts'],
  COMPLIANCE_OFFICER: ['/dashboard', '/alerts', '/cases/all', '/regulatory-reports', '/screening'],
  MLRO: ['/dashboard', '/regulatory-reports', '/reports', '/alerts'],
  SENIOR_ANALYST: ['/dashboard', '/alerts', '/cases/all', '/reports'],
  INVESTIGATOR: ['/dashboard', '/alerts', '/cases/all', '/transaction-monitoring/live'],
  SCREENING_ANALYST: ['/dashboard', '/screening', '/kyc-documents'],
  AUDITOR: ['/dashboard', '/audit', '/reports', '/alerts'],
  PSP_USER: ['/dashboard', '/messages', '/screening', '/cases/all'],
}

/** Returns true when the page has meaningful visible text (not blank shell). */
async function pageHasContent(page) {
  const text = await page.evaluate(() => {
    const main = document.querySelector('.hokeka-dashboard-content') || document.body
    return (main.innerText || '').replace(/\s+/g, ' ').trim()
  })
  return text.length > 40
}

const browser = await chromium.launch()

for (const role of ROLES) {
  const paths = routesByRole[role] || ['/dashboard']
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
      await page.waitForTimeout(1500)
      const slug = path.replace(/[^\w]+/g, '-').replace(/^-|-$/g, '') || 'root'
      const file = `${outDir}/${role.toLowerCase()}-${slug}-${viewport.tag}.png`
      if (!(await pageHasContent(page))) {
        throw new Error(`Blank capture for ${role} ${path} @ ${viewport.tag}`)
      }
      await page.screenshot({ path: file, fullPage: false })
      console.log('saved', file)
    }
    await context.close()
  }
}

await browser.close()
console.log('Role screenshots OK →', outDir)
