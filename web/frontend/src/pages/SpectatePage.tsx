import React from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useTheme } from "../router/App";

interface GameStateResponse {
    gameId: string;
    fen: string;
    turn: string;
    moveHistory: string[];
    gameResult: {
        status: string;
        reason?: string;
        winner?: string;
    };
    whiteTime?: {
        initialTimeMs: number;
        remainingTimeMs: number;
    };
    blackTime?: {
        initialTimeMs: number;
        remainingTimeMs: number;
    };
    legalMoves?: string[];
}

export function SpectatePage() {
    const navigate = useNavigate();
    const { gameId } = useParams<{ gameId: string }>();
    const { theme, toggleTheme } = useTheme();
    const [gameState, setGameState] = React.useState<GameStateResponse | null>(null);
    const [error, setError] = React.useState<string | null>(null);
    const [connected, setConnected] = React.useState<boolean>(false);

    React.useEffect(() => {
        if (!gameId) return;

        let eventSource: EventSource | null = null;

        const connectSSE = () => {
            eventSource = new EventSource(`/v1/chess/games/${gameId}/events`);
            setConnected(true);

            eventSource.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    if (data.error) {
                        setError(data.error);
                    } else {
                        setGameState(data);
                        setError(null);
                    }
                } catch (e) {
                    console.error("Failed to parse SSE data:", e);
                }
            };

            eventSource.onerror = () => {
                setConnected(false);
                eventSource?.close();
                // Reconnect after 3 seconds
                setTimeout(connectSSE, 3000);
            };
        };

        connectSSE();

        return () => {
            eventSource?.close();
        };
    }, [gameId]);

    const formatTime = (ms: number) => {
        const seconds = Math.floor(ms / 1000);
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins}:${secs.toString().padStart(2, '0')}`;
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
                <button onClick={() => navigate("/games")} className="ghost" style={{ padding: "0.3rem 0.6rem" }}>← Games</button>
                <span style={{ fontWeight: 700, fontSize: "0.95rem" }}>👁 Spectate Game</span>
                <div style={{ marginLeft: "auto", display: "flex", gap: "0.5rem", alignItems: "center" }}>
                    <span style={{
                        padding: "0.15rem 0.6rem", borderRadius: "999px", fontSize: "0.7rem", fontWeight: 600,
                        background: connected ? "var(--color-success-bg)" : "var(--color-danger-bg)",
                        color: connected ? "var(--color-success)" : "var(--color-danger)",
                        border: `1px solid ${connected ? "var(--color-success)" : "var(--color-danger)"}`,
                    }}>
                        {connected ? "● Live" : "◼ Offline"}
                    </span>
                    <button onClick={toggleTheme} className="ghost" style={{ padding: "0.3rem 0.55rem" }}>
                        {theme === "dark" ? "☀️" : "🌙"}
                    </button>
                </div>
            </div>

            <div style={{ padding: "1.25rem 1.5rem" }}>
                {error && <div style={{ color: "var(--color-danger)", marginBottom: "1rem", padding: "0.75rem", background: "var(--color-danger-bg)", borderRadius: "0.5rem" }}>⚠ {error}</div>}

                {gameState && (
                    <div style={{ display: "flex", gap: "1.5rem", flexWrap: "wrap" }}>
                        {/* Game Info */}
                        <div className="card" style={{ flex: 1, minWidth: 300, padding: "1.25rem" }}>
                            <h3 style={{ fontSize: "1rem", marginBottom: "1rem", color: "var(--color-text)" }}>Game Information</h3>
                            
                            <div style={{ marginBottom: "0.75rem" }}>
                                <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem" }}>Game ID</div>
                                <div style={{ fontSize: "0.85rem", fontFamily: "monospace", color: "var(--color-text)" }}>{gameState.gameId}</div>
                            </div>

                            <div style={{ marginBottom: "0.75rem" }}>
                                <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem" }}>Status</div>
                                <div style={{ fontSize: "0.85rem", color: "var(--color-teal)", fontWeight: 600 }}>
                                    {gameState.gameResult.status.toUpperCase()}
                                </div>
                            </div>

                            {gameState.gameResult.winner && (
                                <div style={{ marginBottom: "0.75rem" }}>
                                    <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem" }}>Winner</div>
                                    <div style={{ fontSize: "0.85rem", color: "var(--color-success)", fontWeight: 600 }}>
                                        {gameState.gameResult.winner}
                                    </div>
                                </div>
                            )}

                            <div style={{ marginBottom: "0.75rem" }}>
                                <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem" }}>Turn</div>
                                <div style={{ fontSize: "0.85rem", color: "var(--color-text)", fontWeight: 600 }}>
                                    {gameState.turn === "white" ? "⬜ White" : "⬛ Black"}
                                </div>
                            </div>

                            <div style={{ marginBottom: "0.75rem" }}>
                                <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem" }}>Moves</div>
                                <div style={{ fontSize: "0.85rem", color: "var(--color-text)" }}>{gameState.moveHistory.length}</div>
                            </div>

                            {/* Time Controls */}
                            {(gameState.whiteTime || gameState.blackTime) && (
                                <div style={{ display: "flex", gap: "1rem", marginTop: "1rem", paddingTop: "1rem", borderTop: "1px solid var(--color-border)" }}>
                                    {gameState.whiteTime && (
                                        <div>
                                            <div style={{ fontSize: "0.7rem", color: "var(--color-text-dim)", marginBottom: "0.2rem" }}>⬜ White</div>
                                            <div style={{ fontSize: "1rem", fontFamily: "monospace", fontWeight: 700, color: "var(--color-text)" }}>
                                                {formatTime(gameState.whiteTime.remainingTimeMs)}
                                            </div>
                                        </div>
                                    )}
                                    {gameState.blackTime && (
                                        <div>
                                            <div style={{ fontSize: "0.7rem", color: "var(--color-text-dim)", marginBottom: "0.2rem" }}>⬛ Black</div>
                                            <div style={{ fontSize: "1rem", fontFamily: "monospace", fontWeight: 700, color: "var(--color-text)" }}>
                                                {formatTime(gameState.blackTime.remainingTimeMs)}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>

                        {/* FEN and Moves */}
                        <div className="card" style={{ flex: 1, minWidth: 300, padding: "1.25rem" }}>
                            <h3 style={{ fontSize: "1rem", marginBottom: "1rem", color: "var(--color-text)" }}>Position</h3>
                            
                            <div style={{ marginBottom: "1rem" }}>
                                <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.25rem" }}>FEN</div>
                                <div style={{ 
                                    fontSize: "0.75rem", 
                                    fontFamily: "monospace", 
                                    color: "var(--color-text-muted)",
                                    wordBreak: "break-all",
                                    background: "var(--color-bg-2)",
                                    padding: "0.5rem",
                                    borderRadius: "0.3rem"
                                }}>
                                    {gameState.fen}
                                </div>
                            </div>

                            <div>
                                <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.5rem" }}>Move History</div>
                                <div style={{ 
                                    maxHeight: "200px", 
                                    overflowY: "auto",
                                    fontSize: "0.8rem", 
                                    fontFamily: "monospace", 
                                    color: "var(--color-text)",
                                    background: "var(--color-bg-2)",
                                    padding: "0.5rem",
                                    borderRadius: "0.3rem"
                                }}>
                                    {gameState.moveHistory.length > 0 ? (
                                        gameState.moveHistory.map((move, i) => (
                                            <div key={i} style={{ padding: "0.2rem 0", borderBottom: "1px solid var(--color-border)" }}>
                                                {Math.floor(i / 2) + 1}. {i % 2 === 0 ? move : ""} {i % 2 === 1 ? move : ""}
                                            </div>
                                        ))
                                    ) : (
                                        <div style={{ color: "var(--color-text-muted)", padding: "0.5rem" }}>No moves yet</div>
                                    )}
                                </div>
                            </div>

                            {gameState.legalMoves && gameState.legalMoves.length > 0 && (
                                <div style={{ marginTop: "1rem" }}>
                                    <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.5rem" }}>Legal Moves ({gameState.legalMoves.length})</div>
                                    <div style={{ 
                                        display: "flex", 
                                        flexWrap: "wrap", 
                                        gap: "0.3rem",
                                        fontSize: "0.75rem", 
                                        fontFamily: "monospace", 
                                        color: "var(--color-teal)"
                                    }}>
                                        {gameState.legalMoves.slice(0, 20).map((move, i) => (
                                            <span key={i} style={{ 
                                                background: "var(--color-bg-2)", 
                                                padding: "0.2rem 0.4rem", 
                                                borderRadius: "0.2rem" 
                                            }}>
                                                {move}
                                            </span>
                                        ))}
                                        {gameState.legalMoves.length > 20 && <span>+{gameState.legalMoves.length - 20} more</span>}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {!gameState && !error && (
                    <div className="card" style={{ padding: "2rem", textAlign: "center", color: "var(--color-text-muted)" }}>
                        <div style={{ fontSize: "2rem", marginBottom: "0.5rem" }}>👁</div>
                        <p>Connecting to game stream...</p>
                    </div>
                )}
            </div>
        </div>
    );
}
