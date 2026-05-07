/**
 * Generic CRUD tab: a table + Add/Edit/Delete dialog for any PSP child entity.
 * Consumers supply: column definitions, row data, mutations, and a form renderer.
 */
import { useState } from "react";
import {
  Box,
  Typography,
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  CircularProgress,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Snackbar,
} from "@mui/material";
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
} from "@mui/icons-material";

const ACCENT = "#8B4049";

export interface ColDef {
  field: string;
  header: string;
  render?: (row: any) => React.ReactNode;
}

interface CrudTabProps {
  title: string;
  description?: string;
  columns: ColDef[];
  rows: any[];
  isLoading: boolean;
  isError: boolean;
  onCreate: (data: any) => Promise<unknown>;
  onUpdate: (id: any, data: any) => Promise<unknown>;
  onDelete: (id: any) => Promise<unknown>;
  /** Renders the form fields inside the dialog. Receives current form state + setter. */
  renderForm: (
    formData: Record<string, any>,
    setFormData: React.Dispatch<React.SetStateAction<Record<string, any>>>
  ) => React.ReactNode;
  defaultFormData: Record<string, any>;
}

export default function CrudTab({
  title,
  description,
  columns,
  rows,
  isLoading,
  isError,
  onCreate,
  onUpdate,
  onDelete,
  renderForm,
  defaultFormData,
}: CrudTabProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const [formData, setFormData] = useState<Record<string, any>>(defaultFormData);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ open: boolean; severity: "success" | "error"; message: string }>({
    open: false,
    severity: "success",
    message: "",
  });

  const openCreate = () => {
    setEditing(null);
    setFormData(defaultFormData);
    setDialogOpen(true);
  };

  const openEdit = (row: any) => {
    setEditing(row);
    setFormData({ ...row });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      if (editing?.id != null) {
        await onUpdate(editing.id, formData);
        setToast({ open: true, severity: "success", message: `${title} record updated.` });
      } else {
        await onCreate(formData);
        setToast({ open: true, severity: "success", message: `${title} record added.` });
      }
      setDialogOpen(false);
    } catch {
      setToast({ open: true, severity: "error", message: "Save failed. Please try again." });
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: any) => {
    if (!window.confirm(`Delete this ${title} record?`)) return;
    try {
      await onDelete(row.id);
      setToast({ open: true, severity: "success", message: `${title} record deleted.` });
    } catch {
      setToast({ open: true, severity: "error", message: "Delete failed." });
    }
  };

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", mb: 2 }}>
        <Box>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
            {title}
          </Typography>
          {description && (
            <Typography variant="body2" color="text.secondary">
              {description}
            </Typography>
          )}
        </Box>
        <Button
          variant="contained"
          size="small"
          startIcon={<AddIcon />}
          onClick={openCreate}
          sx={{
            backgroundColor: ACCENT,
            textTransform: "none",
            "&:hover": { backgroundColor: "#6b313a" },
          }}
        >
          Add
        </Button>
      </Box>

      {isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load {title} records.
        </Alert>
      )}

      <TableContainer
        component={Paper}
        sx={{ border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2, backgroundColor: "background.paper" }}
      >
        <Table size="small">
          <TableHead>
            <TableRow sx={{ backgroundColor: "rgba(0,0,0,0.02)" }}>
              {columns.map((col) => (
                <TableCell key={col.field} sx={{ color: "text.secondary", fontWeight: 600 }}>
                  {col.header}
                </TableCell>
              ))}
              <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={columns.length + 1} align="center" sx={{ py: 5 }}>
                  <CircularProgress size={24} sx={{ color: ACCENT }} />
                </TableCell>
              </TableRow>
            ) : rows.length > 0 ? (
              rows.map((row, idx) => (
                <TableRow key={row.id ?? idx} hover>
                  {columns.map((col) => (
                    <TableCell key={col.field}>
                      {col.render ? col.render(row) : (
                        <Typography variant="body2">{row[col.field] ?? "—"}</Typography>
                      )}
                    </TableCell>
                  ))}
                  <TableCell>
                    <IconButton
                      size="small"
                      onClick={() => openEdit(row)}
                      sx={{ color: ACCENT, mr: 0.5 }}
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => handleDelete(row)}
                      sx={{ color: "#e74c3c" }}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={columns.length + 1} align="center" sx={{ py: 6, color: "text.disabled" }}>
                  No {title} records. Click "Add" to create one.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Add / Edit Dialog */}
      <Dialog open={dialogOpen} onClose={() => !saving && setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 600 }}>
          {editing ? `Edit ${title}` : `Add ${title}`}
        </DialogTitle>
        <DialogContent dividers>
          <Box sx={{ pt: 1 }}>{renderForm(formData, setFormData)}</Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={saving}>
            Cancel
          </Button>
          <Button
            onClick={handleSave}
            variant="contained"
            disabled={saving}
            sx={{ backgroundColor: ACCENT, "&:hover": { backgroundColor: "#6b313a" } }}
          >
            {saving ? <CircularProgress size={18} sx={{ color: "white" }} /> : editing ? "Update" : "Create"}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={toast.open}
        autoHideDuration={4000}
        onClose={() => setToast((t) => ({ ...t, open: false }))}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert
          severity={toast.severity}
          onClose={() => setToast((t) => ({ ...t, open: false }))}
          variant="filled"
          sx={{ width: "100%" }}
        >
          {toast.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
