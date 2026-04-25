import { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { useNavigate } from "react-router-dom";

interface User {
    id: number;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    role: {
        id: number;
        name: string;
        permissions: string[];
    };
    psp: {
        id: number;
        name: string;
        code: string;
        theme?: {
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
        };
    } | null;
    pspId: number; // Always present - 0 for Super Admin, >0 for PSP users
    enabled: boolean;
    createdAt: string;
}

interface AuthContextType {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    login: (username: string, password: string) => Promise<void>;
    logout: () => void;
    refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const navigate = useNavigate();

    // Check for existing session on mount
    useEffect(() => {
        checkSession();
    }, []);

    const checkSession = async () => {
        try {
            const response = await fetch("/api/v1/auth/me", {
                method: "GET",
                credentials: "include", // Include cookies for session-based auth
                headers: {
                    "Content-Type": "application/json",
                },
            });

            if (response.ok) {
                const contentType = response.headers.get("content-type");
                if (contentType && contentType.includes("application/json")) {
                    const userData = await response.json();
                    // Ensure pspId is always present
                    const pspId = userData.pspId ?? userData.psp?.id ?? 0;
                    const userWithPspId = {
                        ...userData,
                        pspId: pspId,
                    };
                    // Remove pspId from user object before storing (don't expose in localStorage)
                    const { pspId: _, ...userWithoutPspId } = userWithPspId;
                    setUser(userWithPspId);
                    
                    // Store user WITHOUT pspId in localStorage (security)
                    localStorage.setItem("user", JSON.stringify(userWithoutPspId));
                    
                    // Store PSP ID separately in sessionStorage via apiClient (not exposed)
                    if (typeof window !== "undefined" && (window as any).apiClient) {
                        (window as any).apiClient.setPspId(pspId);
                    }
                    
                    // Store session ID if available
                    if (userWithPspId.id) {
                        localStorage.setItem("authToken", "session"); // Placeholder for session-based auth
                    }
                } else {
                    // Not JSON response
                    setUser(null);
                    localStorage.removeItem("user");
                    if (typeof window !== "undefined" && (window as any).apiClient) {
                        (window as any).apiClient.clearPspId();
                    }
                }
            } else {
                // Not authenticated
                setUser(null);
                localStorage.removeItem("user");
                if (typeof window !== "undefined" && (window as any).apiClient) {
                    (window as any).apiClient.clearPspId();
                }
            }
        } catch (error) {
            // Don't log error details that might expose PSP ID
            setUser(null);
        } finally {
            setIsLoading(false);
        }
    };

    const login = async (username: string, password: string) => {
        try {
            const response = await fetch("/api/v1/auth/login", {
                method: "POST",
                credentials: "include", // Include cookies for session-based auth
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({ username, password }),
            });

            if (!response.ok) {
                // Try to parse error response
                let errorMessage = "Login failed";
                try {
                    const contentType = response.headers.get("content-type");
                    if (contentType && contentType.includes("application/json")) {
                        const error = await response.json();
                        errorMessage = error.message || errorMessage;
                    } else {
                        // Not JSON, use status text
                        errorMessage = response.statusText || errorMessage;
                    }
                } catch (parseError) {
                    // If parsing fails, use status text
                    errorMessage = response.statusText || errorMessage;
                }
                throw new Error(errorMessage);
            }

            // Check if response is JSON
            const contentType = response.headers.get("content-type");
            if (!contentType || !contentType.includes("application/json")) {
                throw new Error("Invalid response format from server");
            }

            const data = await response.json();

            // Validate response structure
            if (!data || !data.user) {
                throw new Error("Invalid response: missing user data");
            }

            // Store session token (session ID)
            if (data.token) {
                localStorage.setItem("authToken", data.token);
            }

            // Set user data - ensure pspId is always present
            const pspId = data.user.pspId ?? data.user.psp?.id ?? 0;
            const userData = {
                ...data.user,
                pspId: pspId, // Always include pspId in memory
            };
            setUser(userData);
            
            // Remove pspId from user object before storing (don't expose in localStorage)
            const { pspId: _, ...userWithoutPspId } = userData;
            localStorage.setItem("user", JSON.stringify(userWithoutPspId));
            
            // Store PSP ID separately via apiClient (not exposed in localStorage)
            if (typeof window !== "undefined" && (window as any).apiClient) {
                (window as any).apiClient.setPspId(pspId);
            }

            // Navigate to dashboard (use redirectUrl from response if available, otherwise default to /dashboard)
            const redirectUrl = data.redirectUrl || "/dashboard";
            navigate(redirectUrl, { replace: true });
        } catch (error: any) {
            console.error("Login error:", error);
            // Re-throw with user-friendly message
            throw new Error(error.message || "Invalid username or password");
        }
    };

    const logout = async () => {
        try {
            // Call logout endpoint to invalidate session
            await fetch("/api/v1/auth/logout", {
                method: "POST",
                credentials: "include",
                headers: {
                    "Content-Type": "application/json",
                },
            });
        } catch (error) {
            // Don't log error details
        } finally {
            // Clear token and user data
            localStorage.removeItem("authToken");
            localStorage.removeItem("user");
            
            // Clear PSP ID from apiClient
            if (typeof window !== "undefined" && (window as any).apiClient) {
                (window as any).apiClient.clearPspId();
            }

            // Clear user
            setUser(null);

            // Navigate to login
            navigate("/login");
        }
    };

    const refreshUser = async () => {
        await checkSession();
    };

    return (
        <AuthContext.Provider
            value={{
                user,
                isAuthenticated: !!user,
                isLoading,
                login,
                logout,
                refreshUser,
            }}
        >
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error("useAuth must be used within an AuthProvider");
    }
    return context;
}
