import { cn } from "../../lib/utils";

export interface SettingsTabItem {
  id: string;
  label: string;
}

interface SettingsTabBarProps {
  tabs: SettingsTabItem[];
  activeIndex: number;
  onChange: (index: number) => void;
  className?: string;
}

/** Glass-aligned tab strip for Settings / admin shells. */
export default function SettingsTabBar({
  tabs,
  activeIndex,
  onChange,
  className,
}: SettingsTabBarProps) {
  return (
    <div
      className={cn(
        "hokeka-settings-tabs -mx-0.5 mb-5 flex gap-0 overflow-x-auto border-b border-hairline",
        className,
      )}
      role="tablist"
      aria-label="Settings sections"
    >
      {tabs.map((tab, index) => {
        const active = index === activeIndex;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={active}
            id={`settings-tab-${tab.id}`}
            aria-controls={`settings-panel-${tab.id}`}
            onClick={() => onChange(index)}
            className={cn(
              "hokeka-settings-tab whitespace-nowrap px-4 py-2.5 text-xs font-semibold tracking-wide transition-colors",
              active
                ? "border-b-2 border-gold text-gold"
                : "border-b-2 border-transparent text-ink-muted hover:text-ink",
            )}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
