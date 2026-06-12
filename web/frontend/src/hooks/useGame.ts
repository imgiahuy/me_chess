import { useEffect, useState, useRef, useCallback } from "react";
import { getGame, makeMove, getGameStatus } from "../utils/apiClient";
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

    // Check for timeout on the server (uses status endpoint which is lighter)
    const checkTimeout = useCallback(async () => {
        if (!game || game.gameResult.status !== "ongoing") return;
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
    }, [game, gameId]);

    useEffect(() => {
        refresh().finally(() => setLoading(false));
    }, [gameId]);

    // Poll for game state updates every 2 seconds
    useEffect(() => {
        if (!game || game.gameResult.status !== "ongoing") return;

        const interval = setInterval(() => {
            checkTimeout();
        }, 2000);

        return () => clearInterval(interval);
    }, [game?.gameResult.status, gameId, checkTimeout]);

    // Local countdown effect: computes display time without mutating the server-sourced game state.
    // Instead we store a separate "display game" derived from the server game + elapsed offset.
    const [displayGame, setDisplayGame] = useState<GameResponse | null>(null);

    useEffect(() => {
        setDisplayGame(game);
    }, [game]);

    useEffect(() => {
        if (!game || game.gameResult.status !== "ongoing") return;
        if (!game.whiteTime || !game.blackTime) return;

        const countdown = setInterval(() => {
            const elapsed = Date.now() - serverTimestampRef.current;
            const isWhiteTurn = game.turn === "White";

            setDisplayGame({
                ...game,
                whiteTime: game.whiteTime ? {
                    ...game.whiteTime,
                    remainingTimeMs: Math.max(0, game.whiteTime.remainingTimeMs - (isWhiteTurn ? elapsed : 0))
                } : null,
                blackTime: game.blackTime ? {
                    ...game.blackTime,
                    remainingTimeMs: Math.max(0, game.blackTime.remainingTimeMs - (!isWhiteTurn ? elapsed : 0))
                } : null
            });
        }, 200);

        return () => clearInterval(countdown);
    }, [game]);

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

    return { game: displayGame, loading, error, move, refresh, gameEnded, clearGameEndNotification, setGameDirect };
}