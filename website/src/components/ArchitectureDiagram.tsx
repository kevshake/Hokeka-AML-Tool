import { useEffect, useRef } from 'react'
import { useReducedMotion } from '../hooks/useReducedMotion'

/** Self-drawing edge + cloud architecture SVG scrubbed by scroll. */
export default function ArchitectureDiagram() {
  const rootRef = useRef<HTMLDivElement>(null)
  const reduced = useReducedMotion()

  useEffect(() => {
    const root = rootRef.current
    if (!root || reduced) return

    /** Animate path stroke when GSAP loads and the diagram enters view. */
    const run = async () => {
      const [{ default: gsap }, { ScrollTrigger }] = await Promise.all([
        import('gsap'),
        import('gsap/ScrollTrigger'),
      ])
      gsap.registerPlugin(ScrollTrigger)
      const paths = root.querySelectorAll<SVGPathElement>('.arch-draw')
      paths.forEach((path) => {
        const len = path.getTotalLength()
        gsap.set(path, { strokeDasharray: len, strokeDashoffset: len })
      })
      gsap.to(paths, {
        strokeDashoffset: 0,
        ease: 'none',
        scrollTrigger: {
          trigger: root,
          start: 'top 75%',
          end: 'bottom 40%',
          scrub: 1,
        },
      })
    }

    run()
  }, [reduced])

  return (
    <div ref={rootRef} className="architecture-diagram reveal" aria-label="Edge node and cloud control plane">
      <svg viewBox="0 0 720 320" role="img">
        <title>PSP edge evaluation and cloud control plane</title>
        <rect className="arch-zone edge-zone" x="24" y="48" width="200" height="224" rx="6" />
        <text x="44" y="78" className="arch-label">
          PSP Edge Node
        </text>
        <circle cx="124" cy="160" r="36" className="arch-node" />
        <text x="124" y="166" textAnchor="middle" className="arch-node-label">
          Evaluate
        </text>

        <rect className="arch-zone cloud-zone" x="496" y="48" width="200" height="224" rx="6" />
        <text x="516" y="78" className="arch-label">
          Cloud control plane
        </text>
        <circle cx="596" cy="160" r="36" className="arch-node cloud" />
        <text x="596" y="166" textAnchor="middle" className="arch-node-label">
          Ingest
        </text>

        <path className="arch-draw" d="M160 160 H360" />
        <path className="arch-draw" d="M360 160 H496" />
        <circle className="arch-packet p1" cx="160" cy="160" r="5" />
        <circle className="arch-packet p2" cx="360" cy="160" r="5" />
      </svg>
      <p className="architecture-caption">
        Dual-post contract: pre-auth at the edge, durable ingest and investigation in the cloud — same evidence chain.
      </p>
    </div>
  )
}
