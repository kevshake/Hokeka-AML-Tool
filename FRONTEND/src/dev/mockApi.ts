/**
 * Dev-only API mock layer (never bundled for production — gated by VITE_DEV_MOCK_API).
 * Enables authenticated Console screenshots without a live Control Plane.
 */

const MOCK_USER = {
  id: 1,
  username: 'admin',
  email: 'admin@sys.com',
  firstName: 'System',
  lastName: 'Admin',
  role: {
    id: 1,
    name: 'SUPER_ADMIN',
    permissions: ['*'],
  },
  psp: null,
  pspId: 0,
  enabled: true,
  createdAt: new Date().toISOString(),
}

const MOCK_MESSAGES = [
  {
    id: '1',
    subject: 'Edge bundle v2.4.1 published',
    body: 'Fleet-wide rollout scheduled for 02:00 UTC.',
    read: false,
    createdAt: new Date(Date.now() - 3600_000).toISOString(),
  },
  {
    id: '2',
    subject: 'Billing cycle closed',
    body: 'March usage invoices are ready in Platform Billing.',
    read: true,
    createdAt: new Date(Date.now() - 86400_000).toISOString(),
  },
]

const MOCK_NETWORK_GRAPH = {
  nodes: [
    { id: 'case-101', type: 'CASE', label: 'Case #101' },
    { id: 'txn-9001', type: 'TRANSACTION', label: 'Txn 9001' },
    { id: 'merch-44', type: 'MERCHANT', label: 'Acme Retail' },
    { id: 'case-88', type: 'CASE', label: 'Case #88' },
  ],
  edges: [
    { from: 'case-101', to: 'txn-9001', type: 'HAS_TRANSACTION', label: 'linked' },
    { from: 'txn-9001', to: 'merch-44', type: 'HAS_MERCHANT', label: 'merchant' },
    { from: 'case-101', to: 'case-88', type: 'RELATED_CASE', label: 'related' },
  ],
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function pathOf(url: string): string {
  try {
    const u = new URL(url, 'http://mock.local')
    return u.pathname.replace(/^\/api\/v1\/?/, '')
  } catch {
    return url
  }
}

function page<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
    size: content.length,
  }
}

export function isDevMockEnabled(): boolean {
  return import.meta.env.DEV && import.meta.env.VITE_DEV_MOCK_API === 'true'
}

/** Runtime override for screenshots (sessionStorage) or build-time env. */
export function isMockGraphAnalysisEnabled(): boolean {
  if (import.meta.env.VITE_DEV_MOCK_GRAPH_ANALYSIS === 'false') return false
  if (typeof globalThis.sessionStorage !== 'undefined') {
    if (globalThis.sessionStorage.getItem('devMockGraphAnalysis') === 'false') return false
  }
  return true
}

function resolveMockUser() {
  if (typeof globalThis.sessionStorage !== 'undefined') {
    const role = globalThis.sessionStorage.getItem('devMockUserRole')
    if (role === 'PSP_USER') {
      return {
        ...MOCK_USER,
        role: { id: 2, name: 'PSP_USER', permissions: ['PSP_USER'] },
        pspId: 2,
        psp: { pspId: 2, code: 'TECHFLOW_PSP', name: 'TechFlow Inc.' },
      }
    }
  }
  return MOCK_USER
}

export function getMockSessionUser() {
  return resolveMockUser()
}

