import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Role, Psp, Permission, PERMISSION_LABELS, PERMISSION_CATEGORIES } from "../../types/userManagement";
import TwBadge from "../../components/Common/TwBadge";
import { Loader2, Plus, Edit, Trash2, X, ChevronDown, ChevronRight, ShieldAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import TwSnackbar from "../../components/Common/TwSnackbar";

const ADMIN_ROLES = new Set(["SUPER_ADMIN", "ADMIN"]);

export default function RolesTab() {
    const queryClient = useQueryClient();
    const { user: currentUser } = useAuth();
    const isAdmin = !!currentUser?.role?.name && ADMIN_ROLES.has(currentUser.role.name);
    const [openDialog, setOpenDialog] = useState(false);
    const [editingRole, setEditingRole] = useState<Role | null>(null);
    const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
    const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set());
    const [formData, setFormData] = useState({
        name: "", description: "", pspId: "", permissions: [] as Permission[],
    });
    // W32-1 fix: replaced the native alert() on delete failure with the app's own snackbar.
    const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: "success" | "error" }>({
        open: false, message: "", severity: "error",
    });

    const apiFetch = async (input: string, init: RequestInit = {}) => {
        const response = await fetch(input, {
            ...init, credentials: "include",
            headers: { "Content-Type": "application/json", ...(init.headers || {}) },
        });
        if (!response.ok) {
            let message = `${response.status} ${response.statusText}`;
            try { const body = await response.clone().json(); message = body.message || body.error || JSON.stringify(body); }
            catch { try { const txt = await response.text(); if (txt) message = txt; } catch { /* ignore */ } }
            const err = new Error(message); (err as any).status = response.status; throw err;
        }
        return response;
    };

    const { data: roles, isLoading } = useQuery<Role[]>({
        queryKey: ["roles"],
        queryFn: async () => (await apiFetch("/api/v1/roles")).json(),
    });

    const { data: psps } = useQuery<Psp[]>({
        queryKey: ["psps"],
        queryFn: async () => { const response = await fetch("/api/v1/psps"); if (!response.ok) throw new Error("Failed to fetch PSPs"); return response.json(); },
    });

    const saveRoleMutation = useMutation({
        mutationFn: async (roleData: any) => {
            const url = editingRole ? `/api/v1/roles/${editingRole.id}` : "/api/v1/roles";
            return (await apiFetch(url, { method: editingRole ? "PUT" : "POST", body: JSON.stringify(roleData) })).json();
        },
        onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["roles"] }); handleCloseDialog(); },
    });

    const deleteRoleMutation = useMutation({
        mutationFn: async (roleId: number) => { await apiFetch(`/api/v1/roles/${roleId}`, { method: "DELETE" }); },
        onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["roles"] }); },
        onError: () => { setSnackbar({ open: true, message: "Failed to delete role. Please try again.", severity: "error" }); },
    });

    const handleOpenDialog = (role?: Role) => {
        if (role) {
            setEditingRole(role);
            const scopedPspId = role.psp?.pspId ?? role.psp?.id ?? (role as any).pspId;
            setFormData({
                name: role.name, description: role.description,
                pspId: scopedPspId != null ? String(scopedPspId) : "",
                permissions: role.permissions ?? [],
            });
        } else {
            setEditingRole(null);
            setFormData({ name: "", description: "", pspId: "", permissions: [] });
        }
        setOpenDialog(true);
    };

    const handleCloseDialog = () => { setOpenDialog(false); setEditingRole(null); setExpandedCategories(new Set()); };

    const handleSave = () => {
        if (!formData.name) return;
        saveRoleMutation.mutate({
            name: formData.name, description: formData.description,
            pspId: formData.pspId ? parseInt(formData.pspId) : null, permissions: formData.permissions,
        });
    };

    const handleDelete = (roleId: number) => setDeleteConfirmId(roleId);
    const handleConfirmDelete = () => { if (deleteConfirmId !== null) { deleteRoleMutation.mutate(deleteConfirmId); setDeleteConfirmId(null); } };

    const handlePermissionToggle = (permission: Permission) => {
        setFormData((prev) => ({
            ...prev, permissions: prev.permissions.includes(permission)
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
        setExpandedCategories(prev => { const next = new Set(prev); if (next.has(cat)) next.delete(cat); else next.add(cat); return next; });
    };

    // Defense-in-depth: role/permission management is admin-only (backend-enforced too).
    if (!isAdmin) {
        return (
            <div className="flex items-center gap-2 rounded-lg border border-amber-700/30 bg-amber-900/20 px-4 py-3 text-sm text-amber-200">
                <ShieldAlert size={16} /> Role and permission management requires an administrator role.
            </div>
        );
    }

    return (
        <div>
            <div className="mb-3 flex justify-end">
                <button onClick={() => handleOpenDialog()} className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800">
                    <Plus size={14} /> Create Role
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
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Role Name</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Description</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Scope</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Permissions</th>
                                    <th className="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-white/5">
                                {roles && roles.length > 0 ? roles.map((role) => {
                                    const scoped = !!role.psp;
                                    const scopeLabel = role.psp ? (role.psp.legalName || (role.psp as any).name || role.psp.pspCode || (role.psp as any).code || "PSP") : "System";
                                    return (
                                        <tr key={role.id} className="transition-colors hover:bg-white/[0.02]">
                                            <td className="whitespace-nowrap px-4 py-3 text-sm font-medium text-white"><Link to={`/records/ROLE/${role.id}`} className="hover:text-gold">{role.name}</Link></td>
                                            <td className="whitespace-nowrap px-4 py-3 text-sm text-white/80">{role.description}</td>
                                            <td className="whitespace-nowrap px-4 py-3"><TwBadge variant={scoped ? "warning" : "info"}>{scopeLabel}</TwBadge></td>
                                            <td className="whitespace-nowrap px-4 py-3 text-sm text-glass-muted">{(role.permissions?.length ?? 0)} permission{(role.permissions?.length ?? 0) !== 1 ? "s" : ""}</td>
                                            <td className="whitespace-nowrap px-4 py-3">
                                                <div className="flex items-center gap-1">
                                                    <button onClick={() => handleOpenDialog(role)} className="rounded p-1 text-burgundy-400 transition-colors hover:bg-white/10"><Edit size={16} /></button>
                                                    <button onClick={() => handleDelete(role.id)} className="rounded p-1 text-red-400 transition-colors hover:bg-white/10"><Trash2 size={16} /></button>
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                }) : (
                                    <tr><td colSpan={5} className="px-4 py-8 text-center text-sm text-glass-muted">No roles found</td></tr>
                                )}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>

            {/* Create/Edit Role Dialog */}
            {openDialog && (
                <>
                    <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" onClick={handleCloseDialog} />
                    <div className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2">
                        <div className="overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-2)] shadow-2xl">
                            <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
                                <h3 className="text-lg font-semibold text-white">{editingRole ? "Edit Role" : "Create New Role"}</h3>
                                <button onClick={handleCloseDialog} className="rounded p-1 text-glass-muted hover:bg-white/10"><X size={18} /></button>
                            </div>
                            <div className="max-h-[32rem] space-y-4 overflow-y-auto px-6 py-4">
                                {saveRoleMutation.isError && (
                                    <div className="rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">
                                {(saveRoleMutation.error as Error)?.message || "Failed to save role. Please try again."}
                            </div>
                                )}
                                <div>
                                    <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Role Name</label>
                                    <input value={formData.name} onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                                        placeholder="e.g., Compliance Officer, Analyst"
                                        className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                                </div>
                                <div>
                                    <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Description</label>
                                    <textarea value={formData.description} onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                                        rows={2} placeholder="Brief description of this role's responsibilities"
                                        className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                                </div>
                                <div>
                                    <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">PSP Scope (Optional)</label>
                                    <select value={formData.pspId} onChange={(e) => setFormData({ ...formData, pspId: e.target.value })}
                                        className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700">
                                        <option value="">System Role (Global)</option>
                                        {psps?.map((psp) => {
                                            const id = (psp as any).pspId ?? (psp as any).id;
                                            if (id == null) return null;
                                            const label = (psp as any).legalName || (psp as any).name || (psp as any).pspCode || (psp as any).code || `PSP ${id}`;
                                            return <option key={id} value={String(id)}>{label}</option>;
                                        })}
                                    </select>
                                </div>
                                <div>
                                    <p className="mb-2 text-sm font-semibold text-white">Permissions ({formData.permissions.length} selected)</p>
                                    {Object.entries(PERMISSION_CATEGORIES).map(([category, categoryPermissions]) => {
                                        const allSelected = categoryPermissions.every((p) => formData.permissions.includes(p));
                                        const someSelected = categoryPermissions.some((p) => formData.permissions.includes(p));
                                        const isExpanded = expandedCategories.has(category);
                                        return (
                                            <div key={category} className="mb-2 overflow-hidden rounded-lg border border-white/10">
                                                <button onClick={() => toggleCategory(category)} className="flex w-full items-center gap-2 bg-[var(--ink)] px-3 py-2 text-left text-xs font-semibold text-white transition-colors hover:bg-[var(--ink)]">
                                                    {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                                                    <input type="checkbox" checked={allSelected}
                                                        ref={(el) => { if (el) el.indeterminate = someSelected && !allSelected; }}
                                                        onChange={() => handleCategoryToggle(category, categoryPermissions)}
                                                        onClick={(e) => e.stopPropagation()}
                                                        className="rounded border-white/20 bg-white/5 accent-gold text-gold focus:ring-gold" />
                                                    <span>{category}</span>
                                                </button>
                                                {isExpanded && (
                                                    <div className="space-y-1 border-t border-white/10 px-3 py-2">
                                                        {categoryPermissions.map((permission) => (
                                                            <label key={permission} className="flex items-center gap-2 text-sm text-white/80">
                                                                <input type="checkbox" checked={formData.permissions.includes(permission)}
                                                                    onChange={() => handlePermissionToggle(permission)}
                                                                    className="rounded border-white/20 bg-white/5 accent-gold text-gold focus:ring-gold" />
                                                                {PERMISSION_LABELS[permission]}
                                                            </label>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                            <div className="flex justify-end gap-2 border-t border-white/10 px-6 py-3">
                                <button onClick={handleCloseDialog} className="rounded-lg border border-white/10 px-4 py-1.5 text-xs text-white transition-colors hover:bg-white/5">Cancel</button>
                                <button onClick={handleSave} disabled={saveRoleMutation.isPending || !formData.name}
                                    className="flex items-center gap-1.5 rounded-lg bg-burgundy-700 px-4 py-1.5 text-xs font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-30">
                                    {saveRoleMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : null}
                                    {saveRoleMutation.isPending ? "Saving..." : "Save"}
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
                            <div className="border-b border-white/10 px-6 py-4"><h3 className="text-lg font-semibold text-white">Delete Role</h3></div>
                            <div className="px-6 py-4 text-sm text-white/80">Are you sure you want to delete this role? Users with this role will need to be reassigned.</div>
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
