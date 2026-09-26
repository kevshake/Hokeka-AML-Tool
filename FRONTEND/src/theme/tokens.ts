/* ============================================================================
   Console theme layer — runtime brand resolution + MUI CSS variable emission.

   Canonical palette lives in repo-root `design-tokens/` (CSS + TS + JSON).
   This module adds PSP brand overrides and `buildCssVariables()` for ThemeContext.
   ========================================================================== */

import {
    colors,
    decision,
    DEFAULT_PRIMARY,
    DEFAULT_SECONDARY,
    fonts,
    motion,
    radii,
    risk,
    riskSoft,
    semantic,
    shadows,
    softSurface,
    typeScale,
} from "@hokeka/design-tokens";
import {
    contrastRatio,
    darken,
    hexToRgb,
    lighten,
    mix,
    relativeLuminance,
    rgbChannels,
    type Rgb,
} from "@hokeka/design-tokens";

export type { Rgb };
export {
    colors,
    decision,
    DEFAULT_PRIMARY,
    DEFAULT_SECONDARY,
    fonts,
    motion,
    radii,
    risk,
    riskSoft,
    semantic,
    shadows,
    softSurface,
    typeScale,
    contrastRatio,
    darken,
    hexToRgb,
    lighten,
    mix,
    relativeLuminance,
    rgbChannels,
};

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
