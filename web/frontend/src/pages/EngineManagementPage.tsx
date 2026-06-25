import React from "react";
import { useNavigate } from "react-router-dom";
import { useTheme } from "../router/App";

interface EngineConfig {
    name: string;
    enginePath: string;
    options: Record<string, string>;
    defaultDepth: number;
    defaultTimeMs: number;
    isRunning: boolean;
}

interface EngineStatus {
    name: string;
    isRunning: boolean;
    info?: {
        name: string;
        author: string;
    };
}

export function EngineManagementPage() {
    const navigate = useNavigate();
    const { theme, toggleTheme } = useTheme();
    const [engines, setEngines] = React.useState<EngineConfig[]>([]);
    const [engineStatuses, setEngineStatuses] = React.useState<Record<string, EngineStatus>>({});
    const [loading, setLoading] = React.useState<boolean>(true);
    const [error, setError] = React.useState<string | null>(null);

    React.useEffect(() => {
        loadEngines();
    }, []);

    const loadEngines = async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await fetch("/v1/chess/engines");
            if (!response.ok) throw new Error("Failed to load engines");
            const data = await response.json();
            setEngines(data.engines);
            
            const statuses: Record<string, EngineStatus> = {};
            data.engines.forEach((engine: EngineConfig) => {
                statuses[engine.name] = { name: engine.name, isRunning: engine.isRunning };
            });
            setEngineStatuses(statuses);
        } catch (e) {
            setError("Failed to load engine configurations");
        } finally {
            setLoading(false);
        }
    };

    const handleStartEngine = async (engineName: string) => {
        try {
            const response = await fetch(`/v1/chess/engines/${engineName}/start`, {
                method: "POST"
            });
            if (!response.ok) throw new Error("Failed to start engine");
            const data = await response.json();
            setEngineStatuses(prev => ({
                ...prev,
                [engineName]: { 
                    name: engineName, 
                    isRunning: data.isRunning, 
                    info: data.info ? { name: data.info.name, author: data.info.author } : undefined
                }
            }));
        } catch (e) {
            setError(`Failed to start ${engineName}`);
        }
    };

    const handleStopEngine = async (engineName: string) => {
        try {
            const response = await fetch(`/v1/chess/engines/${engineName}/stop`, {
                method: "POST"
            });
            if (!response.ok) throw new Error("Failed to stop engine");
            const data = await response.json();
            setEngineStatuses(prev => ({
                ...prev,
                [engineName]: { 
                    name: engineName, 
                    isRunning: data.isRunning, 
                    info: undefined
                }
            }));
        } catch (e) {
            setError(`Failed to stop ${engineName}`);
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
                <button onClick={() => navigate("/")} className="ghost" style={{ padding: "0.3rem 0.6rem" }}>← Menu</button>
                <span style={{ fontWeight: 700, fontSize: "0.95rem" }}>⚙ Engine Management</span>
                <div style={{ marginLeft: "auto" }}>
                    <button onClick={toggleTheme} className="ghost" style={{ padding: "0.3rem 0.55rem" }}>
                        {theme === "dark" ? "☀️" : "🌙"}
                    </button>
                </div>
            </div>

            <div style={{ padding: "1.25rem 1.5rem" }}>
                {loading && <div style={{ color: "var(--color-text-muted)", textAlign: "center", padding: "2rem" }}>Loading engines…</div>}
                {error && <div style={{ color: "var(--color-danger)", marginBottom: "1rem" }}>⚠ {error}</div>}

                {!loading && (
                    <>
                        {/* Info Card */}
                        <div className="card" style={{ marginBottom: "1.25rem", padding: "1rem 1.25rem" }}>
                            <h3 style={{ fontSize: "0.875rem", marginBottom: "0.5rem", color: "var(--color-text)" }}>UCI Engine Management</h3>
                            <p style={{ fontSize: "0.8rem", color: "var(--color-text-muted)", lineHeight: 1.5 }}>
                                Manage external chess engines (Stockfish) for analysis and bot play. 
                                Engines can be started/stopped on demand and configured with custom options.
                            </p>
                        </div>

                        {/* Engine Cards */}
                        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(350px, 1fr))", gap: "1rem" }}>
                            {engines.map(engine => {
                                const status = engineStatuses[engine.name];
                                return (
                                    <div key={engine.name} className="card" style={{ padding: "1.25rem" }}>
                                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "1rem" }}>
                                            <div>
                                                <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--color-text)", margin: 0 }}>
                                                    {engine.name}
                                                </h3>
                                                <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginTop: "0.25rem" }}>
                                                    {status?.isRunning ? "● Running" : "○ Stopped"}
                                                </div>
                                            </div>
                                            <span style={{
                                                padding: "0.2rem 0.5rem",
                                                background: status?.isRunning ? "var(--color-success-bg)" : "var(--color-bg-3)",
                                                borderRadius: "0.3rem",
                                                fontSize: "0.7rem",
                                                fontWeight: 600,
                                                color: status?.isRunning ? "var(--color-success)" : "var(--color-text-muted)",
                                            }}>
                                                {status?.isRunning ? "Active" : "Inactive"}
                                            </span>
                                        </div>

                                        {/* Engine Info */}
                                        {status?.info && (
                                            <div style={{ marginBottom: "1rem", padding: "0.75rem", background: "var(--color-bg-2)", borderRadius: "0.4rem" }}>
                                                <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem" }}>Engine Name</div>
                                                <div style={{ fontSize: "0.85rem", color: "var(--color-text)", fontWeight: 600 }}>{status.info.name}</div>
                                                <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem", marginTop: "0.5rem" }}>Author</div>
                                                <div style={{ fontSize: "0.85rem", color: "var(--color-text-muted)" }}>{status.info.author}</div>
                                            </div>
                                        )}

                                        {/* Configuration */}
                                        <div style={{ marginBottom: "1rem" }}>
                                            <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.5rem", fontWeight: 600 }}>Configuration</div>
                                            <div style={{ fontSize: "0.8rem", color: "var(--color-text-muted)", marginBottom: "0.25rem" }}>
                                                Depth: <span style={{ color: "var(--color-text)", fontWeight: 600 }}>{engine.defaultDepth}</span>
                                            </div>
                                            <div style={{ fontSize: "0.8rem", color: "var(--color-text-muted)", marginBottom: "0.25rem" }}>
                                                Time: <span style={{ color: "var(--color-text)", fontWeight: 600 }}>{engine.defaultTimeMs}ms</span>
                                            </div>
                                            <div style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                                                Options: <span style={{ color: "var(--color-text)", fontWeight: 600 }}>{Object.keys(engine.options).length}</span>
                                            </div>
                                        </div>

                                        {/* Options Preview */}
                                        <div style={{ marginBottom: "1rem" }}>
                                            <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.5rem", fontWeight: 600 }}>Options</div>
                                            <div style={{ display: "flex", flexWrap: "wrap", gap: "0.3rem" }}>
                                                {Object.entries(engine.options).map(([key, value]) => (
                                                    <span key={key} style={{
                                                        padding: "0.2rem 0.4rem",
                                                        background: "var(--color-bg-2)",
                                                        borderRadius: "0.2rem",
                                                        fontSize: "0.7rem",
                                                        color: "var(--color-text-muted)",
                                                        fontFamily: "monospace",
                                                    }}>
                                                        {key}={value}
                                                    </span>
                                                ))}
                                            </div>
                                        </div>

                                        {/* Actions */}
                                        <div style={{ display: "flex", gap: "0.5rem" }}>
                                            {status?.isRunning ? (
                                                <button
                                                    onClick={() => handleStopEngine(engine.name)}
                                                    style={{
                                                        flex: 1,
                                                        padding: "0.5rem",
                                                        background: "var(--color-danger)",
                                                        color: "white",
                                                        border: "none",
                                                        borderRadius: "0.4rem",
                                                        fontSize: "0.85rem",
                                                        fontWeight: 600,
                                                        cursor: "pointer",
                                                    }}
                                                >
                                                    Stop Engine
                                                </button>
                                            ) : (
                                                <button
                                                    onClick={() => handleStartEngine(engine.name)}
                                                    style={{
                                                        flex: 1,
                                                        padding: "0.5rem",
                                                        background: "var(--color-teal)",
                                                        color: "white",
                                                        border: "none",
                                                        borderRadius: "0.4rem",
                                                        fontSize: "0.85rem",
                                                        fontWeight: 600,
                                                        cursor: "pointer",
                                                    }}
                                                >
                                                    Start Engine
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        {/* Add Custom Engine */}
                        <div className="card" style={{ marginTop: "1.25rem", padding: "1.25rem" }}>
                            <h3 style={{ fontSize: "0.875rem", marginBottom: "1rem", color: "var(--color-text)" }}>Add Custom Engine</h3>
                            <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", alignItems: "flex-end" }}>
                                <div style={{ flex: 1, minWidth: 200 }}>
                                    <label style={{ display: "block", fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem" }}>Engine Name</label>
                                    <input
                                        type="text"
                                        placeholder="my-engine"
                                        style={{
                                            width: "100%",
                                            padding: "0.5rem",
                                            background: "var(--color-bg-2)",
                                            border: "1px solid var(--color-border)",
                                            borderRadius: "0.4rem",
                                            color: "var(--color-text)",
                                        }}
                                    />
                                </div>
                                <div style={{ flex: 2, minWidth: 300 }}>
                                    <label style={{ display: "block", fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem" }}>Engine Path</label>
                                    <input
                                        type="text"
                                        placeholder="C:\\path\\to\\engine.exe"
                                        style={{
                                            width: "100%",
                                            padding: "0.5rem",
                                            background: "var(--color-bg-2)",
                                            border: "1px solid var(--color-border)",
                                            borderRadius: "0.4rem",
                                            color: "var(--color-text)",
                                        }}
                                    />
                                </div>
                                <button
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
                                    onClick={async () => {
                                        const nameInput = document.querySelector('input[placeholder="my-engine"]') as HTMLInputElement;
                                        const pathInput = document.querySelector('input[placeholder="C:\\\\path\\\\to\\\\engine.exe"]') as HTMLInputElement;
                                        if (nameInput && pathInput && nameInput.value && pathInput.value) {
                                            try {
                                                const response = await fetch(`/v1/chess/engines/${nameInput.value}/register`, {
                                                    method: "POST",
                                                    headers: { "Content-Type": "application/json" },
                                                    body: JSON.stringify({
                                                        enginePath: pathInput.value,
                                                        options: {},
                                                        defaultDepth: 15,
                                                        defaultTimeMs: 1000
                                                    })
                                                });
                                                if (response.ok) {
                                                    loadEngines();
                                                    nameInput.value = "";
                                                    pathInput.value = "";
                                                } else {
                                                    setError("Failed to register engine");
                                                }
                                            } catch (e) {
                                                setError("Failed to register engine");
                                            }
                                        }
                                    }}
                                >
                                    Add Engine
                                </button>
                            </div>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}
