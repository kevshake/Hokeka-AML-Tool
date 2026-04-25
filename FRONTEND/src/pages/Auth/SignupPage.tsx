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
    Link,
} from "@mui/material";
import { Visibility, VisibilityOff, PersonAdd } from "@mui/icons-material";
import { Link as RouterLink } from "react-router-dom";

export default function SignupPage() {
    const [formData, setFormData] = useState({
        username: "",
        email: "",
        firstName: "",
        lastName: "",
        password: "",
        confirmPassword: "",
    });
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [isLoading, setIsLoading] = useState(false);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");
        setSuccess("");

        // Validation
        if (formData.password !== formData.confirmPassword) {
            setError("Passwords do not match");
            return;
        }

        if (formData.password.length < 8) {
            setError("Password must be at least 8 characters long");
            return;
        }

        setIsLoading(true);

        try {
            const response = await fetch("/api/v1/auth/register", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    username: formData.username,
                    email: formData.email,
                    firstName: formData.firstName,
                    lastName: formData.lastName,
                    password: formData.password,
                }),
            });

            if (!response.ok) {
                const data = await response.json();
                throw new Error(data.message || "Registration failed");
            }

            setSuccess("Account created successfully! Please log in.");
            setFormData({
                username: "",
                email: "",
                firstName: "",
                lastName: "",
                password: "",
                confirmPassword: "",
            });
        } catch (err: any) {
            setError(err.message || "Failed to create account");
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
                        Create Account
                    </Typography>
                    <Typography variant="body2" sx={{ color: "text.secondary" }}>
                        Join AML Fraud Detector
                    </Typography>
                </Box>

                {/* Error Alert */}
                {error && (
                    <Alert severity="error" sx={{ mb: 3 }}>
                        {error}
                    </Alert>
                )}

                {/* Success Alert */}
                {success && (
                    <Alert severity="success" sx={{ mb: 3 }}>
                        {success}
                    </Alert>
                )}

                {/* Signup Form */}
                <form onSubmit={handleSubmit}>
                    <TextField
                        fullWidth
                        label="Username"
                        name="username"
                        value={formData.username}
                        onChange={handleChange}
                        required
                        autoFocus
                        sx={{ mb: 2 }}
                    />

                    <Box sx={{ display: "flex", gap: 2 }}>
                        <TextField
                            fullWidth
                            label="First Name"
                            name="firstName"
                            value={formData.firstName}
                            onChange={handleChange}
                            required
                            sx={{ mb: 2 }}
                        />
                        <TextField
                            fullWidth
                            label="Last Name"
                            name="lastName"
                            value={formData.lastName}
                            onChange={handleChange}
                            required
                            sx={{ mb: 2 }}
                        />
                    </Box>

                    <TextField
                        fullWidth
                        label="Email"
                        name="email"
                        type="email"
                        value={formData.email}
                        onChange={handleChange}
                        required
                        sx={{ mb: 2 }}
                    />

                    <TextField
                        fullWidth
                        label="Password"
                        name="password"
                        type={showPassword ? "text" : "password"}
                        value={formData.password}
                        onChange={handleChange}
                        required
                        sx={{ mb: 2 }}
                        InputProps={{
                            endAdornment: (
                                <InputAdornment position="end">
                                    <IconButton
                                        onClick={() => setShowPassword(!showPassword)}
                                        edge="end"
                                    >
                                        {showPassword ? <VisibilityOff /> : <Visibility />}
                                    </IconButton>
                                </InputAdornment>
                            ),
                        }}
                    />

                    <TextField
                        fullWidth
                        label="Confirm Password"
                        name="confirmPassword"
                        type={showConfirmPassword ? "text" : "password"}
                        value={formData.confirmPassword}
                        onChange={handleChange}
                        required
                        sx={{ mb: 3 }}
                        InputProps={{
                            endAdornment: (
                                <InputAdornment position="end">
                                    <IconButton
                                        onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                        edge="end"
                                    >
                                        {showConfirmPassword ? <VisibilityOff /> : <Visibility />}
                                    </IconButton>
                                </InputAdornment>
                            ),
                        }}
                    />

                    <Button
                        type="submit"
                        fullWidth
                        variant="contained"
                        size="large"
                        disabled={isLoading}
                        startIcon={isLoading ? <CircularProgress size={20} /> : <PersonAdd />}
                        sx={{
                            backgroundColor: "#8B4049",
                            "&:hover": { backgroundColor: "#6B3037" },
                            py: 1.5,
                            fontSize: "1rem",
                            fontWeight: 600,
                        }}
                    >
                        {isLoading ? "Creating Account..." : "Create Account"}
                    </Button>
                </form>

                {/* Login Link */}
                <Box sx={{ mt: 3, textAlign: "center" }}>
                    <Typography variant="body2" sx={{ color: "text.secondary" }}>
                        Already have an account?{" "}
                        <Link component={RouterLink} to="/login" sx={{ color: "#8B4049", fontWeight: 600 }}>
                            Sign In
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
