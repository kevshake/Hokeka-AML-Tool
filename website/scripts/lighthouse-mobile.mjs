/**
 * Run mobile Lighthouse performance repeatedly and print scores.
 * Usage: node scripts/lighthouse-mobile.mjs <url> [runs]
 */
import { execSync } from 'node:child_process'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'

const url = process.argv[2] || 'http://127.0.0.1:4174/'
const runs = Number(process.argv[3] || 3)
const scores = []

/** Execute N Lighthouse mobile performance runs and collect scores. */
function main() {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  for (let i = 0; i < runs; i++) {
    const out = `/tmp/lh-${i}.json`
    execSync(
      `npx --yes lighthouse@12.2.1 ${url} --only-categories=performance --chrome-flags="--headless --no-sandbox" --output=json --output-path=${out} --quiet`,
      { stdio: 'inherit' },
    )
    const report = JSON.parse(readFileSync(out, 'utf8'))
    const perf = Math.round(report.categories.performance.score * 100)
    const lcp = report.audits['largest-contentful-paint'].displayValue
    scores.push({ perf, lcp })
    console.log(`Run ${i + 1}: performance=${perf} LCP=${lcp}`)
  }
  writeFileSync('/opt/cursor/artifacts/lighthouse-mobile-runs.json', JSON.stringify(scores, null, 2))
  writeFileSync('/opt/cursor/artifacts/lighthouse.json', readFileSync(`/tmp/lh-${runs - 1}.json`))
  const min = Math.min(...scores.map((s) => s.perf))
  const max = Math.max(...scores.map((s) => s.perf))
  console.log(`Range: ${min}-${max}`)
}

main()
