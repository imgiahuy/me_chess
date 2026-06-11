import React from "react";
import { useNavigate } from "react-router-dom";
import { createGame, loadLatestGame } from "../utils/apiClient";

type TimeControlOption = "unlimited" | "bullet" | "blitz" | "rapid" | "classical";

const TIME_CONTROLS: { value: TimeControlOption; label: string; description: string }[] = [
    { value: "unlimited", label: "Unlimited", description: "No time limit" },
    { value: "bullet", label: "Bullet", description: "1 minute" },
    { value: "blitz", label: "Blitz", description: "3+2 minutes" },
    { value: "rapid", label: "Rapid", description: "10+5 minutes" },
    { value: "classical", label: "Classical", description: "90+30 minutes" },
];

export function MenuPage() {
    const navigate = useNavigate();
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<string | null>(null);
    const [showSetup, setShowSetup] = React.useState(false);
    const [playerForm, setPlayerForm] = React.useState({ white: "White", black: "Black", timeControl: "unlimited" as TimeControlOption });

    async function handleCreate() {
        setShowSetup(true);
        setError(null);
    }

    async function handleStartGame() {
        setLoading(true);
        setError(null);
        try {
            const whitePlayer = playerForm.white.trim() || "White";
            const blackPlayer = playerForm.black.trim() || "Black";
            const timeControl = playerForm.timeControl === "unlimited" ? null : playerForm.timeControl;
            const res = await createGame(whitePlayer, blackPlayer, timeControl);
            navigate(`/game/${res.gameId}`);
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
            if (!res?.gameId) {
                throw new Error(res?.error || "No latest game available");
            }
            navigate(`/game/${res.gameId}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load latest game");
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="container">
            <h1 style={{ color: "#e0e0e0" }}>ME Chess</h1>
            <p style={{ marginBottom: "2rem", color: "#b0b0b0" }}>
                A minimalist chess game by me
            </p>
            
            {error && <div className="error">{error}</div>}
            
            <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
                <button onClick={handleCreate} disabled={loading}>
                    {loading ? "Creating..." : "New Game"}
                </button>
                <button onClick={handleLoadGames} disabled={loading} className="secondary">
                    Load Latest Game
                </button>
                <button onClick={() => navigate("/games")} disabled={loading} className="secondary">
                    View Games
                </button>
            </div>

            {/* Player Setup Dialog */}
            {showSetup && (
                <div style={{
                    position: "fixed",
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundColor: "rgba(0, 0, 0, 0.5)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    zIndex: 1000
                }}>
                    <div style={{
                        background: "#1E1E1E",
                        padding: "2rem",
                        borderRadius: "8px",
                        border: "1px solid #444",
                        minWidth: "300px",
                        maxWidth: "400px"
                    }}>
                        <h3 style={{ color: "#e0e0e0" }}>New Game Setup</h3>
                        <div style={{ marginBottom: "1rem" }}>
                            <label style={{ display: "block", marginBottom: "0.5rem", color: "#b0b0b0" }}>
                                White Player:
                            </label>
                            <input
                                type="text"
                                value={playerForm.white}
                                onChange={(e) => setPlayerForm({ ...playerForm, white: e.target.value })}
                                style={{
                                    width: "100%",
                                    padding: "0.5rem",
                                    border: "1px solid #555",
                                    borderRadius: "4px",
                                    background: "#2C2C2C",
                                    color: "#e0e0e0"
                                }}
                            />
                        </div>
                        <div style={{ marginBottom: "1.5rem" }}>
                            <label style={{ display: "block", marginBottom: "0.5rem", color: "#b0b0b0" }}>
                                Black Player:
                            </label>
                            <input
                                type="text"
                                value={playerForm.black}
                                onChange={(e) => setPlayerForm({ ...playerForm, black: e.target.value })}
                                style={{
                                    width: "100%",
                                    padding: "0.5rem",
                                    border: "1px solid #555",
                                    borderRadius: "4px",
                                    background: "#2C2C2C",
                                    color: "#e0e0e0"
                                }}
                            />
                        </div>
                        <div style={{ marginBottom: "1.5rem" }}>
                            <label style={{ display: "block", marginBottom: "0.5rem", color: "#b0b0b0" }}>
                                Time Control:
                            </label>
                            <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
                                {TIME_CONTROLS.map((tc) => (
                                    <label
                                        key={tc.value}
                                        style={{
                                            display: "flex",
                                            alignItems: "center",
                                            gap: "0.5rem",
                                            color: "#b0b0b0",
                                            cursor: "pointer",
                                            padding: "0.5rem",
                                            borderRadius: "4px",
                                            background: playerForm.timeControl === tc.value ? "#3a3a3a" : "transparent",
                                            border: playerForm.timeControl === tc.value ? "1px solid #555" : "1px solid transparent"
                                        }}
                                    >
                                        <input
                                            type="radio"
                                            name="timeControl"
                                            value={tc.value}
                                            checked={playerForm.timeControl === tc.value}
                                            onChange={(e) => setPlayerForm({ ...playerForm, timeControl: e.target.value as TimeControlOption })}
                                        />
                                        <div>
                                            <div style={{ fontWeight: "bold", color: "#e0e0e0" }}>{tc.label}</div>
                                            <div style={{ fontSize: "0.875rem", color: "#888" }}>{tc.description}</div>
                                        </div>
                                    </label>
                                ))}
                            </div>
                        </div>
                        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end" }}>
                            <button
                                onClick={() => setShowSetup(false)}
                                className="secondary"
                                style={{ marginRight: "0.5rem" }}
                            >
                                Cancel
                            </button>
                            <button onClick={handleStartGame} disabled={loading}>
                                {loading ? "Starting..." : "Start Game"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}