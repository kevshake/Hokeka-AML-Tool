import { useEffect, useRef } from 'react'
import { useReducedMotion } from '../hooks/useReducedMotion'

type Node = { x: number; y: number; r: number }
type Edge = { from: number; to: number; flagged?: boolean; phase: number }

/** Canvas2D transaction network with flowing particles and a flagged Hokeka AI recommendation. */
export default function NetworkGraphHero() {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const reduced = useReducedMotion()

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    let raf = 0
    let visible = true
    const nodes: Node[] = []
    const edges: Edge[] = []
    const dprCap = 1.5

    /** Build a stable graph layout scaled to the canvas size. */
    const buildGraph = (w: number, h: number) => {
      nodes.length = 0
      edges.length = 0
      const cols = w < 520 ? 4 : 6
      const rows = w < 520 ? 3 : 4
      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          nodes.push({
            x: ((x + 1) / (cols + 1)) * w,
            y: ((y + 1) / (rows + 1)) * h,
            r: w < 520 ? 3 : 4,
          })
        }
      }
      for (let i = 0; i < nodes.length; i++) {
        const col = i % cols
        const row = Math.floor(i / cols)
        if (col < cols - 1) edges.push({ from: i, to: i + 1, phase: Math.random(), flagged: i === 5 })
        if (row < rows - 1) edges.push({ from: i, to: i + cols, phase: Math.random() })
      }
      if (edges[0]) edges[0].flagged = true
    }

    /** Resize canvas backing store to device pixels (capped DPR). */
    const resize = () => {
      const rect = canvas.getBoundingClientRect()
      const dpr = Math.min(window.devicePixelRatio || 1, dprCap)
      canvas.width = Math.floor(rect.width * dpr)
      canvas.height = Math.floor(rect.height * dpr)
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      buildGraph(rect.width, rect.height)
    }

    /** Draw one animation frame: edges, particles, nodes, and the AI flag label. */
    const draw = (t: number) => {
      if (!visible) return
      const { width, height } = canvas.getBoundingClientRect()
      ctx.clearRect(0, 0, width, height)

      ctx.fillStyle = 'rgb(8 9 9 / 0.35)'
      ctx.fillRect(0, 0, width, height)

      edges.forEach((edge) => {
        const a = nodes[edge.from]
        const b = nodes[edge.to]
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.strokeStyle = edge.flagged ? 'rgb(232 119 107 / 0.85)' : 'rgb(117 183 171 / 0.35)'
        ctx.lineWidth = edge.flagged ? 2 : 1
        ctx.stroke()

        const progress = (t * 0.00035 + edge.phase) % 1
        const px = a.x + (b.x - a.x) * progress
        const py = a.y + (b.y - a.y) * progress
        ctx.beginPath()
        ctx.arc(px, py, edge.flagged ? 3 : 2, 0, Math.PI * 2)
        ctx.fillStyle = edge.flagged ? '#e8776b' : '#75b7ab'
        ctx.fill()
      })

      nodes.forEach((node, idx) => {
        const pulse = edgeTouchesFlag(idx) ? 1 + Math.sin(t * 0.006) * 0.35 : 1
        ctx.beginPath()
        ctx.arc(node.x, node.y, node.r * pulse, 0, Math.PI * 2)
        ctx.fillStyle = edgeTouchesFlag(idx) ? '#d3b371' : '#f5f2eb'
        ctx.fill()
      })

      const flagged = edges.find((e) => e.flagged)
      if (flagged) {
        const a = nodes[flagged.from]
        const b = nodes[flagged.to]
        const lx = (a.x + b.x) / 2
        const ly = (a.y + b.y) / 2 - 14
        const label = 'Flagged · Hokeka AI recommendation'
        ctx.font = '600 11px Manrope, system-ui, sans-serif'
        const padX = 10
        const padY = 6
        const tw = ctx.measureText(label).width
        ctx.fillStyle = 'rgb(12 14 13 / 0.92)'
        ctx.strokeStyle = 'rgb(211 179 113 / 0.55)'
        ctx.lineWidth = 1
        roundRect(ctx, lx - tw / 2 - padX, ly - 10 - padY, tw + padX * 2, 20 + padY, 4)
        ctx.fill()
        ctx.stroke()
        ctx.fillStyle = '#f5f2eb'
        ctx.fillText(label, lx - tw / 2, ly + 4)
      }

      raf = requestAnimationFrame(draw)
    }

    /** True when node index participates in the flagged edge. */
    const edgeTouchesFlag = (idx: number) => edges.some((e) => e.flagged && (e.from === idx || e.to === idx))

    /** Helper to draw rounded rects on Canvas2D without a built-in API. */
    const roundRect = (
      context: CanvasRenderingContext2D,
      x: number,
      y: number,
      w: number,
      h: number,
      r: number,
    ) => {
      context.beginPath()
      context.moveTo(x + r, y)
      context.arcTo(x + w, y, x + w, y + h, r)
      context.arcTo(x + w, y + h, x, y + h, r)
      context.arcTo(x, y + h, x, y, r)
      context.arcTo(x, y, x + w, y, r)
      context.closePath()
    }

    const observer = new IntersectionObserver(
      (entries) => {
        visible = entries[0]?.isIntersecting ?? true
        if (visible && !raf && !reduced) raf = requestAnimationFrame(draw)
      },
      { threshold: 0.05 },
    )
    observer.observe(canvas)

    resize()
    window.addEventListener('resize', resize)
    if (!reduced) raf = requestAnimationFrame(draw)

    return () => {
      window.removeEventListener('resize', resize)
      observer.disconnect()
      cancelAnimationFrame(raf)
    }
  }, [reduced])

  return (
    <canvas
      ref={canvasRef}
      className="hero-network-canvas"
      aria-hidden="true"
      role="presentation"
    />
  )
}
