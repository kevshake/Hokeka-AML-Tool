import { Box, Paper, Typography, TextField, Button, Grid } from "@mui/material";

export default function LimitsAmlPage() {
  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 3, fontWeight: 600 }}>
        Limits & AML
      </Typography>

      <Paper sx={{ p: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Typography variant="h6" sx={{ color: "text.primary", mb: 3 }}>
          AML Limits Configuration
        </Typography>

        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Transaction Limit"
              type="number"
              sx={{
                "& .MuiOutlinedInput-root": {
                  color: "text.primary",
                  "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                  "&:hover fieldset": { bordercolor: "text.disabled" },
                },
                "& .MuiInputLabel-root": { color: "text.secondary" },
              }}
            />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Daily Limit"
              type="number"
              sx={{
                "& .MuiOutlinedInput-root": {
                  color: "text.primary",
                  "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                  "&:hover fieldset": { bordercolor: "text.disabled" },
                },
                "& .MuiInputLabel-root": { color: "text.secondary" },
              }}
            />
          </Grid>
          <Grid item xs={12}>
            <Button variant="contained" sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}>
              Save Limits
            </Button>
          </Grid>
        </Grid>
      </Paper>
    </Box>
  );
}

