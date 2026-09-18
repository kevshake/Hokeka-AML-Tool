import { useEffect, useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Role, Permission, PERMISSION_LABELS, PERMISSION_CATEGORIES } from "../../types/userManagement";
import TwBadge from "../../components/Common/TwBadge";
import GlassModal from "../../components/Common/GlassModal";
import GlassButton from "../../components/Common/GlassButton";
import { Loader2, Plus, Edit, Trash2, ChevronDown, ChevronRight, ShieldAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import TwSnackbar from "../../components/Common/TwSnackbar";
import { apiClient } from "../../lib/apiClient";
import {
  canManageRoles,
  filterPspsForUser,
  isPlatformAdmin,
  lockedPspId,
  pspOptionId,
  pspOptionLabel,
} from "../../lib/userAccess";

export default function RolesTab() {
  const queryClient = useQueryClient();
  const { user: currentUser } = useAuth();
  const platformAdmin = isPlatformAdmin(currentUser);
  const tenantPspId = lockedPspId(currentUser);

  const [openDialog, setOpenDialog] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set());
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    pspId: "",
    permissions: [] as Permission[],
  });
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false,
    message: "",
    severity: "error",
  });

  const { data: roles, isLoading } = useQuery<Role[]>({
    queryKey: ["roles"],
    queryFn: () => apiClient.get<Role[]>("roles"),
  });

  const { data: allPsps } = useQuery({
    queryKey: ["psps"],
    queryFn: () => apiClient.get<Array<{ id?: number; pspId?: number; name?: string; legalName?: string }>>("psps"),
  });

  const scopedPsps = useMemo(
    () => filterPspsForUser(currentUser, allPsps),
    [allPsps, currentUser],
  );

  useEffect(() => {
    if (tenantPspId != null && !editingRole && openDialog) {
      setFormData((current) => ({ ...current, pspId: String(tenantPspId) }));
    }
  }, [tenantPspId, editingRole, openDialog]);

  const saveRoleMutation = useMutation({
    mutationFn: async (roleData: Record<string, unknown>) => {
      return editingRole
        ? apiClient.put(`roles/${editingRole.id}`, roleData)
        : apiClient.post("roles", roleData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["roles"] });
      handleCloseDialog();
    },
  });

  const deleteRoleMutation = useMutation({
    mutationFn: async (roleId: number) => apiClient.delete(`roles/${roleId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["roles"] });
    },
    onError: () => {
      setSnackbar({ open: true, message: "Failed to delete role. Please try again.", severity: "error" });
    },
  });

  const handleOpenDialog = (role?: Role) => {
    if (role) {
      setEditingRole(role);
      const scopedPspId = role.psp?.pspId ?? role.psp?.id ?? (role as { pspId?: number }).pspId;
      setFormData({
        name: role.name,
        description: role.description,
        pspId: scopedPspId != null ? String(scopedPspId) : tenantPspId != null ? String(tenantPspId) : "",
        permissions: role.permissions ?? [],
      });
    } else {
      setEditingRole(null);
      setFormData({
        name: "",
        description: "",
        pspId: tenantPspId != null ? String(tenantPspId) : "",
        permissions: [],
      });
    }
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
    setEditingRole(null);
    setExpandedCategories(new Set());
  };

  const handleSave = () => {
    if (!formData.name) return;
    const resolvedPspId = tenantPspId ?? (formData.pspId ? parseInt(formData.pspId, 10) : null);
    saveRoleMutation.mutate({
      name: formData.name,
      description: formData.description,
      pspId: resolvedPspId,
      permissions: formData.permissions,
    });
  };

  const handlePermissionToggle = (permission: Permission) => {
    setFormData((prev) => ({
      ...prev,
      permissions: prev.permissions.includes(permission)
        ? prev.permissions.filter((p) => p !== permission)
        : [...prev.permissions, permission],
    }));
  };

  const handleCategoryToggle = (_category: string, categoryPermissions: Permission[]) => {
    const allSelected = categoryPermissions.every((p) => formData.permissions.includes(p));
    setFormData((prev) => ({
      ...prev,
      permissions: allSelected
        ? prev.permissions.filter((p) => !categoryPermissions.includes(p))
        : [...new Set([...prev.permissions, ...categoryPermissions])],
    }));
  };

  const toggleCategory = (cat: string) => {
    setExpandedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(cat)) next.delete(cat);
      else next.add(cat);
      return next;
    });
  };

  if (!canManageRoles(currentUser)) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-ink">
        <ShieldAlert size={16} className="text-warning" />
        Role and permission management requires an administrator role.
      </div>
    );
  }

  const pspSelectOptions = [
    ...(platformAdmin ? [{ value: "", label: "System Role (Global)" }] : []),
    ...scopedPsps
      .filter((psp) => pspOptionId(psp) > 0)
      .map((psp) => ({ value: String(pspOptionId(psp)), label: pspOptionLabel(psp) })),
  ];

  return (
    <div>
      <div className="mb-3 flex justify-end">
        <button type="button" onClick={() => handleOpenDialog()} className="hokeka-btn-primary">
          <Plus size={14} /> Create Role
        </button>
      </div>

      <div className="hokeka-table-wrap" style={{ maxHeight: "calc(100vh - 320px)" }}>
        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 size={24} className="animate-spin text-glass-muted" />
          </div>
        ) : (
          <table className="hokeka-table">
            <thead>
              <tr>
                <th>Role Name</th>
                <th>Description</th>
                <th>Scope</th>
                <th>Permissions</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {roles && roles.length > 0 ? (
                roles.map((role) => {
                  const scoped = !!role.psp;
                  const scopeLabel = role.psp
                    ? pspOptionLabel(role.psp as Parameters<typeof pspOptionLabel>[0])
                    : "System";
                  return (
                    <tr key={role.id}>
                      <td className="font-medium">
                        <Link to={`/records/ROLE/${role.id}`} className="text-gold hover:underline">
                          {role.name}
                        </Link>
                      </td>
                      <td className="text-ink-muted">{role.description}</td>
                      <td>
                        <TwBadge variant={scoped ? "warning" : "info"}>{scopeLabel}</TwBadge>
                      </td>
                      <td className="text-glass-muted">
                        {(role.permissions?.length ?? 0)} permission
                        {(role.permissions?.length ?? 0) !== 1 ? "s" : ""}
                      </td>
                      <td>
                        <div className="flex items-center gap-1">
                          <button
                            type="button"
                            onClick={() => handleOpenDialog(role)}
                            className="rounded p-1 text-gold transition-colors hover:bg-burgundy-800"
                          >
                            <Edit size={16} />
                          </button>
                          <button
                            type="button"
                            onClick={() => setDeleteConfirmId(role.id)}
                            className="rounded p-1 text-danger transition-colors hover:bg-burgundy-800"
                          >
                            <Trash2 size={16} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-glass-muted">
                    No roles found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      <GlassModal
        open={openDialog}
        onClose={handleCloseDialog}
        title={editingRole ? "Edit Role" : "Create New Role"}
        maxWidth="md"
        bodyClassName="max-h-[32rem] space-y-4"
        footer={
          <>
            <GlassButton variant="default" size="sm" onClick={handleCloseDialog}>
              Cancel
            </GlassButton>
            <button
              type="button"
              onClick={handleSave}
              disabled={saveRoleMutation.isPending || !formData.name}
              className="hokeka-btn-primary disabled:opacity-45"
            >
              {saveRoleMutation.isPending ? (
                <>
                  <Loader2 size={14} className="animate-spin" /> Saving…
                </>
              ) : (
                "Save"
              )}
            </button>
          </>
        }
      >
        {saveRoleMutation.isError && (
          <div className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-ink">
            {(saveRoleMutation.error as Error)?.message || "Failed to save role. Please try again."}
          </div>
        )}
        <div>
          <label className="hokeka-field-label">Role Name</label>
          <input
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder="e.g., Compliance Officer, Analyst"
            className="hokeka-field mt-1"
          />
        </div>
        <div>
          <label className="hokeka-field-label">Description</label>
          <textarea
            value={formData.description}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            rows={2}
            placeholder="Brief description of this role's responsibilities"
            className="hokeka-field mt-1 min-h-[4rem] resize-y"
          />
        </div>
        {platformAdmin ? (
          <div>
            <label className="hokeka-field-label">PSP Scope (Optional)</label>
            <select
              value={formData.pspId}
              onChange={(e) => setFormData({ ...formData, pspId: e.target.value })}
              className="hokeka-field mt-1"
            >
              {pspSelectOptions.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
        ) : tenantPspId != null ? (
          <div>
            <label className="hokeka-field-label">PSP Scope</label>
            <p className="mt-1 text-sm text-ink">
              {currentUser?.psp?.name || pspOptionLabel(scopedPsps[0] ?? { pspId: tenantPspId })}
            </p>
            <p className="mt-0.5 text-xs text-ink-muted">Roles are scoped to your organization.</p>
          </div>
        ) : null}
        <div>
          <p className="mb-2 text-sm font-semibold text-ink">
            Permissions ({formData.permissions.length} selected)
          </p>
          {Object.entries(PERMISSION_CATEGORIES).map(([category, categoryPermissions]) => {
            const allSelected = categoryPermissions.every((p) => formData.permissions.includes(p));
            const someSelected = categoryPermissions.some((p) => formData.permissions.includes(p));
            const isExpanded = expandedCategories.has(category);
            return (
              <div key={category} className="mb-2 overflow-hidden rounded-lg border border-hairline">
                <button
                  type="button"
                  onClick={() => toggleCategory(category)}
                  className="flex w-full items-center gap-2 bg-burgundy-900 px-3 py-2 text-left text-xs font-semibold text-ink transition-colors hover:bg-burgundy-800"
                >
                  {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                  <input
                    type="checkbox"
                    checked={allSelected}
                    ref={(el) => {
                      if (el) el.indeterminate = someSelected && !allSelected;
                    }}
                    onChange={() => handleCategoryToggle(category, categoryPermissions)}
                    onClick={(e) => e.stopPropagation()}
                    className="rounded border-hairline accent-gold"
                  />
                  <span>{category}</span>
                </button>
                {isExpanded && (
                  <div className="space-y-1 border-t border-hairline px-3 py-2">
                    {categoryPermissions.map((permission) => (
                      <label key={permission} className="flex items-center gap-2 text-sm text-ink-muted">
                        <input
                          type="checkbox"
                          checked={formData.permissions.includes(permission)}
                          onChange={() => handlePermissionToggle(permission)}
                          className="rounded border-hairline accent-gold"
                        />
                        {PERMISSION_LABELS[permission]}
                      </label>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </GlassModal>

      <GlassModal
        open={deleteConfirmId !== null}
        onClose={() => setDeleteConfirmId(null)}
        title="Delete Role"
        maxWidth="sm"
        footer={
          <>
            <GlassButton variant="default" size="sm" onClick={() => setDeleteConfirmId(null)}>
              Cancel
            </GlassButton>
            <button
              type="button"
              onClick={() => {
                if (deleteConfirmId !== null) {
                  deleteRoleMutation.mutate(deleteConfirmId);
                  setDeleteConfirmId(null);
                }
              }}
              className="hokeka-btn-primary !border-danger !bg-danger"
            >
              Delete
            </button>
          </>
        }
      >
        <p className="text-sm text-ink-muted">
          Are you sure you want to delete this role? Users with this role will need to be reassigned.
        </p>
      </GlassModal>

      <TwSnackbar
        open={snackbar.open}
        message={snackbar.message}
        severity={snackbar.severity}
        onClose={() => setSnackbar((prev) => ({ ...prev, open: false }))}
      />
    </div>
  );
}
