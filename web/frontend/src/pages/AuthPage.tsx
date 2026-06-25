import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { login, register, setAccessToken, setRefreshToken } from "../utils/apiClient";
import { useTheme } from "../router/App";

type AuthMode = "login" | "register";

const inputStyle: React.CSSProperties = {
    display: "block",
    width: "100%",
    padding: "0.5rem 0.75rem",
    background: "var(--color-bg-2)",
    color: "var(--color-text)",
    border: "1px solid var(--color-border)",
    borderRadius: "var(--radius-sm)",
    fontSize: "0.875rem",
    fontFamily: "inherit",
    outline: "none",
    marginBottom: "0.75rem",
    boxSizing: "border-box",
};

export function AuthPage() {
    const navigate = useNavigate();
    const { theme, toggleTheme } = useTheme();
    const [mode, setMode] = useState<AuthMode>("login");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [formData, setFormData] = useState({
        username: "",
        email: "",
        password: "",
        firstName: "",
        lastName: "",
    });

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);

        try {
            if (mode === "login") {
                const response = await login(formData.username, formData.password);
                setAccessToken(response.access_token);
                setRefreshToken(response.refresh_token);
                navigate("/");
            } else {
                await register(
                    formData.username,
                    formData.email,
                    formData.password,
                    formData.firstName,
                    formData.lastName
                );
                // Auto-login after registration
                const loginResponse = await login(formData.username, formData.password);
                setAccessToken(loginResponse.access_token);
                setRefreshToken(loginResponse.refresh_token);
                navigate("/");
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : "Authentication failed");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "2rem 1rem" }}>
            {/* Theme toggle */}
            <button
                onClick={toggleTheme}
                className="ghost"
                style={{ position: "fixed", top: "1.25rem", right: "1.25rem", fontSize: "1.1rem", padding: "0.4rem 0.7rem", borderRadius: "var(--radius-sm)" }}
                title={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
            >
                {theme === "dark" ? "☀️" : "🌙"}
            </button>

            {/* Hero */}
            <div style={{ textAlign: "center", marginBottom: "2.5rem" }}>
                <div style={{ fontSize: "3.5rem", marginBottom: "0.75rem" }}>♟</div>
                <h1 style={{ fontSize: "2.5rem", fontWeight: 700, letterSpacing: "-0.04em", marginBottom: "0.5rem" }}>ME Chess</h1>
                <p style={{ color: "var(--color-text-muted)", fontSize: "1rem" }}>
                    {mode === "login" ? "Sign in to play" : "Create your account"}
                </p>
            </div>

            {/* Card */}
            <div style={{
                background: "var(--color-surface)",
                border: "1px solid var(--color-border)",
                borderRadius: "var(--radius-lg)",
                boxShadow: "var(--shadow-lg)",
                width: "100%",
                maxWidth: 420,
                overflow: "hidden",
            }}>
                {/* Card header */}
                <div style={{
                    padding: "1.25rem 1.5rem",
                    borderBottom: "1px solid var(--color-border)",
                }}>
                    <h3 style={{ margin: 0, fontSize: "1.0625rem" }}>
                        {mode === "login" ? "Sign In" : "Create Account"}
                    </h3>
                </div>

                {/* Card body */}
                <div style={{ padding: "1.5rem" }}>
                    {error && <div className="error" style={{ marginBottom: "1.25rem" }}>{error}</div>}

                    <form onSubmit={handleSubmit}>
                        {mode === "register" && (
                            <>
                                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.75rem", marginBottom: "0" }}>
                                    <div>
                                        <label style={{ display: "block", fontSize: "0.8125rem", fontWeight: 500, color: "var(--color-text-muted)", marginBottom: "0.35rem" }}>
                                            First Name
                                        </label>
                                        <input
                                            type="text"
                                            placeholder="First"
                                            value={formData.firstName}
                                            onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                                            required
                                            style={{ ...inputStyle, marginBottom: 0 }}
                                        />
                                    </div>
                                    <div>
                                        <label style={{ display: "block", fontSize: "0.8125rem", fontWeight: 500, color: "var(--color-text-muted)", marginBottom: "0.35rem" }}>
                                            Last Name
                                        </label>
                                        <input
                                            type="text"
                                            placeholder="Last"
                                            value={formData.lastName}
                                            onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                                            required
                                            style={{ ...inputStyle, marginBottom: 0 }}
                                        />
                                    </div>
                                </div>
                                <div style={{ marginTop: "0.75rem" }}>
                                    <label style={{ display: "block", fontSize: "0.8125rem", fontWeight: 500, color: "var(--color-text-muted)", marginBottom: "0.35rem" }}>
                                        Email
                                    </label>
                                    <input
                                        type="email"
                                        placeholder="you@example.com"
                                        value={formData.email}
                                        onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                                        required
                                        style={inputStyle}
                                    />
                                </div>
                            </>
                        )}

                        <div style={{ marginTop: mode === "register" ? 0 : undefined }}>
                            <label style={{ display: "block", fontSize: "0.8125rem", fontWeight: 500, color: "var(--color-text-muted)", marginBottom: "0.35rem" }}>
                                Username
                            </label>
                            <input
                                type="text"
                                placeholder="Username"
                                value={formData.username}
                                onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                                required
                                style={inputStyle}
                            />
                        </div>

                        <div>
                            <label style={{ display: "block", fontSize: "0.8125rem", fontWeight: 500, color: "var(--color-text-muted)", marginBottom: "0.35rem" }}>
                                Password
                            </label>
                            <input
                                type="password"
                                placeholder="••••••••"
                                value={formData.password}
                                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                                required
                                style={{ ...inputStyle, marginBottom: "1.5rem" }}
                            />
                        </div>

                        <button type="submit" disabled={loading} style={{ width: "100%" }}>
                            {loading ? "Processing…" : mode === "login" ? "Sign In" : "Create Account"}
                        </button>
                    </form>
                </div>

                {/* Card footer */}
                <div style={{
                    padding: "1rem 1.5rem",
                    borderTop: "1px solid var(--color-border)",
                    textAlign: "center",
                }}>
                    <button
                        type="button"
                        onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(null); }}
                        className="ghost"
                        style={{ fontSize: "0.875rem", width: "100%" }}
                    >
                        {mode === "login" ? "Don't have an account? Sign up" : "Already have an account? Sign in"}
                    </button>
                </div>
            </div>
        </div>
    );
}
