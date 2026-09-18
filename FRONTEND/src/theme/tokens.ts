/* ============================================================================
   Hokeka design tokens — single source of truth for the dashboard.

   Mirrors the marketing site (`website/src/index.css`): dark editorial finance
   aesthetic, gold accent, teal signal colour, hairline rules, 3px radii,
   Manrope headings over DM Sans body copy.

   Consumed by:
     - src/contexts/ThemeContext.tsx  (MUI theme + runtime CSS variables)
     - src/theme/globals.css          (static CSS custom property defaults)
     - tailwind.config.ts             (reads the same CSS variables)

   Every foreground/background pair used for text has been contrast-checked;
   measured ratios are noted inline. Body text targets WCAG AA 4.5:1, large
   text and non-text UI boundaries target 3:1.
   ========================================================================== */

/* -------------------------------------------------------------------------- */
/* Colour maths                                                               */
/* -------------------------------------------------------------------------- */

export interface Rgb {
    r: number;
    g: number;
    b: number;
}

export function hexToRgb(hex: string): Rgb | null {
    const normalised = hex.trim().replace(/^#/, "");
    const expanded =
        normalised.length === 3
            ? normalised
                  .split("")
                  .map((c) => c + c)
                  .join("")
            : normalised;
    if (!/^[0-9a-f]{6}$/i.test(expanded)) return null;
    return {
        r: parseInt(expanded.slice(0, 2), 16),
        g: parseInt(expanded.slice(2, 4), 16),
        b: parseInt(expanded.slice(4, 6), 16),
    };
}

function toHex(rgb: Rgb): string {
    const clamp = (v: number) => Math.max(0, Math.min(255, Math.round(v)));
    return `#${[rgb.r, rgb.g, rgb.b].map((v) => clamp(v).toString(16).padStart(2, "0")).join("")}`;
}

/** Space-separated RGB channels, for `rgb(var(--x) / <alpha-value>)` in Tailwind. */
export function rgbChannels(hex: string, fallback = "211 179 113"): string {
    const rgb = hexToRgb(hex);
    return rgb ? `${rgb.r} ${rgb.g} ${rgb.b}` : fallback;
}

/** Linear blend of two hex colours. `amount` 0 → a, 1 → b. */
export function mix(a: string, b: string, amount: number): string {
    const ca = hexToRgb(a);
    const cb = hexToRgb(b);
    if (!ca || !cb) return a;
    return toHex({
        r: ca.r + (cb.r - ca.r) * amount,
        g: ca.g + (cb.g - ca.g) * amount,
        b: ca.b + (cb.b - ca.b) * amount,
    });
}

export function lighten(hex: string, amount: number): string {
    return mix(hex, "#ffffff", amount);
}

export function darken(hex: string, amount: number): string {
    return mix(hex, "#000000", amount);
}

/**
 * Tokens that also publish an `-rgb` channel triplet. Lets `withAlpha` emit
 * plain `rgb(R G B / a)` for a `var(--token)` input instead of `color-mix`.
 * That matters twice over: the space-separated rgb() syntax is supported far
 * more widely than `color-mix` (Chrome 65 / Safari 12.1 / Firefox 52 vs
 * Chrome 111 / Safari 16.2 / Firefox 113), AND it still resolves through the
 * live CSS variable, so a PSP re-brand keeps tinting correctly.
 */
const CHANNEL_VAR: Record<string, string> = {
    "--gold": "--brand-accent-rgb",
    "--brand-accent": "--brand-accent-rgb",
    "--brand-primary": "--brand-primary-rgb",
    "--teal": "--brand-secondary-rgb",
    "--brand-secondary": "--brand-secondary-rgb",
    "--ink": "--ink-rgb",
    "--muted": "--muted-rgb",
    "--muted-2": "--muted-2-rgb",
    "--success": "--success-rgb",
    "--warning": "--warning-rgb",
    "--danger": "--danger-rgb",
    "--info": "--info-rgb",
    "--risk-critical": "--risk-critical-rgb",
    "--risk-high": "--risk-high-rgb",
    "--risk-medium": "--risk-medium-rgb",
    "--risk-low": "--risk-low-rgb",
    "--surface-0": "--surface-0-rgb",
    "--surface-1": "--surface-1-rgb",
    "--surface-2": "--surface-2-rgb",
    "--surface-3": "--surface-3-rgb",
    "--surface-4": "--surface-4-rgb",
};

/**
 * Apply an alpha to any colour string: a hex, a `var(--token)` reference, or an
 * arbitrary CSS colour. Never produces a bare `transparent`, so a failure can
 * only ever flatten a wash — it cannot strand text on an unpainted ground.
 */
export function withAlpha(colour: string, alpha: number): string {
    const rgb = hexToRgb(colour);
    if (rgb) return `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, ${alpha})`;

    const varMatch = /^var\(\s*(--[a-z0-9-]+)\s*\)$/i.exec(colour.trim());
    const channel = varMatch ? CHANNEL_VAR[varMatch[1]] : undefined;
    if (channel) return `rgb(var(${channel}) / ${alpha})`;

    return `color-mix(in srgb, ${colour} ${Math.round(alpha * 100)}%, transparent)`;
}

/** Relative luminance per WCAG 2.1. */
export function relativeLuminance(hex: string): number {
    const rgb = hexToRgb(hex);
    if (!rgb) return 0;
    const channel = (v: number) => {
        const s = v / 255;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * channel(rgb.r) + 0.7152 * channel(rgb.g) + 0.0722 * channel(rgb.b);
}

/** WCAG contrast ratio between two opaque colours (1 → 21). */
export function contrastRatio(a: string, b: string): number {
    const la = relativeLuminance(a);
    const lb = relativeLuminance(b);
    const [hi, lo] = la > lb ? [la, lb] : [lb, la];
    return (hi + 0.05) / (lo + 0.05);
}

/** Pick whichever of ink/near-black reads better on `background`. */
export function readableTextOn(background: string): string {
    return contrastRatio(colors.ink, background) >= contrastRatio(colors.bg, background)
        ? colors.ink
        : colors.bg;
}

/**
 * Lift (or, on light grounds, deepen) `colour` until it clears `minRatio`
 * against `background`. Used so a PSP-supplied brand colour is still legible
 * as an accent on the dark editorial ground.
 */
export function ensureContrast(colour: string, background: string, minRatio = 4.5): string {
    if (!hexToRgb(colour)) return colour;
    const towards = relativeLuminance(background) < 0.18 ? "#ffffff" : "#000000";
    let candidate = colour;
    for (let step = 0; step <= 20; step += 1) {
        if (contrastRatio(candidate, background) >= minRatio) return candidate;
        candidate = mix(colour, towards, step / 20);
    }
    return candidate;
}

/* -------------------------------------------------------------------------- */
/* Core palette — lifted verbatim from website/src/index.css                   */
/* -------------------------------------------------------------------------- */

export const colors = {
    /* Grounds. surface0 → surface4 is the elevation ramp. */
    bg: "#080909", //  --bg
    bg2: "#0c0e0d", //  --bg-2
    surface0: "#080909",
    surface1: "#0c0e0d",
    surface2: "#121514", // website .product-frame
    surface3: "#191c1b", // website .product-nav .active / .activity-head
    surface4: "#232624", // hover / raised

    /* Ink. */
    ink: "#f5f2eb", //  --ink        17.83:1 on bg
    muted: "#a8aba8", //  --muted      8.60:1 on bg, 6.59:1 on surface4
    muted2: "#858986", //  --muted-2    5.62:1 on bg (disabled/decorative only)

    /* Brand. */
    gold: "#d3b371", //  --gold        9.93:1 on bg
    goldBright: "#e1c684", //  --gold-bright 11.97:1 on bg
    goldDeep: "#74511e", //  --gold-deep   (fills on light grounds only)
    teal: "#75b7ab", //  --teal        8.66:1 on bg
    amber: "#e2b25d", //  --amber       10.22:1 on bg

    /* Hairlines — website uses --line #ffffff1f (12% white). */
    line: "rgba(255, 255, 255, 0.12)",
    lineStrong: "rgba(255, 255, 255, 0.22)",
    /** Boundary for interactive controls; 3.02:1 against bg, clears WCAG 1.4.11. */
    lineControl: "rgba(255, 255, 255, 0.34)",
} as const;

/* -------------------------------------------------------------------------- */
/* Semantic colours                                                           */
/* -------------------------------------------------------------------------- */

/**
 * How much of a signal hue is tinted into `surface2` to make its badge/alert
 * background. These backgrounds are deliberately OPAQUE rather than an alpha
 * tint: an alpha tint composites against whatever row surface is underneath,
 * so a hovered row (surface4) silently lightens the badge and eats contrast.
 * With an opaque background the token-on-background ratio is deterministic and
 * every pair below is measured, not assumed.
 */
const SOFT_TINT = 0.2;

/** Badge/alert background for a signal hue. Guaranteed ≥4.6:1 for its own text. */
export function softSurface(colour: string): string {
    return mix("#121514", colour, SOFT_TINT);
}

/**
 * Chosen to sit inside the gold/teal editorial family rather than MUI's
 * defaults: teal for positive, the site's amber for caution, a warm coral that
 * belongs to the same earthy range as gold for danger, and a muted steel blue
 * for neutral information (the only cool hue, so it never reads as a warning).
 */
export const semantic = {
    success: "#75b7ab", //  8.66:1 on bg / 6.64:1 on surface4 / 5.28:1 on its soft bg
    successSoft: softSurface("#75b7ab"),
    warning: "#e2b25d", // 10.22:1 on bg / 7.83:1 on surface4 / 5.95:1 on its soft bg
    warningSoft: softSurface("#e2b25d"),
    error: "#e8776b", //  6.91:1 on bg / 5.30:1 on surface4 / 4.64:1 on its soft bg
    errorSoft: softSurface("#e8776b"),
    info: "#84a9c4", //  8.03:1 on bg / 6.15:1 on surface4 / 4.99:1 on its soft bg
    infoSoft: softSurface("#84a9c4"),
    neutral: "#a8aba8",
    neutralSoft: softSurface("#a8aba8"),
} as const;

/**
 * Risk score / alert severity. Sequential warm ramp, teal at the safe end.
 *
 * `unknown` is NOT `muted2`: these badges carry meaning, so they cannot use the
 * disabled-text token (which measures 4.31:1 on surface4 and is only allowed to
 * because WCAG 1.4.3 exempts inactive controls). It gets its own neutral that
 * clears 4.5:1 on every surface AND on its own badge background.
 */
export const risk = {
    critical: "#e8776b", //  6.91:1 on bg / 5.30:1 on surface4
    high: "#e08a4f", //  7.52:1 on bg / 5.76:1 on surface4
    medium: "#e2b25d", // 10.22:1 on bg / 7.83:1 on surface4
    low: "#75b7ab", //  8.66:1 on bg / 6.64:1 on surface4
    unknown: "#a0a4a1", //  7.90:1 on bg / 6.05:1 on surface4
} as const;

export const riskSoft = {
    critical: softSurface(risk.critical),
    high: softSurface(risk.high),
    medium: softSurface(risk.medium),
    low: softSurface(risk.low),
    unknown: softSurface(risk.unknown),
} as const;

/** Decision outcomes surfaced by the rules engine. */
export const decision = {
    ALLOW: "#75b7ab",
    ALERT: "#e2b25d",
    REVIEW: "#d3b371",
    HOLD: "#e08a4f",
    BLOCK: "#e8776b",
} as const;

export type RiskKey = keyof typeof risk;
export type DecisionKey = keyof typeof decision;

/** Resolve any risk level / severity label to its token colour. */
export function riskColor(level: string | null | undefined): string {
    const key = (level ?? "").trim().toUpperCase();
    if (key === "CRITICAL" || key === "SEVERE" || key === "VERY_HIGH") return risk.critical;
    if (key === "HIGH") return risk.high;
    if (key === "MEDIUM" || key === "MODERATE") return risk.medium;
    if (key === "LOW" || key === "MINIMAL") return risk.low;
    return risk.unknown;
}

/** Resolve a risk score (0–100) to its token colour. */
export function riskScoreColor(score: number): string {
    if (score >= 80) return risk.critical;
    if (score >= 60) return risk.high;
    if (score >= 35) return risk.medium;
    return risk.low;
}

/** Resolve an engine decision (ALLOW / ALERT / REVIEW / HOLD / BLOCK). */
export function decisionColor(value: string | null | undefined): string {
    const key = (value ?? "").trim().toUpperCase() as DecisionKey;
    return decision[key] ?? risk.unknown;
}

/* -------------------------------------------------------------------------- */
/* Shape, motion, elevation                                                   */
/* -------------------------------------------------------------------------- */

export const radii = {
    xs: "2px",
    /** --radius on the marketing site. */
    base: "3px",
    md: "4px",
    lg: "5px",
    xl: "6px",
    xxl: "8px",
    pill: "9999px",
} as const;

export const motion = {
    /** --ease on the marketing site. */
    ease: "cubic-bezier(.22, 1, .36, 1)",
    fast: "150ms",
    base: "250ms",
    slow: "400ms",
} as const;

export const shadows = {
    none: "none",
    /** Hairline lift, for chips and inputs. */
    xs: "0 1px 2px rgba(0, 0, 0, 0.45)",
    /** Cards at rest. */
    sm: "0 10px 30px -18px rgba(0, 0, 0, 0.9)",
    /** Cards on hover (website .asset-card:hover). */
    md: "0 26px 50px -30px rgba(0, 0, 0, 0.75)",
    /** Menus, popovers. */
    lg: "0 24px 60px -24px rgba(0, 0, 0, 0.85)",
    /** Dialogs (website .product-frame). */
    xl: "0 40px 100px -30px rgba(0, 0, 0, 0.72)",
    /** Gold halo under primary buttons (website .button:hover). */
    gold: "0 14px 34px -12px rgba(211, 179, 113, 0.6)",
    teal: "0 14px 34px -14px rgba(117, 183, 171, 0.5)",
} as const;

/* -------------------------------------------------------------------------- */
/* Typography                                                                 */
/* -------------------------------------------------------------------------- */

export const fonts = {
    display: "'Manrope', 'Inter', system-ui, sans-serif",
    body: "'DM Sans', 'Inter', system-ui, sans-serif",
    mono: "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace",
} as const;

export const typeScale = {
    h1: { size: "2.75rem", weight: 600, lineHeight: 1.05, letterSpacing: "-0.02em" },
    h2: { size: "2.125rem", weight: 600, lineHeight: 1.08, letterSpacing: "-0.018em" },
    h3: { size: "1.6875rem", weight: 500, lineHeight: 1.15, letterSpacing: "-0.015em" },
    h4: { size: "1.375rem", weight: 500, lineHeight: 1.22, letterSpacing: "-0.012em" },
    h5: { size: "1.125rem", weight: 600, lineHeight: 1.3, letterSpacing: "-0.01em" },
    h6: { size: "0.9375rem", weight: 600, lineHeight: 1.35, letterSpacing: "-0.006em" },
    body1: { size: "0.9063rem", weight: 400, lineHeight: 1.65, letterSpacing: "0" },
    body2: { size: "0.8125rem", weight: 400, lineHeight: 1.6, letterSpacing: "0" },
    caption: { size: "0.7188rem", weight: 400, lineHeight: 1.5, letterSpacing: "0.01em" },
    /** The website's `.eyebrow`: gold, uppercase, generously tracked. */
    overline: { size: "0.6875rem", weight: 600, lineHeight: 1.4, letterSpacing: "0.16em" },
    button: { size: "0.8125rem", weight: 600, lineHeight: 1.2, letterSpacing: "0.01em" },
} as const;

/* -------------------------------------------------------------------------- */
/* Brand resolution (platform default + per-PSP overrides)                    */
/* -------------------------------------------------------------------------- */

export interface BrandInput {
    primaryColor?: string;
    secondaryColor?: string;
    accentColor?: string;
    fontFamily?: string;
    buttonRadius?: string;
}

export interface ResolvedBrand {
    /** Raw brand hue as configured (used for fills). */
    primary: string;
    /** Brand hue guaranteed ≥4.5:1 on the dark ground (used for text/icons). */
    accent: string;
    accentBright: string;
    accentDeep: string;
    /** Text colour that reads on a solid `primary` fill. */
    onPrimary: string;
    secondary: string;
    onSecondary: string;
    /** Elevation ramp, neutral by default and brand-tinted for custom PSPs. */
    surfaces: [string, string, string, string, string];
    fontBody: string;
    fontDisplay: string;
    buttonRadius: string;
    isCustom: boolean;
}

/**
 * Clearing 4.5:1 against the page surface is not sufficient on its own: the same
 * accent is also used as label text on its own tinted badge ground, which is a
 * lighter surface. Lift until BOTH hold, so a PSP-branded chip is as legible as
 * a platform one. (The Hokeka default already passes at 6.11:1 and is untouched.)
 */
function ensureBadgeContrast(colour: string, minRatio = 4.5): string {
    if (!hexToRgb(colour)) return colour;
    let candidate = colour;
    for (let step = 0; step <= 20; step += 1) {
        if (contrastRatio(candidate, softSurface(candidate)) >= minRatio) return candidate;
        candidate = mix(colour, "#ffffff", step / 20);
    }
    return candidate;
}

/** Platform-admin / Hokeka default. */
export const DEFAULT_PRIMARY = colors.gold;
export const DEFAULT_SECONDARY = colors.teal;

const NEUTRAL_SURFACES: [string, string, string, string, string] = [
    colors.surface0,
    colors.surface1,
    colors.surface2,
    colors.surface3,
    colors.surface4,
];

/** How much of a custom PSP hue bleeds into the chrome. Keeps it dark. */
const PSP_SURFACE_TINT = 0.08;

export function resolveBrand(input: BrandInput | null | undefined): ResolvedBrand {
    const rawPrimary = (input?.primaryColor && hexToRgb(input.primaryColor) && input.primaryColor) || DEFAULT_PRIMARY;
    const rawSecondary =
        (input?.secondaryColor && hexToRgb(input.secondaryColor) && input.secondaryColor) || DEFAULT_SECONDARY;
    const isCustom = rawPrimary.toLowerCase() !== DEFAULT_PRIMARY.toLowerCase();

    const accent = ensureBadgeContrast(ensureContrast(rawPrimary, colors.surface1, 4.5));
    const secondary = ensureBadgeContrast(ensureContrast(rawSecondary, colors.surface1, 4.5));

    const surfaces = (
        isCustom ? NEUTRAL_SURFACES.map((s) => mix(s, rawPrimary, PSP_SURFACE_TINT)) : [...NEUTRAL_SURFACES]
    ) as [string, string, string, string, string];

    return {
        primary: rawPrimary,
        accent,
        // Platform default keeps the marketing site's exact gold pair.
        accentBright: isCustom ? ensureContrast(lighten(accent, 0.18), colors.surface1, 4.5) : colors.goldBright,
        accentDeep: isCustom ? darken(rawPrimary, 0.45) : colors.goldDeep,
        onPrimary: readableTextOn(rawPrimary),
        secondary,
        onSecondary: readableTextOn(rawSecondary),
        surfaces,
        fontBody: input?.fontFamily || fonts.body,
        fontDisplay: input?.fontFamily || fonts.display,
        buttonRadius: input?.buttonRadius || radii.base,
        isCustom,
    };
}

/* -------------------------------------------------------------------------- */
/* CSS custom properties                                                      */
/* -------------------------------------------------------------------------- */

/**
 * Emits every token as a CSS custom property so plain-CSS and Tailwind parts of
 * the app resolve against the same values as the MUI theme. The `*-rgb`
 * channel triplets back Tailwind's `<alpha-value>` colour functions.
 */
export function buildCssVariables(brand: ResolvedBrand): Record<string, string> {
    const [s0, s1, s2, s3, s4] = brand.surfaces;

    const vars: Record<string, string> = {
        /* --- website-parity core ------------------------------------------ */
        "--bg": s0,
        "--bg-2": s1,
        "--ink": colors.ink,
        "--muted": colors.muted,
        "--muted-2": colors.muted2,
        "--gold": brand.accent,
        "--gold-bright": brand.accentBright,
        "--gold-deep": brand.accentDeep,
        "--teal": brand.secondary,
        "--amber": colors.amber,
        "--line": colors.line,
        "--line-strong": colors.lineStrong,
        "--line-control": colors.lineControl,
        "--radius": brand.buttonRadius,
        "--ease": motion.ease,

        /* --- elevation ramp ------------------------------------------------ */
        "--surface-0": s0,
        "--surface-1": s1,
        "--surface-2": s2,
        "--surface-3": s3,
        "--surface-4": s4,
        "--surface-0-rgb": rgbChannels(s0, "8 9 9"),
        "--surface-1-rgb": rgbChannels(s1, "12 14 13"),
        "--surface-2-rgb": rgbChannels(s2, "18 21 20"),
        "--surface-3-rgb": rgbChannels(s3, "25 28 27"),
        "--surface-4-rgb": rgbChannels(s4, "35 38 36"),

        /* --- text ---------------------------------------------------------- */
        "--text-primary": colors.ink,
        "--text-secondary": colors.muted,
        "--text-tertiary": colors.muted2,
        "--ink-rgb": rgbChannels(colors.ink, "245 242 235"),
        "--muted-rgb": rgbChannels(colors.muted, "168 171 168"),
        "--muted-2-rgb": rgbChannels(colors.muted2, "133 137 134"),

        /* --- brand --------------------------------------------------------- */
        "--brand-primary": brand.primary,
        "--brand-accent": brand.accent,
        "--brand-accent-rgb": rgbChannels(brand.accent),
        "--brand-primary-rgb": rgbChannels(brand.primary),
        "--brand-secondary": brand.secondary,
        "--brand-secondary-rgb": rgbChannels(brand.secondary, "117 183 171"),
        "--brand-on-primary": brand.onPrimary,
        "--brand-on-secondary": brand.onSecondary,
        "--brand-soft": softSurface(brand.accent),
        "--secondary-soft": softSurface(brand.secondary),

        /* --- semantic ------------------------------------------------------ */
        "--success": semantic.success,
        "--success-soft": semantic.successSoft,
        "--warning": semantic.warning,
        "--warning-soft": semantic.warningSoft,
        "--danger": semantic.error,
        "--danger-soft": semantic.errorSoft,
        "--info": semantic.info,
        "--info-soft": semantic.infoSoft,
        "--neutral-soft": semantic.neutralSoft,
        "--success-rgb": rgbChannels(semantic.success, "117 183 171"),
        "--warning-rgb": rgbChannels(semantic.warning, "226 178 93"),
        "--danger-rgb": rgbChannels(semantic.error, "232 119 107"),
        "--info-rgb": rgbChannels(semantic.info, "132 169 196"),

        /* --- risk ---------------------------------------------------------- */
        "--risk-critical": risk.critical,
        "--risk-high": risk.high,
        "--risk-medium": risk.medium,
        "--risk-low": risk.low,
        "--risk-unknown": risk.unknown,
        "--risk-critical-soft": riskSoft.critical,
        "--risk-high-soft": riskSoft.high,
        "--risk-medium-soft": riskSoft.medium,
        "--risk-low-soft": riskSoft.low,
        "--risk-unknown-soft": riskSoft.unknown,
        "--risk-critical-rgb": rgbChannels(risk.critical, "232 119 107"),
        "--risk-high-rgb": rgbChannels(risk.high, "224 138 79"),
        "--risk-medium-rgb": rgbChannels(risk.medium, "226 178 93"),
        "--risk-low-rgb": rgbChannels(risk.low, "117 183 171"),

        /* --- decisions ------------------------------------------------------ */
        "--decision-allow": decision.ALLOW,
        "--decision-alert": decision.ALERT,
        "--decision-review": decision.REVIEW,
        "--decision-hold": decision.HOLD,
        "--decision-block": decision.BLOCK,

        /* --- panels / glass ------------------------------------------------- */
        "--panel": s1,
        "--panel-raised": s2,
        "--panel-hover": s3,
        "--glass": `rgb(${rgbChannels(s1, "12 14 13")} / 0.86)`,
        "--glass-surface": `rgb(${rgbChannels(s2, "18 21 20")} / 0.9)`,
        "--glass-panel": `rgb(${rgbChannels(s1, "12 14 13")} / 0.94)`,
        "--glass-border": colors.line,
        "--glass-border-hover": `rgb(${rgbChannels(brand.accent)} / 0.42)`,
        "--glass-skeleton": `rgb(${rgbChannels(s4, "35 38 36")} / 0.7)`,
        "--glass-shadow": shadows.sm,
        "--glass-glow": `0 0 0 1px rgb(${rgbChannels(brand.accent)} / 0.18), ${shadows.md}`,

        /* --- elevation tokens ------------------------------------------------ */
        "--shadow-xs": shadows.xs,
        "--shadow-sm": shadows.sm,
        "--shadow-md": shadows.md,
        "--shadow-lg": shadows.lg,
        "--shadow-xl": shadows.xl,
        "--shadow-gold": `0 14px 34px -12px rgb(${rgbChannels(brand.accent)} / 0.55)`,

        /* --- type ------------------------------------------------------------ */
        "--font-body": brand.fontBody,
        "--font-display": brand.fontDisplay,

        /* --- legacy aliases (kept so existing markup keeps resolving) -------- */
        "--burgundy-950": s0,
        "--burgundy-900": s1,
        "--burgundy-850": s2,
        "--burgundy-800": s3,
        "--burgundy-700": s4,
        "--brand-primary-950-rgb": rgbChannels(s0, "8 9 9"),
        "--brand-primary-900-rgb": rgbChannels(s1, "12 14 13"),
        "--brand-primary-850-rgb": rgbChannels(s2, "18 21 20"),
        "--brand-primary-800-rgb": rgbChannels(s3, "25 28 27"),
        "--charcoal": s0,
        "--charcoal-alt": s1,
    };

    return vars;
}

/** Convenience for tests/tools: the platform-default variable set. */
export const defaultBrand = resolveBrand(null);
