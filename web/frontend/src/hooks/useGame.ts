import { useEffect, useState, useRef, useCallback } from "react";
import { getGame, makeMove, getGameStatus, playBotMove, pauseGame, resumeGame } from "../utils/apiClient";
import type { GameResponse } from "../types/chess";

export function useGame(gameId: string) {
    const [game, setGame] = useState<GameResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [gameEnded, setGameEnded] = useState<{ status: string; winner: string | null; reason: string | null } | null>(null);
    const lastGameResultRef = useRef<string | null>(null);
    // Track when server data was received so we can compute local countdown offset
    const serverTimestampRef = useRef<number>(Date.now());
    // Guard against concurrent fetches
    const fetchingRef = useRef(false);
    // Track current game state to avoid stale closures in callbacks
    const gameRef = useRef<GameResponse | null>(null);

    function detectGameEnd(data: { gameResult: { status: string; winner: string | null; reason: string | null } }) {
        const wasOngoingOrFirstLoad = lastGameResultRef.current === "ongoing" || lastGameResultRef.current === null;
        if (data.gameResult.status !== "ongoing" && wasOngoingOrFirstLoad) {
            setGameEnded({
                status: data.gameResult.status,
                winner: data.gameResult.winner,
                reason: data.gameResult.reason
            });
        }
        lastGameResultRef.current = data.gameResult.status;
    }

    async function refresh() {
        if (fetchingRef.current) return;
        fetchingRef.current = true;
        setError(null);
        try {
            const data = await getGame(gameId);
            serverTimestampRef.current = Date.now();
            detectGameEnd(data);
            setGame(data);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load game");
        } finally {
            fetchingRef.current = false;
        }
    }

    async function move(from: string, to: string, promotion?: string | null, castling?: string | null) {
        setError(null);
        try {
            const data = await makeMove(gameId, from, to, promotion, castling);
            serverTimestampRef.current = Date.now();
            detectGameEnd(data);
            setGame(data);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to make move");
            throw e;
        }
    }

    async function botMove(botType: string) {
        setError(null);
        try {
            const data = await playBotMove(gameId, botType);
            serverTimestampRef.current = Date.now();
            detectGameEnd(data);
            setGame(data);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to play bot move");
            throw e;
        }
    }

    // Check for timeout on the server (uses status endpoint which is lighter)
    const checkTimeout = useCallback(async () => {
        const currentGame = gameRef.current;
        if (!currentGame || currentGame.gameResult.status !== "ongoing") return;
        if (fetchingRef.current) return;
        fetchingRef.current = true;

        try {
            const status = await getGameStatus(gameId);
            if (status.gameResult.status !== "ongoing") {
                if (lastGameResultRef.current === "ongoing") {
                    setGameEnded({
                        status: status.gameResult.status,
                        winner: status.gameResult.winner,
                        reason: status.gameResult.reason
                    });
                }
                lastGameResultRef.current = status.gameResult.status;
                // Fetch full state after timeout detected
                fetchingRef.current = false;
                await refresh();
                return;
            }
        } catch (e) {
            // Ignore timeout check errors
        } finally {
            fetchingRef.current = false;
        }
    }, [gameId]);

    useEffect(() => {
        refresh().finally(() => setLoading(false));
    }, [gameId]);

    // Auto-pause when user leaves the page (browser close, tab close, or navigation)
    useEffect(() => {
        const currentGame = gameRef.current;
        if (!currentGame || currentGame.gameResult.status !== "ongoing" || currentGame.isPaused) return;
        if (!currentGame.whiteTime && !currentGame.blackTime) return; // No time control

        const handleBeforeUnload = () => {
            // Use sendBeacon for reliable delivery during page unload
            navigator.sendBeacon(`/v1/chess/games/${gameId}/pause`, "");
        };

        window.addEventListener("beforeunload", handleBeforeUnload);

        return () => {
            window.removeEventListener("beforeunload", handleBeforeUnload);
            // Also pause when navigating away within the app (React cleanup)
            const g = gameRef.current;
            if (g && g.gameResult.status === "ongoing" && !g.isPaused && (g.whiteTime || g.blackTime)) {
                navigator.sendBeacon(`/v1/chess/games/${gameId}/pause`, "");
            }
        };
    }, [game?.gameResult.status, game?.isPaused, gameId]);

    // SSE: subscribe to real-time game state events instead of polling
    useEffect(() => {
        if (!game || game.gameResult.status !== "ongoing") return;
        if (game.isPaused) return;

        const eventSource = new EventSource(`/v1/chess/games/${gameId}/events`);

        eventSource.addEventListener("game-state", (event: MessageEvent) => {
            try {
                const data = JSON.parse(event.data) as GameResponse;
                serverTimestampRef.current = Date.now();
                detectGameEnd(data);
                setGame(data);
            } catch {
                // Ignore malformed events
            }
        });

        eventSource.addEventListener("error", () => {
            // EventSource reconnects automatically; fall back to a single poll
            checkTimeout();
        });

        return () => eventSource.close();
    }, [game?.gameResult.status, game?.isPaused, gameId, checkTimeout]);

    async function pause() {
        if (fetchingRef.current) return;
        fetchingRef.current = true;
        setError(null);
        try {
            const data = await pauseGame(gameId);
            serverTimestampRef.current = Date.now();
            setGame(data);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to pause game");
            throw e;
        } finally {
            fetchingRef.current = false;
        }
    }

    async function resume() {
        if (fetchingRef.current) return;
        fetchingRef.current = true;
        setError(null);
        try {
            const data = await resumeGame(gameId);
            serverTimestampRef.current = Date.now();
            setGame(data);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to resume game");
            throw e;
        } finally {
            fetchingRef.current = false;
        }
    }

    // Local countdown effect: computes display time without mutating the server-sourced game state.
    // Instead we store a separate "display game" derived from the server game + elapsed offset.
    const [displayGame, setDisplayGame] = useState<GameResponse | null>(null);

    useEffect(() => {
        gameRef.current = game;
        setDisplayGame(game);
    }, [game]);

    useEffect(() => {
        const currentGame = gameRef.current;
        if (!currentGame || currentGame.gameResult.status !== "ongoing") return;
        if (!currentGame.whiteTime || !currentGame.blackTime) return;
        // Do not tick down while paused
        if (currentGame.isPaused) return;

        const countdown = setInterval(() => {
            const latestGame = gameRef.current;
            if (!latestGame || !latestGame.whiteTime || !latestGame.blackTime) return;
            if (latestGame.isPaused) return;

            const elapsed = Date.now() - serverTimestampRef.current;
            const isWhiteTurn = latestGame.turn === "White";

            setDisplayGame({
                ...latestGame,
                whiteTime: {
                    ...latestGame.whiteTime,
                    remainingTimeMs: Math.max(0, latestGame.whiteTime.remainingTimeMs - (isWhiteTurn ? elapsed : 0))
                },
                blackTime: {
                    ...latestGame.blackTime,
                    remainingTimeMs: Math.max(0, latestGame.blackTime.remainingTimeMs - (!isWhiteTurn ? elapsed : 0))
                }
            });
        }, 200);

        return () => clearInterval(countdown);
    }, [game?.gameResult.status, game?.whiteTime, game?.blackTime, game?.isPaused]);

    const clearGameEndNotification = useCallback(() => {
        setGameEnded(null);
    }, []);

    // Set game state directly (used by external updates like resign)
    const setGameDirect = useCallback((newGame: GameResponse) => {
        serverTimestampRef.current = Date.now();
        setGame(newGame);
        lastGameResultRef.current = newGame.gameResult.status;
        if (newGame.gameResult.status !== "ongoing") {
            setGameEnded({
                status: newGame.gameResult.status,
                winner: newGame.gameResult.winner,
                reason: newGame.gameResult.reason
            });
        }
    }, []);

    return { game: displayGame, loading, error, move, botMove, refresh, gameEnded, clearGameEndNotification, setGameDirect, pause, resume };
}