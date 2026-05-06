import { useEffect, useState } from "react";
import { getGame, makeMove } from "../utils/apiClient";
import type {GameResponse} from "../types/chess";

export function useGame(gameId: string) {
    const [game, setGame] = useState<GameResponse | null>(null);
    const [loading, setLoading] = useState(true);

    async function refresh() {
        const data = await getGame(gameId);
        setGame(data);
    }

    async function move(from: string, to: string) {
        const data = await makeMove(gameId, from, to);
        setGame(data);
    }

    useEffect(() => {
        refresh().finally(() => setLoading(false));
    }, [gameId]);

    return { game, loading, move, refresh };
}