import React from "react";
import { useEffect, useState } from "react";
import { listGames, deleteGame } from "../utils/apiClient";
import { Link, useNavigate } from "react-router-dom";
import type { GameSummary } from "../types/chess";

export function GameList() {
    const [games, setGames] = useState<GameSummary[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();

    useEffect(() => {
        loadGames();
    }, []);

    async function loadGames() {
        setLoading(true);
        setError(null);
        try {
            const data = await listGames();
            setGames(data.games);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load games");
        } finally {
            setLoading(false);
        }
    }

    async function handleDelete(gameId: string) {
        if (!confirm("Are you sure you want to delete this game?")) return;
        
        try {
            await deleteGame(gameId);
            await loadGames();
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to delete game");
        }
    }

    if (loading) return <div className="loading">Loading games...</div>;

    return (
        <div className="container">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "2rem" }}>
                <h1 style={{ color: "#e0e0e0" }}>Games ({games.length})</h1>
                <button onClick={() => navigate("/")} className="secondary">
                    Back to Menu
                </button>
            </div>

            {error && <div className="error">{error}</div>}

            {games.length === 0 ? (
                <p style={{ color: "#b0b0b0" }}>No active games. Create a new game to get started.</p>
            ) : (
                <div style={{ display: "grid", gap: "1rem" }}>
                    {games.map(game => (
                        <div
                            key={game.gameId}
                            style={{
                                background: "#1E1E1E",
                                padding: "1.5rem",
                                borderRadius: "8px",
                                border: "1px solid #444",
                                display: "flex",
                                justifyContent: "space-between",
                                alignItems: "center"
                            }}
                        >
                            <div>
                                <h3 style={{ marginBottom: "0.5rem", color: "#e0e0e0" }}>
                                    <Link to={`/game/${game.gameId}`}>
                                        Game {game.gameId.slice(0, 8)}...
                                    </Link>
                                </h3>
                                <p style={{ color: "#b0b0b0", fontSize: "0.875rem" }}>
                                    Turn: {game.turn} • Moves: {game.moveCount}
                                    {game.isGameOver && " • Game Over"}
                                </p>
                            </div>
                            <div style={{ display: "flex", gap: "0.5rem" }}>
                                <button onClick={() => navigate(`/game/${game.gameId}`)}>
                                    Play
                                </button>
                                <button onClick={() => handleDelete(game.gameId)} className="danger">
                                    Delete
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}