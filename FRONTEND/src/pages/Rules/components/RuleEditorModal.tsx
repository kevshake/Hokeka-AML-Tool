import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Grid,
  Typography,
  Chip,
  Box,
  Divider,
} from '@mui/material';

interface RuleEditorModalProps {
  open: boolean;
  onClose: () => void;
  onSave: (rule: any) => void;
  editingRule?: any;
  isSystemRule?: boolean;
}

const RULE_TYPES = ['Transaction', 'User', 'Screening'];
const ACTIONS = ['FLAG', 'SUSPEND', 'BLOCK', 'REVIEW'];
const OPERATORS = ['>', '>=', '<', '<=', '=', '!=', 'contains', 'in', 'between'];

const PARAMETER_TEMPLATES: Record<string, any[]> = {
  Transaction: [
    { key: 'amount', label: 'Transaction Amount', type: 'number', operator: true },
    { key: 'country', label: 'Country', type: 'text', operator: true },
    { key: 'currency', label: 'Currency', type: 'text', operator: true },
    { key: 'transactionCount', label: 'Number of Transactions', type: 'number', operator: true },
    { key: 'timeWindow', label: 'Time Window (hours/days)', type: 'text', operator: false },
    { key: 'ipAddress', label: 'IP Address', type: 'text', operator: true },
    { key: 'paymentIdentifier', label: 'Payment Identifier (Card/Account)', type: 'text', operator: true },
    { key: 'roundAmount', label: 'Round Amount (e.g. 10000, 5000)', type: 'number', operator: true },
    { key: 'levenshteinDistance', label: 'Levenshtein Distance (name similarity)', type: 'number', operator: true },
    { key: 'velocityScore', label: 'Velocity Score (txns per hour)', type: 'number', operator: true },
    { key: 'diversityScore', label: 'Diversity Score (unique merchants)', type: 'number', operator: true },
    { key: 'ipChangeCount', label: 'IP Address Changes (last 24h)', type: 'number', operator: true },
    { key: 'roundValuePattern', label: 'Round Value Pattern Match', type: 'boolean', operator: true },
  ],
  User: [
    { key: 'userStatus', label: 'User Status', type: 'text', operator: true },
    { key: 'countryOfResidence', label: 'Country of Residence', type: 'text', operator: true },
    { key: 'inactivityDays', label: 'Days of Inactivity', type: 'number', operator: true },
    { key: 'addressChangeCount', label: 'Address Changes', type: 'number', operator: true },
    { key: 'deviceFingerprintChange', label: 'Device Fingerprint Changes', type: 'number', operator: true },
  ],
  Screening: [
    { key: 'screeningType', label: 'Screening Type', type: 'select', options: ['Sanctions', 'PEP', 'Adverse Media'] },
    { key: 'matchThreshold', label: 'Match Threshold (%)', type: 'number', operator: true },
    { key: 'sanctionsHitCount', label: 'Sanctions Hit Count', type: 'number', operator: true },
  ],
  Velocity: [
    { key: 'maxTxnsPerHour', label: 'Max Transactions per Hour', type: 'number', operator: true },
    { key: 'maxAmountPerDay', label: 'Max Amount per Day', type: 'number', operator: true },
    { key: 'distinctMerchants', label: 'Distinct Merchants in Window', type: 'number', operator: true },
  ],
  Risk: [
    { key: 'riskScoreThreshold', label: 'Risk Score Threshold', type: 'number', operator: true },
    { key: 'countryRiskLevel', label: 'Country Risk Level', type: 'select', options: ['LOW', 'MEDIUM', 'HIGH'] },
    { key: 'entityRiskTier', label: 'Entity Risk Tier', type: 'select', options: ['1', '2', '3', '4'] },
  ],
};

