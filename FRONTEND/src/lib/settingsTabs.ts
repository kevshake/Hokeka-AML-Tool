import { isPlatformAdmin, type UserAccessContext } from "./userAccess";
import { canViewDataSettings } from "./dataResidency";

export interface SettingsTabDef {
  id: string;
  label: string;
}

/** Settings tabs visible for the authenticated user (platform vs PSP tenant). */
export function getSettingsTabsForUser(user: UserAccessContext): SettingsTabDef[] {
  const isPspUser = !!user && (user.pspId ?? 0) > 0;
  if (isPspUser) {
    // Data residency tabs come last so the Billing / Webhooks indices stay stable.
    return [
      { id: "billing", label: "Billing" },
      { id: "webhooks", label: "Webhooks" },
      ...(canViewDataSettings(user)
        ? [
            { id: "data-retention", label: "Data Retention" },
            { id: "data-sharing", label: "Support & Data Sharing" },
          ]
        : []),
    ];
  }

  const platformOperator = isPlatformAdmin(user);
  return [
    { id: "theme", label: "PSP Theme" },
    ...(platformOperator ? [{ id: "system", label: "System Settings" }] : []),
    ...(platformOperator ? [{ id: "platform-admin", label: "Platform Admin" }] : []),
    ...(platformOperator ? [{ id: "ai", label: "Hokeka AI" }] : []),
  ];
}

export function canAccessAiSettingsTab(user: UserAccessContext): boolean {
  return isPlatformAdmin(user);
}

export function settingsTabIndex(tabs: SettingsTabDef[], tabId: string): number {
  return tabs.findIndex((tab) => tab.id === tabId);
}
