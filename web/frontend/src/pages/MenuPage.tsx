import React from "react";
import { useNavigate } from "react-router-dom";
import { createGame, loadGame } from "../utils/apiClient";

export function MenuPage() {
    const navigate = useNavigate();
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<string | null>(null);

    async function handleCreate() {
        setLoading(true);
        setError(null);
        try {
            const res = await createGame();
            navigate(`/game/${res.gameId}`);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to create game");
        } finally {
            setLoading(false);
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
            <h1>ME Chess</h1>
            <p style={{ marginBottom: "2rem", color: "#666" }}>
                A minimalist chess interface
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
        </div>
    );
}