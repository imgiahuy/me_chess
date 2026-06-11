import { useEffect, useState, useRef, useCallback } from "react";
import { getGame, makeMove, getGameStatus } from "../utils/apiClient";
import type { GameResponse, TimeControlInfo } from "../types/chess";

// Helper to get current remaining time from a TimeControlInfo with local countdown
function getCurrentRemainingTime(timeInfo: TimeControlInfo | null, isCurrentTurn: boolean): number | null {
    if (!timeInfo || timeInfo.remainingTimeMs === null) return null;
    if (!isCurrentTurn) return timeInfo.remainingTimeMs;

    // For the current player, count down locally between server updates
    // Subtract 1000ms (1 second) to approximate real-time countdown
    return Math.max(0, timeInfo.remainingTimeMs - 1000);
}

export function useGame(gameId: string) {
    const [game, setGame] = useState<GameResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [gameEnded, setGameEnded] = useState<{ status: string; winner: string | null; reason: string | null } | null>(null);
    const lastGameResultRef = useRef<string | null>(null);
    const countdownIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    async function refresh() {
        setError(null);
        try {
            const data = await getGame(gameId);

            // Detect game end: either transition from ongoing, or first load of a finished game
            const wasOngoingOrFirstLoad = lastGameResultRef.current === "ongoing" || lastGameResultRef.current === null;
            if (data.gameResult.status !== "ongoing" && wasOngoingOrFirstLoad) {
                setGameEnded({
                    status: data.gameResult.status,
                    winner: data.gameResult.winner,
                    reason: data.gameResult.reason
                });
            }
            lastGameResultRef.current = data.gameResult.status;

            setGame(data);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load game");
        }
    }

    async function move(from: string, to: string, promotion?: string | null, castling?: string | null) {
        setError(null);
        try {
            const data = await makeMove(gameId, from, to, promotion, castling);

            // Detect game end after move
            if (data.gameResult.status !== "ongoing" && lastGameResultRef.current === "ongoing") {
                setGameEnded({
                    status: data.gameResult.status,
                    winner: data.gameResult.winner,
                    reason: data.gameResult.reason
                });
            }
            lastGameResultRef.current = data.gameResult.status;

            setGame(data);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to make move");
            throw e;
        }
    }

    // Check for timeout on the server
    const checkTimeout = useCallback(async () => {
        if (!game || game.gameResult.status !== "ongoing") return;

        try {
            const status = await getGameStatus(gameId);
            if (status.gameResult.status !== "ongoing") {
                // Game ended due to timeout or other reason
                if (lastGameResultRef.current === "ongoing") {
                    setGameEnded({
                        status: status.gameResult.status,
                        winner: status.gameResult.winner,
                        reason: status.gameResult.reason
                    });
                }
                lastGameResultRef.current = status.gameResult.status;
                // Refresh full game data to get updated state
                refresh();
            }
        } catch (e) {
            // Ignore timeout check errors
        }
    }, [game, gameId]);

    useEffect(() => {
        refresh().finally(() => setLoading(false));
    }, [gameId]);

    // Poll for game state updates every 2 seconds and check for timeouts
    useEffect(() => {
        if (!game || game.gameResult.status !== "ongoing") {
            if (countdownIntervalRef.current) {
                clearInterval(countdownIntervalRef.current);
                countdownIntervalRef.current = null;
            }
            return;
        }

        const interval = setInterval(() => {
            refresh();
            checkTimeout();
        }, 2000);

        countdownIntervalRef.current = interval;

        return () => {
            clearInterval(interval);
            countdownIntervalRef.current = null;
        };
    }, [game, gameId, checkTimeout]);

    // Local countdown effect to make clocks appear to tick
    useEffect(() => {
        if (!game || game.gameResult.status !== "ongoing") return;

        const countdown = setInterval(() => {
            setGame(currentGame => {
                if (!currentGame || currentGame.gameResult.status !== "ongoing") return currentGame;

                const isWhiteTurn = currentGame.turn === "White";

                return {
                    ...currentGame,
                    whiteTime: currentGame.whiteTime ? {
                        ...currentGame.whiteTime,
                        remainingTimeMs: getCurrentRemainingTime(currentGame.whiteTime, isWhiteTurn)
                    } : null,
                    blackTime: currentGame.blackTime ? {
                        ...currentGame.blackTime,
                        remainingTimeMs: getCurrentRemainingTime(currentGame.blackTime, !isWhiteTurn)
                    } : null
                };
            });
        }, 1000);

        return () => clearInterval(countdown);
    }, [game?.turn, game?.gameResult.status]);

    const clearGameEndNotification = useCallback(() => {
        setGameEnded(null);
    }, []);

    // Set game state directly (used by external updates like resign)
    const setGameDirect = useCallback((newGame: GameResponse) => {
        setGame(newGame);
        // Sync the result ref so game-end detection works correctly
        lastGameResultRef.current = newGame.gameResult.status;
        // Trigger game-end dialog if the game is now over
        if (newGame.gameResult.status !== "ongoing") {
            setGameEnded({
                status: newGame.gameResult.status,
                winner: newGame.gameResult.winner,
                reason: newGame.gameResult.reason
            });
        }
    }, []);

    return { game, loading, error, move, refresh, gameEnded, clearGameEndNotification, setGameDirect };
}