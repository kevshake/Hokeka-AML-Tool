import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { User } from "../../types/userManagement";
import { useUsers, useRoles, useAllPsps } from "../../features/api/queries";
import { Plus, Edit, Trash2, ToggleLeft, ToggleRight, Loader2, Eye, ShieldAlert, KeyRound } from "lucide-react";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import TwSnackbar from "../../components/Common/TwSnackbar";
import GlassModal from "../../components/Common/GlassModal";
import GlassButton from "../../components/Common/GlassButton";
import { TwSelect } from "../../components/Common/TwInput";
import { useAuth } from "../../contexts/AuthContext";
import { apiClient } from "../../lib/apiClient";
import {
  canManageUsers,
  filterPspsForUser,
  isPlatformAdmin,
  isPspAdmin,
  lockedPspId,
  pspOptionId,
  pspOptionLabel,
} from "../../lib/userAccess";

export default function UsersTab() {
  const queryClient = useQueryClient();
  const { user: currentUser } = useAuth();
  const platformAdmin = isPlatformAdmin(currentUser);
  const pspAdmin = isPspAdmin(currentUser);
  const tenantPspId = lockedPspId(currentUser);

  const [openDialog, setOpenDialog] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
  const [pspFilter, setPspFilter] = useState("");
  const [formData, setFormData] = useState({
    username: "",
    email: "",
    firstName: "",
    lastName: "",
    password: "",
    roleId: "",
    pspId: "",
    enabled: true,
  });

  const [page, setPage] = useState({ index: 0, size: 25 });
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
    open: false,
    message: "",
    severity: "error",
  });

  const filterPspId = platformAdmin && pspFilter ? Number(pspFilter) : undefined;
  const { data: usersPage, isLoading } = useUsers({
    page: page.index,
    size: page.size,
    pspId: filterPspId,
  });
  const users = usersPage?.content || [];
  const { data: roles } = useRoles();
  const { data: allPsps } = useAllPsps();

  const scopedPsps = useMemo(
    () => filterPspsForUser(currentUser, allPsps),
    [allPsps, currentUser],
  );

  const tenantPsps = useMemo(
    () =>
      (allPsps ?? []).filter((psp) => {
        const id = pspOptionId(psp);
        const code = (psp as { pspCode?: string }).pspCode || "";
        return id > 0 && code !== "HOKEKA_PLATFORM";
      }),
    [allPsps],
  );

  const assignableRoles = useMemo(() => {
    if (!roles?.length) return [];
    if (platformAdmin) return roles;
    if (tenantPspId == null) return roles;
    return roles.filter((role) => {
      const rolePspId = role.psp?.pspId ?? role.psp?.id ?? (role as { pspId?: number }).pspId;
      return rolePspId == null || Number(rolePspId) === tenantPspId;
    });
  }, [platformAdmin, roles, tenantPspId]);

  useEffect(() => {
    if (tenantPspId != null && !editingUser && openDialog) {
      setFormData((current) => ({
        ...current,
        pspId: String(tenantPspId),
      }));
    }
  }, [tenantPspId, editingUser, openDialog]);

  const saveUserMutation = useMutation({
    mutationFn: async (userData: Record<string, unknown>) => {
      return editingUser
        ? apiClient.put(`users/${editingUser.id}`, userData)
        : apiClient.post("users", userData);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      handleCloseDialog();
    },
  });

  const deleteUserMutation = useMutation({
    mutationFn: async (userId: number) => apiClient.delete(`users/${userId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: () => {
      setSnackbar({ open: true, message: "Failed to delete user. Please try again.", severity: "error" });
    },
  });

  const toggleUserMutation = useMutation({
    mutationFn: async ({ userId, enabled }: { userId: number; enabled: boolean }) =>
      apiClient.patch(`users/${userId}/toggle`, { enabled }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: () => {
      setSnackbar({ open: true, message: "Failed to update user status.", severity: "error" });
    },
  });

  const resetPasswordMutation = useMutation({
    mutationFn: async (identifier: string) =>
      apiClient.post("auth/password-reset/request", { identifier }),
    onSuccess: () => {
      setSnackbar({ open: true, message: "Password reset link sent.", severity: "success" });
    },
    onError: () => {
      setSnackbar({ open: true, message: "Failed to send password reset link.", severity: "error" });
    },
  });

  const handleOpenDialog = (user?: User) => {
    if (user) {
      setEditingUser(user);
      const userPspId = user.psp?.pspId ?? user.psp?.id;
      setFormData({
        username: user.username,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        password: "",
        roleId: user.role.id.toString(),
        pspId: userPspId != null ? String(userPspId) : tenantPspId != null ? String(tenantPspId) : "",
        enabled: user.enabled,
      });
    } else {
      setEditingUser(null);
      setFormData({
        username: "",
        email: "",
        firstName: "",
        lastName: "",
        password: "",
        roleId: "",
        pspId: tenantPspId != null ? String(tenantPspId) : "",
        enabled: true,
      });
    }
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
    setEditingUser(null);
  };

  const handleSave = () => {
    if (
      !formData.username ||
      !formData.firstName ||
      !formData.lastName ||
      !formData.email ||
      !formData.roleId ||
      (!editingUser && !formData.password)
    ) {
      return;
    }

    const resolvedPspId = tenantPspId ?? (formData.pspId ? parseInt(formData.pspId, 10) : null);
    const userData: Record<string, unknown> = {
      username: formData.username,
      email: formData.email,
      firstName: formData.firstName,
      lastName: formData.lastName,
      roleId: parseInt(formData.roleId, 10),
      pspId: resolvedPspId,
      enabled: formData.enabled,
    };
    if (!editingUser || formData.password) userData.password = formData.password;
    saveUserMutation.mutate(userData);
  };

  const handleConfirmDelete = () => {
    if (deleteConfirmId !== null) {
      deleteUserMutation.mutate(deleteConfirmId);
      setDeleteConfirmId(null);
    }
  };

  if (!canManageUsers(currentUser)) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-ink">
        <ShieldAlert size={16} className="text-warning" />
        User administration requires an administrator role or MANAGE_USERS permission.
      </div>
    );
  }

  const pspFilterOptions = [
    { value: "", label: "All PSPs" },
    ...tenantPsps.map((psp) => ({
      value: String(pspOptionId(psp)),
      label: pspOptionLabel(psp),
    })),
  ];

  const pspSelectOptions = [
    ...(platformAdmin ? [{ value: "", label: "None (System User)" }] : []),
    ...scopedPsps
      .filter((psp) => pspOptionId(psp) > 0)
      .map((psp) => ({ value: String(pspOptionId(psp)), label: pspOptionLabel(psp) })),
  ];

  const roleSelectOptions = [
    { value: "", label: "Select role…" },
    ...assignableRoles.map((role) => ({
      value: role.id.toString(),
      label: `${role.name}${role.psp ? ` (${pspOptionLabel(role.psp as Parameters<typeof pspOptionLabel>[0])})` : " (System)"}`,
    })),
  ];

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        {platformAdmin && (
          <div className="min-w-[200px]">
            <TwSelect
              label="Filter by PSP"
              value={pspFilter}
              options={pspFilterOptions}
              onChange={(event) => {
                setPspFilter(event.target.value);
                setPage((current) => ({ ...current, index: 0 }));
              }}
            />
          </div>
        )}
        <button type="button" onClick={() => handleOpenDialog()} className="ml-auto hokeka-btn-primary">
          <Plus size={14} /> Add User
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
                <th>Username</th>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>PSP</th>
                <th>Status</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.length > 0 ? (
                users.map((user: User) => (
                  <tr key={user.id}>
                    <td className="font-medium">{user.username}</td>
                    <td>
                      {user.firstName} {user.lastName}
                    </td>
                    <td className="text-ink-muted">{user.email}</td>
                    <td>
                      <TwBadge variant="info">{user.role.name}</TwBadge>
                    </td>
                    <td className="text-glass-muted">{user.psp?.name || "System"}</td>
                    <td>
                      <TwBadge variant={user.enabled ? "success" : "default"}>
                        {user.enabled ? "Active" : "Disabled"}
                      </TwBadge>
                    </td>
                    <td className="text-glass-muted">
                      {new Date(user.createdAt).toLocaleDateString()}
                    </td>
                    <td>
                      <div className="flex items-center gap-1">
                        <Link
                          to={`/records/USER/${user.id}`}
                          title="Trace record"
                          className="rounded p-1 text-info transition-colors hover:bg-burgundy-800"
                        >
                          <Eye size={16} />
                        </Link>
                        <button
                          type="button"
                          onClick={() => handleOpenDialog(user)}
                          className="rounded p-1 text-gold transition-colors hover:bg-burgundy-800"
                        >
                          <Edit size={16} />
                        </button>
                        <button
                          type="button"
                          onClick={() => resetPasswordMutation.mutate(user.email || user.username)}
                          disabled={resetPasswordMutation.isPending}
                          title="Send password reset link"
                          className="rounded p-1 text-info transition-colors hover:bg-burgundy-800 disabled:opacity-50"
                        >
                          <KeyRound size={16} />
                        </button>
                        <button
                          type="button"
                          onClick={() =>
                            toggleUserMutation.mutate({ userId: user.id, enabled: !user.enabled })
                          }
                          className={`rounded p-1 transition-colors hover:bg-burgundy-800 ${
                            user.enabled ? "text-warning" : "text-success"
                          }`}
                        >
                          {user.enabled ? <ToggleLeft size={16} /> : <ToggleRight size={16} />}
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleteConfirmId(user.id)}
                          className="rounded p-1 text-danger transition-colors hover:bg-burgundy-800"
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-glass-muted">
                    No users found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      <TwPagination
        page={page.index}
        totalPages={Math.ceil((usersPage?.totalElements || 0) / page.size) || 1}
        totalCount={usersPage?.totalElements || 0}
        rowsPerPage={page.size}
        onPageChange={(p) => setPage((prev) => ({ ...prev, index: p }))}
        onRowsPerPageChange={(s) => setPage({ index: 0, size: s })}
      />

      <GlassModal
        open={openDialog}
        onClose={handleCloseDialog}
        title={editingUser ? "Edit User" : "Create New User"}
        maxWidth="md"
        bodyClassName="space-y-4"
        footer={
          <>
            <GlassButton variant="default" size="sm" onClick={handleCloseDialog}>
              Cancel
            </GlassButton>
            <button
              type="button"
              onClick={handleSave}
              disabled={saveUserMutation.isPending}
              className="hokeka-btn-primary disabled:opacity-45"
            >
              {saveUserMutation.isPending ? (
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
        {saveUserMutation.isError && (
          <div className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-ink">
            Failed to save user. Please try again.
          </div>
        )}
        <div>
          <label className="hokeka-field-label">Username</label>
          <input
            value={formData.username}
            onChange={(e) => setFormData({ ...formData, username: e.target.value })}
            disabled={!!editingUser}
            className="hokeka-field mt-1 disabled:opacity-50"
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="hokeka-field-label">First Name</label>
            <input
              value={formData.firstName}
              onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
              className="hokeka-field mt-1"
            />
          </div>
          <div>
            <label className="hokeka-field-label">Last Name</label>
            <input
              value={formData.lastName}
              onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
              className="hokeka-field mt-1"
            />
          </div>
        </div>
        <div>
          <label className="hokeka-field-label">Email</label>
          <input
            type="email"
            value={formData.email}
            onChange={(e) => setFormData({ ...formData, email: e.target.value })}
            className="hokeka-field mt-1"
          />
        </div>
        <div>
          <label className="hokeka-field-label">
            {editingUser ? "New Password (leave blank to keep current)" : "Password"}
          </label>
          <input
            type="password"
            value={formData.password}
            onChange={(e) => setFormData({ ...formData, password: e.target.value })}
            className="hokeka-field mt-1"
          />
        </div>
        <TwSelect
          label="Role"
          value={formData.roleId}
          options={roleSelectOptions}
          onChange={(e) => setFormData({ ...formData, roleId: e.target.value })}
        />
        {platformAdmin ? (
          <TwSelect
            label="PSP (Optional)"
            value={formData.pspId}
            options={pspSelectOptions}
            onChange={(e) => setFormData({ ...formData, pspId: e.target.value })}
          />
        ) : pspAdmin && tenantPspId != null ? (
          <div>
            <label className="hokeka-field-label">PSP</label>
            <p className="mt-1 text-sm text-ink">
              {currentUser?.psp?.name || pspOptionLabel(scopedPsps[0] ?? { pspId: tenantPspId })}
            </p>
            <p className="mt-0.5 text-xs text-ink-muted">Users are scoped to your organization.</p>
          </div>
        ) : null}
        <label className="flex items-center gap-2 text-sm text-ink">
          <input
            type="checkbox"
            checked={formData.enabled}
            onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
            className="rounded border-hairline bg-burgundy-900 accent-gold"
          />
          Enabled
        </label>
      </GlassModal>

      <GlassModal
        open={deleteConfirmId !== null}
        onClose={() => setDeleteConfirmId(null)}
        title="Delete User"
        maxWidth="sm"
        footer={
          <>
            <GlassButton variant="default" size="sm" onClick={() => setDeleteConfirmId(null)}>
              Cancel
            </GlassButton>
            <button type="button" onClick={handleConfirmDelete} className="hokeka-btn-primary !border-danger !bg-danger">
              Delete
            </button>
          </>
        }
      >
        <p className="text-sm text-ink-muted">
          Are you sure you want to permanently delete this user? This action cannot be undone.
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
