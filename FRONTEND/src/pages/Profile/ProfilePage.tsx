import { useState, useEffect } from "react";
import { useCurrentUser } from "../../features/api/queries";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import { PERMISSION_LABELS, Permission } from "../../types/userManagement";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import TwBadge from "../../components/Common/TwBadge";
import { User, Lock, Save, History, Eye, EyeOff, Loader2 } from "lucide-react";

function TabPanel({ children, value, index }: { children?: React.ReactNode; value: number; index: number }) {
  return value === index ? <div className="p-4">{children}</div> : null;
}

export default function ProfilePage() {
  const queryClient = useQueryClient();
  const { data: user, isLoading } = useCurrentUser();
  const [tabValue, setTabValue] = useState(0);
  const [showPassword, setShowPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [profileData, setProfileData] = useState({ firstName: "", lastName: "", email: "" });
  const [passwordData, setPasswordData] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });

  const updateProfileMutation = useMutation({
    mutationFn: async (data: any) => apiClient.put("users/me", data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["user", "me"] }); },
  });

  const changePasswordMutation = useMutation({
    mutationFn: async (data: any) => apiClient.put("users/me/password", data),
    onSuccess: () => { setPasswordData({ currentPassword: "", newPassword: "", confirmPassword: "" }); },
  });

  useEffect(() => {
    if (user) setProfileData({ firstName: user.firstName || "", lastName: user.lastName || "", email: user.email || "" });
  }, [user]);

  const handleUpdateProfile = () => updateProfileMutation.mutate(profileData);

  const handleChangePassword = () => {
    if (passwordData.newPassword !== passwordData.confirmPassword) return;
    changePasswordMutation.mutate({ currentPassword: passwordData.currentPassword, newPassword: passwordData.newPassword });
  };

  const getInitials = () => {
    if (!user) return "?";
    return `${user.firstName?.[0] || ""}${user.lastName?.[0] || ""}`.toUpperCase();
  };

  const tabs = [
    { label: "Personal Information", icon: User },
    { label: "Security", icon: Lock },
    { label: "Permissions", icon: History },
  ];

  if (isLoading) return <div className="p-4 text-sm text-glass-muted">Loading profile...</div>;

  return (
    <HokekaPageShell title="My Profile" subtitle="Personal information, security, and permissions" noCard>
      {/* Profile Header */}
      <div className="mb-4 rounded-lg border border-white/10 bg-[var(--surface-2)] p-6">
        <div className="flex items-center gap-4">
          <div className="flex h-20 w-20 items-center justify-center rounded-full bg-burgundy-700 text-2xl font-bold text-white">
            {getInitials()}
          </div>
          <div className="min-w-0 flex-1">
            <h3 className="text-lg font-semibold text-white">{user?.firstName} {user?.lastName}</h3>
            <p className="text-sm text-glass-muted">@{user?.username}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <TwBadge variant="info">{user?.role?.name || "No Role"}</TwBadge>
              <TwBadge variant={user?.psp ? "warning" : "default"}>{user?.psp?.name || "System User"}</TwBadge>
              <TwBadge variant={user?.enabled ? "success" : "danger"}>{user?.enabled ? "Active" : "Disabled"}</TwBadge>
            </div>
          </div>
          <div className="shrink-0 text-right">
            <p className="text-xs text-glass-muted">Member Since</p>
            <p className="text-sm font-semibold text-white">{user?.createdAt ? new Date(user.createdAt).toLocaleDateString() : "N/A"}</p>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="rounded-lg border border-white/10 bg-[var(--surface-2)]">
        <div className="flex border-b border-white/10">
          {tabs.map((tab, idx) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.label}
                onClick={() => setTabValue(idx)}
                className={`flex items-center gap-2 px-5 py-3 text-sm font-medium transition-colors ${
                  tabValue === idx
                    ? "border-b-2 border-burgundy-700 text-burgundy-400"
                    : "text-glass-muted hover:text-white"
                }`}
              >
                <Icon size={16} /> {tab.label}
              </button>
            );
          })}
        </div>

        {/* Personal Information Tab */}
        <TabPanel value={tabValue} index={0}>
          {updateProfileMutation.isSuccess && (
            <div className="mb-4 rounded-lg border border-emerald-700/30 bg-emerald-900/30 px-4 py-3 text-sm text-emerald-200">Profile updated successfully!</div>
          )}
          {updateProfileMutation.isError && (
            <div className="mb-4 rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">Failed to update profile. Please try again.</div>
          )}
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Username</label>
              <input value={user?.username || ""} disabled
                className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white/50 focus:outline-none" />
              <p className="mt-1 text-xs text-glass-muted">Username cannot be changed</p>
            </div>
            <div>
              <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Email</label>
              <input type="email" value={profileData.email}
                onChange={(e) => setProfileData({ ...profileData, email: e.target.value })}
                className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
            </div>
            <div>
              <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">First Name</label>
              <input value={profileData.firstName}
                onChange={(e) => setProfileData({ ...profileData, firstName: e.target.value })}
                className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
            </div>
            <div>
              <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Last Name</label>
              <input value={profileData.lastName}
                onChange={(e) => setProfileData({ ...profileData, lastName: e.target.value })}
                className="mt-1 w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
            </div>
          </div>
          <hr className="my-4 border-white/10" />
          <button onClick={handleUpdateProfile} disabled={updateProfileMutation.isPending}
            className="flex items-center gap-2 rounded-lg bg-burgundy-700 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-50">
            {updateProfileMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
            {updateProfileMutation.isPending ? "Saving..." : "Save Changes"}
          </button>
        </TabPanel>

        {/* Security Tab */}
        <TabPanel value={tabValue} index={1}>
          <h4 className="mb-4 text-base font-semibold text-white">Change Password</h4>
          {changePasswordMutation.isSuccess && (
            <div className="mb-4 rounded-lg border border-emerald-700/30 bg-emerald-900/30 px-4 py-3 text-sm text-emerald-200">Password changed successfully!</div>
          )}
          {changePasswordMutation.isError && (
            <div className="mb-4 rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">Failed to change password. Please check your current password.</div>
          )}
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="md:col-span-2">
              <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Current Password</label>
              <div className="relative mt-1">
                <input type={showPassword ? "text" : "password"} value={passwordData.currentPassword}
                  onChange={(e) => setPasswordData({ ...passwordData, currentPassword: e.target.value })}
                  className="w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 pr-10 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                <button onClick={() => setShowPassword(!showPassword)} className="absolute right-2 top-1/2 -translate-y-1/2 text-glass-muted">
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>
            <div>
              <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">New Password</label>
              <div className="relative mt-1">
                <input type={showNewPassword ? "text" : "password"} value={passwordData.newPassword}
                  onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })}
                  className="w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 pr-10 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700" />
                <button onClick={() => setShowNewPassword(!showNewPassword)} className="absolute right-2 top-1/2 -translate-y-1/2 text-glass-muted">
                  {showNewPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>
            <div>
              <label className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Confirm New Password</label>
              <div className="relative mt-1">
                <input type={showConfirmPassword ? "text" : "password"} value={passwordData.confirmPassword}
                  onChange={(e) => setPasswordData({ ...passwordData, confirmPassword: e.target.value })}
                  className={`w-full rounded-lg border px-3 py-2 pr-10 text-sm text-white focus:outline-none focus:ring-1 ${
                    passwordData.newPassword !== passwordData.confirmPassword && passwordData.confirmPassword !== ""
                      ? "border-red-500 focus:ring-red-500"
                      : "border-white/10 focus:ring-burgundy-700"
                  }`} />
                <button onClick={() => setShowConfirmPassword(!showConfirmPassword)} className="absolute right-2 top-1/2 -translate-y-1/2 text-glass-muted">
                  {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {passwordData.newPassword !== passwordData.confirmPassword && passwordData.confirmPassword !== "" && (
                <p className="mt-1 text-xs text-red-400">Passwords do not match</p>
              )}
            </div>
          </div>
          <hr className="my-4 border-white/10" />
          <button onClick={handleChangePassword} disabled={changePasswordMutation.isPending || !passwordData.currentPassword || !passwordData.newPassword || passwordData.newPassword !== passwordData.confirmPassword}
            className="flex items-center gap-2 rounded-lg bg-burgundy-700 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-50">
            {changePasswordMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Lock size={16} />}
            {changePasswordMutation.isPending ? "Changing..." : "Change Password"}
          </button>
        </TabPanel>

        {/* Permissions Tab */}
        <TabPanel value={tabValue} index={2}>
          <h4 className="mb-2 text-base font-semibold text-white">Your Permissions</h4>
          <p className="mb-4 text-sm text-glass-muted">These are the permissions granted to your role: <span className="font-semibold text-white">{user?.role?.name}</span></p>
          {user?.role?.permissions && user.role.permissions.length > 0 ? (
            <div className="overflow-hidden rounded-lg border border-white/10">
              <table className="w-full border-collapse">
                <thead>
                  <tr className="border-b border-white/10 bg-[var(--surface-3)]">
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Permission</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-glass-muted">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {user.role.permissions.map((permission) => (
                    <tr key={permission} className="transition-colors hover:bg-white/[0.02]">
                      <td className="px-4 py-3 text-sm text-white">{PERMISSION_LABELS[permission as Permission] || permission}</td>
                      <td className="px-4 py-3"><TwBadge variant="success">Granted</TwBadge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="rounded-lg border border-sky-700/30 bg-sky-900/30 px-4 py-3 text-sm text-sky-200">No permissions assigned to your role.</div>
          )}
        </TabPanel>
      </div>
    </HokekaPageShell>
  );
}