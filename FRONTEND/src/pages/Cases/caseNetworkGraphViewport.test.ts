import { describe, expect, it } from 'vitest'
import { fitGraphViewport } from './caseNetworkGraphViewport'

describe('fitGraphViewport', () => {
  it('centers and scales nodes into the container', () => {
    const nodes = [
      { x: 100, y: 100 },
      { x: 800, y: 400 },
    ]
    const { panX, panY, zoom } = fitGraphViewport(nodes, 900, 520, 28)
    expect(zoom).toBeGreaterThan(0.5)
    expect(Number.isFinite(panX)).toBe(true)
    expect(Number.isFinite(panY)).toBe(true)
  })
})
