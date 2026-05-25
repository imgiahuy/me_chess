import React from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useGame } from "../hooks/useGame";
import { Board } from "../components/Board";
import { saveGame, exportPgn } from "../utils/apiClient";

// Helper function to convert Position(x,y) to algebraic notation
function convertPositionToAlgebraic(message: string): string {
    return message.replace(/Position\((\d+),(\d+)\)/g, (match, x, y) => {
        const file = String.fromCharCode(97 + parseInt(x)); // 0->a, 1->b, etc.
        const rank = parseInt(y) + 1;
        return `${file}${rank}`;
    });
}

export function GamePage() {
    const { gameId } = useParams();
    const navigate = useNavigate();
    const { game, loading, move, refresh } = useGame(gameId!);
    const [moveError, setMoveError] = React.useState<string | null>(null);
    const [saveMessage, setSaveMessage] = React.useState<string | null>(null);
    const [exportMessage, setExportMessage] = React.useState<string | null>(null);
    const [notification, setNotification] = React.useState<string>("Welcome to the game!");
    const [showExportDialog, setShowExportDialog] = React.useState(false);
    const [exportForm, setExportForm] = React.useState({ event: "Web Game", site: "Online" });
    const moveHistoryRef = React.useRef<HTMLDivElement>(null);

    async function handleMove(from: string, to: string) {
        setMoveError(null);
        setSaveMessage(null);
        setNotification(`Move: ${from} → ${to}`);
        try {
            await move(from, to);
            setNotification(`Move successful: ${from} → ${to}`);
            // Scroll to bottom of move history after successful move
            setTimeout(() => {
                if (moveHistoryRef.current) {
                    moveHistoryRef.current.scrollTop = moveHistoryRef.current.scrollHeight;
                }
            }, 100);
        } catch (e) {
            const errorMsg = e instanceof Error ? e.message : "Failed to make move";
            setMoveError(errorMsg);
            setNotification(convertPositionToAlgebraic(errorMsg));
        }
    }

    async function handleExportPgn() {
        setMoveError(null);
        setSaveMessage(null);
        setExportMessage(null);
        setNotification("Exporting game to PGN...");
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
            setNotification("Game exported successfully to " + response.filename);
            setShowExportDialog(false);
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to export game");
            setNotification("Failed to export game");
        }
    }

    async function handleSave() {
        setMoveError(null);
        setSaveMessage(null);
        setNotification("Saving game...");
        try {
            await saveGame(gameId!);
            setSaveMessage("Game saved successfully");
            setNotification("Game saved successfully");
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to save game");
            setNotification("Failed to save game");
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

            <div style={{ minHeight: "60px", marginBottom: "1rem" }}>
                {moveError && <div className="error">{convertPositionToAlgebraic(moveError)}</div>}
                {saveMessage && <div className="success">{saveMessage}</div>}
                {exportMessage && <div className="success">{exportMessage}</div>}
            </div>

            <div style={{ display: "flex", gap: "2rem", flexWrap: "wrap" }}>
                <div>
                    <Board
                        fen={game.fen}
                        onMove={handleMove}
                    />
                </div>

                <div style={{ flex: 1, minWidth: "250px" }}>
                    <div style={{ background: "#1E1E1E", padding: "1.5rem", borderRadius: "8px", border: "1px solid #444", marginBottom: "1rem" }}>
                        <h3 style={{ color: "#e0e0e0" }}>Game Status</h3>
                        <p style={{ color: "#b0b0b0" }}>
                            <strong>Turn:</strong> {game.turn}
                        </p>
                        <p style={{ color: "#b0b0b0" }}>
                            <strong>Moves:</strong> {game.moveHistory.length}
                        </p>
                        {game.isGameOver && (
                            <p style={{ color: "#ff6b6b", fontWeight: "bold" }}>
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

                    <div style={{ marginTop: "1rem", background: "#1E1E1E", padding: "1.5rem", borderRadius: "8px", border: "1px solid #444" }}>
                        <h3 style={{ color: "#e0e0e0" }}>Move History</h3>
                        <div 
                            ref={moveHistoryRef}
                            style={{ 
                                height: "200px", 
                                overflowY: "auto", 
                                color: "#b0b0b0", 
                                fontSize: "0.875rem",
                                display: "flex",
                                flexDirection: "column"
                            }}
                        >
                            {game.moveHistory.length > 0 ? (
                                game.moveHistory.map((move, i) => (
                                    <div key={i} style={{ padding: "2px 0" }}>{move}</div>
                                ))
                            ) : (
                                <div style={{ color: "#666", fontStyle: "italic" }}>No moves yet</div>
                            )}
                        </div>
                    </div>

                    <div style={{ marginTop: "1rem", background: "#1E1E1E", padding: "1.5rem", borderRadius: "8px", border: "1px solid #444" }}>
                        <h3 style={{ color: "#e0e0e0" }}>Notifications</h3>
                        <div style={{ color: "#e0e0e0", fontSize: "0.875rem" }}>
                            {notification}
                        </div>
                    </div>
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
                        background: "#1E1E1E",
                        padding: "2rem",
                        borderRadius: "8px",
                        border: "1px solid #444",
                        minWidth: "300px",
                        maxWidth: "400px"
                    }}>
                        <h3 style={{ color: "#e0e0e0" }}>Export Game to PGN</h3>
                        <div style={{ marginBottom: "1rem" }}>
                            <label style={{ display: "block", marginBottom: "0.5rem", color: "#b0b0b0" }}>
                                Event:
                            </label>
                            <input
                                type="text"
                                value={exportForm.event}
                                onChange={(e) => setExportForm({ ...exportForm, event: e.target.value })}
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
                                Site:
                            </label>
                            <input
                                type="text"
                                value={exportForm.site}
                                onChange={(e) => setExportForm({ ...exportForm, site: e.target.value })}
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