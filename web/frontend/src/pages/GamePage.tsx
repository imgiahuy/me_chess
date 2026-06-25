import React from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { useGame } from "../hooks/useGame";
import { Board } from "../components/Board";
import { saveGame, exportPgn, resign, getAvailableBots } from "../utils/apiClient";
import { useTheme } from "../router/App";
import { fenToBoard } from "../utils/fen";
import type { BotInfo } from "../types/chess";

// Helper function to convert Position(x,y) to algebraic notation
function convertPositionToAlgebraic(message: string): string {
    return message.replace(/Position\((\d+),(\d+)\)/g, (_match, x, y) => {
        const file = String.fromCharCode(97 + parseInt(x)); // 0->a, 1->b, etc.
        const rank = parseInt(y) + 1;
        return `${file}${rank}`;
    });
}

function formatTime(ms: number | undefined | null): string {
    if (ms == null || isNaN(ms) || ms < 0) return "0:00";
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

function getGameResultText(gameResult: any): string {
    if (gameResult.status === "ongoing") return "";
    if (gameResult.status === "checkmate") return `Checkmate · ${gameResult.winner} wins`;
    if (gameResult.status === "draw") return `Draw · ${gameResult.reason}`;
    if (gameResult.status === "resignation") return `Resignation · ${gameResult.winner} wins`;
    if (gameResult.status === "timeout") return `Timeout · ${gameResult.winner} wins`;
    return "Game Over";
}

const PIECE_SYMBOLS: Record<string, string> = {
    'K': '♔', 'Q': '♕', 'R': '♖', 'B': '♗', 'N': '♘', 'P': '♙',
    'k': '♚', 'q': '♛', 'r': '♜', 'b': '♝', 'n': '♞', 'p': '♟',
};

const PIECE_VALUES: Record<string, number> = { p: 1, n: 3, b: 3, r: 5, q: 9, k: 0 };

function getCapturedPieces(fen: string): { whiteCaptured: string[]; blackCaptured: string[] } {
    const startCounts: Record<string, number> = { p: 8, n: 2, b: 2, r: 2, q: 1, k: 1, P: 8, N: 2, B: 2, R: 2, Q: 1, K: 1 };
    const board = fenToBoard(fen);
    const currentCounts: Record<string, number> = {};
    board.flat().forEach(piece => { if (piece) currentCounts[piece] = (currentCounts[piece] || 0) + 1; });
    const whiteCaptured: string[] = [];
    const blackCaptured: string[] = [];
    Object.entries(startCounts).forEach(([piece, count]) => {
        const remaining = currentCounts[piece] || 0;
        const captured = count - remaining;
        for (let i = 0; i < captured; i++) {
            if (piece === piece.toUpperCase()) blackCaptured.push(piece);
            else whiteCaptured.push(piece);
        }
    });
    const order = ['q', 'r', 'b', 'n', 'p'];
    const sort = (arr: string[]) => arr.sort((a, b) => order.indexOf(a.toLowerCase()) - order.indexOf(b.toLowerCase()));
    return { whiteCaptured: sort(whiteCaptured), blackCaptured: sort(blackCaptured) };
}

function materialScore(pieces: string[]): number {
    return pieces.reduce((sum, p) => sum + (PIECE_VALUES[p.toLowerCase()] || 0), 0);
}

function CapturedPieces({ fen }: { fen: string }) {
    const { whiteCaptured, blackCaptured } = getCapturedPieces(fen);
    const whiteScore = materialScore(whiteCaptured);
    const blackScore = materialScore(blackCaptured);
    const diff = whiteScore - blackScore;

    return (
        <div className="card" style={{ marginBottom: "0.75rem" }}>
            <p style={{ fontSize: "0.7rem", fontWeight: 600, letterSpacing: "0.08em", textTransform: "uppercase", color: "var(--color-text-dim)", marginBottom: "0.6rem" }}>Captured Pieces</p>
            {[
                { label: "By White", pieces: whiteCaptured, score: whiteScore, advantage: diff > 0 ? `+${diff}` : null },
                { label: "By Black", pieces: blackCaptured, score: blackScore, advantage: diff < 0 ? `+${Math.abs(diff)}` : null },
            ].map(({ label, pieces, advantage }) => (
                <div key={label} style={{ marginBottom: "0.4rem" }}>
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "0.2rem" }}>
                        <span style={{ fontSize: "0.7rem", color: "var(--color-text-dim)" }}>{label}</span>
                        {advantage && <span style={{ fontSize: "0.65rem", fontWeight: 700, color: "var(--color-success)", background: "var(--color-success-bg)", padding: "0 5px", borderRadius: "999px" }}>{advantage}</span>}
                    </div>
                    <div style={{ display: "flex", flexWrap: "wrap", gap: "1px", minHeight: "1.4rem" }}>
                        {pieces.length === 0
                            ? <span style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", fontStyle: "italic" }}>—</span>
                            : pieces.map((p, i) => (
                                <span key={i} style={{ fontSize: "1rem", lineHeight: 1, opacity: 0.85, color: p === p.toUpperCase() ? "var(--color-text)" : "var(--color-text-muted)" }}>
                                    {PIECE_SYMBOLS[p] || p}
                                </span>
                            ))
                        }
                    </div>
                </div>
            ))}
        </div>
    );
}

