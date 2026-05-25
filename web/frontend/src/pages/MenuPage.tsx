import React from "react";
import { useNavigate } from "react-router-dom";
import { createGame, loadGame } from "../utils/apiClient";

export function MenuPage() {
    const navigate = useNavigate();
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<string | null>(null);
    const [showSetup, setShowSetup] = React.useState(false);
    const [playerForm, setPlayerForm] = React.useState({ white: "White", black: "Black" });

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
            const res = await createGame(whitePlayer, blackPlayer);
            navigate(`/game/${res.gameId}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to create game");
        } finally {
            setLoading(false);
            setShowSetup(false);
        }
    }

    async function handleLoad() {
        setLoading(true);
        setError(null);
        try {
            const res = await loadGame();
            navigate(`/game/${res.gameId}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load game");
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
                <button onClick={handleLoad} disabled={loading} className="secondary">
                    {loading ? "Loading..." : "Load Saved Game"}
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