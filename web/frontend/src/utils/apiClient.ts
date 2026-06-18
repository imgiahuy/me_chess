const BASE_URL = "/v1/chess";

export async function createGame(whitePlayer: string, blackPlayer: string, timeControl?: string | null) {
    const res = await fetch(`${BASE_URL}/games`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ whitePlayer, blackPlayer, timeControl }),
    });
    if (!res.ok) throw new Error(`Failed to create game: ${res.status}`);
    return res.json();
}

export async function getGame(gameId: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}`);
    if (!res.ok) throw new Error(`Failed to get game: ${res.status}`);
    return res.json();
}

export async function makeMove(gameId: string, from: string, to: string, promotion?: string | null, castling?: string | null) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/moves`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ from, to, promotion, castling }),
    });
    if (!res.ok) {
        const error = await res.json();
        throw new Error(error.error || `Failed to make move: ${res.status}`);
    }
    return res.json();
}

export async function listGames() {
    const res = await fetch(`${BASE_URL}/games`);
    if (!res.ok) throw new Error(`Failed to list games: ${res.status}`);
    return res.json();
}

export async function deleteGame(gameId: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}`, {
        method: "DELETE",
    });
    if (!res.ok) throw new Error(`Failed to delete game: ${res.status}`);
    return res.json();
}

export async function getGameStatus(gameId: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/status`);
    if (!res.ok) throw new Error(`Failed to get game status: ${res.status}`);
    return res.json();
}

export async function saveGame(gameId: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/save`, {
        method: "POST",
    });
    if (!res.ok) throw new Error(`Failed to save game: ${res.status}`);
    return res.json();
}

export async function loadGame(gameId: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/load`, {
        method: "POST",
    });
    if (!res.ok) throw new Error(`Failed to load game: ${res.status}`);
    return res.json();
}

export async function loadLatestGame() {
    const res = await fetch(`${BASE_URL}/games/load-latest`, {
        method: "POST",
    });
    if (!res.ok) throw new Error(`Failed to load latest game: ${res.status}`);
    return res.json();
}

export async function exportPgn(gameId: string, event: string, site: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/export`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ event, site }),
    });
    if (!res.ok) throw new Error(`Failed to export PGN: ${res.status}`);
    return res.json();
}

export async function resign(gameId: string, color: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/resign`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ color }),
    });
    if (!res.ok) {
        const error = await res.json();
        throw new Error(error.error || `Failed to resign: ${res.status}`);
    }
    return res.json();
}

export async function getAvailableBots() {
    const res = await fetch(`${BASE_URL}/bots`);
    if (!res.ok) throw new Error(`Failed to get bots: ${res.status}`);
    return res.json();
}

export async function getLeaderboard() {
    const res = await fetch(`${BASE_URL}/leaderboard`);
    if (!res.ok) throw new Error(`Failed to get leaderboard: ${res.status}`);
    return res.json();
}

export async function pauseGame(gameId: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/pause`, {
        method: "POST",
    });
    if (!res.ok) {
        const error = await res.json();
        throw new Error(error.error || `Failed to pause game: ${res.status}`);
    }
    return res.json();
}

export async function resumeGame(gameId: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/resume`, {
        method: "POST",
    });
    if (!res.ok) {
        const error = await res.json();
        throw new Error(error.error || `Failed to resume game: ${res.status}`);
    }
    return res.json();
}

export async function playBotMove(gameId: string, botType: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/bot-move`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ botType }),
    });
    if (!res.ok) {
        const error = await res.json();
        throw new Error(error.error || `Failed to play bot move: ${res.status}`);
    }
    return res.json();
}