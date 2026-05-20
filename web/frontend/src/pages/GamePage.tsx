import React from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useGame } from "../hooks/useGame";
import { Board } from "../components/Board";
import { saveGame, exportPgn } from "../utils/apiClient";

export function GamePage() {
    const { gameId } = useParams();
    const navigate = useNavigate();
    const { game, loading, move, refresh } = useGame(gameId!);
    const [moveError, setMoveError] = React.useState<string | null>(null);
    const [saveMessage, setSaveMessage] = React.useState<string | null>(null);
    const [exportMessage, setExportMessage] = React.useState<string | null>(null);
    const [showExportDialog, setShowExportDialog] = React.useState(false);
    const [exportForm, setExportForm] = React.useState({ event: "Web Game", site: "Online" });

    async function handleMove(from: string, to: string) {
        setMoveError(null);
        setSaveMessage(null);
        try {
            await move(from, to);
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to make move");
        }
    }

    async function handleExportPgn() {
        setMoveError(null);
        setSaveMessage(null);
        setExportMessage(null);
        try {
            const response = await exportPgn(gameId!, exportForm.event, exportForm.site);
            // Download the PGN file
            const blob = new Blob([response.pgnContent], { type: 'text/plain' });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = response.filename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            setExportMessage("Game exported successfully");
            setShowExportDialog(false);
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to export game");
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
            {exportMessage && <div className="success">{exportMessage}</div>}

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
                        <button onClick={() => setShowExportDialog(true)} disabled={game.isGameOver}>
                            Export PGN
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

            {/* Export PGN Dialog */}
            {showExportDialog && (
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
                        background: "white",
                        padding: "2rem",
                        borderRadius: "8px",
                        border: "1px solid #e0e0e0",
                        minWidth: "300px",
                        maxWidth: "400px"
                    }}>
                        <h3>Export Game to PGN</h3>
                        <div style={{ marginBottom: "1rem" }}>
                            <label style={{ display: "block", marginBottom: "0.5rem", color: "#666" }}>
                                Event:
                            </label>
                            <input
                                type="text"
                                value={exportForm.event}
                                onChange={(e) => setExportForm({ ...exportForm, event: e.target.value })}
                                style={{
                                    width: "100%",
                                    padding: "0.5rem",
                                    border: "1px solid #ccc",
                                    borderRadius: "4px"
                                }}
                            />
                        </div>
                        <div style={{ marginBottom: "1.5rem" }}>
                            <label style={{ display: "block", marginBottom: "0.5rem", color: "#666" }}>
                                Site:
                            </label>
                            <input
                                type="text"
                                value={exportForm.site}
                                onChange={(e) => setExportForm({ ...exportForm, site: e.target.value })}
                                style={{
                                    width: "100%",
                                    padding: "0.5rem",
                                    border: "1px solid #ccc",
                                    borderRadius: "4px"
                                }}
                            />
                        </div>
                        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end" }}>
                            <button
                                onClick={() => setShowExportDialog(false)}
                                className="secondary"
                                style={{ marginRight: "0.5rem" }}
                            >
                                Cancel
                            </button>
                            <button onClick={handleExportPgn}>
                                Export
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}