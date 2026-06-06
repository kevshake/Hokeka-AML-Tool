import { cn } from '../../lib/utils'

interface HokekaLogoProps {
  className?: string
  shieldColor?: string
  letterColor?: string
  accentColor?: string
  showWordmark?: boolean
}

/**
 * Hokeka brand logo: navy/blue shield outline with a centered "H" and three
 * small connecting nodes in the lower-right of the shield. Defaults are tuned
 * for the dark sidebar variant (white "H", blue accent nodes).
 */
export default function HokekaLogo({
  className,
  shieldColor = '#1F6FEB',
  letterColor = '#FFFFFF',
  accentColor = '#1F6FEB',
  showWordmark = true,
}: HokekaLogoProps) {
  return (
    <div className={cn('flex flex-col items-center select-none', className)}>
      <svg
        width="64"
        height="64"
        viewBox="0 0 64 64"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-label="Hokeka"
        role="img"
      >
        {/* Shield outline */}
        <path
          d="M32 4 L56 14 V32 C56 46 45 56 32 60 C19 56 8 46 8 32 V14 Z"
          stroke={shieldColor}
          strokeWidth="2.5"
          fill="rgba(31, 111, 235, 0.08)"
          strokeLinejoin="round"
        />
        {/* Centered H */}
        <path
          d="M22 20 V44 M42 20 V44 M22 32 H42"
          stroke={letterColor}
          strokeWidth="3.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {/* Connecting nodes (bottom-right) */}
        <circle cx="46" cy="44" r="2.5" fill={accentColor} />
        <circle cx="51" cy="49" r="2" fill={accentColor} />
        <circle cx="42" cy="50" r="1.75" fill={accentColor} />
        <path
          d="M46 44 L51 49 M46 44 L42 50"
          stroke={accentColor}
          strokeWidth="1"
          strokeLinecap="round"
          opacity="0.8"
        />
      </svg>

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
            className="text-slate-400 mt-1"
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