export async function mockFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
  const method = (init?.method ?? (typeof input !== 'string' && !(input instanceof URL) ? input.method : 'GET')).toUpperCase()
  const path = pathOf(url)

  if (path === 'auth/me' && method === 'GET') {
    return jsonResponse(resolveMockUser())
  }

  if (path === 'auth/login' && method === 'POST') {
    return jsonResponse(resolveMockUser())
  }

  if (path.startsWith('messages/unread/count')) {
    const count = MOCK_MESSAGES.filter((m) => !m.read).length
    return jsonResponse({ count })
  }

  if (path.startsWith('messages/read-all') && method === 'PUT') {
    MOCK_MESSAGES.forEach((m) => { m.read = true })
    return jsonResponse({ updated: MOCK_MESSAGES.length })
  }

  if (/^messages\/[^/]+\/read$/.test(path) && method === 'PUT') {
    const id = path.split('/')[1]
    const row = MOCK_MESSAGES.find((m) => m.id === id)
    if (row) row.read = true
    return jsonResponse({})
  }

  if (path.startsWith('messages')) {
    return jsonResponse(MOCK_MESSAGES)
  }

  if (path.startsWith('dashboard/')) {
    if (path.includes('stats')) {
      return jsonResponse({
        totalMerchants: 1280,
        activeMerchants: 1194,
        pendingScreening: 18,
        openCases: 4,
        openAlertsCount: 12,
        urgentCases: 2,
        flaggedToday: 37,
        transactionsMonitoredToday: 8420,
        highRiskCustomerCount: 56,
        complianceHealthScore: 91,
        trends: {
          totalTransactionsDelta: 4.2,
          flaggedDelta: -1.1,
          openCasesDelta: 0,
          highRiskCustomersDelta: 2.4,
        },
      })
    }
    if (path.includes('live-alerts')) {
      return jsonResponse([
        { id: 1, severity: 'HIGH', title: 'Velocity spike', createdAt: new Date().toISOString() },
      ])
    }
    if (path.includes('sparklines')) {
      const labels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
      const series = [12, 18, 14, 22, 19, 24, 28]
      return jsonResponse({
        transactionVolume: { labels, data: series },
        alertTrends: { labels, data: [2, 3, 1, 4, 2, 5, 3] },
        caseTrends: { labels, data: [1, 1, 2, 1, 0, 2, 1] },
        screeningMatchTrends: { labels, data: [0, 1, 0, 2, 1, 1, 0] },
        highRiskTrends: { labels, data: [4, 5, 4, 6, 5, 7, 6] },
      })
    }
    if (path.includes('trends') || path.includes('transaction-volume')) {
      return jsonResponse({ labels: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'], data: [4, 6, 5, 8, 7, 9, 6] })
    }
    if (path.includes('top-risk')) {
      return jsonResponse([
        { rank: 1, merchantId: 44, name: 'Acme Retail', riskScore: 88, riskLevel: 'HIGH' },
      ])
    }
    if (path.includes('risk-heatmap')) {
      return jsonResponse([
        { countryCode: 'US', countryName: 'United States', riskLevel: 'LOW', transactionCount: 1200, alertCount: 3 },
      ])
    }
    return jsonResponse({})
  }

  if (path.startsWith('network/graph-analysis/status')) {
    const mockEnabled = isMockGraphAnalysisEnabled()
    return jsonResponse({
      enabled: mockEnabled,
      available: mockEnabled,
      reason: mockEnabled
        ? null
        : 'Graph analysis is disabled on this Control Plane (neo4j.enabled=false).',
    })
  }

  if (/^cases\/\d+\/network/.test(path)) {
    if (!isMockGraphAnalysisEnabled()) {
      return jsonResponse({ nodes: [], edges: [] })
    }
    return jsonResponse(MOCK_NETWORK_GRAPH)
  }

  if (path.startsWith('compliance/cases')) {
    const now = new Date().toISOString()
    const mockUser = { id: 1, username: 'admin', email: 'admin@sys.com' }
    return jsonResponse(
      page([
        {
          id: 101,
          caseReference: 'CASE-2026-0101',
          description: 'Structuring pattern across linked merchants',
          status: 'INVESTIGATING',
          priority: 'HIGH',
          createdBy: mockUser,
          createdAt: now,
          updatedAt: now,
        },
        {
          id: 88,
          caseReference: 'CASE-2026-0088',
          description: 'Linked merchant review',
          status: 'ASSIGNED',
          priority: 'MEDIUM',
          createdBy: mockUser,
          createdAt: now,
          updatedAt: now,
        },
      ]),
    )
  }

  if (path.includes('edge/nodes') || path.includes('edge-nodes')) {
    return jsonResponse([
      {
        id: 1,
        nodeId: 'edge-demo-1',
        hostname: 'psp-edge-01',
        status: 'ACTIVE',
        bundleVersion: '2.4.1',
        lastHeartbeatAt: new Date().toISOString(),
        pspId: 2,
        pspCode: 'TECHFLOW_PSP',
      },
    ])
  }

  if (path.includes('webhooks/subscriptions')) {
    return jsonResponse([
      {
        id: 1,
        callbackUrl: 'https://psp.example/hooks/aml',
        eventType: 'RISK_ALERT',
        active: true,
        createdAt: new Date().toISOString(),
      },
    ])
  }

  if (path.includes('billing/revenue/summary')) {
    return jsonResponse({
      currentMonthRevenuePaid: 124_500,
      currentMonthRevenueExpected: 138_200,
      overdueAmount: 12_400,
      activeSubscriptions: 6,
      paidInvoicesThisMonth: 14,
      overdueInvoicesCount: 2,
      currency: 'USD',
    })
  }

  if (path.includes('billing')) {
    return jsonResponse({})
  }

  if (path.includes('settings/psps')) {
    return jsonResponse([{ id: 2, code: 'TECHFLOW_PSP', name: 'TechFlow Inc.' }])
  }

  if (path.startsWith('ai/audit')) {
    return jsonResponse([
      {
        id: 1,
        recommendation: 'REVIEW',
        confidence: 0.82,
        reasons: ['Elevated velocity vs peer group', 'New beneficiary account'],
        createdAt: new Date().toISOString(),
        aiApplied: false,
        advisoryOnly: true,
      },
    ])
  }

  if (path.startsWith('alerts')) {
    return jsonResponse(page([]))
  }

  if (path.startsWith('sanctions/screen') && method === 'POST') {
    return jsonResponse({
      matchFound: true,
      aiScreeningHitId: 9001,
      matches: [{ name: 'Sample Entity', listName: 'Demo', score: 0.91, pepLevel: 'TIER_2' }],
    })
  }

  if (path.startsWith('audit/logs')) {
    return jsonResponse(page([]))
  }

  if (method === 'GET') {
    return jsonResponse({})
  }

  return jsonResponse({ ok: true })
}

export function installDevMockFetch(): void {
  if (!isDevMockEnabled()) return
  const native = window.fetch.bind(window)
  window.fetch = async (input, init) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    if (url.includes('/api/v1/')) {
      try {
        return await mockFetch(input, init)
      } catch {
        return native(input, init)
      }
    }
    return native(input, init)
  }
}
