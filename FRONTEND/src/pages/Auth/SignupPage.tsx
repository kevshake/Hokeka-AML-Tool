import { useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { Eye, EyeOff, UserPlus, Loader2 } from "lucide-react";

export default function SignupPage() {
    const [formData, setFormData] = useState({
        username: "", email: "", firstName: "", lastName: "", password: "", confirmPassword: "",
    });
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [isLoading, setIsLoading] = useState(false);

    const handleChange = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
        setFormData(prev => ({ ...prev, [field]: e.target.value }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");
        setSuccess("");

        if (!formData.username || !formData.email || !formData.password) {
            setError("Please fill in all required fields.");
            return;
        }
        if (formData.password !== formData.confirmPassword) {
            setError("Passwords do not match.");
            return;
        }

        setIsLoading(true);
        try {
            const response = await fetch("/api/v1/auth/register", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(formData),
            });
            if (!response.ok) {
                const err = await response.json().catch(() => null);
                throw new Error(err?.message || `Registration failed (${response.status})`);
            }
            setSuccess("Account created successfully! You can now log in.");
            setFormData({ username: "", email: "", firstName: "", lastName: "", password: "", confirmPassword: "" });
        } catch (err: any) {
            setError(err.message || "An unexpected error occurred.");
        } finally {
            setIsLoading(false);
        }
    };

    const inputClass = "w-full rounded-lg border border-white/10 bg-[var(--surface-2)] px-3 py-2.5 text-sm text-white placeholder:text-white/30 focus:outline-none focus:ring-1 focus:ring-burgundy-700";
    const labelClass = "text-[11px] font-semibold uppercase tracking-wider text-glass-muted";

    return (
        <div className="flex min-h-screen items-center justify-center bg-[var(--ink)] px-4">
            <div className="w-full max-w-md">
                <div className="mb-6 text-center">
                    <div className="mb-2 flex items-center justify-center gap-2">
                        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-burgundy-700">
                            <span className="text-lg font-bold text-white">H</span>
                        </div>
                        <span className="text-xl font-bold text-white">Hokeka AML</span>
                    </div>
                    <h2 className="text-lg font-semibold text-white">Create Account</h2>
                    <p className="text-sm text-glass-muted">Register for a new platform account</p>
                </div>

                <div className="rounded-xl border border-white/10 bg-[var(--surface-2)] p-6 shadow-2xl">
                    <form onSubmit={handleSubmit} className="space-y-4">
                        {error && <div className="rounded-lg border border-red-700/30 bg-red-900/30 px-4 py-3 text-sm text-red-200">{error}</div>}
                        {success && <div className="rounded-lg border border-emerald-700/30 bg-emerald-900/30 px-4 py-3 text-sm text-emerald-200">{success}</div>}

                        <div>
                            <label className={labelClass}>Username</label>
                            <input value={formData.username} onChange={handleChange("username")} placeholder="Choose a username" className={inputClass} />
                        </div>
                        <div>
                            <label className={labelClass}>Email</label>
                            <input type="email" value={formData.email} onChange={handleChange("email")} placeholder="you@example.com" className={inputClass} />
                        </div>
                        <div className="grid grid-cols-2 gap-3">
                            <div>
                                <label className={labelClass}>First Name</label>
                                <input value={formData.firstName} onChange={handleChange("firstName")} className={inputClass} />
                            </div>
                            <div>
                                <label className={labelClass}>Last Name</label>
                                <input value={formData.lastName} onChange={handleChange("lastName")} className={inputClass} />
                            </div>
                        </div>
                        <div>
                            <label className={labelClass}>Password</label>
                            <div className="relative">
                                <input type={showPassword ? "text" : "password"} value={formData.password} onChange={handleChange("password")} className={inputClass + " pr-10"} />
                                <button type="button" onClick={() => setShowPassword(!showPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-glass-muted">
                                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                                </button>
                            </div>
                        </div>
                        <div>
                            <label className={labelClass}>Confirm Password</label>
                            <div className="relative">
                                <input type={showConfirmPassword ? "text" : "password"} value={formData.confirmPassword} onChange={handleChange("confirmPassword")} className={inputClass + " pr-10"} />
                                <button type="button" onClick={() => setShowConfirmPassword(!showConfirmPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-glass-muted">
                                    {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                                </button>
                            </div>
                        </div>

                        <button type="submit" disabled={isLoading}
                            className="flex w-full items-center justify-center gap-2 rounded-lg bg-burgundy-700 py-2.5 text-sm font-medium text-white transition-colors hover:bg-burgundy-800 disabled:opacity-50">
                            {isLoading ? <Loader2 size={16} className="animate-spin" /> : <UserPlus size={16} />}
                            {isLoading ? "Creating account..." : "Create Account"}
                        </button>
                    </form>
                </div>

                <p className="mt-4 text-center text-xs text-glass-muted">
                    Already have an account?{" "}
                    <RouterLink to="/login" className="text-burgundy-400 hover:underline">Log in</RouterLink>
                </p>
            </div>
        </div>
    );
}