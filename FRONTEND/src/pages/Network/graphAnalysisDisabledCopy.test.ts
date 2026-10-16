import { describe, expect, it } from 'vitest'
import { graphAnalysisDisabledCopy } from './graphAnalysisDisabledCopy'

describe('graphAnalysisDisabledCopy', () => {
  const status = {
    enabled: false,
    available: false,
    reason: 'Graph analysis is disabled on this Control Plane (neo4j.enabled=false).',
  }

  it('hides config keys from PSP users', () => {
    const copy = graphAnalysisDisabledCopy(status, false, 'disabled')
    expect(copy.body).not.toMatch(/neo4j/i)
    expect(copy.title).toMatch(/turned off/i)
  })

  it('preserves operator-facing reason text', () => {
    const copy = graphAnalysisDisabledCopy(status, true, 'disabled')
    expect(copy.body).toMatch(/neo4j\.enabled=false/)
  })
})
