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
    Snackbar,
    Dialog,          
    DialogTitle,     
    DialogContent,   
    DialogActions,
} from "@mui/material";
import { Visibility, VisibilityOff, Login as LoginIcon } from "@mui/icons-material";
import { Link as RouterLink } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function LoginPage() {
    const { login } = useAuth();
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState("");
    const [isLoading, setIsLoading] = useState(false);
    const [infoSnackbar, setInfoSnackbar] = useState("");
    const [forgotOpen, setForgotOpen] = useState(false);
const [resetEmail, setResetEmail] = useState("");
const [resetError, setResetError] = useState("");
const [resetLoading, setResetLoading] = useState(false);

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
    const handleForgotClose = () => {
    setForgotOpen(false);
    setResetEmail("");
    setResetError("");
};

const handleResetSubmit = async () => {
    setResetLoading(true);
    try {
        // await yourApi.sendResetEmail(resetEmail);
        handleForgotClose();
        setInfoSnackbar("Reset instructions sent! Check your email.");
    } catch (err: any) {
        setResetError(err.message || "Failed to send reset email.");
    } finally {
        setResetLoading(false);
    }
};

    return (
        <>
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
                    <Typography variant="h6" sx={{ fontWeight: 700, color: "#3D2C2E", mb: 0.5 }}>
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
                            onClick={() => setForgotOpen(true)}
                            sx={{ color: "#8B4049", textTransform: "none" }}
                        >
                            Forgot Password?
                        </Button>
                    </Box>
                </form>

                {/* Operator accounts are provisioned by an administrator —
                    self-signup is intentionally not exposed for an AML platform.
                    PSP onboarding uses /clients/register and /psps/register. */}

                {/* Footer */}
                <Box sx={{ mt: 3, textAlign: "center" }}>
                    <Typography variant="caption" sx={{ color: "text.secondary" }}>
                        © 2026 Hokeka. All rights reserved.
                    </Typography>
                </Box>
            </Paper>
        </Box>
<Snackbar
            open={!!infoSnackbar}
            autoHideDuration={6000}
            onClose={() => setInfoSnackbar("")}
            anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        >
            <Alert severity="info" onClose={() => setInfoSnackbar("")} sx={{ width: "100%" }}>
                {infoSnackbar}
            </Alert>
        </Snackbar>

        <Dialog open={forgotOpen} onClose={handleForgotClose} maxWidth="xs" fullWidth>
            <DialogTitle sx={{ fontWeight: 700, color: "#3D2C2E" }}>
                Reset Your Password
            </DialogTitle>
            <DialogContent>
                <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
                    Enter your email and we'll send you reset instructions.
                </Typography>
                {resetError && <Alert severity="error" sx={{ mb: 2 }}>{resetError}</Alert>}
                <TextField
                    fullWidth
                    label="Email Address"
                    type="email"
                    value={resetEmail}
                    onChange={(e) => setResetEmail(e.target.value)}
                    autoFocus
                    onKeyDown={(e) => e.key === "Enter" && handleResetSubmit()}
                />
            </DialogContent>
            <DialogActions sx={{ px: 3, pb: 3 }}>
                <Button onClick={handleForgotClose} sx={{ color: "#8B4049", textTransform: "none" }}>
                    Cancel
                </Button>
                <Button
                    onClick={handleResetSubmit}
                    variant="contained"
                    disabled={resetLoading}
                    startIcon={resetLoading ? <CircularProgress size={18} /> : null}
                    sx={{ backgroundColor: "#8B4049", "&:hover": { backgroundColor: "#6B3037" }, textTransform: "none", fontWeight: 600 }}
                >
                    {resetLoading ? "Sending..." : "Send Reset Instructions"}
                </Button>
            </DialogActions>
        </Dialog>
    </>
    );
}
