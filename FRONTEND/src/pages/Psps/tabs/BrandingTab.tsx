import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Check, Loader2, Save } from "lucide-react";
import { apiClient } from "../../../lib/apiClient";
import { useAuth } from "../../../contexts/AuthContext";
import { useTheme } from "../../../contexts/ThemeContext";
import TwSnackbar from "../../../components/Common/TwSnackbar";

interface BrandingTabProps { pspId: string }

interface PspTheme {
  pspId: number;
  pspName: string;
  brandingTheme?: string;
  primaryColor?: string;
  secondaryColor?: string;
  accentColor?: string;
  logoUrl?: string;
  fontFamily?: string;
  buttonRadius?: string;
}

type ThemePresets = Record<string, {
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
}>;

const DEFAULT_THEME: PspTheme = {
  pspId: 0,
  pspName: "",
  brandingTheme: "default",
  // Literal hexes: these feed <input type="color"> and are persisted to the API.
  primaryColor: "#d3b371",
  secondaryColor: "#75b7ab",
  accentColor: "#e2b25d",
  logoUrl: "",
  fontFamily: "'DM Sans', 'Inter', system-ui, sans-serif",
  buttonRadius: "3px",
};

export default function BrandingTab({ pspId }: BrandingTabProps) {
  const { refreshUser } = useAuth();
  const { updateTheme } = useTheme();
  const [form, setForm] = useState<PspTheme>(DEFAULT_THEME);
  const [toast, setToast] = useState({ open: false, severity: "success" as "success" | "error", message: "" });

  const themeQuery = useQuery({
    queryKey: ["settings", "psps", pspId, "theme"],
    queryFn: () => apiClient.get<PspTheme>(`settings/psps/${pspId}/theme`),
    enabled: !!pspId,
  });
  const presetsQuery = useQuery({
    queryKey: ["settings", "themes", "presets"],
    queryFn: () => apiClient.get<ThemePresets>("settings/themes/presets"),
  });

  useEffect(() => {
    if (themeQuery.data) setForm({ ...DEFAULT_THEME, ...themeQuery.data });
  }, [themeQuery.data]);

  const save = useMutation({
    mutationFn: () => apiClient.put<PspTheme>(`settings/psps/${pspId}/theme`, form),
    onSuccess: async (saved) => {
      setForm({ ...DEFAULT_THEME, ...saved });
      updateTheme(saved);
      await refreshUser();
      setToast({ open: true, severity: "success", message: "Organization branding saved." });
    },
    onError: () => setToast({ open: true, severity: "error", message: "Branding could not be saved." }),
  });

  const applyPreset = (name: string) => {
    const preset = presetsQuery.data?.[name];
    if (!preset) return;
    setForm((current) => ({ ...current, ...preset, brandingTheme: name }));
  };

  const inputClass = "w-full rounded-lg border border-white/10 bg-[var(--surface-3)] px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-burgundy-700";
  const labelClass = "mb-1 block text-[11px] font-semibold uppercase tracking-wider text-glass-muted";

  if (themeQuery.isLoading) return <div className="flex justify-center py-10"><Loader2 className="animate-spin text-gold" /></div>;
  if (themeQuery.isError) return <p className="text-sm text-red-300">Branding settings could not be loaded.</p>;

  return (
    <div className="max-w-4xl">
      <div className="mb-5">
        <h4 className="text-base font-semibold text-white">Organization Branding</h4>
        <p className="mt-1 text-xs text-glass-muted">Colors and logo are applied to your users after save.</p>
      </div>

      <div className="mb-6 flex flex-wrap gap-2">
        {Object.entries(presetsQuery.data ?? {}).map(([name, preset]) => (
          <button key={name} type="button" onClick={() => applyPreset(name)}
            className={`flex h-9 items-center gap-2 rounded-lg border px-3 text-xs capitalize transition-colors ${form.brandingTheme === name ? "border-gold text-white" : "border-white/10 text-glass-muted hover:border-white/25 hover:text-white"}`}>
            <span className="h-4 w-4 rounded-sm" style={{ backgroundColor: preset.primaryColor }} />
            {name}
            {form.brandingTheme === name && <Check size={14} className="text-gold" />}
          </button>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {(["primaryColor", "secondaryColor", "accentColor"] as const).map((field) => (
          <label key={field}>
            <span className={labelClass}>{field.replace("Color", " color")}</span>
            <div className="flex h-10 items-center gap-2 rounded-lg border border-white/10 bg-[var(--surface-3)] px-2">
              <input type="color" value={form[field] ?? "var(--ink)"}
                onChange={(event) => setForm((current) => ({ ...current, [field]: event.target.value, brandingTheme: "custom" }))}
                className="h-7 w-9 cursor-pointer border-0 bg-transparent p-0" />
              <span className="text-xs uppercase text-white">{form[field]}</span>
            </div>
          </label>
        ))}
      </div>

      <div className="mt-5 grid gap-4 md:grid-cols-2">
        <label><span className={labelClass}>Logo URL</span><input value={form.logoUrl ?? ""} onChange={(event) => setForm((current) => ({ ...current, logoUrl: event.target.value }))} className={inputClass} placeholder="https://..." /></label>
        <label><span className={labelClass}>Font family</span><select value={form.fontFamily ?? "Inter, system-ui, sans-serif"} onChange={(event) => setForm((current) => ({ ...current, fontFamily: event.target.value }))} className={inputClass}>
          <option value="Inter, system-ui, sans-serif">Inter</option><option value="Roboto, sans-serif">Roboto</option><option value="Arial, sans-serif">Arial</option><option value="Georgia, serif">Georgia</option>
        </select></label>
        <label><span className={labelClass}>Button radius</span><select value={form.buttonRadius ?? "8px"} onChange={(event) => setForm((current) => ({ ...current, buttonRadius: event.target.value }))} className={inputClass}>
          <option value="0px">Square</option><option value="4px">Compact</option><option value="8px">Standard</option><option value="12px">Soft</option>
        </select></label>
        <div className="flex min-h-16 items-center gap-3 rounded-lg border border-white/10 bg-white/[0.03] px-3">
          <div className="flex h-11 w-20 items-center justify-center overflow-hidden rounded bg-white/5">
            {form.logoUrl ? <img src={form.logoUrl} alt="Organization logo preview" className="max-h-10 max-w-[72px] object-contain" /> : <span className="text-[10px] text-glass-muted">No logo</span>}
          </div>
          <span className="text-xs text-glass-muted">Logo preview</span>
        </div>
      </div>

      <div className="mt-6 flex justify-end">
        <button type="button" onClick={() => save.mutate()} disabled={save.isPending}
          className="flex items-center gap-2 rounded-lg bg-burgundy-700 px-4 py-2 text-sm font-medium text-white hover:bg-burgundy-800 disabled:opacity-50">
          {save.isPending ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />} Save Branding
        </button>
      </div>
      <TwSnackbar open={toast.open} message={toast.message} severity={toast.severity} onClose={() => setToast((current) => ({ ...current, open: false }))} />
    </div>
  );
}
