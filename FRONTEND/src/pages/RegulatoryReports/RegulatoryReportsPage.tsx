import { useState } from "react";
import { Box, Paper, Typography, Button, Tabs, Tab, Grid, Card, CardContent, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from "@mui/material";
import { useRegulatoryReport } from "../../features/api/queries";
import { Download as DownloadIcon } from "@mui/icons-material";

export default function RegulatoryReportsPage() {
  const [reportType, setReportType] = useState<"ctr" | "lctr" | "iftr">("ctr");
  const { data: report, isLoading } = useRegulatoryReport(reportType);

  const handleGenerate = () => {
    // Report is already fetched via the query hook
    // This could trigger a refetch if needed
  };

  const handleExport = () => {
    // TODO: Implement export functionality
    alert(`Exporting ${reportType.toUpperCase()} report...`);
  };

  return (
    <Box>
      <Typography variant="h5" sx={{ color: "text.primary", mb: 3, fontWeight: 600 }}>
        Regulatory Reports
      </Typography>

      <Tabs
        value={reportType}
        onChange={(_, newValue) => setReportType(newValue)}
        sx={{
          mb: 3,
          "& .MuiTab-root": {
            color: "text.secondary",
            "&.Mui-selected": { color: "#a93226" },
          },
          "& .MuiTabs-indicator": { backgroundColor: "#a93226" },
        }}
      >
        <Tab label="CTR (Currency Transaction Report)" value="ctr" />
        <Tab label="LCTR (Large Cash Transaction Report)" value="lctr" />
        <Tab label="IFTR (International Funds Transfer Report)" value="iftr" />
      </Tabs>

      <Paper sx={{ p: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
          <Typography variant="h6" sx={{ color: "text.primary" }}>
            {reportType.toUpperCase()} Report
          </Typography>
          <Box sx={{ display: "flex", gap: 2 }}>
            <Button
              variant="contained"
              onClick={handleGenerate}
              sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}
            >
              Generate Report
            </Button>
            <Button
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={handleExport}
              sx={{ borderColor: "#a93226", color: "#a93226", "&:hover": { borderColor: "#922b21" } }}
            >
              Export
            </Button>
          </Box>
        </Box>

        {isLoading ? (
          <Typography sx={{ color: "text.disabled" }}>Generating report...</Typography>
        ) : report ? (
          <>
            <Grid container spacing={2} sx={{ mb: 3 }}>
              <Grid item xs={12} md={4}>
                <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                  <CardContent>
                    <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                      Total Transactions
                    </Typography>
                    <Typography variant="h5" sx={{ color: "text.primary" }}>
                      {report.totalTransactions || report.transactionCount || 0}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={4}>
                <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                  <CardContent>
                    <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                      Total Amount
                    </Typography>
                    <Typography variant="h5" sx={{ color: "text.primary" }}>
                      ${report.totalAmount ? (report.totalAmount / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "0.00"}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={4}>
                <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                  <CardContent>
                    <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                      Report Period
                    </Typography>
                    <Typography variant="h6" sx={{ color: "text.primary" }}>
                      {report.startDate && report.endDate
                        ? `${new Date(report.startDate).toLocaleDateString()} - ${new Date(report.endDate).toLocaleDateString()}`
                        : "Last 30 days"}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            {report.transactions && Array.isArray(report.transactions) && report.transactions.length > 0 && (
              <TableContainer sx={{ backgroundColor: "background.paper", borderRadius: 1 }}>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ color: "text.secondary" }}>Transaction ID</TableCell>
                      <TableCell sx={{ color: "text.secondary" }}>Merchant</TableCell>
                      <TableCell sx={{ color: "text.secondary" }}>Amount</TableCell>
                      <TableCell sx={{ color: "text.secondary" }}>Date</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {report.transactions.slice(0, 20).map((txn: any, idx: number) => (
                      <TableRow key={idx} hover>
                        <TableCell sx={{ color: "text.primary" }}>#{txn.id || txn.transactionId || idx}</TableCell>
                        <TableCell sx={{ color: "text.primary" }}>{txn.merchantId || "N/A"}</TableCell>
                        <TableCell sx={{ color: "text.primary" }}>
                          ${txn.amountCents ? (txn.amountCents / 100).toFixed(2) : "0.00"}
                        </TableCell>
                        <TableCell sx={{ color: "text.secondary" }}>
                          {txn.txnTs || txn.timestamp ? new Date(txn.txnTs || txn.timestamp).toLocaleDateString() : "N/A"}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}

            {report.summary && (
              <Box sx={{ mt: 3, p: 2, backgroundColor: "background.paper", borderRadius: 1 }}>
                <Typography variant="h6" sx={{ color: "text.primary", mb: 1 }}>
                  Report Summary
                </Typography>
                <Typography variant="body2" sx={{ color: "text.primary", whiteSpace: "pre-wrap" }}>
                  {typeof report.summary === "string" ? report.summary : JSON.stringify(report.summary, null, 2)}
                </Typography>
              </Box>
            )}
          </>
        ) : (
          <Typography sx={{ color: "text.disabled" }}>
            Click "Generate Report" to create a {reportType.toUpperCase()} report
          </Typography>
        )}
      </Paper>
    </Box>
  );
}

