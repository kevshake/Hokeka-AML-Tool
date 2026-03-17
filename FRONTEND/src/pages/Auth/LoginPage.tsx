import { useState } from "react";
import {
    Box,
    Paper,
    TextField,
    Button,
    Typography,
    Alert,
    InputAdornment,
    IconButton,
    CircularProgress,
    Tooltip,
    Link,
} from "@mui/material";
import { Visibility, VisibilityOff, Login as LoginIcon } from "@mui/icons-material";
import { Link as RouterLink } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";

export default function LoginPage() {
    const { login } = useAuth();
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState("");
    const [isLoading, setIsLoading] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");
        setIsLoading(true);

        try {
            await login(username, password);
        } catch (err: any) {
            setError(err.message || "Invalid username or password");
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Box
            sx={{
                minHeight: "100vh",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                background: "linear-gradient(135deg, #FAF8F5 0%, #F5F0E8 100%)",
            }}
        >
            <Paper
                elevation={3}
                sx={{
                    p: 4,
                    maxWidth: 450,
                    width: "100%",
                    mx: 2,
                    borderRadius: 3,
                    boxShadow: "0 8px 32px rgba(139, 64, 73, 0.15)",
                }}
            >
                {/* Logo */}
                <Box sx={{ textAlign: "center", mb: 4 }}>
                    <Box
                        sx={{
                            width: 80,
                            height: 80,
                            borderRadius: "16px",
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                            overflow: "hidden",
                            boxShadow: "0 4px 12px rgba(139, 64, 73, 0.25)",
                            mb: 2,
                        }}
                    >
                        <img
                            src="/hokeka-logo.jpg"
                            alt="Hokeka Logo"
                            style={{
                                width: "100%",
                                height: "100%",
                                objectFit: "cover"
                            }}
                        />
                    </Box>
                    <Typography variant="h4" sx={{ fontWeight: 700, color: "#3D2C2E", mb: 0.5 }}>
                        AML Fraud Detector
                    </Typography>
                    <Typography variant="body2" sx={{ color: "text.secondary" }}>
                        Powered by Hokeka
                    </Typography>
                </Box>

                {/* Error Alert */}
                {error && (
                    <Alert severity="error" sx={{ mb: 3 }}>
                        {error}
                    </Alert>
                )}

                {/* Login Form */}
                <form onSubmit={handleSubmit}>
                    <Tooltip title="Enter your username or email address to sign in" arrow placement="top">
                        <TextField
                            fullWidth
                            label="Username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                            autoFocus
                            sx={{ mb: 2 }}
                        />
                    </Tooltip>

                    <Tooltip title="Enter your password. Click the eye icon to show/hide password" arrow placement="top">
                        <TextField
                            fullWidth
                            label="Password"
                            type={showPassword ? "text" : "password"}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                            sx={{ mb: 3 }}
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment position="end">
                                        <Tooltip title={showPassword ? "Hide password" : "Show password"} arrow>
                                            <IconButton
                                                onClick={() => setShowPassword(!showPassword)}
                                                edge="end"
                                            >
                                                {showPassword ? <VisibilityOff /> : <Visibility />}
                                            </IconButton>
                                        </Tooltip>
                                    </InputAdornment>
                                ),
                            }}
                        />
                    </Tooltip>

                    <Tooltip title="Click to sign in to your account" arrow placement="top">
                        <span>
                            <Button
                                type="submit"
                                fullWidth
                                variant="contained"
                                size="large"
                                disabled={isLoading}
                                startIcon={isLoading ? <CircularProgress size={20} /> : <LoginIcon />}
                                sx={{
                                    backgroundColor: "#8B4049",
                                    "&:hover": { backgroundColor: "#6B3037" },
                                    py: 1.5,
                                    fontSize: "1rem",
                                    fontWeight: 600,
                                }}
                            >
                                {isLoading ? "Signing in..." : "Sign In"}
                            </Button>
                        </span>
                    </Tooltip>

                    {/* Forgot Password Link */}
                    <Box sx={{ mt: 2, textAlign: "center" }}>
                        <Button
                            variant="text"
                            size="small"
                            onClick={() => alert("Please contact your administrator to reset your password.")}
                            sx={{ color: "#8B4049", textTransform: "none" }}
                        >
                            Forgot Password?
                        </Button>
                    </Box>
                </form>

                {/* Sign Up Link */}
                <Box sx={{ mt: 2, textAlign: "center" }}>
                    <Typography variant="body2" sx={{ color: "text.secondary" }}>
                        Don't have an account?{" "}
                        <Link component={RouterLink} to="/signup" sx={{ color: "#8B4049", fontWeight: 600 }}>
                            Sign Up
                        </Link>
                    </Typography>
                </Box>

                {/* Footer */}
                <Box sx={{ mt: 3, textAlign: "center" }}>
                    <Typography variant="caption" sx={{ color: "text.secondary" }}>
                        © 2026 Hokeka. All rights reserved.
                    </Typography>
                </Box>
            </Paper>
        </Box>
    );
}