export default function RuleEditorModal({
  open,
  onClose,
  onSave,
  editingRule,
  isSystemRule = false,
}: RuleEditorModalProps) {
  const [formData, setFormData] = useState<any>({
    ruleName: '',
    description: '',
    ruleType: 'Transaction',
    typology: '',
    defaultAction: 'FLAG',
    parameters: {},
    isActive: true,
  });

  const [selectedParameters, setSelectedParameters] = useState<any[]>([]);

  useEffect(() => {
    if (editingRule) {
      setFormData(editingRule);
    } else {
      resetForm();
    }
  }, [editingRule, open]);

  const resetForm = () => {
    setFormData({
      ruleName: '',
      description: '',
      ruleType: 'Transaction',
      typology: '',
      defaultAction: 'FLAG',
      parameters: {},
      isActive: true,
    });
    setSelectedParameters([]);
  };

  const handleRuleTypeChange = (type: string) => {
    setFormData({ ...formData, ruleType: type });
    setSelectedParameters([]);
  };

  const addParameter = (param: any) => {
    const newParam = {
      ...param,
      value: '',
      operator: param.operator ? '>=' : '',
    };
    setSelectedParameters([...selectedParameters, newParam]);
  };

  const updateParameter = (index: number, field: string, value: any) => {
    const updated = [...selectedParameters];
    updated[index][field] = value;
    setSelectedParameters(updated);
  };

  const removeParameter = (index: number) => {
    const updated = selectedParameters.filter((_, i) => i !== index);
    setSelectedParameters(updated);
  };

  const handleSave = () => {
    const rulePayload = {
      ...formData,
      parameters: selectedParameters.reduce((acc: any, p: any) => {
        acc[p.key] = { operator: p.operator, value: p.value };
        return acc;
      }, {}),
    };
    onSave(rulePayload);
    onClose();
  };

  const currentParams = PARAMETER_TEMPLATES[formData.ruleType] || [];

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {editingRule ? 'Edit Rule' : 'Create New AML Rule'}
        {isSystemRule && <Chip label="System Rule" color="primary" size="small" sx={{ ml: 2 }} />}
      </DialogTitle>

      <DialogContent dividers>
        <Grid container spacing={3}>
          {/* Basic Info */}
          <Grid item xs={12}>
            <Typography variant="subtitle2" gutterBottom>Basic Information</Typography>
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Rule Name"
              value={formData.ruleName}
              onChange={(e) => setFormData({ ...formData, ruleName: e.target.value })}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <FormControl fullWidth>
              <InputLabel>Rule Type</InputLabel>
              <Select
                value={formData.ruleType}
                onChange={(e) => handleRuleTypeChange(e.target.value)}
                label="Rule Type"
              >
                {RULE_TYPES.map((type) => (
                  <MenuItem key={type} value={type}>{type}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={12}>
            <TextField
              fullWidth
              multiline
              rows={2}
              label="Description"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            />
          </Grid>

          {/* Action & Typology */}
          <Grid item xs={12} md={6}>
            <FormControl fullWidth>
              <InputLabel>Default Action</InputLabel>
              <Select
                value={formData.defaultAction}
                onChange={(e) => setFormData({ ...formData, defaultAction: e.target.value })}
                label="Default Action"
              >
                {ACTIONS.map((action) => (
                  <MenuItem key={action} value={action}>{action}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          {/* Parameters Section */}
          <Grid item xs={12}>
            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" gutterBottom>
              Rule Parameters (Click to add)
            </Typography>

            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
              {currentParams.map((param, idx) => (
                <Chip
                  key={idx}
                  label={param.label}
                  onClick={() => addParameter(param)}
                  color="primary"
                  variant="outlined"
                />
              ))}
            </Box>
          </Grid>

          {/* Dynamic Parameter Inputs */}
          {selectedParameters.length > 0 && (
            <Grid item xs={12}>
              <Typography variant="subtitle2" gutterBottom>Configured Parameters</Typography>
              {selectedParameters.map((param, index) => (
                <Grid container spacing={2} key={index} sx={{ mb: 2 }}>
                  <Grid item xs={3}>
                    <TextField fullWidth label="Field" value={param.label} disabled />
                  </Grid>
                  {param.operator && (
                    <Grid item xs={3}>
                      <FormControl fullWidth>
                        <InputLabel>Operator</InputLabel>
                        <Select
                          value={param.operator}
                          onChange={(e) => updateParameter(index, 'operator', e.target.value)}
                          label="Operator"
                        >
                          {OPERATORS.map((op) => (
                            <MenuItem key={op} value={op}>{op}</MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                  )}
                  <Grid item xs={param.operator ? 4 : 7}>
                    <TextField
                      fullWidth
                      label="Value"
                      value={param.value}
                      onChange={(e) => updateParameter(index, 'value', e.target.value)}
                      placeholder={param.type === 'number' ? '10000' : 'Enter value'}
                    />
                  </Grid>
                  <Grid item xs={2}>
                    <Button color="error" onClick={() => removeParameter(index)}>
                      Remove
                    </Button>
                  </Grid>
                </Grid>
              ))}
            </Grid>
          )}
        </Grid>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSave}>
          {editingRule ? 'Update Rule' : 'Create Rule'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}