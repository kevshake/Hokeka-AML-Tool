export interface GraphViewportNode {
  x: number
  y: number
}

/** Fit force-layout node coordinates into an SVG container (pan/zoom for translate-then-scale group). */
export function fitGraphViewport(
  nodes: GraphViewportNode[],
  containerWidth: number,
  containerHeight: number,
  nodeRadius: number,
  labelBand = 36,
  padding = 56,
): { panX: number; panY: number; zoom: number } {
  if (nodes.length === 0 || containerWidth <= 0 || containerHeight <= 0) {
    return { panX: 0, panY: 0, zoom: 1 }
  }

  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity

  for (const node of nodes) {
    minX = Math.min(minX, node.x - nodeRadius)
    maxX = Math.max(maxX, node.x + nodeRadius)
    minY = Math.min(minY, node.y - nodeRadius)
    maxY = Math.max(maxY, node.y + nodeRadius + labelBand)
  }

  const graphW = Math.max(maxX - minX, 1)
  const graphH = Math.max(maxY - minY, 1)
  const cx = (minX + maxX) / 2
  const cy = (minY + maxY) / 2

  const zoom = Math.min(
    (containerWidth - padding * 2) / graphW,
    (containerHeight - padding * 2) / graphH,
    2.75,
  )

  const clampedZoom = Math.max(0.35, zoom)
  // GraphCanvas group uses translate(pan) then scale(zoom) → screen = (coord + pan) * zoom
  return {
    panX: containerWidth / (2 * clampedZoom) - cx,
    panY: containerHeight / (2 * clampedZoom) - cy,
    zoom: clampedZoom,
  }
}
