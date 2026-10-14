/** Shared easing and duration tokens for GSAP + Motion (one motion language). */
export const MOTION_EASE = 'power3.out'
export const MOTION_EASE_EXPO = 'expo.out'
export const MOTION_DUR = {
  fast: 0.2,
  base: 0.4,
  slow: 0.8,
  story: 1.2,
} as const

/** API base for marketing-site form posts (proxied to Control Plane in dev). */
export const PUBLIC_API_BASE = import.meta.env.VITE_PUBLIC_API_BASE ?? '/api/v1'
