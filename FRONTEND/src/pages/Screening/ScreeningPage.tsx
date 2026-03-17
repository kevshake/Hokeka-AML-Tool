import { useState } from "react";
import { Box, Paper, Typography, TextField, Button, Alert } from "@mui/material";
import { apiClient } from "../../lib/apiClient";

export default function ScreeningPage() {
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  const handleScreening = async () => {
    if (!name.trim()) {
      setError("Please enter a name to screen");
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const response = await apiClient.post("sanctions/screen", { name });
      setResult(response);
    } catch (err: any) {
      setError(err.message || "Screening failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h5" sx={{ color: "text.primary", mb: 3, fontWeight: 600 }}>
        Screening
      </Typography>

      <Paper sx={{ p: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
          Sanctions Screening
        </Typography>

        <Box sx={{ display: "flex", gap: 2, mb: 3 }}>
          <TextField
            fullWidth
            label="Name to Screen"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyPress={(e) => e.key === "Enter" && handleScreening()}
            sx={{
              "& .MuiOutlinedInput-root": {
                color: "text.primary",
                "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                "&:hover fieldset": { bordercolor: "text.disabled" },
              },
              "& .MuiInputLabel-root": { color: "text.secondary" },
            }}
          />
          <Button
            variant="contained"
            onClick={handleScreening}
            disabled={loading}
            sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" }, minWidth: 150 }}
          >
            {loading ? "Screening..." : "Screen"}
          </Button>
        </Box>

        {error && (
          <Alert severity="error" sx={{ mb: 2, backgroundColor: "background.paper", color: "#e74c3c" }}>
            {error}
          </Alert>
        )}

        {result && (
          <Paper sx={{ p: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="h6" sx={{ color: "text.primary", mb: 1 }}>
              Screening Results
            </Typography>
            <pre style={{ color: "text.primary", margin: 0, whiteSpace: "pre-wrap" }}>
              {JSON.stringify(result, null, 2)}
            </pre>
          </Paper>
        )}
      </Paper>
    </Box>
  );
}

