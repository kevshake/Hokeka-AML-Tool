import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Grid,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Alert,
  CircularProgress,
  Tabs,
  Tab,
  Chip,
  Tooltip,
  Divider,
} from "@mui/material";
import { apiClient } from "../../lib/apiClient";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, useEffect } from "react";
import { BRAND_THEMES } from "../../config/themes";
import { useAuth } from "../../contexts/AuthContext";


interface Psp {
  id: number;
  code: string;
  name: string;
  status: string;
}

interface PspTheme {
  pspId: number;
  pspName: string;
  brandingTheme?: string;
  primaryColor?: string;
  secondaryColor?: string;
  accentColor?: string;
  logoUrl?: string;
  fontFamily?: string;
  fontSize?: string;
  buttonRadius?: string;
  buttonStyle?: string;
  navStyle?: string;
}

interface ThemePresets {
  [key: string]: {
    primaryColor: string;
    secondaryColor: string;
    accentColor: string;
  };
}

function TabPanel(props: { children?: React.ReactNode; index: number; value: number }) {
  const { children, value, index, ...other } = props;
  return (
    <div role="tabpanel" hidden={value !== index} {...other}>
      {value === index && <Box sx={{ pt: 2 }}>{children}</Box>}
    </div>
  );
}

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const [tabValue, setTabValue] = useState(0);
  const [selectedPspId, setSelectedPspId] = useState<number | null>(null);
  const [themeData, setThemeData] = useState<PspTheme | null>(null);
  const [saving, setSaving] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Fetch all PSPs
  const { data: psps, isLoading: isLoadingPsps } = useQuery<Psp[]>({
    queryKey: ["settings", "psps"],
    queryFn: () => apiClient.get<Psp[]>("settings/psps"),
  });

  // Fetch theme presets
  const { data: presets } = useQuery<ThemePresets>({
    queryKey: ["settings", "themes", "presets"],
    queryFn: () => apiClient.get<ThemePresets>("settings/themes/presets"),
  });

  // Fetch PSP theme when PSP is selected
  const { data: currentTheme, isLoading: isLoadingTheme } = useQuery<PspTheme>({
    queryKey: ["settings", "psps", selectedPspId, "theme"],
    queryFn: () => apiClient.get<PspTheme>(`settings/psps/${selectedPspId}/theme`),
    enabled: selectedPspId !== null,
  });

  // Update theme data when current theme loads
  useEffect(() => {
    if (currentTheme) {
      setThemeData(currentTheme);
    }
  }, [currentTheme]);

  // Update theme mutation
  const updateThemeMutation = useMutation({
    mutationFn: (data: Partial<PspTheme>) =>
      apiClient.put<PspTheme>(`settings/psps/${selectedPspId}/theme`, data),
    onSuccess: () => {
      setSuccessMessage("Theme updated successfully!");
      queryClient.invalidateQueries({ queryKey: ["settings", "psps", selectedPspId, "theme"] });
      queryClient.invalidateQueries({ queryKey: ["user", "me"] }); // Refresh user to get new theme
      setTimeout(() => setSuccessMessage(null), 3000);
    },
    onError: () => {
      setSuccessMessage("Failed to update theme");
      setTimeout(() => setSuccessMessage(null), 3000);
    },
  });

  const handlePspChange = (pspId: number) => {
    setSelectedPspId(pspId);
    setThemeData(null);
  };

  const handlePresetSelect = (presetId: string) => {
    if (!presets || !themeData) return;
    const preset = presets[presetId];
    if (preset) {
      setThemeData({
        ...themeData,
        brandingTheme: presetId,
        primaryColor: preset.primaryColor,
        secondaryColor: preset.secondaryColor,
        accentColor: preset.accentColor,
      });
    }
  };

  const handleSaveTheme = async () => {
    if (!selectedPspId || !themeData) return;
    setSaving(true);
    try {
      await updateThemeMutation.mutateAsync({
        brandingTheme: themeData.brandingTheme,
        primaryColor: themeData.primaryColor,
        secondaryColor: themeData.secondaryColor,
        accentColor: themeData.accentColor,
        logoUrl: themeData.logoUrl,
        fontFamily: themeData.fontFamily,
        fontSize: themeData.fontSize,
        buttonRadius: themeData.buttonRadius,
        buttonStyle: themeData.buttonStyle,
        navStyle: themeData.navStyle,
      });
    } finally {
      setSaving(false);
    }
  };

  const { user } = useAuth();
  const isSuperAdmin = user?.pspId === 0;

  // System Settings Interface
  interface SystemSettings {
    maintenanceMode: boolean;
    debugLogging: boolean;
    riskThresholdHigh: number;
    riskThresholdMedium: number;
    auditRetentionDays: number;
    allowCrossBorderTxns: boolean;
  }

  // Fetch system settings
  const { data: systemSettingsData, isLoading: isLoadingSystemSettings } = useQuery<SystemSettings>({
    queryKey: ["settings", "system"],
    queryFn: () => apiClient.get<SystemSettings>("settings/system"),
    enabled: isSuperAdmin,
  });

  // System Settings State (with defaults)
  const [systemSettings, setSystemSettings] = useState<SystemSettings>({
    maintenanceMode: false,
    debugLogging: false,
    riskThresholdHigh: 80,
    riskThresholdMedium: 50,
    auditRetentionDays: 90,
    allowCrossBorderTxns: true,
  });

  // Update local state when data loads
  useEffect(() => {
    if (systemSettingsData) {
      setSystemSettings(systemSettingsData);
    }
  }, [systemSettingsData]);

  const handleSystemSettingChange = (setting: string, value: any) => {
    setSystemSettings(prev => ({
      ...prev,
      [setting]: value
    }));
  };

  // Update system settings mutation
  const updateSystemSettingsMutation = useMutation({
    mutationFn: async (data: SystemSettings) => {
      return apiClient.put<SystemSettings>("settings/system", data);
    },
    onSuccess: () => {
      setSuccessMessage("System settings saved successfully");
      queryClient.invalidateQueries({ queryKey: ["settings", "system"] });
      setTimeout(() => setSuccessMessage(null), 3000);
    },
    onError: () => {
      setSuccessMessage("Failed to save system settings");
      setTimeout(() => setSuccessMessage(null), 3000);
    },
  });

  const handleSaveSystemSettings = async () => {
    await updateSystemSettingsMutation.mutateAsync(systemSettings);
  };

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 0.5, fontWeight: 600 }}>
        Settings
      </Typography>

      <Tabs value={tabValue} onChange={(_, newValue) => setTabValue(newValue)} sx={{ mb: 3 }}>
        <Tab label="PSP Theme Management" />
        {isSuperAdmin && <Tab label="System Settings" />}
      </Tabs>

      <TabPanel value={tabValue} index={0}>
        <Paper sx={{ p: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
          <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
            PSP Theme Customization
          </Typography>

          {successMessage && (
            <Alert severity={successMessage.includes("successfully") ? "success" : "error"} sx={{ mb: 2 }}>
              {successMessage}
            </Alert>
          )}

          {/* PSP Selection */}
          <Grid container spacing={3} sx={{ mb: 2 }}>
            <Grid item xs={12} md={6}>
              <Tooltip title="Select a Payment Service Provider (PSP) from the dropdown to customize its theme and branding settings. Once selected, you can modify colors, fonts, logo, button styles, and navigation layout. Each PSP can have a unique visual identity that is automatically applied when users from that PSP log into the system." arrow enterDelay={2000}>
                <FormControl fullWidth>
                  <InputLabel>Select PSP</InputLabel>
                  <Select
                    value={selectedPspId ?? ""}
                    onChange={(e) => handlePspChange(e.target.value as number)}
                    label="Select PSP"
                    disabled={isLoadingPsps}
                  >
                  {psps?.map((psp) => (
                    <MenuItem key={psp.id} value={psp.id}>
                      {psp.name} ({psp.code}) - {psp.status}
                    </MenuItem>
                  ))}
                  </Select>
                </FormControl>
              </Tooltip>
            </Grid>
          </Grid>

          {isLoadingTheme && selectedPspId ? (
            <Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
              <CircularProgress />
            </Box>
          ) : themeData ? (
            <>
              {/* Theme Presets */}
              <Box sx={{ mb: 2 }}>
                <Typography variant="subtitle1" sx={{ mb: 2, fontWeight: 600 }}>
                  Theme Presets
                </Typography>
                <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
                  {BRAND_THEMES.map((preset) => (
                    <Tooltip key={preset.id} title={`Apply the ${preset.name} theme preset. This will automatically populate all color fields and styling options with predefined values that match this brand's visual identity. You can further customize individual settings after applying a preset.`} arrow enterDelay={2000}>
                      <Chip
                        label={preset.name}
                        onClick={() => handlePresetSelect(preset.id)}
                        sx={{
                          cursor: "pointer",
                          backgroundColor:
                            themeData.brandingTheme === preset.id ? preset.primaryColor : "transparent",
                          color: themeData.brandingTheme === preset.id ? "#fff" : "text.primary",
                          border: `2px solid ${preset.primaryColor}`,
                          "&:hover": {
                            backgroundColor: preset.primaryColor + "20",
                          },
                        }}
                      />
                    </Tooltip>
                  ))}
                </Box>
              </Box>

              {/* Color Customization */}
              <Grid container spacing={3} sx={{ mb: 2 }}>
                <Grid item xs={12} md={4}>
                  <Tooltip title="Set the primary brand color used throughout the application for primary buttons, links, highlights, and key interactive elements. This is the main color that represents the PSP's brand identity. Choose a color that aligns with the PSP's corporate branding guidelines. The color picker allows you to select any color value." arrow enterDelay={2000}>
                    <TextField
                      fullWidth
                      label="Primary Color"
                      type="color"
                      value={themeData.primaryColor || "#8B4049"}
                      onChange={(e) => setThemeData({ ...themeData, primaryColor: e.target.value })}
                      InputLabelProps={{ shrink: true }}
                    />
                  </Tooltip>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Tooltip title="Set the secondary brand color used for secondary buttons, accents, borders, and supporting UI elements. This color complements the primary color and provides visual hierarchy. Typically, this is a lighter or complementary shade of the primary color that maintains brand consistency while providing contrast." arrow enterDelay={2000}>
                    <TextField
                      fullWidth
                      label="Secondary Color"
                      type="color"
                      value={themeData.secondaryColor || "#C9A961"}
                      onChange={(e) => setThemeData({ ...themeData, secondaryColor: e.target.value })}
                      InputLabelProps={{ shrink: true }}
                    />
                  </Tooltip>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Tooltip title="Set the accent color used for error states, warnings, emphasis, and attention-grabbing elements. This color is typically used for error messages, danger actions, and critical notifications. Choose a color that stands out (often red or orange tones) to ensure important alerts are clearly visible to users." arrow enterDelay={2000}>
                    <TextField
                      fullWidth
                      label="Accent Color"
                      type="color"
                      value={themeData.accentColor || "#A0525C"}
                      onChange={(e) => setThemeData({ ...themeData, accentColor: e.target.value })}
                      InputLabelProps={{ shrink: true }}
                    />
                  </Tooltip>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Tooltip title="Enter the full URL (web address) to the PSP's logo image file. Supported formats include PNG, SVG, or JPG. The logo will be displayed in the application header and branding areas. Ensure the URL is publicly accessible and the image is optimized for web display. Example: https://example.com/logo.png" arrow enterDelay={2000}>
                    <TextField
                      fullWidth
                      label="Logo URL"
                      value={themeData.logoUrl || ""}
                      onChange={(e) => setThemeData({ ...themeData, logoUrl: e.target.value })}
                      placeholder="https://example.com/logo.png"
                    />
                  </Tooltip>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Tooltip title="Specify the font family (typeface) used throughout the entire application interface. Enter CSS font-family values such as 'Inter', 'Outfit', 'Roboto', or a font stack like 'Arial, sans-serif'. The font should be web-safe or loaded via web fonts. This affects all text elements including headings, body text, buttons, and form labels to create a consistent typographic identity." arrow enterDelay={2000}>
                    <TextField
                      fullWidth
                      label="Font Family"
                      value={themeData.fontFamily || ""}
                      onChange={(e) => setThemeData({ ...themeData, fontFamily: e.target.value })}
                      placeholder="'Inter', 'Outfit', sans-serif"
                    />
                  </Tooltip>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Tooltip title="Set the base font size for the application interface. Enter a CSS size value such as '14px', '1rem', or '16px'. This base size is used as the default for body text, and other text elements (headings, buttons) scale relative to this value. Common values are 14px-16px for optimal readability. Use 'rem' units for better accessibility as it respects user browser font size preferences." arrow enterDelay={2000}>
                    <TextField
                      fullWidth
                      label="Font Size"
                      value={themeData.fontSize || ""}
                      onChange={(e) => setThemeData({ ...themeData, fontSize: e.target.value })}
                      placeholder="14px or 1rem"
                    />
                  </Tooltip>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Tooltip title="Set the border radius (rounded corners) for all buttons in the application. Enter a CSS value like '12px', '0.5rem', or '8px'. Higher values create more rounded buttons (softer appearance), while lower values create sharper, more angular buttons. This affects the visual style and modern feel of the interface." arrow enterDelay={2000}>
                    <TextField
                      fullWidth
                      label="Button Border Radius"
                      value={themeData.buttonRadius || ""}
                      onChange={(e) => setThemeData({ ...themeData, buttonRadius: e.target.value })}
                      placeholder="12px or 0.5rem"
                    />
                  </Tooltip>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Tooltip title="Choose the visual style for all buttons in the application. Options: Flat (minimal design with no shadow or border, modern and clean), Raised (elevated appearance with shadow, creates depth), or Outlined (border-only style with transparent fill, subtle appearance). This affects the overall button aesthetic and user interface feel." arrow enterDelay={2000}>
                    <FormControl fullWidth>
                      <InputLabel>Button Style</InputLabel>
                      <Select
                        value={themeData.buttonStyle || "flat"}
                        onChange={(e) => setThemeData({ ...themeData, buttonStyle: e.target.value })}
                        label="Button Style"
                      >
                        <MenuItem value="flat">Flat</MenuItem>
                        <MenuItem value="raised">Raised</MenuItem>
                        <MenuItem value="outlined">Outlined</MenuItem>
                      </Select>
                    </FormControl>
                  </Tooltip>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Tooltip title="Select the navigation layout style for the application. Drawer (side panel) displays a collapsible sidebar menu on the left side, ideal for applications with many menu items. Top Bar (horizontal menu) displays navigation links in a horizontal bar at the top, better for applications with fewer menu items. This affects the overall layout and user navigation experience." arrow enterDelay={2000}>
                    <FormControl fullWidth>
                      <InputLabel>Navigation Style</InputLabel>
                      <Select
                        value={themeData.navStyle || "drawer"}
                        onChange={(e) => setThemeData({ ...themeData, navStyle: e.target.value })}
                        label="Navigation Style"
                      >
                        <MenuItem value="drawer">Drawer</MenuItem>
                        <MenuItem value="topbar">Top Bar</MenuItem>
                      </Select>
                    </FormControl>
                  </Tooltip>
                </Grid>
              </Grid>

              <Box sx={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
                <Tooltip title="Reset all theme settings to the last saved configuration, discarding any unsaved changes made in this session. This restores colors, fonts, logo, and styling options to their previously saved state. Useful if you want to undo recent modifications without saving." arrow enterDelay={2000}>
                  <Button variant="outlined" onClick={() => setThemeData(currentTheme || null)}>
                    Reset
                  </Button>
                </Tooltip>
                <Tooltip title="Save the current theme configuration for this PSP. All color settings, fonts, logo URL, button styles, and navigation preferences will be persisted and automatically applied when users from this PSP log into the system. The theme takes effect immediately after saving." arrow enterDelay={2000}>
                  <Button
                    variant="contained"
                    onClick={handleSaveTheme}
                    disabled={saving}
                    sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}
                  >
                    {saving ? "Saving..." : "Save Theme"}
                  </Button>
                </Tooltip>
              </Box>
            </>
          ) : selectedPspId ? (
            <Alert severity="info">Loading theme configuration...</Alert>
          ) : (
            <Alert severity="info">Please select a PSP to manage its theme.</Alert>
          )}
        </Paper>
      </TabPanel>

      {isSuperAdmin && (
        <TabPanel value={tabValue} index={1}>
          <Paper sx={{ p: 2, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
            <Typography variant="h6" sx={{ color: "text.primary", mb: 2 }}>
              System Configuration
            </Typography>

            <Grid container spacing={3}>
              {isLoadingSystemSettings ? (
                <Grid item xs={12}>
                  <Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
                    <CircularProgress />
                  </Box>
                </Grid>
              ) : (
                <>
                  <Grid item xs={12} md={6}>
                    <Typography variant="subtitle2" sx={{ mb: 1 }}>System Status</Typography>
                    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                      <Tooltip title="Enable maintenance mode to temporarily restrict user access during system updates, deployments, or scheduled maintenance windows. When enabled, users will see a maintenance message and cannot access the system. Disable after maintenance is complete to restore normal access. Use this feature to prevent data inconsistencies during system changes." arrow enterDelay={2000}>
                        <TextField
                          select
                          label="Maintenance Mode"
                          value={systemSettings.maintenanceMode ? "true" : "false"}
                          onChange={(e) => handleSystemSettingChange('maintenanceMode', e.target.value === "true")}
                          fullWidth
                          disabled={updateSystemSettingsMutation.isPending}
                        >
                          <MenuItem value="true">Enabled</MenuItem>
                          <MenuItem value="false">Disabled</MenuItem>
                        </TextField>
                      </Tooltip>
                      <Tooltip title="Enable debug logging to capture detailed system diagnostics, error traces, and performance metrics. When enabled, the system logs extensive information useful for troubleshooting issues, performance analysis, and development. Disable in production to reduce log volume and improve performance. Debug logs may contain sensitive information." arrow enterDelay={2000}>
                        <TextField
                          select
                          label="Debug Logging"
                          value={systemSettings.debugLogging ? "true" : "false"}
                          onChange={(e) => handleSystemSettingChange('debugLogging', e.target.value === "true")}
                          fullWidth
                          disabled={updateSystemSettingsMutation.isPending}
                        >
                          <MenuItem value="true">Enabled</MenuItem>
                          <MenuItem value="false">Disabled</MenuItem>
                        </TextField>
                      </Tooltip>
                    </Box>
                  </Grid>

              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>Risk & Compliance</Typography>
                <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                  <Tooltip title="Set the risk score threshold (0-100) above which transactions are automatically classified as high risk. Transactions with risk scores at or above this value will trigger high-risk alerts, require manual review, or be automatically declined based on your risk management rules. Lower values mean more transactions are flagged as high risk (stricter)." arrow enterDelay={2000}>
                    <TextField
                      label="High Risk Score Threshold"
                      type="number"
                      value={systemSettings.riskThresholdHigh}
                      onChange={(e) => handleSystemSettingChange('riskThresholdHigh', parseInt(e.target.value))}
                      fullWidth
                      disabled={updateSystemSettingsMutation.isPending}
                    />
                  </Tooltip>
                  <Tooltip title="Set the risk score threshold (0-100) above which transactions are classified as medium risk. Transactions with scores between this value and the high risk threshold require additional scrutiny but may be approved with review. This threshold helps categorize risk levels for appropriate handling workflows." arrow enterDelay={2000}>
                    <TextField
                      label="Medium Risk Score Threshold"
                      type="number"
                      value={systemSettings.riskThresholdMedium}
                      onChange={(e) => handleSystemSettingChange('riskThresholdMedium', parseInt(e.target.value))}
                      fullWidth
                      disabled={updateSystemSettingsMutation.isPending}
                    />
                  </Tooltip>
                </Box>
              </Grid>

              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>Data Policies</Typography>
                <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                  <Tooltip title="Set the number of days to retain audit logs in the system before automatic deletion. Audit logs older than this retention period will be automatically purged to manage database storage. Consider regulatory requirements (e.g., 7 years for financial compliance) when setting this value. Higher values require more storage capacity but provide longer audit trail history." arrow enterDelay={2000}>
                    <TextField
                      label="Audit Log Retention (Days)"
                      type="number"
                      value={systemSettings.auditRetentionDays}
                      onChange={(e) => handleSystemSettingChange('auditRetentionDays', parseInt(e.target.value))}
                      fullWidth
                      disabled={updateSystemSettingsMutation.isPending}
                    />
                  </Tooltip>
                </Box>
              </Grid>

              <Grid item xs={12}>
                <Divider sx={{ my: 2 }} />
                <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
                  <Tooltip title="Save all system configuration settings including maintenance mode, debug logging, risk thresholds, and audit retention policies. These settings apply globally across the entire system and affect all users and PSPs. Changes take effect immediately after saving. Ensure you have proper authorization before modifying system-wide settings." arrow enterDelay={2000}>
                    <Button 
                      variant="contained" 
                      color="primary" 
                      onClick={handleSaveSystemSettings}
                      disabled={updateSystemSettingsMutation.isPending}
                    >
                      {updateSystemSettingsMutation.isPending ? "Saving..." : "Save System Settings"}
                    </Button>
                  </Tooltip>
                </Box>
              </Grid>
                </>
              )}
            </Grid>
          </Paper>
        </TabPanel>
      )}
    </Box>
  );
}
