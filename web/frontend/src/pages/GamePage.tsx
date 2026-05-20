import React from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useGame } from "../hooks/useGame";
import { Board } from "../components/Board";
import { saveGame } from "../utils/apiClient";

export function GamePage() {
    const { gameId } = useParams();
    const navigate = useNavigate();
    const { game, loading, move, refresh } = useGame(gameId!);
    const [moveError, setMoveError] = React.useState<string | null>(null);
    const [saveMessage, setSaveMessage] = React.useState<string | null>(null);

    async function handleMove(from: string, to: string) {
        setMoveError(null);
        setSaveMessage(null);
        try {
            await move(from, to);
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to make move");
        }
    }

    async function handleSave() {
        setMoveError(null);
        setSaveMessage(null);
        try {
            await saveGame(gameId!);
            setSaveMessage("Game saved successfully");
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to save game");
        }
    }

    if (loading) return <div className="loading">Loading game...</div>;
    if (!game) return <div className="error">No game found</div>;

    return (
        <div className="container">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "2rem" }}>
                <h1>Game {game.gameId.slice(0, 8)}...</h1>
                <button onClick={() => navigate("/")} className="secondary">
                    Back to Menu
                </button>
            </div>

            {moveError && <div className="error">{moveError}</div>}
            {saveMessage && <div className="success">{saveMessage}</div>}

            <div style={{ display: "flex", gap: "2rem", flexWrap: "wrap" }}>
                <div>
                    <Board
                        fen={game.fen}
                        onMove={handleMove}
                    />
                </div>

                <div style={{ flex: 1, minWidth: "250px" }}>
                    <div style={{ background: "white", padding: "1.5rem", borderRadius: "8px", border: "1px solid #e0e0e0", marginBottom: "1rem" }}>
                        <h3>Game Status</h3>
                        <p style={{ color: "#666" }}>
                            <strong>Turn:</strong> {game.turn}
                        </p>
                        <p style={{ color: "#666" }}>
                            <strong>Moves:</strong> {game.moveHistory.length}
                        </p>
                        {game.isGameOver && (
                            <p style={{ color: "#c33", fontWeight: "bold" }}>
                                Game Over - {game.winner || "Draw"}
                            </p>
                        )}
                    </div>

                    <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                        <button onClick={handleSave} disabled={game.isGameOver}>
                            Save Game
                        </button>
                        <button onClick={refresh} className="secondary">
                            Refresh
                        </button>
                    </div>

                    {game.moveHistory.length > 0 && (
                        <div style={{ marginTop: "1rem", background: "white", padding: "1.5rem", borderRadius: "8px", border: "1px solid #e0e0e0" }}>
                            <h3>Move History</h3>
                            <div style={{ maxHeight: "200px", overflowY: "auto", color: "#666", fontSize: "0.875rem" }}>
                                {game.moveHistory.map((move, i) => (
                                    <div key={i}>{move}</div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}