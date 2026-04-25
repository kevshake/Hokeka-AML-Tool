import { useState, useEffect } from "react";
import {
  Box,
  Paper,
  Typography,
  Tabs,
  Tab,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Grid,
  Card,
  CardContent,
  Switch,
  FormControlLabel,
  Snackbar,
  Alert,
} from "@mui/material";
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  PlayArrow as EnableIcon,
  Stop as DisableIcon,
  Assessment as EffectivenessIcon,
} from "@mui/icons-material";
import {
  useAmlRules,
  useVelocityRules,
  useRiskThresholds,
  useRuleEffectiveness,
  useAllPsps,
  useCurrentUser,
} from "../../features/api/queries";
import {
  useCreateAmlRule,
  useUpdateAmlRule,
  useDeleteAmlRule,
  useEnableAmlRule,
  useDisableAmlRule,
  useCreateVelocityRule,
  useUpdateVelocityRule,
  useDeleteVelocityRule,
  useCreateRiskThreshold,
} from "../../features/api/mutations";
import type { AmlRule, VelocityRule, RiskThreshold } from "../../types/rules";

export default function RulesGenerationPage() {
  const [tab, setTab] = useState(0);
  const [openDialog, setOpenDialog] = useState(false);
  const [editingRule, setEditingRule] = useState<any>(null);
  const [effectivenessDialog, setEffectivenessDialog] = useState<number | null>(null);
  const [filterBy, setFilterBy] = useState<"all" | "super-admin" | "my-psp">("all");
  const [errorSnackbar, setErrorSnackbar] = useState("");

  const { data: currentUser } = useCurrentUser();
  const { data: psps } = useAllPsps();
  const { data: amlRules, isLoading: amlRulesLoading } = useAmlRules();
  const { data: velocityRules, isLoading: velocityRulesLoading } = useVelocityRules();
  const { data: riskThresholds, isLoading: riskThresholdsLoading } = useRiskThresholds();
  const { data: effectiveness } = useRuleEffectiveness(effectivenessDialog || 0);

  // Create PSP color map
  const pspColorMap = new Map<number, string>();
  if (psps && Array.isArray(psps)) {
    psps.forEach((psp: any) => {
      if (psp.pspId && psp.primaryColor) {
        pspColorMap.set(psp.pspId, psp.primaryColor);
      }
    });
  }

  // Helper function to check if current user is super admin
  const isCurrentUserSuperAdmin = () => {
    if (!currentUser) return false;
    return (
      !currentUser.pspId ||
      currentUser.role?.name === "ADMIN" ||
      currentUser.role?.name === "SUPER_ADMIN" ||
      currentUser.role?.name === "MLRO" ||
      currentUser.role?.name === "PLATFORM_ADMIN" ||
      currentUser.role?.name === "APP_CONTROLLER"
    );
  };

  // Helper function to check if user can modify a rule
  const canModifyRule = (rule: any) => {
    if (!rule) return false;
    // Super admin can modify all rules
    if (isCurrentUserSuperAdmin()) return true;
    // PSP users can only modify their own PSP's rules (not super admin rules)
    if (rule.isSuperAdmin || !rule.pspId) return false;
    // PSP users can modify their own PSP's rules
    return rule.pspId === currentUser?.pspId;
  };

  // Helper function to get rule color
  const getRuleColor = (rule: any) => {
    if (rule.isSuperAdmin || !rule.pspId) {
      return "#3498db"; // Blue for super admin
    }
    if (rule.pspId && pspColorMap.has(rule.pspId)) {
      return pspColorMap.get(rule.pspId) || "#a93226"; // PSP theme color
    }
    return "#a93226"; // Default theme color
  };

  // Helper function to get rule creator info
  const getRuleCreator = (rule: any) => {
    if (rule.isSuperAdmin || !rule.pspId) {
      return "Super Admin";
    }
    if (rule.createdByUser?.username) {
      return rule.createdByUser.username;
    }
    if (rule.pspId && psps) {
      const psp = (psps as any[]).find((p: any) => p.pspId === rule.pspId);
      return psp?.tradingName || psp?.legalName || `PSP ${rule.pspId}`;
    }
    return "Unknown";
  };

  // Filter rules based on selected filter
  const filterRules = (rules: any[]) => {
    if (!rules) return [];
    if (filterBy === "all") return rules;
    if (filterBy === "super-admin") {
      return rules.filter((r) => r.isSuperAdmin || !r.pspId);
    }
    if (filterBy === "my-psp" && currentUser?.pspId) {
      return rules.filter((r) => r.pspId === currentUser.pspId);
    }
    return rules;
  };

  const createAmlRule = useCreateAmlRule();
  const updateAmlRule = useUpdateAmlRule();
  const deleteAmlRule = useDeleteAmlRule();
  const enableAmlRule = useEnableAmlRule();
  const disableAmlRule = useDisableAmlRule();
  const createVelocityRule = useCreateVelocityRule();
  const updateVelocityRule = useUpdateVelocityRule();
  const deleteVelocityRule = useDeleteVelocityRule();
  const createRiskThreshold = useCreateRiskThreshold();

  const handleCreateRule = () => {
    setEditingRule(null);
    setOpenDialog(true);
  };

  const handleEditRule = (rule: any) => {
    setEditingRule(rule);
    setOpenDialog(true);
  };

  const handleDeleteRule = async (id: number, type: "aml" | "velocity" | "threshold") => {
    if (window.confirm("Are you sure you want to delete this rule?")) {
      if (type === "aml") {
        await deleteAmlRule.mutateAsync(id);
      } else if (type === "velocity") {
        await deleteVelocityRule.mutateAsync(id);
      }
    }
  };

  const handleSaveRule = async (ruleData: any) => {
    try {
      if (tab === 0) {
        // AML Rule
        if (editingRule?.id) {
          await updateAmlRule.mutateAsync({ id: editingRule.id, rule: ruleData });
        } else {
          await createAmlRule.mutateAsync(ruleData);
        }
      } else if (tab === 1) {
        // Velocity Rule
        if (editingRule?.id) {
          await updateVelocityRule.mutateAsync({ id: editingRule.id, rule: ruleData });
        } else {
          await createVelocityRule.mutateAsync(ruleData);
        }
      } else if (tab === 2) {
        // Risk Threshold
        await createRiskThreshold.mutateAsync(ruleData);
      }
      setOpenDialog(false);
      setEditingRule(null);
    } catch (error: any) {
      console.error("Error saving rule:", error);
      setErrorSnackbar(error?.message || "Failed to save rule. Please check if the API endpoint is available.");
    }
  };

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600 }}>
          Rules Generation
        </Typography>
        <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
          <FormControl size="small" sx={{ minWidth: 150 }}>
            <InputLabel sx={{ color: "text.secondary" }}>Filter</InputLabel>
            <Select
              value={filterBy}
              onChange={(e) => setFilterBy(e.target.value as any)}
              label="Filter"
              sx={{ color: "text.primary" }}
            >
              <MenuItem value="all">All Rules</MenuItem>
              <MenuItem value="super-admin">Super Admin</MenuItem>
              <MenuItem value="my-psp">My PSP</MenuItem>
            </Select>
          </FormControl>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={handleCreateRule}
            sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}
          >
            Create Rule
          </Button>
        </Box>
      </Box>

      <Tabs
        value={tab}
        onChange={(_, newValue) => setTab(newValue)}
        sx={{
          mb: 3,
          "& .MuiTab-root": {
            color: "text.secondary",
            "&.Mui-selected": { color: "#a93226" },
          },
          "& .MuiTabs-indicator": { backgroundColor: "#a93226" },
        }}
      >
        <Tab label="AML Rules" />
        <Tab label="Velocity Rules" />
        <Tab label="Risk Thresholds" />
      </Tabs>

      {/* AML Rules Tab */}
      {tab === 0 && (
        <>
          <Paper sx={{ p: 2, mb: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="body2" sx={{ color: "text.primary", mb: 1 }}>
              <strong>AML Rules:</strong> Create dynamic rules using SpEL expressions, Java Bean evaluators, or Drools DRL.
              Rules are evaluated in real-time during transaction processing. SpEL expressions can reference transaction
              variables like <code>#tx.amount</code>, <code>#tx.currency</code>, and <code>#history</code> for pattern detection.
            </Typography>
            <Box sx={{ display: "flex", gap: 2, mt: 1 }}>
              <Chip
                label="Super Admin Rules"
                size="small"
                sx={{
                  backgroundColor: "#3498db20",
                  color: "#3498db",
                  border: "1px solid #3498db",
                }}
              />
              <Typography variant="caption" sx={{ color: "text.secondary", alignSelf: "center" }}>
                Rules created by super admin are shown in blue. All PSPs can view and use these rules.
              </Typography>
            </Box>
          </Paper>
          <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ color: "text.secondary" }}>Rule Name</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Creator</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Type</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Action</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Priority</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Status</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {amlRulesLoading ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 4 }}>
                      Loading rules...
                    </TableCell>
                  </TableRow>
                ) : filterRules(amlRules || []).length > 0 ? (
                  filterRules(amlRules || []).map((rule: AmlRule) => {
                    const ruleColor = getRuleColor(rule);
                    const creator = getRuleCreator(rule);
                    return (
                      <TableRow key={rule.id} hover>
                        <TableCell sx={{ color: ruleColor, fontWeight: 500 }}>{rule.ruleName}</TableCell>
                        <TableCell>
                          <Chip
                            label={creator}
                            size="small"
                            sx={{
                              backgroundColor: rule.isSuperAdmin ? "#3498db20" : `${ruleColor}20`,
                              color: ruleColor,
                              border: `1px solid ${ruleColor}`,
                            }}
                          />
                        </TableCell>
                      <TableCell>
                        <Chip
                          label={rule.ruleType}
                          size="small"
                          sx={{
                            backgroundColor: "#3498db20",
                            color: "#3498db",
                            border: "1px solid #3498db",
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={rule.action}
                          size="small"
                          sx={{
                            backgroundColor:
                              rule.action === "BLOCK"
                                ? "#e74c3c20"
                                : rule.action === "HOLD"
                                ? "#f39c1220"
                                : "#2ecc7120",
                            color:
                              rule.action === "BLOCK"
                                ? "#e74c3c"
                                : rule.action === "HOLD"
                                ? "#f39c12"
                                : "#2ecc71",
                            border: `1px solid ${
                              rule.action === "BLOCK"
                                ? "#e74c3c"
                                : rule.action === "HOLD"
                                ? "#f39c12"
                                : "#2ecc71"
                            }`,
                          }}
                        />
                      </TableCell>
                      <TableCell sx={{ color: "text.primary" }}>{rule.priority}</TableCell>
                      <TableCell>
                        <Chip
                          label={rule.enabled ? "Enabled" : "Disabled"}
                          size="small"
                          sx={{
                            backgroundColor: rule.enabled ? "#2ecc7120" : "#95a5a620",
                            color: rule.enabled ? "#2ecc71" : "#95a5a6",
                            border: `1px solid ${rule.enabled ? "#2ecc71" : "#95a5a6"}`,
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        <IconButton
                          size="small"
                          onClick={() => handleEditRule(rule)}
                          disabled={!canModifyRule(rule)}
                          sx={{ color: canModifyRule(rule) ? "#a93226" : "rgba(255,255,255,0.3)", mr: 1 }}
                          title={!canModifyRule(rule) ? "Cannot modify super admin rules" : "Edit rule"}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                        {rule.enabled ? (
                          <IconButton
                            size="small"
                            onClick={() => rule.id && disableAmlRule.mutate(rule.id)}
                            disabled={!canModifyRule(rule)}
                            sx={{ color: canModifyRule(rule) ? "#f39c12" : "rgba(255,255,255,0.3)", mr: 1 }}
                            title={!canModifyRule(rule) ? "Cannot modify super admin rules" : "Disable rule"}
                          >
                            <DisableIcon fontSize="small" />
                          </IconButton>
                        ) : (
                          <IconButton
                            size="small"
                            onClick={() => rule.id && enableAmlRule.mutate(rule.id)}
                            disabled={!canModifyRule(rule)}
                            sx={{ color: canModifyRule(rule) ? "#2ecc71" : "rgba(255,255,255,0.3)", mr: 1 }}
                            title={!canModifyRule(rule) ? "Cannot modify super admin rules" : "Enable rule"}
                          >
                            <EnableIcon fontSize="small" />
                          </IconButton>
                        )}
                        <IconButton
                          size="small"
                          onClick={() => rule.id && setEffectivenessDialog(rule.id)}
                          sx={{ color: "#3498db", mr: 1 }}
                          title="View effectiveness"
                        >
                          <EffectivenessIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          onClick={() => rule.id && handleDeleteRule(rule.id, "aml")}
                          disabled={!canModifyRule(rule)}
                          sx={{ color: canModifyRule(rule) ? "#e74c3c" : "rgba(255,255,255,0.3)" }}
                          title={!canModifyRule(rule) ? "Cannot delete super admin rules" : "Delete rule"}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                      </TableRow>
                    );
                  })
                ) : (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 4 }}>
                      No AML rules found. Create your first rule!
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
        </>
      )}

      {/* Velocity Rules Tab */}
      {tab === 1 && (
        <>
          <Paper sx={{ p: 2, mb: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="body2" sx={{ color: "text.primary", mb: 1 }}>
              <strong>Velocity Rules:</strong> Monitor transaction patterns within a time window. Set maximum transactions
              and amount limits to detect unusual activity patterns like rapid-fire transactions or volume spikes.
            </Typography>
            <Typography variant="caption" sx={{ color: "text.secondary" }}>
              Rules are color-coded by creator: Super Admin (blue) and PSP rules (PSP theme color). All rules are visible to everyone.
            </Typography>
          </Paper>
          <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ color: "text.secondary" }}>Rule Name</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Creator</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Max Transactions</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Max Amount</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Time Window</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Risk Level</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Status</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {velocityRulesLoading ? (
                  <TableRow>
                    <TableCell colSpan={8} align="center" sx={{ color: "text.disabled", py: 4 }}>
                      Loading velocity rules...
                    </TableCell>
                  </TableRow>
                ) : filterRules(velocityRules || []).length > 0 ? (
                  filterRules(velocityRules || []).map((rule: VelocityRule) => {
                    const ruleColor = getRuleColor(rule);
                    const creator = getRuleCreator(rule);
                    return (
                      <TableRow key={rule.id} hover>
                        <TableCell sx={{ color: ruleColor, fontWeight: 500 }}>{rule.ruleName}</TableCell>
                        <TableCell>
                          <Chip
                            label={creator}
                            size="small"
                            sx={{
                              backgroundColor: rule.isSuperAdmin ? "#3498db20" : `${ruleColor}20`,
                              color: ruleColor,
                              border: `1px solid ${ruleColor}`,
                            }}
                          />
                        </TableCell>
                      <TableCell sx={{ color: "text.primary" }}>{rule.maxTransactions}</TableCell>
                      <TableCell sx={{ color: "text.primary" }}>
                        ${rule.maxAmount.toFixed(2)}
                      </TableCell>
                      <TableCell sx={{ color: "text.primary" }}>
                        {rule.timeWindowMinutes} min
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={rule.riskLevel}
                          size="small"
                          sx={{
                            backgroundColor:
                              rule.riskLevel === "CRITICAL"
                                ? "#e74c3c20"
                                : rule.riskLevel === "HIGH"
                                ? "#e67e2220"
                                : rule.riskLevel === "MEDIUM"
                                ? "#f39c1220"
                                : "#2ecc7120",
                            color:
                              rule.riskLevel === "CRITICAL"
                                ? "#e74c3c"
                                : rule.riskLevel === "HIGH"
                                ? "#e67e22"
                                : rule.riskLevel === "MEDIUM"
                                ? "#f39c12"
                                : "#2ecc71",
                            border: `1px solid ${
                              rule.riskLevel === "CRITICAL"
                                ? "#e74c3c"
                                : rule.riskLevel === "HIGH"
                                ? "#e67e22"
                                : rule.riskLevel === "MEDIUM"
                                ? "#f39c12"
                                : "#2ecc71"
                            }`,
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={rule.status}
                          size="small"
                          sx={{
                            backgroundColor: rule.status === "ACTIVE" ? "#2ecc7120" : "#95a5a620",
                            color: rule.status === "ACTIVE" ? "#2ecc71" : "#95a5a6",
                            border: `1px solid ${rule.status === "ACTIVE" ? "#2ecc71" : "#95a5a6"}`,
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        <IconButton
                          size="small"
                          onClick={() => handleEditRule(rule)}
                          disabled={!canModifyRule(rule)}
                          sx={{ color: canModifyRule(rule) ? "#a93226" : "rgba(255,255,255,0.3)", mr: 1 }}
                          title={!canModifyRule(rule) ? "Cannot modify super admin rules" : "Edit rule"}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          onClick={() => rule.id && handleDeleteRule(rule.id, "velocity")}
                          disabled={!canModifyRule(rule)}
                          sx={{ color: canModifyRule(rule) ? "#e74c3c" : "rgba(255,255,255,0.3)" }}
                          title={!canModifyRule(rule) ? "Cannot delete super admin rules" : "Delete rule"}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                      </TableRow>
                    );
                  })
                ) : (
                  <TableRow>
                    <TableCell colSpan={8} align="center" sx={{ color: "text.disabled", py: 4 }}>
                      No velocity rules found. Create your first rule!
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
        </>
      )}

      {/* Risk Thresholds Tab */}
      {tab === 2 && (
        <>
          <Paper sx={{ p: 2, mb: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="body2" sx={{ color: "text.primary", mb: 1 }}>
              <strong>Risk Thresholds:</strong> Define risk score ranges and corresponding actions. These thresholds help
              categorize transactions by risk level and determine appropriate responses (Block, Hold, Alert, or Allow).
            </Typography>
            <Typography variant="caption" sx={{ color: "text.secondary" }}>
              All thresholds are visible to everyone. Super Admin thresholds are shown in blue, PSP thresholds in their theme color.
            </Typography>
          </Paper>
          <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ color: "text.secondary" }}>Threshold Name</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Creator</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Risk Level</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Score Range</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Action</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Status</TableCell>
                  <TableCell sx={{ color: "text.secondary" }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {riskThresholdsLoading ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 4 }}>
                      Loading risk thresholds...
                    </TableCell>
                  </TableRow>
                ) : filterRules(riskThresholds || []).length > 0 ? (
                  filterRules(riskThresholds || []).map((threshold: RiskThreshold) => {
                    const ruleColor = getRuleColor(threshold as any);
                    const creator = getRuleCreator(threshold as any);
                    return (
                      <TableRow key={threshold.id} hover>
                        <TableCell sx={{ color: ruleColor, fontWeight: 500 }}>{threshold.thresholdName}</TableCell>
                        <TableCell>
                          <Chip
                            label={creator}
                            size="small"
                            sx={{
                              backgroundColor: (threshold as any).isSuperAdmin ? "#3498db20" : `${ruleColor}20`,
                              color: ruleColor,
                              border: `1px solid ${ruleColor}`,
                            }}
                          />
                        </TableCell>
                      <TableCell>
                        <Chip
                          label={threshold.riskLevel}
                          size="small"
                          sx={{
                            backgroundColor:
                              threshold.riskLevel === "CRITICAL"
                                ? "#e74c3c20"
                                : threshold.riskLevel === "HIGH"
                                ? "#e67e2220"
                                : threshold.riskLevel === "MEDIUM"
                                ? "#f39c1220"
                                : "#2ecc7120",
                            color:
                              threshold.riskLevel === "CRITICAL"
                                ? "#e74c3c"
                                : threshold.riskLevel === "HIGH"
                                ? "#e67e22"
                                : threshold.riskLevel === "MEDIUM"
                                ? "#f39c12"
                                : "#2ecc71",
                          }}
                        />
                      </TableCell>
                      <TableCell sx={{ color: "text.primary" }}>
                        {threshold.minScore !== undefined && threshold.maxScore !== undefined
                          ? `${threshold.minScore} - ${threshold.maxScore}`
                          : threshold.minScore !== undefined
                          ? `≥ ${threshold.minScore}`
                          : threshold.maxScore !== undefined
                          ? `≤ ${threshold.maxScore}`
                          : "N/A"}
                      </TableCell>
                      <TableCell sx={{ color: "text.primary" }}>{threshold.action}</TableCell>
                      <TableCell>
                        <Chip
                          label={threshold.enabled ? "Enabled" : "Disabled"}
                          size="small"
                          sx={{
                            backgroundColor: threshold.enabled ? "#2ecc7120" : "#95a5a620",
                            color: threshold.enabled ? "#2ecc71" : "#95a5a6",
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        <IconButton
                          size="small"
                          onClick={() => handleEditRule(threshold)}
                          disabled={!canModifyRule(threshold as any)}
                          sx={{ color: canModifyRule(threshold as any) ? "#a93226" : "rgba(255,255,255,0.3)" }}
                          title={!canModifyRule(threshold as any) ? "Cannot modify super admin thresholds" : "Edit threshold"}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                      </TableRow>
                    );
                  })
                ) : (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 4 }}>
                      No risk thresholds found. Create your first threshold!
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
        </>
      )}

      {/* Create/Edit Rule Dialog */}
      <RuleDialog
        open={openDialog}
        onClose={() => {
          setOpenDialog(false);
          setEditingRule(null);
        }}
        onSave={handleSaveRule}
        rule={editingRule}
        ruleType={tab === 0 ? "aml" : tab === 1 ? "velocity" : "threshold"}
      />

      {/* Effectiveness Dialog */}
      <EffectivenessDialog
        open={effectivenessDialog !== null}
        onClose={() => setEffectivenessDialog(null)}
        effectiveness={effectiveness}
      />

      <Snackbar
        open={!!errorSnackbar}
        autoHideDuration={8000}
        onClose={() => setErrorSnackbar("")}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity="error" onClose={() => setErrorSnackbar("")} sx={{ width: "100%" }}>
          {errorSnackbar}
        </Alert>
      </Snackbar>
    </Box>
  );
}

// Rule Dialog Component
function RuleDialog({
  open,
  onClose,
  onSave,
  rule,
  ruleType,
}: {
  open: boolean;
  onClose: () => void;
  onSave: (rule: any) => void;
  rule: any;
  ruleType: "aml" | "velocity" | "threshold";
}) {
  const getInitialData = () => {
    if (rule) return rule;
    return {
      ruleName: "",
      description: "",
      ruleType: "SPEL",
      ruleExpression: "",
      priority: 100,
      enabled: true,
      action: "ALERT",
      ...(ruleType === "velocity"
        ? {
            maxTransactions: 10,
            maxAmount: 10000,
            timeWindowMinutes: 60,
            riskLevel: "MEDIUM",
            status: "ACTIVE",
          }
        : ruleType === "threshold"
        ? {
            thresholdName: "",
            riskLevel: "MEDIUM",
            minScore: 0,
            maxScore: 100,
            action: "ALERT",
            enabled: true,
          }
        : {}),
    };
  };

  const [formData, setFormData] = useState<any>(getInitialData());

  // Reset form when dialog opens/closes or rule changes
  useEffect(() => {
    if (open) {
      setFormData(getInitialData());
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, rule, ruleType]);

  const handleSubmit = () => {
    onSave(formData);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ backgroundColor: "background.paper", color: "text.primary" }}>
        {rule ? "Edit Rule" : "Create New Rule"}
      </DialogTitle>
      <DialogContent sx={{ backgroundColor: "background.paper" }}>
        <Grid container spacing={2} sx={{ mt: 1 }}>
          <Grid item xs={12}>
            <TextField
              fullWidth
              label="Rule Name"
              value={formData.ruleName}
              onChange={(e) => setFormData({ ...formData, ruleName: e.target.value })}
              required
              sx={{
                "& .MuiOutlinedInput-root": {
                  color: "text.primary",
                  "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                },
                "& .MuiInputLabel-root": { color: "text.secondary" },
              }}
            />
          </Grid>
          <Grid item xs={12}>
            <TextField
              fullWidth
              label="Description"
              value={formData.description || ""}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              multiline
              rows={3}
              sx={{
                "& .MuiOutlinedInput-root": {
                  color: "text.primary",
                  "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                },
                "& .MuiInputLabel-root": { color: "text.secondary" },
              }}
            />
          </Grid>

          {ruleType === "aml" && (
            <>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel sx={{ color: "text.secondary" }}>Rule Type</InputLabel>
                  <Select
                    value={formData.ruleType}
                    onChange={(e) => setFormData({ ...formData, ruleType: e.target.value })}
                    label="Rule Type"
                    sx={{ color: "text.primary" }}
                  >
                    <MenuItem value="SPEL">SpEL Expression</MenuItem>
                    <MenuItem value="JAVA_BEAN">Java Bean</MenuItem>
                    <MenuItem value="DROOLS_DRL">Drools DRL</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel sx={{ color: "text.secondary" }}>Action</InputLabel>
                  <Select
                    value={formData.action}
                    onChange={(e) => setFormData({ ...formData, action: e.target.value })}
                    label="Action"
                    sx={{ color: "text.primary" }}
                  >
                    <MenuItem value="BLOCK">Block</MenuItem>
                    <MenuItem value="HOLD">Hold</MenuItem>
                    <MenuItem value="ALERT">Alert</MenuItem>
                    <MenuItem value="ALLOW">Allow</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Priority"
                  type="number"
                  value={formData.priority}
                  onChange={(e) => setFormData({ ...formData, priority: Number(e.target.value) })}
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={formData.enabled}
                      onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                    />
                  }
                  label="Enabled"
                  sx={{ color: "text.primary", mt: 2 }}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label={
                    formData.ruleType === "SPEL"
                      ? "SpEL Expression (e.g., #tx.amount >= 10000)"
                      : formData.ruleType === "JAVA_BEAN"
                      ? "Java Bean Class Name"
                      : "Drools DRL Content"
                  }
                  value={formData.ruleExpression || ""}
                  onChange={(e) => setFormData({ ...formData, ruleExpression: e.target.value })}
                  multiline
                  rows={6}
                  placeholder={
                    formData.ruleType === "SPEL"
                      ? "#tx.amount >= 10000 and #tx.currency == 'USD'"
                      : formData.ruleType === "JAVA_BEAN"
                      ? "com.posgateway.aml.service.rules.LargeAmountRuleEvaluator"
                      : "rule \"Rule Name\"\nwhen\n    $tx : TransactionFact(amount >= 10000)\nthen\n    $tx.setDecision(\"BLOCK\");\nend"
                  }
                  required
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
                {formData.ruleType === "SPEL" && (
                  <Box sx={{ mt: 1, p: 1.5, backgroundColor: "background.paper", borderRadius: 1 }}>
                    <Typography variant="caption" sx={{ color: "text.secondary", display: "block", mb: 0.5 }}>
                      <strong>SpEL Examples:</strong>
                    </Typography>
                    <Typography variant="caption" sx={{ color: "text.disabled", display: "block", fontFamily: "monospace" }}>
                      • Large amount: <code>#tx.amount &gt;= 10000</code>
                      <br />
                      • Structuring: <code>#tx.amount &gt;= 9000 and #tx.amount &lt; 10000 and #history.panTxnCount1h &gt;= 3</code>
                      <br />
                      • Volume spike: <code>#history.avgVolume30d &gt; 0 and (#tx.amount / #history.avgVolume30d) &gt; 3.0</code>
                      <br />
                      • Available variables: <code>#tx</code> (transaction), <code>#merchant</code>, <code>#history</code> (transaction history)
                    </Typography>
                  </Box>
                )}
              </Grid>
            </>
          )}

          {ruleType === "velocity" && (
            <>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Max Transactions"
                  type="number"
                  value={formData.maxTransactions}
                  onChange={(e) =>
                    setFormData({ ...formData, maxTransactions: Number(e.target.value) })
                  }
                  required
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Max Amount"
                  type="number"
                  value={formData.maxAmount}
                  onChange={(e) => setFormData({ ...formData, maxAmount: Number(e.target.value) })}
                  required
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Time Window (minutes)"
                  type="number"
                  value={formData.timeWindowMinutes}
                  onChange={(e) =>
                    setFormData({ ...formData, timeWindowMinutes: Number(e.target.value) })
                  }
                  required
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel sx={{ color: "text.secondary" }}>Risk Level</InputLabel>
                  <Select
                    value={formData.riskLevel}
                    onChange={(e) => setFormData({ ...formData, riskLevel: e.target.value })}
                    label="Risk Level"
                    sx={{ color: "text.primary" }}
                  >
                    <MenuItem value="LOW">Low</MenuItem>
                    <MenuItem value="MEDIUM">Medium</MenuItem>
                    <MenuItem value="HIGH">High</MenuItem>
                    <MenuItem value="CRITICAL">Critical</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
            </>
          )}

          {ruleType === "threshold" && (
            <>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Threshold Name"
                  value={formData.thresholdName || ""}
                  onChange={(e) => setFormData({ ...formData, thresholdName: e.target.value })}
                  required
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel sx={{ color: "text.secondary" }}>Risk Level</InputLabel>
                  <Select
                    value={formData.riskLevel}
                    onChange={(e) => setFormData({ ...formData, riskLevel: e.target.value })}
                    label="Risk Level"
                    sx={{ color: "text.primary" }}
                  >
                    <MenuItem value="LOW">Low</MenuItem>
                    <MenuItem value="MEDIUM">Medium</MenuItem>
                    <MenuItem value="HIGH">High</MenuItem>
                    <MenuItem value="CRITICAL">Critical</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Action"
                  value={formData.action}
                  onChange={(e) => setFormData({ ...formData, action: e.target.value })}
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Min Score"
                  type="number"
                  value={formData.minScore || ""}
                  onChange={(e) =>
                    setFormData({ ...formData, minScore: e.target.value ? Number(e.target.value) : undefined })
                  }
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Max Score"
                  type="number"
                  value={formData.maxScore || ""}
                  onChange={(e) =>
                    setFormData({ ...formData, maxScore: e.target.value ? Number(e.target.value) : undefined })
                  }
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
              </Grid>
              <Grid item xs={12}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={formData.enabled}
                      onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                    />
                  }
                  label="Enabled"
                  sx={{ color: "text.primary" }}
                />
              </Grid>
            </>
          )}
        </Grid>
      </DialogContent>
      <DialogActions sx={{ backgroundColor: "background.paper" }}>
        <Button onClick={onClose} sx={{ color: "text.secondary" }}>
          Cancel
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}
        >
          {rule ? "Update" : "Create"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// Effectiveness Dialog Component
function EffectivenessDialog({
  open,
  onClose,
  effectiveness,
}: {
  open: boolean;
  onClose: () => void;
  effectiveness: any;
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ backgroundColor: "background.paper", color: "text.primary" }}>
        Rule Effectiveness Metrics
      </DialogTitle>
      <DialogContent sx={{ backgroundColor: "background.paper" }}>
        {effectiveness ? (
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12} md={6}>
              <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                <CardContent>
                  <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                    Total Executions
                  </Typography>
                  <Typography variant="h6" sx={{ color: "text.primary" }}>
                    {effectiveness.totalExecutions || 0}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                <CardContent>
                  <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                    Triggered Count
                  </Typography>
                  <Typography variant="h6" sx={{ color: "text.primary" }}>
                    {effectiveness.triggeredCount || 0}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                <CardContent>
                  <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                    False Positive Rate
                  </Typography>
                  <Typography variant="h6" sx={{ color: "text.primary" }}>
                    {effectiveness.falsePositiveRate
                      ? (effectiveness.falsePositiveRate * 100).toFixed(2)
                      : 0}
                    %
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                <CardContent>
                  <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                    Avg Execution Time
                  </Typography>
                  <Typography variant="h6" sx={{ color: "text.primary" }}>
                    {effectiveness.averageExecutionTime
                      ? `${effectiveness.averageExecutionTime.toFixed(2)}ms`
                      : "N/A"}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        ) : (
          <Typography sx={{ color: "text.disabled" }}>Loading effectiveness data...</Typography>
        )}
      </DialogContent>
      <DialogActions sx={{ backgroundColor: "background.paper" }}>
        <Button onClick={onClose} sx={{ color: "text.secondary" }}>
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

