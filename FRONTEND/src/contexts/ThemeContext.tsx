import { createContext, useContext, useState, useEffect, ReactNode, useMemo } from "react";
import { createTheme, Theme, ThemeProvider as MuiThemeProvider } from "@mui/material";
import { useAuth } from "./AuthContext";

interface PspTheme {
    brandingTheme?: string;
    primaryColor?: string;
    secondaryColor?: string;
    accentColor?: string;
    logoUrl?: string;
    fontFamily?: string;
    fontSize?: string;
    buttonRadius?: string;
    buttonStyle?: string;
    navStyle?: string;
}

interface ThemeContextType {
    theme: Theme;
    pspTheme: PspTheme | null;
    updateTheme: (theme: PspTheme) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

// Default theme colors
const DEFAULT_PRIMARY = "#8B4049";
const DEFAULT_SECONDARY = "#C9A961";
const DEFAULT_ACCENT = "#A0525C";

// Helper function to convert hex to RGB
function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result
        ? {
              r: parseInt(result[1], 16),
              g: parseInt(result[2], 16),
              b: parseInt(result[3], 16),
          }
        : null;
}

// Helper function to lighten color
function lightenColor(hex: string, percent: number): string {
    const rgb = hexToRgb(hex);
    if (!rgb) return hex;
    const r = Math.min(255, Math.round(rgb.r + (255 - rgb.r) * percent));
    const g = Math.min(255, Math.round(rgb.g + (255 - rgb.g) * percent));
    const b = Math.min(255, Math.round(rgb.b + (255 - rgb.b) * percent));
    return `#${[r, g, b].map((x) => x.toString(16).padStart(2, "0")).join("")}`;
}

// Helper function to darken color
function darkenColor(hex: string, percent: number): string {
    const rgb = hexToRgb(hex);
    if (!rgb) return hex;
    const r = Math.max(0, Math.round(rgb.r * (1 - percent)));
    const g = Math.max(0, Math.round(rgb.g * (1 - percent)));
    const b = Math.max(0, Math.round(rgb.b * (1 - percent)));
    return `#${[r, g, b].map((x) => x.toString(16).padStart(2, "0")).join("")}`;
}

// Create MUI theme from PSP theme configuration
function createPspTheme(pspTheme: PspTheme | null): Theme {
    const primaryColor = pspTheme?.primaryColor || DEFAULT_PRIMARY;
    const secondaryColor = pspTheme?.secondaryColor || DEFAULT_SECONDARY;
    const accentColor = pspTheme?.accentColor || DEFAULT_ACCENT;
    const fontFamily = pspTheme?.fontFamily || "'Inter', 'Outfit', sans-serif";
    const buttonRadius = pspTheme?.buttonRadius || "12px";

    return createTheme({
        palette: {
            mode: "light",
            primary: {
                main: primaryColor,
                light: lightenColor(primaryColor, 0.2),
                dark: darkenColor(primaryColor, 0.2),
            },
            secondary: {
                main: secondaryColor,
                light: lightenColor(secondaryColor, 0.2),
                dark: darkenColor(secondaryColor, 0.2),
            },
            error: {
                main: accentColor,
                light: lightenColor(accentColor, 0.2),
                dark: darkenColor(accentColor, 0.2),
            },
            background: {
                default: "#FAF8F5",
                paper: "#FFFFFF",
            },
            text: {
                primary: "#3D2C2E",
                secondary: "#7A6B5D",
            },
        },
        typography: {
            fontFamily: fontFamily,
            h1: { fontWeight: 700, color: "#2C3E50" },
            h2: { fontWeight: 700, color: "#2C3E50" },
            h3: { fontWeight: 700, color: "#2C3E50" },
            h4: { fontWeight: 700, color: "#2C3E50" },
            h5: { fontWeight: 700, color: "#2C3E50" },
            h6: { fontWeight: 600, color: "#2C3E50" },
        },
        components: {
            MuiPaper: {
                styleOverrides: {
                    root: {
                        backgroundColor: "#FFFFFF",
                        boxShadow: "0px 4px 20px rgba(0, 0, 0, 0.05)",
                        borderRadius: "20px",
                    },
                },
            },
            MuiCard: {
                styleOverrides: {
                    root: {
                        borderRadius: "20px",
                        boxShadow: "0px 4px 20px rgba(0, 0, 0, 0.05)",
                    },
                },
            },
            MuiButton: {
                styleOverrides: {
                    root: {
                        borderRadius: buttonRadius,
                        textTransform: "none",
                        fontWeight: 600,
                    },
                },
            },
        },
    });
}

export function ThemeProvider({ children }: { children: ReactNode }) {
    const { user } = useAuth();
    const [pspTheme, setPspTheme] = useState<PspTheme | null>(null);

    // Extract theme from user's PSP when user changes
    useEffect(() => {
        if (user?.psp?.theme) {
            setPspTheme(user.psp.theme);
        } else {
            // Default theme for Super Admin or users without PSP theme
            setPspTheme(null);
        }
    }, [user]);

    // Create MUI theme from PSP theme
    const theme = useMemo(() => createPspTheme(pspTheme), [pspTheme]);

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
            <MuiThemeProvider theme={theme}>
                {children}
            </MuiThemeProvider>
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