function ClockCard({ label, timeMs, isActive, isLow }: { label: string; timeMs: number; isActive: boolean; isLow: boolean }) {
    return (
        <div style={{
            flex: 1, padding: "0.75rem 0.875rem",
            background: isActive ? "var(--color-bg-3)" : "var(--color-surface-2)",
            borderRadius: "var(--radius-sm)",
            border: `1px solid ${isActive ? "var(--color-teal)" : "var(--color-border)"}`,
            transition: "all 0.2s",
        }}>
            <div style={{ fontSize: "0.7rem", color: "var(--color-text-dim)", marginBottom: "0.2rem" }}>{label}</div>
            <div style={{
                fontSize: "1.375rem", fontWeight: 700, fontFamily: "monospace",
                color: isLow ? "var(--color-danger)" : isActive ? "var(--color-teal)" : "var(--color-text)",
                letterSpacing: "0.04em",
            }}>
                {formatTime(timeMs)}
            </div>
            {isActive && <div style={{ fontSize: "0.65rem", color: "var(--color-teal)", marginTop: "0.2rem" }}>▶ Active</div>}
        </div>
    );
}

export function GamePage() {
    const { gameId } = useParams();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const { game, loading, move, botMove, refresh, gameEnded, clearGameEndNotification, setGameDirect, pause, resume } = useGame(gameId!);
    const [moveError, setMoveError] = React.useState<string | null>(null);
    const [saveMessage, setSaveMessage] = React.useState<string | null>(null);

    const [notification, setNotification] = React.useState<string>("Welcome to the game!");
    const [showExportDialog, setShowExportDialog] = React.useState(false);
    const [showGameEndDialog, setShowGameEndDialog] = React.useState(false);
    const [exportForm, setExportForm] = React.useState({ event: "Web Game", site: "Online" });
    const moveHistoryRef = React.useRef<HTMLDivElement>(null);

    // Bot configuration from URL params
    const whiteBotType = searchParams.get("whiteBot");
    const blackBotType = searchParams.get("blackBot");
    const [availableBots, setAvailableBots] = React.useState<BotInfo[]>([]);
    const [selectedBot, setSelectedBot] = React.useState<string>("random");
    const [isBotMoving, setIsBotMoving] = React.useState(false);
    const [moveSuggestion, setMoveSuggestion] = React.useState<{ bestMove: string; score: string } | null>(null);
    const [isAnalyzing, setIsAnalyzing] = React.useState(false);

    // Load available bots
    React.useEffect(() => {
        getAvailableBots().then(res => setAvailableBots(res.bots || [])).catch(() => setAvailableBots([]));
    }, []);

    // Set default bot from URL param
    React.useEffect(() => {
        const currentTurnBot = game?.turn === "White" ? whiteBotType : blackBotType;
        if (currentTurnBot) {
            setSelectedBot(currentTurnBot);
        }
    }, [game?.turn, whiteBotType, blackBotType]);

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

    async function handleBackToMenu() {
        if (game && game.gameResult.status === "ongoing" && !game.isPaused && (game.whiteTime || game.blackTime)) {
            try { await pause(); } catch { /* best-effort */ }
        }
        navigate("/");
    }

    async function handlePause() {
        setMoveError(null);
        setSaveMessage(null);
        setNotification("Pausing game...");
        try {
            await pause();
            setNotification("Game paused");
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to pause game");
            setNotification("Failed to pause game");
        }
    }

    async function handleResume() {
        setMoveError(null);
        setSaveMessage(null);
        setNotification("Resuming game...");
        try {
            await resume();
            setNotification("Game resumed");
        } catch (e) {
            setMoveError(e instanceof Error ? e.message : "Failed to resume game");
            setNotification("Failed to resume game");
        }
    }

    async function handleBotMove(botType?: string) {
        const botToUse = botType || selectedBot;
        if (!botToUse || isBotMoving) return;

        setIsBotMoving(true);
        setMoveError(null);
        setSaveMessage(null);
        setNotification(`Bot (${botToUse}) is thinking...`);
        try {
            await botMove(botToUse);
            setNotification(`Bot (${botToUse}) made a move`);
        } catch (e) {
            const errorMsg = e instanceof Error ? e.message : "Bot move failed";
            setMoveError(errorMsg);
            setNotification(`Bot error: ${errorMsg}`);
        } finally {
            setIsBotMoving(false);
        }
    }

    async function handleGetMoveSuggestion() {
        if (!game) return;
        setIsAnalyzing(true);
        setMoveError(null);
        setNotification("Analyzing position with Stockfish...");
        try {
            const response = await fetch("/v1/chess/suggest", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    fen: game.fen,
                    engineName: "stockfish",
                    depth: 15
                })
            });
            if (!response.ok) throw new Error("Failed to get move suggestion");
            const data = await response.json();
            setMoveSuggestion({ bestMove: data.bestMove, score: data.score });
            setNotification(`Stockfish suggests: ${data.bestMove} (${data.score})`);
        } catch (e) {
            const errorMsg = e instanceof Error ? e.message : "Analysis failed";
            setMoveError(errorMsg);
            setNotification(errorMsg);
        } finally {
            setIsAnalyzing(false);
        }
    }

    // Auto-trigger bot move when it's bot's turn (skip when paused)
    React.useEffect(() => {
        if (!game || game.gameResult.status !== "ongoing" || isBotMoving || game.isPaused) return;

        const currentTurnBot = game.turn === "White" ? whiteBotType : blackBotType;
        if (currentTurnBot) {
            const timer = setTimeout(() => {
                handleBotMove(currentTurnBot);
            }, 500); // Small delay for better UX
            return () => clearTimeout(timer);
        }
    }, [game?.turn, game?.gameResult.status, game?.isPaused, whiteBotType, blackBotType, isBotMoving]);

    // Fallback: if it's bot vs bot and no move happened for 5 seconds, force a retry
    React.useEffect(() => {
        if (!game || game.gameResult.status !== "ongoing" || game.isPaused) return;
        if (!whiteBotType && !blackBotType) return; // Not a bot vs bot game

        const currentTurnBot = game.turn === "White" ? whiteBotType : blackBotType;
        if (!currentTurnBot) return; // Current player is human

        const timer = setTimeout(() => {
            if (!isBotMoving) {
                // Force a bot move if stuck
                handleBotMove(currentTurnBot);
            }
        }, 5000); // 5 second timeout

        return () => clearTimeout(timer);
    }, [game?.turn, game?.gameResult.status, game?.isPaused, whiteBotType, blackBotType, isBotMoving]);

    const { theme, toggleTheme } = useTheme();
    const isOngoing = game?.gameResult.status === "ongoing";

    if (loading) return <div className="loading">Loading game…</div>;
    if (!game) return <div className="error">No game found</div>;

    // Pair moves: [white, black?]
    const movePairs: [string, string | undefined][] = [];
    for (let i = 0; i < game.moveHistory.length; i += 2) {
        movePairs.push([game.moveHistory[i], game.moveHistory[i + 1]]);
    }

    return (
        <div style={{ minHeight: "100vh", background: "var(--color-bg)" }}>
            {/* Top navbar */}
            <div style={{
                display: "flex", alignItems: "center", gap: "0.75rem",
                padding: "0.75rem 1.5rem",
                background: "var(--color-surface)",
                borderBottom: "1px solid var(--color-border)",
                position: "sticky", top: 0, zIndex: 50,
            }}>
                <button onClick={handleBackToMenu} className="ghost" style={{ padding: "0.3rem 0.6rem" }}>← Menu</button>
                <span style={{ fontSize: "0.875rem", color: "var(--color-text-muted)", fontFamily: "monospace" }}>
                    #{game.gameId.slice(0, 8)}
                </span>
                {isOngoing && (
                    <span style={{
                        marginLeft: "0.25rem", padding: "0.15rem 0.6rem",
                        borderRadius: "999px", fontSize: "0.7rem", fontWeight: 600,
                        background: game.isPaused ? "var(--color-warning-bg)" : "var(--color-success-bg)",
                        color: game.isPaused ? "var(--color-warning)" : "var(--color-success)",
                        border: `1px solid ${game.isPaused ? "var(--color-warning)" : "var(--color-success)"}`,
                    }}>
                        {game.isPaused ? "⏸ Paused" : "● Live"}
                    </span>
                )}
                {!isOngoing && (
                    <span style={{ padding: "0.15rem 0.6rem", borderRadius: "999px", fontSize: "0.7rem", fontWeight: 600, background: "var(--color-danger-bg)", color: "var(--color-danger)", border: "1px solid var(--color-danger)" }}>
                        ◼ Ended
                    </span>
                )}
                <div style={{ marginLeft: "auto", display: "flex", gap: "0.5rem", alignItems: "center" }}>
                    {moveError && (
                        <span style={{ fontSize: "0.75rem", color: "var(--color-danger)", maxWidth: 260, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                            ⚠ {convertPositionToAlgebraic(moveError)}
                        </span>
                    )}
                    {saveMessage && <span style={{ fontSize: "0.75rem", color: "var(--color-success)" }}>✓ {saveMessage}</span>}
                    <button onClick={toggleTheme} className="ghost" style={{ padding: "0.3rem 0.55rem", fontSize: "0.95rem" }} title="Toggle theme">
                        {theme === "dark" ? "☀️" : "🌙"}
                    </button>
                    <button onClick={refresh} className="ghost" style={{ padding: "0.3rem 0.6rem", fontSize: "0.8rem" }}>↻ Refresh</button>
                </div>
            </div>

            {/* Main layout */}
            <div style={{ display: "flex", gap: "1.25rem", padding: "1.25rem 1.5rem", flexWrap: "wrap", alignItems: "flex-start" }}>
                {/* Board column */}
                <div style={{ display: "flex", flexDirection: "column", gap: "0.625rem" }}>
                    {/* Game result banner */}
                    {!isOngoing && (
                        <div style={{
                            padding: "0.6rem 1rem", borderRadius: "var(--radius-sm)",
                            background: "var(--color-danger-bg)", border: "1px solid var(--color-danger)",
                            color: "var(--color-danger)", fontWeight: 600, fontSize: "0.875rem", textAlign: "center",
                        }}>
                            {getGameResultText(game.gameResult)}
                        </div>
                    )}
                    <Board fen={game.fen} onMove={handleMove} turn={game.turn} gameOver={!isOngoing} />
                    <p style={{ fontSize: "0.7rem", color: "var(--color-text-dim)", textAlign: "center" }}>
                        {notification}
                    </p>
                </div>

                {/* Side panel */}
                <div style={{ flex: 1, minWidth: 280, maxWidth: 340, display: "flex", flexDirection: "column", gap: "0.75rem" }}>

                    {/* Status row */}
                    <div className="card" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0.75rem 1rem" }}>
                        <div>
                            <span style={{ fontSize: "0.7rem", color: "var(--color-text-dim)", textTransform: "uppercase", letterSpacing: "0.06em" }}>Turn</span>
                            <div style={{ fontWeight: 700, fontSize: "0.9375rem" }}>{game.turn}</div>
                        </div>
                        <div style={{ textAlign: "right" }}>
                            <span style={{ fontSize: "0.7rem", color: "var(--color-text-dim)", textTransform: "uppercase", letterSpacing: "0.06em" }}>Moves</span>
                            <div style={{ fontWeight: 700, fontSize: "0.9375rem" }}>{game.moveHistory.length}</div>
                        </div>
                    </div>

                    {/* Clocks */}
                    {game.whiteTime && game.blackTime && (
                        <div className="card" style={{ padding: "0.75rem 1rem" }}>
                            <p style={{ fontSize: "0.7rem", fontWeight: 600, letterSpacing: "0.08em", textTransform: "uppercase", color: "var(--color-text-dim)", marginBottom: "0.6rem" }}>Clocks</p>
                            <div style={{ display: "flex", gap: "0.5rem" }}>
                                <ClockCard label="⬜ White" timeMs={game.whiteTime.remainingTimeMs} isActive={isOngoing && game.turn === "White" && !game.isPaused} isLow={game.whiteTime.remainingTimeMs < 30000} />
                                <ClockCard label="⬛ Black" timeMs={game.blackTime.remainingTimeMs} isActive={isOngoing && game.turn === "Black" && !game.isPaused} isLow={game.blackTime.remainingTimeMs < 30000} />
                            </div>
                        </div>
                    )}
                    {(!game.whiteTime || !game.blackTime) && (
                        <div className="card" style={{ padding: "0.625rem 1rem", textAlign: "center" }}>
                            <span style={{ fontSize: "0.8rem", color: "var(--color-text-dim)" }}>∞ Unlimited time</span>
                        </div>
                    )}

                    {/* Captured pieces */}
                    <CapturedPieces fen={game.fen} />

                    {/* Action buttons */}
                    <div className="card" style={{ padding: "0.75rem 1rem" }}>
                        <p style={{ fontSize: "0.7rem", fontWeight: 600, letterSpacing: "0.08em", textTransform: "uppercase", color: "var(--color-text-dim)", marginBottom: "0.6rem" }}>Actions</p>
                        <div style={{ display: "flex", flexWrap: "wrap", gap: "0.4rem" }}>
                            <button onClick={handleSave} disabled={!isOngoing} style={{ fontSize: "0.8rem", padding: "0.4rem 0.75rem" }}>💾 Save</button>
                            <button onClick={() => setShowExportDialog(true)} className="secondary" style={{ fontSize: "0.8rem", padding: "0.4rem 0.75rem" }}>📄 PGN</button>
                            {isOngoing && (
                                game.isPaused
                                    ? <button onClick={handleResume} style={{ fontSize: "0.8rem", padding: "0.4rem 0.75rem", background: "var(--color-success)" }}>▶ Resume</button>
                                    : <button onClick={handlePause} style={{ fontSize: "0.8rem", padding: "0.4rem 0.75rem", background: "var(--color-warning)", color: "#111" }}>⏸ Pause</button>
                            )}
                            {isOngoing && (
                                <>
                                    <button onClick={() => handleResign("white")} className="secondary" style={{ fontSize: "0.8rem", padding: "0.4rem 0.75rem" }}>🏳 White</button>
                                    <button onClick={() => handleResign("black")} className="secondary" style={{ fontSize: "0.8rem", padding: "0.4rem 0.75rem" }}>🏳 Black</button>
                                </>
                            )}
                        </div>
                    </div>

                    {/* Bot controls */}
                    {isOngoing && (
                        <div className="card" style={{ padding: "0.75rem 1rem" }}>
                            <p style={{ fontSize: "0.7rem", fontWeight: 600, letterSpacing: "0.08em", textTransform: "uppercase", color: "var(--color-text-dim)", marginBottom: "0.6rem" }}>🤖 Bot</p>
                            <div style={{ display: "flex", gap: "0.5rem" }}>
                                <select value={selectedBot} onChange={e => setSelectedBot(e.target.value)} disabled={isBotMoving} style={{ flex: 1, fontSize: "0.8rem" }}>
                                    {availableBots.map(bot => (
                                        <option key={bot.id} value={bot.id}>{bot.name} ({bot.difficulty})</option>
                                    ))}
                                </select>
                                <button onClick={() => handleBotMove()} disabled={isBotMoving || game.isPaused} style={{ fontSize: "0.8rem", padding: "0.4rem 0.75rem", background: isBotMoving ? "var(--color-text-dim)" : "#7c3aed", flexShrink: 0 }}>
                                    {isBotMoving ? "…" : "Play"}
                                </button>
                            </div>
                        </div>
                    )}

                    {/* Move suggestion */}
                    <div className="card" style={{ padding: "0.75rem 1rem" }}>
                        <p style={{ fontSize: "0.7rem", fontWeight: 600, letterSpacing: "0.08em", textTransform: "uppercase", color: "var(--color-text-dim)", marginBottom: "0.6rem" }}>💡 Stockfish Analysis</p>
                        <button 
                            onClick={handleGetMoveSuggestion} 
                            disabled={isAnalyzing || !isOngoing}
                            style={{ 
                                fontSize: "0.8rem", 
                                padding: "0.4rem 0.75rem", 
                                background: isAnalyzing ? "var(--color-text-dim)" : "#10b981",
                                width: "100%",
                                marginBottom: moveSuggestion ? "0.5rem" : 0
                            }}
                        >
                            {isAnalyzing ? "Analyzing…" : "Get Suggestion"}
                        </button>
                        {moveSuggestion && (
                            <div style={{ 
                                background: "var(--color-bg-3)", 
                                padding: "0.5rem 0.75rem", 
                                borderRadius: "var(--radius-sm)",
                                marginTop: "0.5rem"
                            }}>
                                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.25rem" }}>
                                    <span style={{ fontSize: "0.75rem", color: "var(--color-text-dim)" }}>Best Move:</span>
                                    <span style={{ fontSize: "0.9rem", fontWeight: 700, fontFamily: "monospace", color: "var(--color-success)" }}>
                                        {moveSuggestion.bestMove}
                                    </span>
                                </div>
                                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                                    <span style={{ fontSize: "0.75rem", color: "var(--color-text-dim)" }}>Score:</span>
                                    <span style={{ fontSize: "0.85rem", fontWeight: 600, color: moveSuggestion.score.startsWith("+") ? "var(--color-success)" : moveSuggestion.score.startsWith("-") ? "var(--color-danger)" : "var(--color-text)" }}>
                                        {moveSuggestion.score}
                                    </span>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Move history */}
                    <div className="card" style={{ padding: "0.75rem 1rem" }}>
                        <p style={{ fontSize: "0.7rem", fontWeight: 600, letterSpacing: "0.08em", textTransform: "uppercase", color: "var(--color-text-dim)", marginBottom: "0.6rem" }}>Move History</p>
                        <div ref={moveHistoryRef} style={{ maxHeight: 200, overflowY: "auto", fontSize: "0.8rem" }}>
                            {movePairs.length === 0
                                ? <span style={{ color: "var(--color-text-dim)", fontStyle: "italic" }}>No moves yet</span>
                                : movePairs.map(([w, b], i) => (
                                    <div key={i} style={{ display: "flex", gap: "0.5rem", padding: "1px 0", borderBottom: "1px solid var(--color-border)" }}>
                                        <span style={{ color: "var(--color-text-dim)", width: "1.5rem", flexShrink: 0 }}>{i + 1}.</span>
                                        <span style={{ color: "var(--color-text)", flex: 1, fontFamily: "monospace" }}>{w}</span>
                                        <span style={{ color: "var(--color-text-muted)", flex: 1, fontFamily: "monospace" }}>{b ?? ""}</span>
                                    </div>
                                ))
                            }
                        </div>
                    </div>
                </div>
            </div>

            {/* Export PGN modal */}
            {showExportDialog && (
                <div onClick={e => { if (e.target === e.currentTarget) setShowExportDialog(false); }}
                    style={{ position: "fixed", inset: 0, backgroundColor: "rgba(0,0,0,0.6)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: "1rem" }}>
                    <div style={{ background: "var(--color-surface)", border: "1px solid var(--color-border)", borderRadius: "var(--radius-lg)", boxShadow: "var(--shadow-lg)", width: "100%", maxWidth: 380 }}>
                        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "1rem 1.25rem", borderBottom: "1px solid var(--color-border)" }}>
                            <h3 style={{ margin: 0 }}>Export to PGN</h3>
                            <button onClick={() => setShowExportDialog(false)} className="ghost" style={{ padding: "0.2rem 0.45rem" }}>✕</button>
                        </div>
                        <div style={{ padding: "1.25rem" }}>
                            <label style={{ display: "block", marginBottom: "0.35rem", fontSize: "0.8rem", color: "var(--color-text-muted)" }}>Event</label>
                            <input type="text" value={exportForm.event} onChange={e => setExportForm({ ...exportForm, event: e.target.value })} style={{ marginBottom: "0.875rem" }} />
                            <label style={{ display: "block", marginBottom: "0.35rem", fontSize: "0.8rem", color: "var(--color-text-muted)" }}>Site</label>
                            <input type="text" value={exportForm.site} onChange={e => setExportForm({ ...exportForm, site: e.target.value })} />
                        </div>
                        <div style={{ display: "flex", gap: "0.75rem", justifyContent: "flex-end", padding: "0.875rem 1.25rem", borderTop: "1px solid var(--color-border)" }}>
                            <button onClick={() => setShowExportDialog(false)} className="secondary">Cancel</button>
                            <button onClick={handleExportPgn}>Export ↓</button>
                        </div>
                    </div>
                </div>
            )}

            {/* Game end modal */}
            {showGameEndDialog && gameEnded && (
                <div style={{ position: "fixed", inset: 0, backgroundColor: "rgba(0,0,0,0.7)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 2000, padding: "1rem" }}>
                    <div style={{ background: "var(--color-surface)", border: "1px solid var(--color-border)", borderRadius: "var(--radius-lg)", boxShadow: "var(--shadow-lg)", padding: "2rem", maxWidth: 380, width: "100%", textAlign: "center" }}>
                        <div style={{ fontSize: "3rem", marginBottom: "0.75rem" }}>
                            {gameEnded.status === "draw" ? "🤝" : gameEnded.status === "checkmate" ? "♛" : "🏁"}
                        </div>
                        <h2 style={{ marginBottom: "0.5rem" }}>
                            {gameEnded.status === "draw" ? "Draw!" : `${gameEnded.winner} wins!`}
                        </h2>
                        <p style={{ color: "var(--color-text-muted)", marginBottom: "1.5rem", fontSize: "0.875rem" }}>
                            {gameEnded.status === "checkmate" && "by Checkmate"}
                            {gameEnded.status === "resignation" && "by Resignation"}
                            {gameEnded.status === "timeout" && "by Timeout"}
                            {gameEnded.status === "draw" && `Reason: ${gameEnded.reason}`}
                        </p>
                        <div style={{ display: "flex", gap: "0.75rem", justifyContent: "center" }}>
                            <button onClick={() => { setShowGameEndDialog(false); clearGameEndNotification(); }}>Continue</button>
                            <button onClick={handleBackToMenu} className="secondary">← Menu</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}