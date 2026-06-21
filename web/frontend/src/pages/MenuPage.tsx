import React from "react";
import { useNavigate } from "react-router-dom";
import { createGame, loadLatestGame, getAvailableBots } from "../utils/apiClient";
import { useTheme } from "../router/App";
import type { BotInfo } from "../types/chess";

type TimeControlOption = "unlimited" | "bullet" | "blitz" | "rapid" | "classical";

const TIME_CONTROLS: { value: TimeControlOption; label: string; description: string; icon: string }[] = [
    { value: "unlimited", label: "Unlimited", description: "No time limit", icon: "∞" },
    { value: "bullet", label: "Bullet", description: "1 min", icon: "⚡" },
    { value: "blitz", label: "Blitz", description: "3+2 min", icon: "🔥" },
    { value: "rapid", label: "Rapid", description: "10+5 min", icon: "⏱" },
    { value: "classical", label: "Classical", description: "90+30 min", icon: "🏛" },
];

type PlayerType = "human" | "bot";

function PlayerSection({
    label, dot, type, name, bot, bots, loadingBots,
    onTypeChange, onNameChange, onBotChange,
}: {
    label: string; dot: string; type: PlayerType; name: string; bot: string;
    bots: BotInfo[]; loadingBots: boolean;
    onTypeChange: (t: PlayerType) => void;
    onNameChange: (v: string) => void;
    onBotChange: (v: string) => void;
}) {
    return (
        <div style={{ marginBottom: "1.25rem" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.6rem" }}>
                <span style={{ fontSize: "1rem" }}>{dot}</span>
                <span style={{ fontWeight: 600, fontSize: "0.875rem", color: "var(--color-text)" }}>{label}</span>
            </div>
            <div style={{ display: "flex", gap: "0.5rem", marginBottom: "0.6rem" }}>
                {(["human", "bot"] as PlayerType[]).map(t => (
                    <button
                        key={t}
                        type="button"
                        onClick={() => onTypeChange(t)}
                        style={{
                            flex: 1,
                            padding: "0.4rem",
                            borderRadius: "var(--radius-sm)",
                            border: `1px solid ${type === t ? "var(--color-accent)" : "var(--color-border)"}`,
                            background: type === t ? "var(--color-accent-bg)" : "var(--color-surface-2)",
                            color: type === t ? "var(--color-accent)" : "var(--color-text-muted)",
                            fontWeight: type === t ? 600 : 400,
                            fontSize: "0.8125rem",
                        }}
                    >
                        {t === "human" ? "👤 Human" : "🤖 Bot"}
                    </button>
                ))}
            </div>
            {type === "human" ? (
                <input type="text" value={name} onChange={e => onNameChange(e.target.value)} placeholder="Player name" />
            ) : (
                <>
                    <select value={bot} onChange={e => onBotChange(e.target.value)} disabled={loadingBots}>
                        {bots.map(b => (
                            <option key={b.id} value={b.id}>{b.name} — {b.difficulty}</option>
                        ))}
                    </select>
                    {bot && (
                        <p style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginTop: "0.35rem" }}>
                            {bots.find(b => b.id === bot)?.description}
                        </p>
                    )}
                </>
            )}
        </div>
    );
}

