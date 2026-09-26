#!/usr/bin/env node
/**
 * Ensures UI_COVERAGE_MATRIX has no stale gap statuses and P0 gap-register items
 * reference a Console route documented in the matrix.
 */
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const matrixPath = resolve(root, 'docs/UI_COVERAGE_MATRIX.md')
const gapPath = resolve(root, 'docs/AML-FRAUD-COVERAGE-GAP-REGISTER.md')

const matrix = readFileSync(matrixPath, 'utf8')
const gap = readFileSync(gapPath, 'utf8')

const badStatuses = ['missing', 'broken', 'implemented-but-inconsistent']
const rows = matrix.split('\n').filter((line) => line.startsWith('|') && !line.includes('---'))

let errors = []

for (const row of rows) {
  if (row.includes('Status') && row.includes('Feature')) continue
  if (row.includes('implemented+aligned') && row.includes('Count')) continue
  const cells = row.split('|').map((c) => c.trim()).filter(Boolean)
  const status = cells[cells.length - 1]
  if (badStatuses.includes(status) && !row.includes('blocker:')) {
    errors.push(`Matrix row still ${status}: ${row.slice(0, 120)}…`)
  }
}

const p0Routes = [
  { label: 'Sanctions empty-data hold', route: '/screening' },
  { label: 'PEP classification', route: '/screening' },
  { label: 'Batch monitoring alerts', route: '/transaction-monitoring' },
]

for (const { label, route } of p0Routes) {
  if (!matrix.includes(route)) {
    errors.push(`P0 "${label}" requires Console route ${route} in UI_COVERAGE_MATRIX`)
  }
}

if (!gap.includes('UI_COVERAGE_MATRIX.md')) {
  errors.push('Gap register should reference docs/UI_COVERAGE_MATRIX.md for Console route mapping')
}

if (errors.length) {
  console.error('UI coverage verification failed:\n' + errors.map((e) => `  - ${e}`).join('\n'))
  process.exit(1)
}

console.log('UI coverage matrix and gap-register P0 links OK')
