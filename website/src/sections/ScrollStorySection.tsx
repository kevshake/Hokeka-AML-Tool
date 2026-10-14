import { useEffect, useRef } from 'react'
import { useReducedMotion } from '../hooks/useReducedMotion'

const steps = [
  {
    title: 'Detect',
    copy: 'Ingest transactions and behavioural signals across banking, brokerage, e-money and on-chain activity.',
  },
  {
    title: 'Screen',
    copy: 'Apply sanctions, PEP and wallet exposure checks with explainable hits and fail-closed paths.',
  },
  {
    title: 'Investigate',
    copy: 'Unify alerts, Customer 360 context and network graphs in one PSP-scoped investigation workspace.',
  },
  {
    title: 'File SAR / STR',
    copy: 'Export defensible regulatory narratives with full evidence provenance from first signal to filing.',
  },
]

/** Pinned four-step scroll story: Detect → Screen → Investigate → File SAR/STR. */
export default function ScrollStorySection() {
  const rootRef = useRef<HTMLElement>(null)
  const reduced = useReducedMotion()

  useEffect(() => {
    const root = rootRef.current
    if (!root || reduced) return

    /** Build the scrubbed pin timeline once GSAP is available. */
    const run = async () => {
      const [{ default: gsap }, { ScrollTrigger }] = await Promise.all([
        import('gsap'),
        import('gsap/ScrollTrigger'),
      ])
      gsap.registerPlugin(ScrollTrigger)

      const panels = root.querySelectorAll<HTMLElement>('.story-step')
      gsap.set(panels, { autoAlpha: 0, y: 40 })
      gsap.set(panels[0], { autoAlpha: 1, y: 0 })

      const timeline = gsap.timeline({
        scrollTrigger: {
          trigger: root,
          pin: true,
          scrub: 1,
          start: 'top top',
          end: '+=300%',
          snap: 1 / (steps.length - 1),
        },
      })

      steps.forEach((_, index) => {
        if (index === 0) return
        timeline
          .to(panels[index - 1], { autoAlpha: 0, y: -30, duration: 0.5 }, index)
          .fromTo(panels[index], { autoAlpha: 0, y: 40 }, { autoAlpha: 1, y: 0, duration: 0.5 }, index)
      })
    }

    run()
  }, [reduced])

  return (
    <section className="scroll-story" id="story" ref={rootRef}>
      <div className="container story-inner">
        <p className="eyebrow">How Hokeka works</p>
        <h2 className="section-title story-heading">From signal to defensible filing.</h2>
        <div className="story-steps">
          {steps.map((step, index) => (
            <article className={`story-step ${index === 0 ? 'active' : ''}`} key={step.title} data-step={index}>
              <span className="story-index">0{index + 1}</span>
              <h3>{step.title}</h3>
              <p>{step.copy}</p>
            </article>
          ))}
        </div>
        <div className="story-progress" aria-hidden="true">
          {steps.map((step) => (
            <span key={step.title} />
          ))}
        </div>
      </div>
    </section>
  )
}
