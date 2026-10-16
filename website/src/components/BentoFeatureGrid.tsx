import { useRef, type MouseEvent } from 'react'
import { motion } from 'motion/react'
import { Activity, FileCheck2, Network, ShieldCheck } from 'lucide-react'
import { useReducedMotion } from '../hooks/useReducedMotion'

const tiles = [
  {
    icon: ShieldCheck,
    title: 'Sanctions & PEP',
    copy: 'Fail-closed screening with explainable hits and provider-independent gateways.',
    span: 'bento-span-2',
    accent: 'var(--teal)',
  },
  {
    icon: Activity,
    title: 'Live monitoring',
    copy: 'Velocity, smurfing and cross-rail behaviour in one PSP-scoped stream.',
    span: '',
    accent: 'var(--gold)',
  },
  {
    icon: Network,
    title: 'Network context',
    copy: 'Graph relationships between accounts, merchants and counterparties.',
    span: '',
    accent: 'var(--info)',
  },
  {
    icon: FileCheck2,
    title: 'SAR / STR filing',
    copy: 'Evidence chain from alert to regulatory export without losing provenance.',
    span: 'bento-span-2',
    accent: 'var(--danger)',
  },
]

/** Bento grid with pointer spotlight and subtle 3D tilt on hover. */
export default function BentoFeatureGrid() {
  const reduced = useReducedMotion()

  /** Update CSS custom properties for the radial spotlight under the cursor. */
  const onMove = (event: MouseEvent<HTMLElement>) => {
    if (reduced) return
    const card = event.currentTarget
    const rect = card.getBoundingClientRect()
    card.style.setProperty('--x', `${event.clientX - rect.left}px`)
    card.style.setProperty('--y', `${event.clientY - rect.top}px`)
  }

  return (
    <div className="bento-grid">
      {tiles.map(({ icon: Icon, title, copy, span, accent }, index) => (
        <motion.article
          key={title}
          className={`bento-card reveal ${span}`}
          style={{ ['--i' as string]: index, ['--accent' as string]: accent }}
          onMouseMove={onMove}
          whileHover={reduced ? undefined : { rotateX: 2, rotateY: -2, scale: 1.01 }}
          transition={{ type: 'spring', stiffness: 260, damping: 22 }}
        >
          <div className="bento-icon">
            <Icon size={20} />
          </div>
          <h3>{title}</h3>
          <p>{copy}</p>
          <div className="bento-spark" aria-hidden="true" />
        </motion.article>
      ))}
    </div>
  )
}