export function MenuPage() {
    const navigate = useNavigate();
    const { theme, toggleTheme } = useTheme();
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<string | null>(null);
    const [showSetup, setShowSetup] = React.useState(false);
    const [playerForm, setPlayerForm] = React.useState({
        white: "White", black: "Black",
        timeControl: "unlimited" as TimeControlOption,
        whiteType: "human" as PlayerType, blackType: "human" as PlayerType,
        whiteBot: "random", blackBot: "random",
    });
    const [availableBots, setAvailableBots] = React.useState<BotInfo[]>([]);
    const [loadingBots, setLoadingBots] = React.useState(false);

    async function handleCreate() {
        setShowSetup(true);
        setError(null);
        setLoadingBots(true);
        try {
            const res = await getAvailableBots();
            setAvailableBots(res.bots || []);
        } catch { setAvailableBots([]); }
        finally { setLoadingBots(false); }
    }

    async function handleStartGame() {
        setLoading(true);
        setError(null);
        try {
            const whitePlayer = playerForm.whiteType === "bot"
                ? `Bot (${playerForm.whiteBot})` : (playerForm.white.trim() || "White");
            const blackPlayer = playerForm.blackType === "bot"
                ? `Bot (${playerForm.blackBot})` : (playerForm.black.trim() || "Black");
            const timeControl = playerForm.timeControl === "unlimited" ? null : playerForm.timeControl;
            const res = await createGame(whitePlayer, blackPlayer, timeControl);
            const sp = new URLSearchParams();
            if (playerForm.whiteType === "bot") sp.set("whiteBot", playerForm.whiteBot);
            if (playerForm.blackType === "bot") sp.set("blackBot", playerForm.blackBot);
            navigate(`/game/${res.gameId}${sp.toString() ? `?${sp}` : ""}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to create game");
        } finally {
            setLoading(false);
            setShowSetup(false);
        }
    }

    async function handleLoadGames() {
        setLoading(true);
        setError(null);
        try {
            const res = await loadLatestGame();
            if (!res?.gameId) throw new Error(res?.error || "No latest game available");
            navigate(`/game/${res.gameId}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load latest game");
        } finally { setLoading(false); }
    }

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
            <div style={{ textAlign: "center", marginBottom: "3rem" }}>
                <div style={{ fontSize: "3.5rem", marginBottom: "0.75rem" }}>♟</div>
                <h1 style={{ fontSize: "2.5rem", fontWeight: 700, letterSpacing: "-0.04em", marginBottom: "0.5rem" }}>ME Chess</h1>
                <p style={{ color: "var(--color-text-muted)", fontSize: "1rem" }}>A modern chess experience</p>
            </div>

            {error && <div className="error" style={{ maxWidth: 360, width: "100%", marginBottom: "1rem" }}>{error}</div>}

            {/* Action cards */}
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: "1rem", maxWidth: 720, width: "100%" }}>
                {[
                    { label: "New Game", icon: "➕", onClick: handleCreate, primary: true },
                    { label: "Load Latest", icon: "📂", onClick: handleLoadGames, primary: false },
                    { label: "All Games", icon: "🗂", onClick: () => navigate("/games"), primary: false },
                    { label: "Leaderboard", icon: "🏆", onClick: () => navigate("/leaderboard"), primary: false },
                    { label: "Players", icon: "👤", onClick: () => navigate("/players"), primary: false },
                    { label: "Tournaments", icon: "🎯", onClick: () => navigate("/tournaments"), primary: false },
                    { label: "Analytics", icon: "📊", onClick: () => navigate("/analytics"), primary: false },
                ].map(({ label, icon, onClick, primary }) => (
                    <button
                        key={label}
                        onClick={onClick}
                        disabled={loading}
                        className={primary ? "" : "secondary"}
                        style={{
                            display: "flex", flexDirection: "column", alignItems: "center", gap: "0.5rem",
                            padding: "1.25rem 1rem", borderRadius: "var(--radius-md)",
                            fontSize: "0.875rem", fontWeight: 500,
                        }}
                    >
                        <span style={{ fontSize: "1.75rem", lineHeight: 1 }}>{icon}</span>
                        {label}
                    </button>
                ))}
            </div>

            {/* New Game Setup Modal */}
            {showSetup && (
                <div
                    onClick={e => { if (e.target === e.currentTarget) setShowSetup(false); }}
                    style={{
                        position: "fixed", inset: 0,
                        backgroundColor: "rgba(0,0,0,0.6)",
                        display: "flex", alignItems: "center", justifyContent: "center",
                        zIndex: 1000, padding: "1rem",
                    }}
                >
                    <div style={{
                        background: "var(--color-surface)",
                        border: "1px solid var(--color-border)",
                        borderRadius: "var(--radius-lg)",
                        boxShadow: "var(--shadow-lg)",
                        width: "100%", maxWidth: 440,
                        maxHeight: "90vh",
                        display: "flex", flexDirection: "column",
                        overflow: "hidden",
                    }}>
                        {/* Modal header */}
                        <div style={{
                            display: "flex", alignItems: "center", justifyContent: "space-between",
                            padding: "1.25rem 1.5rem",
                            borderBottom: "1px solid var(--color-border)",
                            flexShrink: 0,
                        }}>
                            <h3 style={{ margin: 0, fontSize: "1.0625rem" }}>New Game Setup</h3>
                            <button
                                onClick={() => setShowSetup(false)}
                                className="ghost"
                                style={{ padding: "0.25rem 0.5rem", fontSize: "1rem", lineHeight: 1 }}
                            >✕</button>
                        </div>

                        {/* Scrollable body */}
                        <div style={{ overflowY: "auto", flex: 1, padding: "1.25rem 1.5rem" }}>
                            <PlayerSection
                                label="White Player" dot="⬜"
                                type={playerForm.whiteType} name={playerForm.white} bot={playerForm.whiteBot}
                                bots={availableBots} loadingBots={loadingBots}
                                onTypeChange={t => setPlayerForm({ ...playerForm, whiteType: t })}
                                onNameChange={v => setPlayerForm({ ...playerForm, white: v })}
                                onBotChange={v => setPlayerForm({ ...playerForm, whiteBot: v })}
                            />

                            <div style={{ height: "1px", background: "var(--color-border)", margin: "0.25rem 0 1.25rem" }} />

                            <PlayerSection
                                label="Black Player" dot="⬛"
                                type={playerForm.blackType} name={playerForm.black} bot={playerForm.blackBot}
                                bots={availableBots} loadingBots={loadingBots}
                                onTypeChange={t => setPlayerForm({ ...playerForm, blackType: t })}
                                onNameChange={v => setPlayerForm({ ...playerForm, black: v })}
                                onBotChange={v => setPlayerForm({ ...playerForm, blackBot: v })}
                            />

                            <div style={{ height: "1px", background: "var(--color-border)", margin: "0.25rem 0 1.25rem" }} />

                            {/* Time control */}
                            <div>
                                <p style={{ fontWeight: 600, fontSize: "0.875rem", marginBottom: "0.6rem", color: "var(--color-text)" }}>
                                    ⏱ Time Control
                                </p>
                                <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: "0.4rem" }}>
                                    {TIME_CONTROLS.map(tc => (
                                        <button
                                            key={tc.value}
                                            type="button"
                                            onClick={() => setPlayerForm({ ...playerForm, timeControl: tc.value })}
                                            style={{
                                                display: "flex", flexDirection: "column", alignItems: "center",
                                                gap: "0.2rem", padding: "0.5rem 0.25rem",
                                                borderRadius: "var(--radius-sm)",
                                                border: `1px solid ${playerForm.timeControl === tc.value ? "var(--color-accent)" : "var(--color-border)"}`,
                                                background: playerForm.timeControl === tc.value ? "var(--color-accent-bg)" : "var(--color-surface-2)",
                                                color: playerForm.timeControl === tc.value ? "var(--color-accent)" : "var(--color-text-muted)",
                                                fontSize: "0.7rem", fontWeight: playerForm.timeControl === tc.value ? 600 : 400,
                                            }}
                                        >
                                            <span style={{ fontSize: "1rem" }}>{tc.icon}</span>
                                            <span>{tc.label}</span>
                                            <span style={{ fontSize: "0.65rem", opacity: 0.75 }}>{tc.description}</span>
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </div>

                        {/* Modal footer */}
                        <div style={{
                            display: "flex", gap: "0.75rem", justifyContent: "flex-end",
                            padding: "1rem 1.5rem",
                            borderTop: "1px solid var(--color-border)",
                            flexShrink: 0,
                        }}>
                            <button onClick={() => setShowSetup(false)} className="secondary">Cancel</button>
                            <button onClick={handleStartGame} disabled={loading}>
                                {loading ? "Starting…" : "Start Game ➜"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}