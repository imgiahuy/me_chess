import { useEffect, useState } from "react";
import { getGame, makeMove } from "../utils/apiClient";
import type { GameResponse } from "../types/chess";

export function useGame(gameId: string) {
    const [game, setGame] = useState<GameResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    async function refresh() {
        setError(null);
        try {
            const data = await getGame(gameId);
            setGame(data);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load game");
        }
    }

    async function move(from: string, to: string) {
        setError(null);
        try {
            const data = await makeMove(gameId, from, to);
            setGame(data);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to make move");
            throw e;
        }
    }

    useEffect(() => {
        refresh().finally(() => setLoading(false));
    }, [gameId]);

    return { game, loading, error, move, refresh };
}