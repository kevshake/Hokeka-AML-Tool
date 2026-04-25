import { useState } from "react";
import { Box, Paper, Typography, TextField, Button, Alert, Chip, Divider, Grid } from "@mui/material";
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
      <Typography variant="h6" sx={{ color: "text.primary", mb: 3, fontWeight: 600 }}>
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
            <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 2 }}>
              <Typography variant="h6" sx={{ color: "text.primary" }}>
                Screening Results
              </Typography>
              {result.matchFound !== undefined && (
                <Chip
                  label={result.matchFound ? "MATCH FOUND" : "NO MATCH"}
                  size="small"
                  sx={{
                    backgroundColor: result.matchFound ? "#e74c3c20" : "#2ecc7120",
                    color: result.matchFound ? "#e74c3c" : "#2ecc71",
                    border: `1px solid ${result.matchFound ? "#e74c3c" : "#2ecc71"}`,
                    fontWeight: 700,
                  }}
                />
              )}
            </Box>
            <Divider sx={{ mb: 2 }} />
            {result.matches && Array.isArray(result.matches) && result.matches.length > 0 ? (
              <Grid container spacing={1}>
                {result.matches.map((match: any) => (
                  <Grid item xs={12} key={match.id ?? match.name ?? match.fullName}>
                    <Paper variant="outlined" sx={{ p: 1.5, borderColor: "#e74c3c40" }}>
                      <Typography variant="body2" sx={{ color: "text.primary", fontWeight: 600 }}>
                        {match.name || match.fullName || "Unknown"}
                      </Typography>
                      {match.listName && (
                        <Typography variant="caption" sx={{ color: "text.secondary" }}>
                          List: {match.listName}
                        </Typography>
                      )}
                      {match.score !== undefined && (
                        <Typography variant="caption" sx={{ color: "text.secondary", ml: 1 }}>
                          Score: {match.score}
                        </Typography>
                      )}
                    </Paper>
                  </Grid>
                ))}
              </Grid>
            ) : (
              <Box>
                {Object.entries(result).map(([key, val]) => (
                  <Box key={key} sx={{ display: "flex", gap: 1, mb: 0.5 }}>
                    <Typography variant="caption" sx={{ color: "text.secondary", minWidth: 120, textTransform: "capitalize" }}>
                      {key.replace(/([A-Z])/g, " $1")}:
                    </Typography>
                    <Typography variant="caption" sx={{ color: "text.primary" }}>
                      {typeof val === "object" ? JSON.stringify(val) : String(val)}
                    </Typography>
                  </Box>
                ))}
              </Box>
            )}
          </Paper>
        )}
      </Paper>
    </Box>
  );
}

