import { createContext, useContext, useState, useEffect, ReactNode, useMemo } from "react";
import { createTheme, Theme, ThemeProvider as MuiThemeProvider } from "@mui/material";
import { useAuth } from "./AuthContext";
import {
    buildCssVariables,
    colors,
    fonts,
    motion,
    radii,
    resolveBrand,
    semantic,
    shadows,
    softSurface,
    typeScale,
    type BrandInput,
    type ResolvedBrand,
} from "../theme/tokens";

interface PspTheme extends BrandInput {
    brandingTheme?: string;
    accentColor?: string;
    logoUrl?: string;
    fontSize?: string;
    buttonStyle?: string;
    navStyle?: string;
}

interface ThemeContextType {
    theme: Theme;
    pspTheme: PspTheme | null;
    updateTheme: (theme: PspTheme) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

/* -------------------------------------------------------------------------- */
/* MUI theme                                                                  */
/* -------------------------------------------------------------------------- */

/**
 * Builds the dark editorial MUI theme from the shared Hokeka tokens. The same
 * function serves platform admins (default gold identity) and PSP users — a
 * PSP's `primaryColor` / `secondaryColor` / `fontFamily` / `buttonRadius` flow
 * in through `resolveBrand`, which also guarantees the resulting accent still
 * clears 4.5:1 on the dark ground.
 */
function createHokekaTheme(brand: ResolvedBrand): Theme {
    const [, surface1, surface2, surface3, surface4] = brand.surfaces;
    const radius = brand.buttonRadius;

    return createTheme({
        palette: {
            mode: "dark",
            common: { black: colors.bg, white: colors.ink },
            primary: {
                main: brand.accent,
                light: brand.accentBright,
                dark: brand.accentDeep,
                contrastText: brand.onPrimary,
            },
            secondary: {
                main: brand.secondary,
                light: "#93c8bd",
                dark: "#3f6d65",
                contrastText: brand.onSecondary,
            },
            success: { main: semantic.success, light: "#93c8bd", dark: "#4c8478", contrastText: colors.bg },
            warning: { main: semantic.warning, light: "#eccb8e", dark: "#a97f34", contrastText: colors.bg },
            error: { main: semantic.error, light: "#f0a096", dark: "#a94b41", contrastText: colors.bg },
            info: { main: semantic.info, light: "#a8c4d8", dark: "#54748b", contrastText: colors.bg },
            background: { default: brand.surfaces[0], paper: surface2 },
            text: {
                primary: colors.ink,
                secondary: colors.muted,
                disabled: colors.muted2,
            },
            divider: colors.line,
            action: {
                hover: surface3,
                selected: surface4,
                disabled: colors.muted2,
                disabledBackground: surface3,
                focus: `rgba(255, 255, 255, 0.08)`,
            },
        },

        shape: { borderRadius: 3 },

        typography: {
            fontFamily: brand.fontBody,
            fontSize: 14,
            h1: {
                fontFamily: brand.fontDisplay,
                fontSize: typeScale.h1.size,
                fontWeight: typeScale.h1.weight,
                lineHeight: typeScale.h1.lineHeight,
                letterSpacing: typeScale.h1.letterSpacing,
            },
            h2: {
                fontFamily: brand.fontDisplay,
                fontSize: typeScale.h2.size,
                fontWeight: typeScale.h2.weight,
                lineHeight: typeScale.h2.lineHeight,
                letterSpacing: typeScale.h2.letterSpacing,
            },
            h3: {
                fontFamily: brand.fontDisplay,
                fontSize: typeScale.h3.size,
                fontWeight: typeScale.h3.weight,
                lineHeight: typeScale.h3.lineHeight,
                letterSpacing: typeScale.h3.letterSpacing,
            },
            h4: {
                fontFamily: brand.fontDisplay,
                fontSize: typeScale.h4.size,
                fontWeight: typeScale.h4.weight,
                lineHeight: typeScale.h4.lineHeight,
                letterSpacing: typeScale.h4.letterSpacing,
            },
            h5: {
                fontFamily: brand.fontDisplay,
                fontSize: typeScale.h5.size,
                fontWeight: typeScale.h5.weight,
                lineHeight: typeScale.h5.lineHeight,
                letterSpacing: typeScale.h5.letterSpacing,
            },
            h6: {
                fontFamily: brand.fontDisplay,
                fontSize: typeScale.h6.size,
                fontWeight: typeScale.h6.weight,
                lineHeight: typeScale.h6.lineHeight,
                letterSpacing: typeScale.h6.letterSpacing,
            },
            subtitle1: { fontSize: "0.9063rem", fontWeight: 500, lineHeight: 1.5 },
            subtitle2: { fontSize: "0.8125rem", fontWeight: 600, lineHeight: 1.5 },
            body1: {
                fontSize: typeScale.body1.size,
                lineHeight: typeScale.body1.lineHeight,
            },
            body2: {
                fontSize: typeScale.body2.size,
                lineHeight: typeScale.body2.lineHeight,
                color: colors.muted,
            },
            caption: {
                fontSize: typeScale.caption.size,
                lineHeight: typeScale.caption.lineHeight,
                color: colors.muted,
            },
            overline: {
                fontSize: typeScale.overline.size,
                fontWeight: typeScale.overline.weight,
                letterSpacing: typeScale.overline.letterSpacing,
                textTransform: "uppercase",
                color: brand.accent,
            },
            button: {
                fontSize: typeScale.button.size,
                fontWeight: typeScale.button.weight,
                letterSpacing: typeScale.button.letterSpacing,
                textTransform: "none",
            },
        },

        components: {
            MuiCssBaseline: {
                styleOverrides: {
                    body: {
                        backgroundColor: brand.surfaces[0],
                        color: colors.ink,
                        fontFamily: brand.fontBody,
                    },
                },
            },

            /* --- Chrome --------------------------------------------------- */
            MuiAppBar: {
                defaultProps: { elevation: 0, color: "transparent" },
                styleOverrides: {
                    root: {
                        backgroundColor: `${surface1}f0`,
                        backdropFilter: "blur(18px) saturate(140%)",
                        borderBottom: `1px solid ${colors.line}`,
                        boxShadow: "none",
                        backgroundImage: "none",
                        color: colors.ink,
                    },
                },
            },
            MuiDrawer: {
                styleOverrides: {
                    paper: {
                        backgroundColor: surface1,
                        backgroundImage: "none",
                        borderRight: `1px solid ${colors.line}`,
                        color: colors.ink,
                    },
                },
            },
            MuiToolbar: {
                styleOverrides: { root: { minHeight: 64 } },
            },

            /* --- Surfaces -------------------------------------------------- */
            MuiPaper: {
                defaultProps: { elevation: 0 },
                styleOverrides: {
                    root: {
                        backgroundColor: surface2,
                        backgroundImage: "none",
                        color: colors.ink,
                        borderRadius: radii.base,
                    },
                    outlined: { border: `1px solid ${colors.line}` },
                    elevation0: { boxShadow: "none" },
                    elevation1: { boxShadow: shadows.sm, border: `1px solid ${colors.line}` },
                    elevation2: { boxShadow: shadows.sm, border: `1px solid ${colors.line}` },
                    elevation3: { boxShadow: shadows.md, border: `1px solid ${colors.line}` },
                },
            },
            MuiCard: {
                defaultProps: { elevation: 0 },
                styleOverrides: {
                    root: {
                        backgroundColor: surface2,
                        backgroundImage: "none",
                        border: `1px solid ${colors.line}`,
                        borderRadius: radii.base,
                        boxShadow: shadows.sm,
                        transition: `border-color .25s ${motion.ease}, box-shadow .25s ${motion.ease}, transform .25s ${motion.ease}`,
                        "&:hover": {
                            borderColor: colors.lineStrong,
                            boxShadow: shadows.md,
                        },
                    },
                },
            },
            MuiCardHeader: {
                styleOverrides: {
                    root: { padding: "18px 20px 6px" },
                    title: {
                        fontFamily: brand.fontDisplay,
                        fontSize: "0.9375rem",
                        fontWeight: 600,
                        letterSpacing: "-0.006em",
                    },
                    subheader: { fontSize: "0.75rem", color: colors.muted },
                },
            },
            MuiCardContent: {
                styleOverrides: { root: { padding: "16px 20px", "&:last-child": { paddingBottom: 20 } } },
            },
            MuiDivider: {
                styleOverrides: { root: { borderColor: colors.line } },
            },

            /* --- Buttons --------------------------------------------------- */
            MuiButton: {
                defaultProps: { disableElevation: true },
                styleOverrides: {
                    root: {
                        position: "relative",
                        overflow: "hidden",
                        borderRadius: radius,
                        textTransform: "none",
                        fontWeight: 600,
                        letterSpacing: "0.01em",
                        paddingInline: 18,
                        minHeight: 38,
                        transition: `transform .25s ${motion.ease}, background-color .2s, border-color .2s, box-shadow .3s`,
                        "&:hover": { transform: "translateY(-2px)" },
                        "&.Mui-disabled": { transform: "none", opacity: 0.45 },
                    },
                    containedPrimary: {
                        backgroundColor: brand.accent,
                        color: brand.onPrimary,
                        border: `1px solid ${brand.accent}`,
                        /* The marketing site's sheen sweep. */
                        "&::after": {
                            content: '""',
                            position: "absolute",
                            top: 0,
                            left: "-120%",
                            width: "60%",
                            height: "100%",
                            background:
                                "linear-gradient(100deg, transparent, rgba(255,255,255,.5), transparent)",
                            transform: "skewX(-18deg)",
                            transition: `left .7s ${motion.ease}`,
                        },
                        "&:hover": {
                            backgroundColor: brand.accentBright,
                            borderColor: brand.accentBright,
                            boxShadow: shadows.gold,
                        },
                        "&:hover::after": { left: "130%" },
                    },
                    containedSecondary: {
                        backgroundColor: brand.secondary,
                        color: brand.onSecondary,
                        border: `1px solid ${brand.secondary}`,
                        "&:hover": { boxShadow: shadows.teal },
                    },
                    outlined: {
                        borderColor: colors.lineControl,
                        color: colors.ink,
                        backgroundColor: "transparent",
                        "&:hover": {
                            borderColor: colors.ink,
                            backgroundColor: colors.ink,
                            color: colors.bg,
                        },
                    },
                    outlinedPrimary: {
                        borderColor: `${brand.accent}80`,
                        color: brand.accent,
                        "&:hover": {
                            borderColor: brand.accent,
                            backgroundColor: brand.accent,
                            color: brand.onPrimary,
                        },
                    },
                    text: {
                        color: colors.muted,
                        "&:hover": { color: colors.ink, backgroundColor: surface3 },
                    },
                    textPrimary: { color: brand.accent, "&:hover": { color: brand.accentBright } },
                    sizeSmall: { minHeight: 30, paddingInline: 12, fontSize: "0.75rem" },
                    sizeLarge: { minHeight: 46, paddingInline: 24 },
                },
            },
            MuiIconButton: {
                styleOverrides: {
                    root: {
                        color: colors.muted,
                        borderRadius: radii.base,
                        transition: `color .2s, background-color .2s`,
                        "&:hover": { color: colors.ink, backgroundColor: surface3 },
                    },
                },
            },
            MuiToggleButton: {
                styleOverrides: {
                    root: {
                        borderColor: colors.line,
                        color: colors.muted,
                        textTransform: "none",
                        borderRadius: radii.base,
                        "&.Mui-selected": {
                            backgroundColor: surface3,
                            color: brand.accent,
                            borderColor: `${brand.accent}66`,
                            "&:hover": { backgroundColor: surface4 },
                        },
                    },
                },
            },

            /* --- Inputs ---------------------------------------------------- */
            MuiOutlinedInput: {
                styleOverrides: {
                    root: {
                        backgroundColor: surface1,
                        borderRadius: radii.base,
                        color: colors.ink,
                        transition: `border-color .18s ${motion.ease}, box-shadow .18s ${motion.ease}`,
                        "& .MuiOutlinedInput-notchedOutline": {
                            borderColor: colors.lineControl,
                            transition: `border-color .18s ${motion.ease}`,
                        },
                        "&:hover .MuiOutlinedInput-notchedOutline": {
                            borderColor: `${brand.accent}80`,
                        },
                        "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
                            borderColor: brand.accent,
                            borderWidth: 1,
                        },
                        "&.Mui-focused": { boxShadow: `0 0 0 3px ${brand.accent}29` },
                        "&.Mui-error .MuiOutlinedInput-notchedOutline": { borderColor: semantic.error },
                    },
                    input: {
                        fontSize: "0.8438rem",
                        "&::placeholder": { color: colors.muted2, opacity: 1 },
                    },
                },
            },
            MuiFilledInput: {
                styleOverrides: {
                    root: {
                        backgroundColor: surface1,
                        borderRadius: radii.base,
                        "&:hover, &.Mui-focused": { backgroundColor: surface2 },
                    },
                },
            },
            MuiInputBase: {
                styleOverrides: { root: { color: colors.ink } },
            },
            MuiInputLabel: {
                styleOverrides: {
                    root: {
                        color: colors.muted,
                        fontSize: "0.8438rem",
                        "&.Mui-focused": { color: brand.accent },
                    },
                },
            },
            MuiFormHelperText: {
                styleOverrides: { root: { color: colors.muted2, fontSize: "0.6875rem", marginLeft: 2 } },
            },
            MuiFormLabel: {
                styleOverrides: { root: { "&.Mui-focused": { color: brand.accent } } },
            },
            MuiSelect: {
                styleOverrides: { icon: { color: colors.muted } },
            },
            MuiCheckbox: {
                styleOverrides: {
                    root: {
                        color: colors.lineControl,
                        "&.Mui-checked": { color: brand.accent },
                    },
                },
            },
            MuiRadio: {
                styleOverrides: {
                    root: { color: colors.lineControl, "&.Mui-checked": { color: brand.accent } },
                },
            },
            MuiSwitch: {
                styleOverrides: {
                    switchBase: {
                        "&.Mui-checked": { color: brand.accent },
                        "&.Mui-checked + .MuiSwitch-track": {
                            backgroundColor: brand.accent,
                            opacity: 0.45,
                        },
                    },
                    track: { backgroundColor: colors.muted2 },
                },
            },
            MuiSlider: {
                styleOverrides: {
                    root: { color: brand.accent },
                    rail: { backgroundColor: surface4, opacity: 1 },
                },
            },

            /* --- Data display ----------------------------------------------- */
            MuiTable: {
                styleOverrides: { root: { borderCollapse: "separate", borderSpacing: 0 } },
            },
            MuiTableCell: {
                styleOverrides: {
                    root: {
                        borderBottom: `1px solid ${colors.line}`,
                        color: colors.ink,
                        fontSize: "0.8125rem",
                        paddingTop: 11,
                        paddingBottom: 11,
                    },
                    head: {
                        backgroundColor: surface3,
                        color: colors.muted,
                        fontSize: "0.6563rem",
                        fontWeight: 600,
                        letterSpacing: "0.1em",
                        textTransform: "uppercase",
                        whiteSpace: "nowrap",
                    },
                },
            },
            MuiTableRow: {
                styleOverrides: {
                    root: {
                        transition: "background-color .2s",
                        "&:hover": { backgroundColor: surface3 },
                        "&.Mui-selected, &.Mui-selected:hover": { backgroundColor: surface4 },
                    },
                    head: { "&:hover": { backgroundColor: surface3 } },
                },
            },
            MuiTablePagination: {
                styleOverrides: {
                    root: { color: colors.muted, borderTop: `1px solid ${colors.line}` },
                    selectIcon: { color: colors.muted },
                },
            },
            MuiChip: {
                styleOverrides: {
                    root: {
                        borderRadius: radii.base,
                        height: 24,
                        fontSize: "0.6875rem",
                        fontWeight: 600,
                        letterSpacing: "0.04em",
                        backgroundColor: surface3,
                        color: colors.ink,
                        border: `1px solid ${colors.line}`,
                    },
                    outlined: { backgroundColor: "transparent", borderColor: colors.lineStrong },
                    /* Opaque grounds so hover/selection cannot erode contrast. */
                    colorPrimary: {
                        backgroundColor: softSurface(brand.accent),
                        color: brand.accent,
                        borderColor: `${brand.accent}59`,
                    },
                    colorSecondary: {
                        backgroundColor: softSurface(brand.secondary),
                        color: brand.secondary,
                        borderColor: `${brand.secondary}59`,
                    },
                    colorSuccess: {
                        backgroundColor: semantic.successSoft,
                        color: semantic.success,
                        borderColor: `${semantic.success}59`,
                    },
                    colorWarning: {
                        backgroundColor: semantic.warningSoft,
                        color: semantic.warning,
                        borderColor: `${semantic.warning}59`,
                    },
                    colorError: {
                        backgroundColor: semantic.errorSoft,
                        color: semantic.error,
                        borderColor: `${semantic.error}59`,
                    },
                    colorInfo: {
                        backgroundColor: semantic.infoSoft,
                        color: semantic.info,
                        borderColor: `${semantic.info}59`,
                    },
                    deleteIcon: { color: "inherit", opacity: 0.7, "&:hover": { opacity: 1 } },
                },
            },
            MuiAvatar: {
                styleOverrides: {
                    root: {
                        backgroundColor: surface3,
                        color: brand.accent,
                        fontFamily: brand.fontDisplay,
                        fontWeight: 600,
                        fontSize: "0.8125rem",
                    },
                },
            },
            MuiBadge: {
                styleOverrides: {
                    badge: { fontSize: "0.625rem", fontWeight: 600, minWidth: 17, height: 17 },
                },
            },
            MuiListItemButton: {
                styleOverrides: {
                    root: {
                        borderRadius: radii.base,
                        color: colors.muted,
                        "&:hover": { backgroundColor: surface3, color: colors.ink },
                        "&.Mui-selected": {
                            backgroundColor: surface3,
                            color: colors.ink,
                            boxShadow: `inset 2px 0 0 0 ${brand.accent}`,
                            "&:hover": { backgroundColor: surface4 },
                        },
                    },
                },
            },
            MuiListItemIcon: {
                styleOverrides: { root: { color: "inherit", minWidth: 34 } },
            },
            MuiLinearProgress: {
                styleOverrides: {
                    root: { backgroundColor: surface4, borderRadius: 999, height: 5 },
                    bar: { backgroundColor: brand.accent, borderRadius: 999 },
                },
            },
            MuiCircularProgress: {
                styleOverrides: { root: { color: brand.accent } },
            },
            MuiSkeleton: {
                styleOverrides: { root: { backgroundColor: surface3 } },
            },

            /* --- Navigation -------------------------------------------------- */
            MuiTabs: {
                styleOverrides: {
                    root: { minHeight: 42, borderBottom: `1px solid ${colors.line}` },
                    indicator: { backgroundColor: brand.accent, height: 1 },
                },
            },
            MuiTab: {
                styleOverrides: {
                    root: {
                        textTransform: "none",
                        fontWeight: 500,
                        fontSize: "0.8125rem",
                        letterSpacing: "0.01em",
                        minHeight: 42,
                        color: colors.muted,
                        transition: "color .2s",
                        "&:hover": { color: colors.ink },
                        "&.Mui-selected": { color: brand.accent, fontWeight: 600 },
                    },
                },
            },
            MuiLink: {
                styleOverrides: {
                    root: {
                        color: brand.accent,
                        textDecorationColor: `${brand.accent}66`,
                        "&:hover": { color: brand.accentBright },
                    },
                },
            },
            MuiBreadcrumbs: {
                styleOverrides: { separator: { color: colors.muted2 } },
            },

            /* --- Overlays ----------------------------------------------------- */
            MuiDialog: {
                styleOverrides: {
                    paper: {
                        backgroundColor: surface2,
                        backgroundImage: "none",
                        border: `1px solid ${colors.line}`,
                        borderRadius: radii.md,
                        boxShadow: shadows.xl,
                    },
                },
            },
            MuiDialogTitle: {
                styleOverrides: {
                    root: {
                        fontFamily: brand.fontDisplay,
                        fontSize: "1.0625rem",
                        fontWeight: 600,
                        letterSpacing: "-0.01em",
                        borderBottom: `1px solid ${colors.line}`,
                        paddingBlock: 16,
                    },
                },
            },
            MuiDialogActions: {
                styleOverrides: { root: { borderTop: `1px solid ${colors.line}`, padding: 16 } },
            },
            MuiBackdrop: {
                styleOverrides: { root: { backgroundColor: "rgba(4, 5, 5, 0.72)" } },
            },
            MuiMenu: {
                styleOverrides: {
                    paper: {
                        backgroundColor: surface2,
                        backgroundImage: "none",
                        border: `1px solid ${colors.line}`,
                        borderRadius: radii.base,
                        boxShadow: shadows.lg,
                    },
                },
            },
            MuiMenuItem: {
                styleOverrides: {
                    root: {
                        fontSize: "0.8125rem",
                        borderRadius: radii.xs,
                        margin: "2px 4px",
                        "&:hover": { backgroundColor: surface3 },
                        "&.Mui-selected": { backgroundColor: surface4, color: brand.accent },
                    },
                },
            },
            MuiPopover: {
                styleOverrides: {
                    paper: {
                        backgroundColor: surface2,
                        backgroundImage: "none",
                        border: `1px solid ${colors.line}`,
                        boxShadow: shadows.lg,
                    },
                },
            },
            MuiTooltip: {
                styleOverrides: {
                    tooltip: {
                        backgroundColor: surface3,
                        border: `1px solid ${colors.line}`,
                        color: colors.ink,
                        fontSize: "0.6875rem",
                        fontWeight: 500,
                        borderRadius: radii.xs,
                        boxShadow: shadows.lg,
                    },
                    arrow: { color: surface3 },
                },
            },
            MuiAlert: {
                styleOverrides: {
                    root: { borderRadius: radii.base, border: `1px solid ${colors.line}`, fontSize: "0.8125rem" },
                    standardSuccess: { backgroundColor: semantic.successSoft, color: semantic.success },
                    standardWarning: { backgroundColor: semantic.warningSoft, color: semantic.warning },
                    standardError: { backgroundColor: semantic.errorSoft, color: semantic.error },
                    standardInfo: { backgroundColor: semantic.infoSoft, color: semantic.info },
                    icon: { opacity: 0.9 },
                },
            },
            MuiSnackbarContent: {
                styleOverrides: {
                    root: {
                        backgroundColor: surface3,
                        color: colors.ink,
                        border: `1px solid ${colors.line}`,
                        borderRadius: radii.base,
                    },
                },
            },
            MuiAccordion: {
                styleOverrides: {
                    root: {
                        backgroundColor: surface2,
                        backgroundImage: "none",
                        border: `1px solid ${colors.line}`,
                        borderRadius: radii.base,
                        "&::before": { display: "none" },
                    },
                },
            },
            MuiAccordionSummary: {
                styleOverrides: {
                    root: { minHeight: 46, "&.Mui-expanded": { minHeight: 46 } },
                    content: { marginBlock: 10, "&.Mui-expanded": { marginBlock: 10 } },
                },
            },
        },
    });
}

