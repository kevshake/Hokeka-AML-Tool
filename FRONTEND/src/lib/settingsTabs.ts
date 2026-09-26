import { isPlatformAdmin, type UserAccessContext } from "./userAccess";

export interface SettingsTabDef {
  id: string;
  label: string;
}

/** Settings tabs visible for the authenticated user (platform vs PSP tenant). */
export function getSettingsTabsForUser(user: UserAccessContext): SettingsTabDef[] {
  const isPspUser = !!user && (user.pspId ?? 0) > 0;
  if (isPspUser) {
    return [
      { id: "billing", label: "Billing" },
      { id: "webhooks", label: "Webhooks" },
    ];
  }

  const platformOperator = isPlatformAdmin(user);
  return [
    { id: "theme", label: "PSP Theme" },
    ...(platformOperator ? [{ id: "system", label: "System Settings" }] : []),
    ...(platformOperator ? [{ id: "platform-admin", label: "Platform Admin" }] : []),
    ...(platformOperator ? [{ id: "jev", label: "Hokeka AI" }] : []),
  ];
}

export function canAccessJevSettingsTab(user: UserAccessContext): boolean {
  return isPlatformAdmin(user);
}

export function settingsTabIndex(tabs: SettingsTabDef[], tabId: string): number {
  return tabs.findIndex((tab) => tab.id === tabId);
}
