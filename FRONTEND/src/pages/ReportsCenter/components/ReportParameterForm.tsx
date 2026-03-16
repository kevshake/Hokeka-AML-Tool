/**
 * Report Parameter Form Component
 * Dynamic form generation based on report parameters
 */

import { useState, useEffect } from "react";
import {
  Box,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormControlLabel,
  Checkbox,
  Grid,
  Typography,
  Chip,
  OutlinedInput,
  InputAdornment,
} from "@mui/material";
import type { ReportParameter } from "../../../types/reports/reportDefinitions";

interface ReportParameterFormProps {
  parameters: ReportParameter[];
  values: Record<string, unknown>;
  onChange: (values: Record<string, unknown>) => void;
}

export default function ReportParameterForm({
  parameters,
  values,
  onChange,
}: ReportParameterFormProps) {
  const [localValues, setLocalValues] = useState<Record<string, unknown>>(values || {});

  useEffect(() => {
    // Initialize default values
    const defaults: Record<string, unknown> = {};
    parameters.forEach((param) => {
      if (param.defaultValue !== undefined) {
        defaults[param.name] = param.defaultValue;
      }
    });
    setLocalValues((prev) => ({ ...defaults, ...prev }));
    onChange({ ...defaults, ...localValues });
  }, [parameters]);

  const handleChange = (name: string, value: unknown) => {
    const newValues = { ...localValues, [name]: value };
    setLocalValues(newValues);
    onChange(newValues);
  };

  const renderField = (param: ReportParameter) => {
    const value = localValues[param.name];

    switch (param.type) {
      case "date":
        return (
          <TextField
            key={param.name}
            fullWidth
            type="date"
            label={param.label}
            value={value || ""}
            onChange={(e) => handleChange(param.name, e.target.value)}
            required={param.required}
            InputLabelProps={{ shrink: true }}
            sx={{
              "& .MuiOutlinedInput-root": {
                borderRadius: "12px",
              },
            }}
          />
        );

      case "daterange":
        return (
          <Box key={param.name}>
            <Typography variant="caption" sx={{ mb: 1, display: "block", color: "text.secondary" }}>
              {param.label}
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  type="date"
                  label="Start Date"
                  value={(value as { start?: string; end?: string })?.start || ""}
                  onChange={(e) =>
                    handleChange(param.name, {
                      ...(value as object || {}),
                      start: e.target.value,
                    })
                  }
                  required={param.required}
                  InputLabelProps={{ shrink: true }}
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      borderRadius: "12px",
                    },
                  }}
                />
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  type="date"
                  label="End Date"
                  value={(value as { start?: string; end?: string })?.end || ""}
                  onChange={(e) =>
                    handleChange(param.name, {
                      ...(value as object || {}),
                      end: e.target.value,
                    })
                  }
                  required={param.required}
                  InputLabelProps={{ shrink: true }}
                  sx={{
                    "& .MuiOutlinedInput-root": {
                      borderRadius: "12px",
                    },
                  }}
                />
              </Grid>
            </Grid>
          </Box>
        );

      case "select":
        return (
          <FormControl key={param.name} fullWidth required={param.required}>
            <InputLabel>{param.label}</InputLabel>
            <Select
              value={value || ""}
              onChange={(e) => handleChange(param.name, e.target.value)}
              label={param.label}
              sx={{
                borderRadius: "12px",
              }}
            >
              {param.options?.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        );

      case "multiselect":
        return (
          <FormControl key={param.name} fullWidth required={param.required}>
            <InputLabel>{param.label}</InputLabel>
            <Select
              multiple
              value={(value as string[]) || []}
              onChange={(e) => handleChange(param.name, e.target.value)}
              input={<OutlinedInput label={param.label} />}
              renderValue={(selected) => (
                <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
                  {(selected as string[]).map((val) => (
                    <Chip
                      key={val}
                      label={param.options?.find((o) => o.value === val)?.label || val}
                      size="small"
                      sx={{
                        backgroundColor: "rgba(128, 0, 32, 0.1)",
                        color: "#800020",
                      }}
                    />
                  ))}
                </Box>
              )}
              sx={{
                borderRadius: "12px",
              }}
            >
              {param.options?.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        );

      case "number":
        return (
          <TextField
            key={param.name}
            fullWidth
            type="number"
            label={param.label}
            value={value || ""}
            onChange={(e) => handleChange(param.name, parseFloat(e.target.value))}
            required={param.required}
            inputProps={{
              min: param.min,
              max: param.max,
            }}
            sx={{
              "& .MuiOutlinedInput-root": {
                borderRadius: "12px",
              },
            }}
          />
        );

      case "currency":
        return (
          <TextField
            key={param.name}
            fullWidth
            type="number"
            label={param.label}
            value={value || ""}
            onChange={(e) => handleChange(param.name, parseFloat(e.target.value))}
            required={param.required}
            InputProps={{
              startAdornment: <InputAdornment position="start">$</InputAdornment>,
            }}
            sx={{
              "& .MuiOutlinedInput-root": {
                borderRadius: "12px",
              },
            }}
          />
        );

      case "checkbox":
        return (
          <FormControlLabel
            key={param.name}
            control={
              <Checkbox
                checked={!!value}
                onChange={(e) => handleChange(param.name, e.target.checked)}
              />
            }
            label={param.label}
          />
        );

      case "text":
      default:
        return (
          <TextField
            key={param.name}
            fullWidth
            label={param.label}
            value={value || ""}
            onChange={(e) => handleChange(param.name, e.target.value)}
            required={param.required}
            placeholder={param.placeholder}
            sx={{
              "& .MuiOutlinedInput-root": {
                borderRadius: "12px",
              },
            }}
          />
        );
    }
  };

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      {parameters.map((param) => (
        <Box key={param.name}>{renderField(param)}</Box>
      ))}
    </Box>
  );
}
