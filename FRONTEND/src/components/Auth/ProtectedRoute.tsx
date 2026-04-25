import { Navigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { Box, CircularProgress, Typography } from "@mui/material";

interface ProtectedRouteProps {
    children: React.ReactNode;
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
    const { isAuthenticated, isLoading } = useAuth();

    if (isLoading) {
        return (
            <Box
                sx={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    minHeight: "100vh",
                    gap: 2,
                }}
            >
                <CircularProgress size={60} sx={{ color: "#8B4049" }} />
                <Typography variant="body1" sx={{ color: "text.secondary" }}>
                    Loading...
                </Typography>
            </Box>
        );
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    return <>{children}</>;
}
