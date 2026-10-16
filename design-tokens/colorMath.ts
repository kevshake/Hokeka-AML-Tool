/** Shared colour maths for Hokeka surfaces (Console, docs-site, marketing). */

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

/** Space-separated RGB channels, for `rgb(var(--x) / <alpha-value>)`. */
export function rgbChannels(hex: string, fallback = "211 179 113"): string {
  const rgb = hexToRgb(hex);
  return rgb ? `${rgb.r} ${rgb.g} ${rgb.b}` : fallback;
}

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

const SOFT_TINT = 0.2;

export function softSurface(colour: string, base = "#121514"): string {
  return mix(base, colour, SOFT_TINT);
}

export function relativeLuminance(hex: string): number {
  const rgb = hexToRgb(hex);
  if (!rgb) return 0;
  const channel = (v: number) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(rgb.r) + 0.7152 * channel(rgb.g) + 0.0722 * channel(rgb.b);
}

export function contrastRatio(a: string, b: string): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  const [hi, lo] = la > lb ? [la, lb] : [lb, la];
  return (hi + 0.05) / (lo + 0.05);
}
