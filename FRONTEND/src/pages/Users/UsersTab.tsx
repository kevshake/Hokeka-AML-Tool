import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { User } from "../../types/userManagement";
import { useUsers, useRoles, useAllPsps } from "../../features/api/queries";
import { Plus, Edit, Trash2, ToggleLeft, ToggleRight, Loader2, Eye, ShieldAlert } from "lucide-react";
import TwBadge from "../../components/Common/TwBadge";
import TwPagination from "../../components/Common/TwPagination";
import TwSnackbar from "../../components/Common/TwSnackbar";
import { useAuth } from "../../contexts/AuthContext";

const ADMIN_ROLES = new Set(["SUPER_ADMIN", "ADMIN"]);

export default function UsersTab() {
    const queryClient = useQueryClient();
    const { user: currentUser } = useAuth();
    const isAdmin = !!currentUser?.role?.name && ADMIN_ROLES.has(currentUser.role.name);
    const [openDialog, setOpenDialog] = useState(false);
    const [editingUser, setEditingUser] = useState<User | null>(null);
    const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
    const [formData, setFormData] = useState({
        username: "", email: "", firstName: "", lastName: "", password: "",
        roleId: "", pspId: "", enabled: true,
    });

    const [page, setPage] = useState({ index: 0, size: 25 });
    // W32-1 fix: replaced native alert() calls with the app's own snackbar.
    const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
        open: false, message: "", severity: "error",
    });

    const { data: usersPage, isLoading } = useUsers({ page: page.index, size: page.size });
    const users = usersPage?.content || [];
    const { data: roles } = useRoles();
    const { data: psps } = useAllPsps();

    const saveUserMutation = useMutation({
        mutationFn: async (userData: any) => {
            const url = editingUser ? `/api/v1/users/${editingUser.id}` : "/api/v1/users";
            const method = editingUser ? "PUT" : "POST";
            const response = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(userData),
            });
            if (!response.ok) throw new Error("Failed to save user");
            return response.json();
        },
        onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["users"] }); handleCloseDialog(); },
    });

    const deleteUserMutation = useMutation({
        mutationFn: async (userId: number) => {
            const response = await fetch(`/api/v1/users/${userId}`, { method: "DELETE" });
            if (!response.ok) throw new Error("Failed to delete user");
        },
        onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["users"] }); },
        onError: () => { setSnackbar({ open: true, message: "Failed to delete user. Please try again.", severity: "error" }); },
    });

    const toggleUserMutation = useMutation({
        mutationFn: async ({ userId, enabled }: { userId: number; enabled: boolean }) => {
            const response = await fetch(`/api/v1/users/${userId}/toggle`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ enabled }),
            });
            if (!response.ok) throw new Error("Failed to toggle user status");
            return response.json();
        },
        onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["users"] }); },
        onError: () => { setSnackbar({ open: true, message: "Failed to update user status.", severity: "error" }); },
    });

    const handleOpenDialog = (user?: User) => {
        if (user) {
            setEditingUser(user);
            setFormData({
                username: user.username, email: user.email,
                firstName: user.firstName, lastName: user.lastName, password: "",
                roleId: user.role.id.toString(),
                pspId: (() => { const id = user.psp?.pspId ?? user.psp?.id; return id != null ? String(id) : ""; })(),
                enabled: user.enabled,
            });
        } else {
            setEditingUser(null);
            setFormData({ username: "", email: "", firstName: "", lastName: "", password: "", roleId: "", pspId: "", enabled: true });
        }
        setOpenDialog(true);
    };

    const handleCloseDialog = () => { setOpenDialog(false); setEditingUser(null); };

    const handleSave = () => {
        if (!formData.username || !formData.firstName || !formData.lastName || !formData.email || !formData.roleId || (!editingUser && !formData.password)) return;
        const userData: any = {
            username: formData.username, email: formData.email,
            firstName: formData.firstName, lastName: formData.lastName,
            roleId: parseInt(formData.roleId), pspId: formData.pspId ? parseInt(formData.pspId) : null,
            enabled: formData.enabled,
        };
        if (!editingUser || formData.password) userData.password = formData.password;
        saveUserMutation.mutate(userData);
    };

    const handleDelete = (userId: number) => setDeleteConfirmId(userId);
    const handleConfirmDelete = () => { if (deleteConfirmId !== null) { deleteUserMutation.mutate(deleteConfirmId); setDeleteConfirmId(null); } };
    const handleToggleEnabled = (userId: number, currentStatus: boolean) => toggleUserMutation.mutate({ userId, enabled: !currentStatus });

    // Defense-in-depth: user administration is admin-only. The backend enforces this on every
    // endpoint; this guard stops non-admins from seeing/triggering create/delete/role-change.
    if (!isAdmin) {
        return (
            <div className="flex items-center gap-2 rounded-lg border border-amber-700/30 bg-amber-900/20 px-4 py-3 text-sm text-amber-200">
                <ShieldAlert size={16} /> User administration requires an administrator role.
            </div>
        );
    }

    return (
        <div>
            <div className="mb-3 flex justify-end">
                <button onClick={() => handleOpenDialog()} className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800">
                    <Plus size={14} /> Add User
                </button>
            </div>

            <div className="overflow-hidden rounded-lg border border-white/10 bg-[var(--surface-2)]">
                <div className="overflow-auto" style={{ maxHeight: "calc(100vh - 320px)" }}>
                    {isLoading ? (
                        <div className="flex items-center justify-center py-8"><Loader2 size={24} className="animate-spin text-glass-muted" /></div>
                    ) : (
                        <table className="w-full border-collapse">
                            <thead className="sticky top-0 z-10">
                                <tr className="border-b border-white/10 bg-[var(--surface-2)]">
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Username</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Name</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Email</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Role</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">PSP</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Status</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Created</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-white/5">
                                {users.length > 0 ? users.map((user: any) => (
                                    <tr key={user.id} className="transition-colors hover:bg-white/[0.02]">
                                        <td className="whitespace-nowrap px-4 py-3 text-sm font-medium text-white">{user.username}</td>
                                        <td className="whitespace-nowrap px-4 py-3 text-sm text-white">{user.firstName} {user.lastName}</td>
                                        <td className="whitespace-nowrap px-4 py-3 text-sm text-white/80">{user.email}</td>
                                        <td className="whitespace-nowrap px-4 py-3"><TwBadge variant="info">{user.role.name}</TwBadge></td>
                                        <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">{user.psp?.name || "System"}</td>
                                        <td className="whitespace-nowrap px-4 py-3"><TwBadge variant={user.enabled ? "success" : "default"}>{user.enabled ? "Active" : "Disabled"}</TwBadge></td>
                                        <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">{new Date(user.createdAt).toLocaleDateString()}</td>
                                        <td className="whitespace-nowrap px-4 py-3">
                                            <div className="flex items-center gap-1">
                                                <Link to={`/records/USER/${user.id}`} title="Trace record" className="rounded p-1 text-sky-400 transition-colors hover:bg-white/10"><Eye size={16} /></Link>
                                                <button onClick={() => handleOpenDialog(user)} className="rounded p-1 text-burgundy-400 transition-colors hover:bg-white/10"><Edit size={16} /></button>
                                                <button onClick={() => handleToggleEnabled(user.id, user.enabled)} className={`rounded p-1 transition-colors hover:bg-white/10 ${user.enabled ? "text-amber-400" : "text-emerald-400"}`}>
                                                    {user.enabled ? <ToggleLeft size={16} /> : <ToggleRight size={16} />}
                                                </button>
                                                <button onClick={() => handleDelete(user.id)} className="rounded p-1 text-red-400 transition-colors hover:bg-white/10"><Trash2 size={16} /></button>
                                            </div>
                                        </td>
                                    </tr>
                                )) : (
                                    <tr><td colSpan={8} className="px-4 py-8 text-center text-sm text-glass-muted">No users found</td></tr>
                                )}
                            </tbody>
                        </table>
                    )}
                </div>
                <TwPagination
                    page={page.index} totalPages={Math.ceil((usersPage?.totalElements || 0) / page.size) || 1}
                    totalCount={usersPage?.totalElements || 0} rowsPerPage={page.size}
                    onPageChange={(p) => setPage(prev => ({ ...prev, index: p }))}
                    onRowsPerPageChange={(s) => setPage({ index: 0, size: s })}
                />
            </div>

            {/* Create/Edit User Dialog */}
            {openDialog && (
                <>
                    <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={handleCloseDialog} />
                    <div className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2">
                        <div className="overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-2)] shadow-2xl">
                            <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
                                <h3 className="text-lg font-semibold text-white">{editingUser ? "Edit User" : "Create New User"}</h3>
                                <button onClick={handleCloseDialog} className="rounded p-1 text-glass-muted hover:bg-white/10">✕</button>
                            </div>
                            <div className="max-h-[28rem] space-y-4 overflow-y-auto px-6 py-4">
                                {saveUserMutation.isError && (
                                    <div className="rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">Failed to save user. Please try again.</div>
                                )}
                                <div>
                                    <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Username</label>
                                    <input value={formData.username} onChange={(e) => setFormData({ ...formData, username: e.target.value })} disabled={!!editingUser}
                                        className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white disabled:opacity-50 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                                </div>
                                <div className="grid grid-cols-2 gap-3">
                                    <div>
                                        <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">First Name</label>
                                        <input value={formData.firstName} onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                                            className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                                    </div>
                                    <div>
                                        <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Last Name</label>
                                        <input value={formData.lastName} onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                                            className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                                    </div>
                                </div>
                                <div>
                                    <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Email</label>
                                    <input type="email" value={formData.email} onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                                        className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                                </div>
                                <div>
                                    <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">{editingUser ? "New Password (leave blank to keep current)" : "Password"}</label>
                                    <input type="password" value={formData.password} onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                                        className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                                </div>
                                {[ 
                                    { label: "Role", value: formData.roleId, onChange: (v: string) => setFormData({ ...formData, roleId: v }), options: [{ value: "", label: "Select..." }, ...(roles || []).map((r: any) => ({ value: r.id.toString(), label: `${r.name}${r.psp ? ` (${r.psp.name})` : " (System)"}` }))] },
                                    { label: "PSP (Optional)", value: formData.pspId, onChange: (v: string) => setFormData({ ...formData, pspId: v }), options: [{ value: "", label: "None (System User)" }, ...(psps || []).map((p: any) => ({ value: String(p.id), label: p.name }))] },
                                ].map((field) => (
                                    <div key={field.label}>
                                        <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">{field.label}</label>
                                        <select value={field.value} onChange={(e) => field.onChange(e.target.value)}
                                            className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700">
                                            {field.options.map((o: any) => <option key={o.value} value={o.value}>{o.label}</option>)}
                                        </select>
                                    </div>
                                ))}
                                <label className="flex items-center gap-2 text-sm text-white">
                                    <input type="checkbox" checked={formData.enabled} onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                                        className="rounded border-white/20 bg-white/5 accent-gold text-gold focus:ring-gold" />
                                    Enabled
                                </label>
                            </div>
                            <div className="flex justify-end gap-2 border-t border-white/10 px-6 py-3">
                                <button onClick={handleCloseDialog} className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white transition-colors hover:bg-white/5">Cancel</button>
                                <button onClick={handleSave} disabled={saveUserMutation.isPending}
                                    className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-4 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-30">
                                    {saveUserMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : null}
                                    {saveUserMutation.isPending ? "Saving..." : "Save"}
                                </button>
                            </div>
                        </div>
                    </div>
                </>
            )}

            {/* Delete Confirmation Dialog */}
            {deleteConfirmId !== null && (
                <>
                    <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={() => setDeleteConfirmId(null)} />
                    <div className="fixed left-1/2 top-1/2 z-50 w-full max-w-sm -translate-x-1/2 -translate-y-1/2">
                        <div className="overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-2)] shadow-2xl">
                            <div className="border-b border-white/10 px-6 py-4">
                                <h3 className="text-lg font-semibold text-white">Delete User</h3>
                            </div>
                            <div className="px-6 py-4 text-sm text-white/80">Are you sure you want to permanently delete this user? This action cannot be undone.</div>
                            <div className="flex justify-end gap-2 border-t border-white/10 px-6 py-3">
                                <button onClick={() => setDeleteConfirmId(null)} className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white transition-colors hover:bg-white/5">Cancel</button>
                                <button onClick={handleConfirmDelete} className="rounded-lg bg-red-700 px-4 py-1.5 text-xs font-medium text-white transition-colors hover:bg-red-800">Delete</button>
                            </div>
                        </div>
                    </div>
                </>
            )}

            <TwSnackbar
                open={snackbar.open}
                message={snackbar.message}
                severity={snackbar.severity}
                onClose={() => setSnackbar(prev => ({ ...prev, open: false }))}
            />
        </div>
    );
}
