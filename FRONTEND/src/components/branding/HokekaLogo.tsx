import { cn } from '../../lib/utils'

export const HOKEKA_LOGO_SRC = '/images/hokeka-logo.png'
/** Fits within the shared 72px app header row beside the wordmark stack */
const HEADER_LOGO_SIZE = 52
const HEADER_LOGO_SIZE_COLLAPSED = 36

const sizeMap = {
  sm: 40,
  md: 64,
  /** Login / auth card shield on white background */
  card: 72,
  lg: 96,
} as const

interface HokekaLogoProps {
  className?: string
  size?: keyof typeof sizeMap
  showWordmark?: boolean
  /** Horizontal sidebar header: shield + HOKEKA / AML INTELLIGENCE stack */
  variant?: 'default' | 'header'
  /** Hide wordmark in header variant (collapsed sidebar) */
  collapsed?: boolean
}

/**
 * Hokeka brand logo — official transparent gold shield (nobg).
 * Use variant="header" for the sidebar top bar on dark #0e0606 (shield + wordmark).
 */
export default function HokekaLogo({
  className,
  size = 'md',
  showWordmark = true,
  variant = 'default',
  collapsed = false,
}: HokekaLogoProps) {
  if (variant === 'header') {
    const headerLogoSize = collapsed ? HEADER_LOGO_SIZE_COLLAPSED : HEADER_LOGO_SIZE

    return (
      <div className={cn('flex min-w-0 items-center gap-3 select-none', className)}>
        <img
          src={HOKEKA_LOGO_SRC}
          alt="Hokeka"
          width={headerLogoSize}
          height={headerLogoSize}
          className="flex-shrink-0 object-contain"
          draggable={false}
        />
        {!collapsed && (
          <div className="flex min-w-0 flex-col justify-center leading-none">
            <span className="truncate text-[16px] font-extrabold uppercase tracking-wide text-white">
              HOKEKA
            </span>
            <span
              className="mt-1 truncate text-[11px] font-medium uppercase tracking-[0.14em]"
              style={{ color: '#C9A96E' }}
            >
              AML INTELLIGENCE
            </span>
          </div>
        )}
      </div>
    )
  }

  const px = sizeMap[size]

  return (
    <div className={cn('flex flex-col items-center select-none', className)}>
      <img
        src={HOKEKA_LOGO_SRC}
        alt="Hokeka"
        width={px}
        height={px}
        className="object-contain"
        draggable={false}
      />

      {showWordmark && (
        <div className="mt-3 flex flex-col items-center">
          <span
            className="text-white leading-none"
            style={{
              fontFamily: 'Inter, system-ui, sans-serif',
              fontWeight: 800,
              fontSize: '28px',
              letterSpacing: '-0.02em',
            }}
          >
            hokeka
          </span>
          <span
            className="text-hokeka-gold mt-1"
            style={{
              fontSize: '10px',
              letterSpacing: '0.2em',
              fontWeight: 500,
            }}
          >
            AML INTELLIGENCE
          </span>
        </div>
      )}
    </div>
  )
}
