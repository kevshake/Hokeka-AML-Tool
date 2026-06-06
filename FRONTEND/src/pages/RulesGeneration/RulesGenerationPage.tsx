import VisualRuleBuilder from "../../components/VisualRuleBuilder";
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
  AutoAwesome as AutoAwesomeIcon,
} from "@mui/icons-material";
import CircularProgress from "@mui/material/CircularProgress";
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
  useGenerateRule,
  type GeneratedRulePreview,
  type GenerateRuleError,
} from "../../features/api/mutations";
import type { AmlRule, VelocityRule, RiskThreshold } from "../../types/rules";

export default function RulesGenerationPage() {
  const [tab, setTab] = useState(0);
  const [openDialog, setOpenDialog] = useState(false);
  const [editingRule, setEditingRule] = useState<any>(null);
  const [effectivenessDialog, setEffectivenessDialog] = useState<number | null>(null);
  const [filterBy, setFilterBy] = useState<"all" | "super-admin" | "my-psp">("all");
  const [errorSnackbar, setErrorSnackbar] = useState("");

  // AI rule generation state — preview only, never auto-saves.
  const [aiPrompt, setAiPrompt] = useState("");
  const [aiPreview, setAiPreview] = useState<GeneratedRulePreview | null>(null);
  const [aiError, setAiError] = useState<GenerateRuleError | null>(null);
  const generateRule = useGenerateRule();

  const handleGenerateFromPrompt = async () => {
    if (!aiPrompt.trim()) return;
    setAiError(null);
    setAiPreview(null);
    try {
      const preview = await generateRule.mutateAsync({ prompt: aiPrompt });
      setAiPreview(preview);
    } catch (err) {
      setAiError(err as GenerateRuleError);
    }
  };

  const handleUseGeneratedRule = () => {
    if (!aiPreview) return;
    // Pre-fill the existing AML rule form. Backend RuleDefinition uses `name`;
    // FE AmlRule uses `ruleName` — translate here.
    setEditingRule({
      ruleName: aiPreview.name,
      description: aiPreview.description ?? "",
      ruleType: aiPreview.ruleType,
      ruleExpression: aiPreview.ruleExpression,
      priority: aiPreview.priority ?? 100,
      action: aiPreview.action ?? "ALERT",
      score: aiPreview.score,
      enabled: aiPreview.enabled ?? false,
    });
    setTab(0); // ensure AML Rules tab is selected
    setOpenDialog(true);
  };

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
    // System-managed seeded rules are editable (parameters / threshold / enabled)
    // by anyone who can otherwise modify rules — only delete is locked.
    // Super admin can modify all rules
    if (isCurrentUserSuperAdmin()) return true;
    // PSP users can only modify their own PSP's rules (not super admin rules)
    if (rule.isSuperAdmin || !rule.pspId) return false;
    // PSP users can modify their own PSP's rules
    return rule.pspId === currentUser?.pspId;
  };

  // System-managed rules from the seeded catalog (V135) cannot be deleted —
  // operators can only disable or edit them. User-created rules remain freely
  // deletable, so this flag is per-row from the backend.
  const canDeleteRule = (rule: any) => {
    if (!rule) return false;
    if (rule.isSystemManaged) return false;
    return canModifyRule(rule);
  };

  const ruleDeleteTitle = (rule: any) => {
    if (rule?.isSystemManaged) return "System rules cannot be deleted — use Disable";
    if (!canModifyRule(rule)) return "Cannot delete super admin rules";
    return "Delete rule";
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

      <Box sx={{ mt: 4, mb: 3 }}>
        <Typography variant="h6" sx={{ mb: 2, color: "text.primary" }}>Visual Rule Builder</Typography>
        <VisualRuleBuilder
          onChange={(expr, json) =>
            setEditingRule({
              ruleName: "Visual Rule",
              ruleExpression: expr,
              ruleType: "SPEL",
              ruleJson: JSON.stringify(json),
            })
          }
          onSave={(rule) => {
            setEditingRule(rule);
            setOpenDialog(true);
          }}
        />
      </Box>

      {/* AML Rules Tab */}
      {tab === 0 && (
        <>
          {/* AI Generate-from-Description panel — Phase 2 of AI rule generation flow.
              Calls POST /rules/generate; the preview is NOT auto-saved. The operator
              clicks "Use This Rule" to pre-fill the existing form for review/save. */}
          <Paper sx={{ p: 2, mb: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
              <AutoAwesomeIcon sx={{ color: "#a93226" }} fontSize="small" />
              <Typography variant="subtitle1" sx={{ color: "text.primary", fontWeight: 600 }}>
                Generate from Description (AI)
              </Typography>
            </Box>
            <Typography variant="caption" sx={{ color: "text.secondary", display: "block", mb: 1 }}>
              Describe the fraud pattern in plain English. The AI will draft a rule preview that you can review and save.
            </Typography>
            <TextField
              fullWidth
              multiline
              minRows={2}
              maxRows={6}
              placeholder='e.g. "Block transactions over $10,000 from new merchants in high-risk countries"'
              value={aiPrompt}
              onChange={(e) => setAiPrompt(e.target.value)}
              disabled={generateRule.isPending}
              sx={{
                "& .MuiOutlinedInput-root": { color: "text.primary" },
              }}
            />
            <Box sx={{ display: "flex", gap: 1, mt: 1, alignItems: "center" }}>
              <Button
                variant="contained"
                onClick={handleGenerateFromPrompt}
                disabled={!aiPrompt.trim() || generateRule.isPending}
                startIcon={
                  generateRule.isPending ? (
                    <CircularProgress size={16} sx={{ color: "white" }} />
                  ) : (
                    <AutoAwesomeIcon />
                  )
                }
                sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}
              >
                {generateRule.isPending ? "Generating..." : "Generate"}
              </Button>
              {(aiPreview || aiError) && (
                <Button
                  variant="text"
                  onClick={() => {
                    setAiPreview(null);
                    setAiError(null);
                  }}
                  sx={{ color: "text.secondary" }}
                >
                  Clear
                </Button>
              )}
            </Box>

            {aiError && (
              <Alert
                severity={aiError.kind === "not_configured" ? "info" : "error"}
                sx={{ mt: 2 }}
              >
                <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                  {aiError.kind === "not_configured"
                    ? "AI generation not configured"
                    : aiError.kind === "ai_failed"
                    ? "AI failed to produce a valid rule"
                    : aiError.kind === "bad_request"
                    ? "Invalid request"
                    : "Generation error"}
                </Typography>
                {aiError.hint && (
                  <Typography variant="caption" sx={{ display: "block" }}>
                    {aiError.hint}
                  </Typography>
                )}
                {aiError.details && (
                  <Typography variant="caption" sx={{ display: "block", fontFamily: "monospace" }}>
                    {aiError.details}
                  </Typography>
                )}
                {!aiError.hint && !aiError.details && (
                  <Typography variant="caption">{aiError.message}</Typography>
                )}
              </Alert>
            )}

            {aiPreview && (
              <Card sx={{ mt: 2, border: "1px solid #a93226" }}>
                <CardContent>
                  <Typography variant="overline" sx={{ color: "#a93226", fontWeight: 600 }}>
                    Preview (not saved)
                  </Typography>
                  <Typography variant="h6" sx={{ color: "text.primary", mt: 0.5 }}>
                    {aiPreview.name}
                  </Typography>
                  {aiPreview.description && (
                    <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                      {aiPreview.description}
                    </Typography>
                  )}
                  <Box sx={{ display: "flex", gap: 1, mb: 1, flexWrap: "wrap" }}>
                    <Chip label={`Type: ${aiPreview.ruleType}`} size="small" />
                    {aiPreview.action && <Chip label={`Action: ${aiPreview.action}`} size="small" />}
                    {typeof aiPreview.score === "number" && (
                      <Chip label={`Score: ${aiPreview.score}`} size="small" />
                    )}
                    {typeof aiPreview.priority === "number" && (
                      <Chip label={`Priority: ${aiPreview.priority}`} size="small" />
                    )}
                  </Box>
                  <Box
                    sx={{
                      backgroundColor: "rgba(0,0,0,0.04)",
                      p: 1,
                      borderRadius: 1,
                      fontFamily: "monospace",
                      fontSize: "0.85rem",
                      color: "text.primary",
                      whiteSpace: "pre-wrap",
                      wordBreak: "break-all",
                    }}
                  >
                    {aiPreview.ruleExpression}
                  </Box>
                  <Box sx={{ mt: 2, display: "flex", gap: 1 }}>
                    <Button
                      variant="contained"
                      onClick={handleUseGeneratedRule}
                      sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}
                    >
                      Use This Rule
                    </Button>
                  </Box>
                </CardContent>
              </Card>
            )}
          </Paper>

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
                          disabled={!canDeleteRule(rule)}
                          sx={{ color: canDeleteRule(rule) ? "#e74c3c" : "rgba(255,255,255,0.3)" }}
                          title={ruleDeleteTitle(rule)}
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
                          disabled={!canDeleteRule(rule)}
                          sx={{ color: canDeleteRule(rule) ? "#e74c3c" : "rgba(255,255,255,0.3)" }}
                          title={ruleDeleteTitle(rule)}
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
  // Backend RuleDefinition exposes the rule name as `name` (not `ruleName`).
  // The form binds to `ruleName`, so without this normalization the field
  // opens blank when editing — that was the "edit doesn't preload" bug.
  // Parameters come back as a JSON string from JSONB; parse for the editor.
  const normalizeRuleForForm = (r: any) => {
    if (!r) return r;
    let parsedParams = r.parameters;
    if (typeof parsedParams === "string") {
      try { parsedParams = JSON.parse(parsedParams); } catch { /* keep raw string */ }
    }
    return {
      ...r,
      ruleName: r.ruleName ?? r.name ?? "",
      action: r.action ?? "ALERT",
      ruleType: r.ruleType ?? "SPEL",
      ruleExpression: r.ruleExpression ?? "",
      priority: r.priority ?? 100,
      enabled: r.enabled ?? false,
      parameters: parsedParams ?? {},
      // taxonomy defaults so dropdowns render even on legacy rules
      category: r.category ?? "AML",
      appliesTo: r.appliesTo ?? "Transaction",
      ruleSubtype: r.ruleSubtype ?? "",
      typology: r.typology ?? "",
      checksFor: r.checksFor ?? "",
    };
  };

  const getInitialData = () => {
    if (rule) return normalizeRuleForForm(rule);
    return {
      ruleName: "",
      description: "",
      ruleType: "SPEL",
      ruleExpression: "",
      priority: 100,
      enabled: true,
      action: "ALERT",
      category: "AML",
      appliesTo: "Transaction",
      ruleSubtype: "",
      typology: "",
      checksFor: "",
      parameters: {},
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
    // Translate back to the backend shape: include both `name` (canonical) and
    // keep `ruleName` for any FE consumers still reading it. Re-serialize the
    // parameters object to JSONB-friendly string.
    const payload = {
      ...formData,
      name: formData.ruleName,
      parameters:
        typeof formData.parameters === "string"
          ? formData.parameters
          : JSON.stringify(formData.parameters ?? {}),
    };
    onSave(payload);
  };

  const isSystemRule = Boolean(rule?.isSystemManaged);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ backgroundColor: "background.paper", color: "text.primary" }}>
        {rule ? "Edit Rule" : "Create New Rule"}
        {isSystemRule && (
          <Chip
            size="small"
            label={`System default${rule?.externalCode ? ` · ${rule.externalCode}` : ""}`}
            sx={{ ml: 1, backgroundColor: "#3498db", color: "#fff" }}
          />
        )}
      </DialogTitle>
      <DialogContent sx={{ backgroundColor: "background.paper" }}>
        {isSystemRule && (
          <Alert severity="info" sx={{ mt: 1, mb: 1 }}>
            This is a curated default rule. You can change parameters, action, and
            enable/disable it, but the rule name and category are locked. Use{" "}
            <strong>Disable</strong> to take it out of evaluation; deletion is not allowed.
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mt: 1 }}>
          <Grid item xs={12}>
            <TextField
              fullWidth
              label="Rule Name"
              value={formData.ruleName}
              onChange={(e) => setFormData({ ...formData, ruleName: e.target.value })}
              required
              disabled={isSystemRule}
              helperText={isSystemRule ? "Locked — system rule identity" : undefined}
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
              {/* ── Taxonomy: dropdown-driven so users don't need to memorize values ── */}
              <Grid item xs={12} md={4}>
                <FormControl fullWidth>
                  <InputLabel sx={{ color: "text.secondary" }}>Category</InputLabel>
                  <Select
                    value={formData.category ?? "AML"}
                    onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                    label="Category"
                    disabled={isSystemRule}
                    sx={{ color: "text.primary" }}
                  >
                    <MenuItem value="AML">AML — Anti-Money Laundering</MenuItem>
                    <MenuItem value="FRAUD">Fraud</MenuItem>
                    <MenuItem value="SCREENING">Screening (Sanctions / PEP / AM)</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={4}>
                <FormControl fullWidth>
                  <InputLabel sx={{ color: "text.secondary" }}>Applies To</InputLabel>
                  <Select
                    value={formData.appliesTo ?? "Transaction"}
                    onChange={(e) => setFormData({ ...formData, appliesTo: e.target.value })}
                    label="Applies To"
                    disabled={isSystemRule}
                    sx={{ color: "text.primary" }}
                  >
                    <MenuItem value="Transaction">Transaction</MenuItem>
                    <MenuItem value="User">User</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={4}>
                <FormControl fullWidth>
                  <InputLabel sx={{ color: "text.secondary" }}>Detection Method</InputLabel>
                  <Select
                    value={formData.ruleSubtype ?? ""}
                    onChange={(e) => setFormData({ ...formData, ruleSubtype: e.target.value })}
                    label="Detection Method"
                    sx={{ color: "text.primary" }}
                  >
                    <MenuItem value="">— Select —</MenuItem>
                    <MenuItem value="Velocity">Velocity</MenuItem>
                    <MenuItem value="Velocity comparison">Velocity comparison</MenuItem>
                    <MenuItem value="Volume">Volume</MenuItem>
                    <MenuItem value="Volume comparison">Volume comparison</MenuItem>
                    <MenuItem value="Anomaly detection">Anomaly detection</MenuItem>
                    <MenuItem value="Pattern recognition">Pattern recognition</MenuItem>
                    <MenuItem value="Diversity">Diversity</MenuItem>
                    <MenuItem value="Blacklist">Blacklist</MenuItem>
                    <MenuItem value="Screening">Screening</MenuItem>
                    <MenuItem value="New activity">New activity</MenuItem>
                    <MenuItem value="Risk exposure">Risk exposure</MenuItem>
                    <MenuItem value="Merchant monitoring">Merchant monitoring</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel sx={{ color: "text.secondary" }}>Typology</InputLabel>
                  <Select
                    value={formData.typology ?? ""}
                    onChange={(e) => setFormData({ ...formData, typology: e.target.value })}
                    label="Typology"
                    sx={{ color: "text.primary" }}
                  >
                    <MenuItem value="">— Select —</MenuItem>
                    <MenuItem value="Unusual behaviour">Unusual behaviour</MenuItem>
                    <MenuItem value="Structuring">Structuring</MenuItem>
                    <MenuItem value="Money mules">Money mules</MenuItem>
                    <MenuItem value="Layering">Layering</MenuItem>
                    <MenuItem value="High risk transactions">High risk transactions</MenuItem>
                    <MenuItem value="Hidden/unusual relationships">Hidden / unusual relationships</MenuItem>
                    <MenuItem value="Account takeover fraud">Account takeover fraud</MenuItem>
                    <MenuItem value="Card fraud">Card fraud</MenuItem>
                    <MenuItem value="Acquiring fraud">Acquiring fraud</MenuItem>
                    <MenuItem value="Issuing fraud">Issuing fraud</MenuItem>
                    <MenuItem value="Internal blacklists">Internal blacklists</MenuItem>
                    <MenuItem value="Screening hits">Screening hits</MenuItem>
                    <MenuItem value="Terrorist financing">Terrorist financing</MenuItem>
                    <MenuItem value="Scams">Scams (romance, Nigerian Prince, inheritance, etc.)</MenuItem>
                  </Select>
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Checks For (input variables)"
                  placeholder="e.g. Transaction amount, Time"
                  value={formData.checksFor ?? ""}
                  onChange={(e) => setFormData({ ...formData, checksFor: e.target.value })}
                  helperText="Comma-separated list of fields the rule examines"
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      color: "text.primary",
                      "& fieldset": { borderColor: "rgba(255,255,255,0.3)" },
                    },
                    "& .MuiInputLabel-root": { color: "text.secondary" },
                  }}
                />
              </Grid>
              {/* ── Engine + Action ── */}
              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel sx={{ color: "text.secondary" }}>Rule Engine</InputLabel>
                  <Select
                    value={formData.ruleType}
                    onChange={(e) => setFormData({ ...formData, ruleType: e.target.value })}
                    label="Rule Engine"
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
                    <MenuItem value="BLOCK">Block — decline transaction</MenuItem>
                    <MenuItem value="HOLD">Hold — suspend for review</MenuItem>
                    <MenuItem value="ALERT">Alert — flag for analyst</MenuItem>
                    <MenuItem value="ALLOW">Allow — pass through</MenuItem>
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
