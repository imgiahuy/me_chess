import React from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useGame } from "../hooks/useGame";
import { Board } from "../components/Board";
import { saveGame, exportPgn, resign } from "../utils/apiClient";

// Helper function to convert Position(x,y) to algebraic notation
function convertPositionToAlgebraic(message: string): string {
    return message.replace(/Position\((\d+),(\d+)\)/g, (_match, x, y) => {
        const file = String.fromCharCode(97 + parseInt(x)); // 0->a, 1->b, etc.
        const rank = parseInt(y) + 1;
        return `${file}${rank}`;
    });
}

// Helper function to format time in milliseconds to minutes:seconds
function formatTime(ms: number): string {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

// Helper function to get game result display text
function getGameResultText(gameResult: any): string {
    if (gameResult.status === "ongoing") return "";
    if (gameResult.status === "checkmate") return `Checkmate - ${gameResult.winner} wins`;
    if (gameResult.status === "draw") return `Draw - ${gameResult.reason}`;
    if (gameResult.status === "resignation") return `Resignation - ${gameResult.winner} wins`;
    if (gameResult.status === "timeout") return `Time out - ${gameResult.winner} wins`;
    return "Game Over";
}

export function GamePage() {
    const { gameId } = useParams();
    const navigate = useNavigate();
    const { game, loading, move, refresh, gameEnded, clearGameEndNotification, setGameDirect } = useGame(gameId!);
    const [moveError, setMoveError] = React.useState<string | null>(null);
    const [saveMessage, setSaveMessage] = React.useState<string | null>(null);
    const [exportMessage, setExportMessage] = React.useState<string | null>(null);
    const [notification, setNotification] = React.useState<string>("Welcome to the game!");
    const [showExportDialog, setShowExportDialog] = React.useState(false);
    const [showGameEndDialog, setShowGameEndDialog] = React.useState(false);
    const [exportForm, setExportForm] = React.useState({ event: "Web Game", site: "Online" });
    const moveHistoryRef = React.useRef<HTMLDivElement>(null);

    // Show game end dialog when game ends
    React.useEffect(() => {
        if (gameEnded) {
            setShowGameEndDialog(true);
        }
    }, [gameEnded]);

    async function handleMove(from: string, to: string, promotion?: string | null, castling?: string | null) {
        setMoveError(null);
        setSaveMessage(null);
        setNotification(`Move: ${from} → ${to}${promotion ? ` (${promotion})` : ''}${castling ? ` [${castling} castling]` : ''}`);
        try {
            await move(from, to, promotion, castling);
            setNotification(`Move successful: ${from} → ${to}${castling ? ` [${castling} castling]` : ''}`);
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

    async function handleResign(color: string) {
        setMoveError(null);
        setSaveMessage(null);
        setNotification(`Resigning as ${color}...`);
        try {
            const response = await resign(gameId!, color);
            setNotification(`Resigned as ${color}`);
            setGameDirect(response);
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to resign");
            setNotification("Failed to resign");
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
                        turn={game.turn}
                        gameOver={game.gameResult.status !== "ongoing"}
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
                        {game.gameResult.status !== "ongoing" && (
                            <p style={{ color: "#ff6b6b", fontWeight: "bold" }}>
                                {getGameResultText(game.gameResult)}
                            </p>
                        )}
                        {game.whiteTime && game.blackTime && (
                            <div style={{
                                display: "flex",
                                gap: "1rem",
                                marginTop: "0.5rem"
                            }}>
                                <div style={{
                                    flex: 1,
                                    padding: "0.75rem",
                                    backgroundColor: game.turn === "White" ? "#2d4a3e" : "#2C2C2C",
                                    borderRadius: "6px",
                                    border: game.turn === "White" ? "2px solid #4ec9b0" : "1px solid #444"
                                }}>
                                    <div style={{ fontSize: "0.75rem", color: "#888", marginBottom: "0.25rem" }}>White</div>
                                    <div style={{
                                        fontSize: "1.25rem",
                                        fontWeight: "bold",
                                        color: game.whiteTime.remainingTimeMs !== null && game.whiteTime.remainingTimeMs < 30000
                                            ? "#ff6b6b"
                                            : game.turn === "White" ? "#4ec9b0" : "#e0e0e0",
                                        fontFamily: "monospace"
                                    }}>
                                        {formatTime(game.whiteTime.remainingTimeMs ?? 0)}
                                    </div>
                                    {game.turn === "White" && (
                                        <div style={{ fontSize: "0.7rem", color: "#4ec9b0", marginTop: "0.25rem" }}>Your turn</div>
                                    )}
                                </div>
                                <div style={{
                                    flex: 1,
                                    padding: "0.75rem",
                                    backgroundColor: game.turn === "Black" ? "#2d4a3e" : "#2C2C2C",
                                    borderRadius: "6px",
                                    border: game.turn === "Black" ? "2px solid #4ec9b0" : "1px solid #444"
                                }}>
                                    <div style={{ fontSize: "0.75rem", color: "#888", marginBottom: "0.25rem" }}>Black</div>
                                    <div style={{
                                        fontSize: "1.25rem",
                                        fontWeight: "bold",
                                        color: game.blackTime.remainingTimeMs !== null && game.blackTime.remainingTimeMs < 30000
                                            ? "#ff6b6b"
                                            : game.turn === "Black" ? "#4ec9b0" : "#e0e0e0",
                                        fontFamily: "monospace"
                                    }}>
                                        {formatTime(game.blackTime.remainingTimeMs ?? 0)}
                                    </div>
                                    {game.turn === "Black" && (
                                        <div style={{ fontSize: "0.7rem", color: "#4ec9b0", marginTop: "0.25rem" }}>Your turn</div>
                                    )}
                                </div>
                            </div>
                        )}
                        {(!game.whiteTime || !game.blackTime) && (
                            <p style={{ color: "#b0b0b0" }}>
                                <strong>Time:</strong> Unlimited
                            </p>
                        )}
                    </div>

                    <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                        <button onClick={handleSave} disabled={game.gameResult.status !== "ongoing"}>
                            Save Game
                        </button>
                        <button onClick={() => setShowExportDialog(true)} disabled={game.gameResult.status !== "ongoing"}>
                            Export PGN
                        </button>
                        <button onClick={refresh} className="secondary">
                            Refresh
                        </button>
                        {game.gameResult.status === "ongoing" && (
                            <>
                                <button onClick={() => handleResign("white")} className="secondary">
                                    Resign (White)
                                </button>
                                <button onClick={() => handleResign("black")} className="secondary">
                                    Resign (Black)
                                </button>
                            </>
                        )}
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

            {/* Game End Dialog */}
            {showGameEndDialog && gameEnded && (
                <div style={{
                    position: "fixed",
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundColor: "rgba(0, 0, 0, 0.7)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    zIndex: 2000
                }}>
                    <div style={{
                        background: "#1E1E1E",
                        padding: "2.5rem",
                        borderRadius: "12px",
                        border: "2px solid #ff6b6b",
                        minWidth: "350px",
                        maxWidth: "500px",
                        textAlign: "center"
                    }}>
                        <h2 style={{ color: "#ff6b6b", marginBottom: "1rem", fontSize: "1.75rem" }}>
                            Game Over!
                        </h2>
                        <div style={{ marginBottom: "1.5rem", color: "#e0e0e0", fontSize: "1.1rem" }}>
                            {gameEnded.status === "checkmate" && (
                                <p>Checkmate! <strong style={{ color: "#4ec9b0" }}>{gameEnded.winner}</strong> wins!</p>
                            )}
                            {gameEnded.status === "resignation" && (
                                <p>Resignation! <strong style={{ color: "#4ec9b0" }}>{gameEnded.winner}</strong> wins!</p>
                            )}
                            {gameEnded.status === "timeout" && (
                                <p>Time out! <strong style={{ color: "#4ec9b0" }}>{gameEnded.winner}</strong> wins!</p>
                            )}
                            {gameEnded.status === "draw" && (
                                <p>Draw! Reason: <strong style={{ color: "#ffd93d" }}>{gameEnded.reason}</strong></p>
                            )}
                        </div>
                        <div style={{ display: "flex", gap: "1rem", justifyContent: "center" }}>
                            <button
                                onClick={() => {
                                    setShowGameEndDialog(false);
                                    clearGameEndNotification();
                                }}
                                style={{ minWidth: "120px" }}
                            >
                                Continue
                            </button>
                            <button
                                onClick={() => navigate("/")}
                                className="secondary"
                                style={{ minWidth: "120px" }}
                            >
                                Back to Menu
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}