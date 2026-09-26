# Hokeka shared design tokens (wave 1)

Single source for the **soft-dark glass** palette used by the AML Console, install docs site, and marketing site.

## Files

| File | Role |
|------|------|
| `tokens.css` | Canonical **CSS custom properties** (`:root`). Import this in any Vite/static build. |
| `primitives.ts` | Typed colour, type, spacing, radii, motion, and shadow constants for React/MUI. |
| `colorMath.ts` | Contrast, alpha, and soft-surface helpers shared with the Console theme layer. |
| `tokens.json` | Machine-readable export for tooling and docs. |
| `index.ts` | Barrel re-export for TypeScript consumers. |

## How each surface consumes tokens

### Console (`FRONTEND/`)

1. **First paint:** `src/theme/globals.css` `@import`s `../../design-tokens/tokens.css`.
2. **Runtime / PSP branding:** `src/theme/tokens.ts` imports primitives from `@hokeka/design-tokens`, builds `buildCssVariables()` for `ThemeContext`.
3. **MUI:** `src/contexts/ThemeContext.tsx` maps tokens into `createTheme()` (palette, typography, component overrides).

Vite alias: `@hokeka/design-tokens` → `../design-tokens/index.ts` (see `FRONTEND/vite.config.ts`).

### Install docs (`docs-site/`)

1. `src/main.tsx` imports `../../design-tokens/tokens.css` before `index.css`.
2. Layout/markdown styles in `src/index.css` reference `var(--*)` only — no duplicate hex for roles that exist in `tokens.css`.

### Marketing site (`website/`)

1. `src/index.css` imports `../../design-tokens/tokens.css` and drops duplicated `:root` colour blocks; site-specific layout rules remain local.

## Rules

- Do not scatter new hex values in components when a token exists.
- Gold is an **accent** (labels, borders, primary actions), not a page fill.
- Motion: `var(--motion-fast)` / `var(--motion-base)` with `var(--ease)`; honour `prefers-reduced-motion` (see `tokens.css`).
