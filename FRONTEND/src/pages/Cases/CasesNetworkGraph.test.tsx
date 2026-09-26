/** @vitest-environment happy-dom */
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import CasesNetworkGraph from './CasesNetworkGraph'

vi.mock('../../features/api/queries', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../features/api/queries')>()
  return {
    ...actual,
    useGraphAnalysisStatus: () => ({
      data: { enabled: false, available: false, reason: 'Graph analysis is disabled on this Control Plane (neo4j.enabled=false).' },
      isLoading: false,
      isError: false,
    }),
    useCases: () => ({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 },
      isLoading: false,
      isError: false,
    }),
  }
})

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: () => ({
    user: { role: { name: 'PSP_USER' }, pspId: 2 },
  }),
}))

function renderGraph() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <CasesNetworkGraph />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('CasesNetworkGraph', () => {
  it('shows disabled copy and no graph nodes when graph analysis is off', () => {
    renderGraph()
    expect(screen.getByRole('heading', { name: /Graph analysis is turned off/i })).toBeTruthy()
    expect(document.querySelector('.graph-node')).toBeNull()
    expect(screen.queryByText(/Select Case/i)).toBeNull()
  })
})