/* -------------------------------------------------------------------------- */
/* Provider                                                                   */
/* -------------------------------------------------------------------------- */

export function ThemeProvider({ children }: { children: ReactNode }) {
    const { user } = useAuth();
    const [pspTheme, setPspTheme] = useState<PspTheme | null>(null);

    // Extract theme from user's PSP when user changes
    useEffect(() => {
        if (user?.psp?.theme) {
            setPspTheme(user.psp.theme);
        } else {
            // Platform-admin (Hokeka) identity, and any user without PSP branding
            setPspTheme(null);
        }
    }, [user]);

    const brand = useMemo(() => resolveBrand(pspTheme), [pspTheme]);

    // Mirror every token onto :root so plain CSS / Tailwind resolve identically
    useEffect(() => {
        const root = document.documentElement;
        const vars = buildCssVariables(brand);
        Object.entries(vars).forEach(([name, value]) => root.style.setProperty(name, value));
        root.style.setProperty("color-scheme", "dark");
        root.style.fontFamily = brand.fontBody || fonts.body;
    }, [brand]);

    const theme = useMemo(() => createHokekaTheme(brand), [brand]);

    const updateTheme = (newTheme: PspTheme) => {
        setPspTheme(newTheme);
    };

    return (
        <ThemeContext.Provider
            value={{
                theme,
                pspTheme,
                updateTheme,
            }}
        >
            <MuiThemeProvider theme={theme}>{children}</MuiThemeProvider>
        </ThemeContext.Provider>
    );
}

export function useTheme() {
    const context = useContext(ThemeContext);
    if (context === undefined) {
        throw new Error("useTheme must be used within a ThemeProvider");
    }
    return context;
}
