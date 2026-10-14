import { useRef, type ReactNode, type MouseEvent } from 'react'
import { useReducedMotion } from '../hooks/useReducedMotion'

type Props = {
  href: string
  className?: string
  children: ReactNode
}

/** Subtle magnetic pull on pointer devices; plain link on touch / reduced motion. */
export default function MagneticButton({ href, className = 'button', children }: Props) {
  const ref = useRef<HTMLAnchorElement>(null)
  const reduced = useReducedMotion()
  const canHover = typeof window !== 'undefined' && window.matchMedia('(hover: hover)').matches

  /** Animate the anchor toward the cursor while the pointer is inside. */
  const onMove = async (event: MouseEvent<HTMLAnchorElement>) => {
    if (reduced || !canHover || !ref.current) return
    const { default: gsap } = await import('gsap')
    const rect = ref.current.getBoundingClientRect()
    const x = event.clientX - rect.left - rect.width / 2
    const y = event.clientY - rect.top - rect.height / 2
    gsap.to(ref.current, { x: x * 0.18, y: y * 0.18, duration: 0.4, ease: 'power3.out' })
  }

  /** Reset transform when the pointer leaves the control. */
  const onLeave = async () => {
    if (!ref.current) return
    const { default: gsap } = await import('gsap')
    gsap.to(ref.current, { x: 0, y: 0, duration: 0.5, ease: 'power3.out' })
  }

  return (
    <a ref={ref} href={href} className={className} onMouseMove={onMove} onMouseLeave={onLeave}>
      {children}
    </a>
  )
}
