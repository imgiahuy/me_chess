import React from "react";
import { useNavigate } from "react-router-dom";
import { useTheme } from "../router/App";

interface ExportRequest {
    format: string;
    startDate?: string;
    endDate?: string;
    includeMoves: boolean;
    includeTimeControl: boolean;
}

interface ExportResponse {
    exportId: string;
    format: string;
    recordCount: number;
    downloadUrl: string;
    expiresAt: string;
}

export function AnalyticsExportPage() {
    const navigate = useNavigate();
    const { theme, toggleTheme } = useTheme();
    const [format, setFormat] = React.useState<string>("json");
    const [startDate, setStartDate] = React.useState<string>("");
    const [endDate, setEndDate] = React.useState<string>("");
    const [includeMoves, setIncludeMoves] = React.useState<boolean>(true);
    const [includeTimeControl, setIncludeTimeControl] = React.useState<boolean>(true);
    const [loading, setLoading] = React.useState<boolean>(false);
    const [error, setError] = React.useState<string | null>(null);
    const [exportResult, setExportResult] = React.useState<ExportResponse | null>(null);

    const handleExport = async () => {
        setLoading(true);
        setError(null);
        setExportResult(null);

        try {
            const request: ExportRequest = {
                format,
                startDate: startDate || undefined,
                endDate: endDate || undefined,
                includeMoves,
                includeTimeControl,
            };

            const response = await fetch("/v1/chess/analytics/export", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(request),
            });

            if (!response.ok) {
                throw new Error("Export failed");
            }

            const result: ExportResponse = await response.json();
            setExportResult(result);
        } catch (e) {
            setError("Failed to export analytics");
        } finally {
            setLoading(false);
        }
    };

    const handleDownload = async () => {
        if (!exportResult) return;

        try {
            const response = await fetch(exportResult.downloadUrl);
            if (!response.ok) throw new Error("Download failed");

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `analytics-${exportResult.exportId}.${exportResult.format}`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        } catch (e) {
            setError("Failed to download file");
        }
    };

    return (
        <div style={{ minHeight: "100vh", background: "var(--color-bg)" }}>
            {/* Navbar */}
            <div style={{
                display: "flex", alignItems: "center", gap: "0.75rem",
                padding: "0.75rem 1.5rem",
                background: "var(--color-surface)",
                borderBottom: "1px solid var(--color-border)",
                position: "sticky", top: 0, zIndex: 50,
            }}>
                <button onClick={() => navigate("/analytics")} className="ghost" style={{ padding: "0.3rem 0.6rem" }}>← Analytics</button>
                <span style={{ fontWeight: 700, fontSize: "0.95rem" }}>📦 Export Analytics</span>
                <div style={{ marginLeft: "auto" }}>
                    <button onClick={toggleTheme} className="ghost" style={{ padding: "0.3rem 0.55rem" }}>
                        {theme === "dark" ? "☀️" : "🌙"}
                    </button>
                </div>
            </div>

            <div style={{ padding: "1.25rem 1.5rem", maxWidth: "600px" }}>
                <div className="card" style={{ padding: "1.5rem" }}>
                    <h2 style={{ fontSize: "1.25rem", marginBottom: "1rem", color: "var(--color-text)" }}>Export Game Analytics</h2>
                    
                    {error && <div style={{ color: "var(--color-danger)", marginBottom: "1rem", padding: "0.75rem", background: "var(--color-danger-bg)", borderRadius: "0.5rem" }}>⚠ {error}</div>}

                    <div style={{ marginBottom: "1rem" }}>
                        <label style={{ display: "block", fontSize: "0.8rem", color: "var(--color-text-dim)", marginBottom: "0.4rem", fontWeight: 600 }}>Format</label>
                        <select
                            value={format}
                            onChange={(e) => setFormat(e.target.value)}
                            style={{ width: "100%", padding: "0.6rem", background: "var(--color-bg-2)", border: "1px solid var(--color-border)", borderRadius: "0.5rem", color: "var(--color-text)" }}
                        >
                            <option value="json">JSON</option>
                            <option value="csv">CSV</option>
                            <option value="pgn">PGN</option>
                        </select>
                    </div>

                    <div style={{ marginBottom: "1rem" }}>
                        <label style={{ display: "block", fontSize: "0.8rem", color: "var(--color-text-dim)", marginBottom: "0.4rem", fontWeight: 600 }}>Start Date (optional)</label>
                        <input
                            type="date"
                            value={startDate}
                            onChange={(e) => setStartDate(e.target.value)}
                            style={{ width: "100%", padding: "0.6rem", background: "var(--color-bg-2)", border: "1px solid var(--color-border)", borderRadius: "0.5rem", color: "var(--color-text)" }}
                        />
                    </div>

                    <div style={{ marginBottom: "1rem" }}>
                        <label style={{ display: "block", fontSize: "0.8rem", color: "var(--color-text-dim)", marginBottom: "0.4rem", fontWeight: 600 }}>End Date (optional)</label>
                        <input
                            type="date"
                            value={endDate}
                            onChange={(e) => setEndDate(e.target.value)}
                            style={{ width: "100%", padding: "0.6rem", background: "var(--color-bg-2)", border: "1px solid var(--color-border)", borderRadius: "0.5rem", color: "var(--color-text)" }}
                        />
                    </div>

                    <div style={{ marginBottom: "1.5rem" }}>
                        <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", fontSize: "0.85rem", color: "var(--color-text)", cursor: "pointer" }}>
                            <input
                                type="checkbox"
                                checked={includeMoves}
                                onChange={(e) => setIncludeMoves(e.target.checked)}
                                style={{ width: "1rem", height: "1rem" }}
                            />
                            Include move history
                        </label>
                        <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", fontSize: "0.85rem", color: "var(--color-text)", cursor: "pointer", marginTop: "0.5rem" }}>
                            <input
                                type="checkbox"
                                checked={includeTimeControl}
                                onChange={(e) => setIncludeTimeControl(e.target.checked)}
                                style={{ width: "1rem", height: "1rem" }}
                            />
                            Include time control info
                        </label>
                    </div>

                    <button
                        onClick={handleExport}
                        disabled={loading}
                        style={{
                            width: "100%",
                            padding: "0.75rem",
                            background: loading ? "var(--color-bg-3)" : "var(--color-teal)",
                            color: "white",
                            border: "none",
                            borderRadius: "0.5rem",
                            fontSize: "0.9rem",
                            fontWeight: 600,
                            cursor: loading ? "not-allowed" : "pointer",
                        }}
                    >
                        {loading ? "Exporting..." : "Export Analytics"}
                    </button>

                    {exportResult && (
                        <div style={{ marginTop: "1.5rem", padding: "1rem", background: "var(--color-success-bg)", borderRadius: "0.5rem", border: "1px solid var(--color-success)" }}>
                            <div style={{ fontSize: "0.85rem", color: "var(--color-success)", marginBottom: "0.5rem", fontWeight: 600 }}>✓ Export Successful</div>
                            <div style={{ fontSize: "0.8rem", color: "var(--color-text)", marginBottom: "0.5rem" }}>
                                Records: {exportResult.recordCount} | Format: {exportResult.format.toUpperCase()}
                            </div>
                            <button
                                onClick={handleDownload}
                                style={{
                                    padding: "0.5rem 1rem",
                                    background: "var(--color-teal)",
                                    color: "white",
                                    border: "none",
                                    borderRadius: "0.4rem",
                                    fontSize: "0.85rem",
                                    fontWeight: 600,
                                    cursor: "pointer",
                                }}
                            >
                                Download File
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
