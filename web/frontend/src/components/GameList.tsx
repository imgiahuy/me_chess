import React from "react";
import { useEffect, useState } from "react";
import { listGames, deleteGame } from "../utils/apiClient";
import { Link, useNavigate } from "react-router-dom";
import { useTheme } from "../router/App";
import type { GameSummary } from "../types/chess";

export function GameList() {
    const [games, setGames] = useState<GameSummary[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();
    const { theme, toggleTheme } = useTheme();

    useEffect(() => { loadGames(); }, []);

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
        if (!confirm("Delete this game?")) return;
        try {
            await deleteGame(gameId);
            await loadGames();
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to delete game");
        }
    }

    return (
        <div style={{ minHeight: "100vh" }}>
            {/* Navbar */}
            <div style={{
                display: "flex", alignItems: "center", gap: "0.75rem",
                padding: "0.75rem 1.5rem",
                background: "var(--color-surface)",
                borderBottom: "1px solid var(--color-border)",
                position: "sticky", top: 0, zIndex: 50,
            }}>
                <button onClick={() => navigate("/")} className="ghost" style={{ padding: "0.3rem 0.6rem" }}>← Menu</button>
                <span style={{ fontWeight: 700, fontSize: "1.0625rem" }}>
                    🗂 Games
                    {!loading && <span style={{ fontSize: "0.8rem", fontWeight: 400, color: "var(--color-text-muted)", marginLeft: "0.4rem" }}>({games.length})</span>}
                </span>
                <div style={{ marginLeft: "auto", display: "flex", gap: "0.5rem" }}>
                    <button onClick={loadGames} className="ghost" style={{ padding: "0.3rem 0.6rem", fontSize: "0.8rem" }}>↻ Reload</button>
                    <button onClick={toggleTheme} className="ghost" style={{ padding: "0.3rem 0.55rem", fontSize: "0.95rem" }}>
                        {theme === "dark" ? "☀️" : "🌙"}
                    </button>
                </div>
            </div>

            <div className="container" style={{ maxWidth: 860 }}>
                {loading && <div className="loading">Loading games…</div>}
                {error && <div className="error" style={{ marginBottom: "1rem" }}>{error}</div>}

                {!loading && games.length === 0 && (
                    <div className="card" style={{ textAlign: "center", padding: "3rem 2rem" }}>
                        <div style={{ fontSize: "2.5rem", marginBottom: "0.75rem" }}>♟</div>
                        <h3 style={{ marginBottom: "0.5rem" }}>No games yet</h3>
                        <p style={{ color: "var(--color-text-muted)", marginBottom: "1.5rem", fontSize: "0.875rem" }}>
                            Start a new game from the menu.
                        </p>
                        <button onClick={() => navigate("/")}>New Game</button>
                    </div>
                )}

                {!loading && games.length > 0 && (
                    <div style={{ display: "grid", gap: "0.625rem" }}>
                        {games.map(game => (
                            <div key={game.gameId} className="card" style={{
                                display: "flex", alignItems: "center", justifyContent: "space-between",
                                gap: "1rem", padding: "0.875rem 1.125rem",
                            }}>
                                <div style={{ minWidth: 0 }}>
                                    <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.2rem" }}>
                                        <Link to={`/game/${game.gameId}`} style={{ fontWeight: 600, fontSize: "0.9375rem" }}>
                                            #{game.gameId.slice(0, 8)}
                                        </Link>
                                        {game.isGameOver
                                            ? <span style={{ fontSize: "0.68rem", fontWeight: 600, padding: "1px 7px", borderRadius: "999px", background: "var(--color-danger-bg)", color: "var(--color-danger)" }}>Over</span>
                                            : <span style={{ fontSize: "0.68rem", fontWeight: 600, padding: "1px 7px", borderRadius: "999px", background: "var(--color-success-bg)", color: "var(--color-success)" }}>● Live</span>
                                        }
                                    </div>
                                    <p style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                                        Turn: <strong style={{ color: "var(--color-text)" }}>{game.turn}</strong>
                                        <span style={{ margin: "0 0.4rem", color: "var(--color-border-2)" }}>·</span>
                                        {game.moveCount} move{game.moveCount !== 1 ? "s" : ""}
                                    </p>
                                </div>
                                <div style={{ display: "flex", gap: "0.4rem", flexShrink: 0 }}>
                                    <button onClick={() => navigate(`/game/${game.gameId}`)} style={{ fontSize: "0.8rem", padding: "0.35rem 0.75rem" }}>
                                        Open
                                    </button>
                                    <button onClick={() => handleDelete(game.gameId)} className="danger" style={{ fontSize: "0.8rem", padding: "0.35rem 0.75rem" }}>
                                        ✕
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}