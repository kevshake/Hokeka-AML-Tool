/** Operator-only install docs (docs-site). Hidden from PSP builds unless explicitly enabled. */

const DOC_PAGES = {
  processMap: '/',
  edgeNode: '/docs/01-client-edge-node',
  console: '/docs/02-console-dashboard',
  controlPlane: '/docs/03-control-plane',
  packagesCdn: '/docs/04-packages-cdn-and-edge-release',
  localStack: '/docs/05-local-developer-stack',
  dualPost: '/docs/06-psp-api-dual-post-integration',
} as const

export type OperatorDocKey = keyof typeof DOC_PAGES

export function isOperatorDocsEnabled(): boolean {
  return import.meta.env.VITE_INCLUDE_OPERATOR_DOCS === 'true'
}

export function operatorDocsBaseUrl(): string {
  const base = import.meta.env.VITE_OPERATOR_DOCS_BASE_URL
  if (typeof base === 'string' && base.trim()) {
    return base.replace(/\/$/, '')
  }
  return 'http://localhost:5175'
}

export function operatorDocUrl(key: OperatorDocKey): string | null {
  if (!isOperatorDocsEnabled()) return null
  return `${operatorDocsBaseUrl()}${DOC_PAGES[key]}`
}

export const OPERATOR_DOC_LINKS: { key: OperatorDocKey; label: string; description: string }[] = [
  { key: 'controlPlane', label: 'Control Plane stack', description: 'Cloud deployment and ops topology' },
  { key: 'console', label: 'Console deployment', description: 'Dashboard hosting and tenant routing' },
  { key: 'edgeNode', label: 'Edge Node install', description: 'PSP on-prem enrollment' },
  { key: 'packagesCdn', label: 'Packages & CDN', description: 'Edge bundle release pipeline' },
  { key: 'dualPost', label: 'Dual-post integration', description: 'PSP API contract (06)' },
  { key: 'localStack', label: 'Local developer stack', description: 'Engineering sandbox (05)' },
]
