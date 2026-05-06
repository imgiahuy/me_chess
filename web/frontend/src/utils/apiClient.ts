const BASE_URL = "/v1/chess";

export async function createGame() {
    const res = await fetch(`${BASE_URL}/games`, {
        method: "POST",
    });
    return res.json();
}

export async function getGame(gameId: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}`);
    return res.json();
}

export async function makeMove(gameId: string, from: string, to: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}/moves`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ from, to }),
    });
    return res.json();
}

export async function listGames() {
    const res = await fetch(`${BASE_URL}/games`);
    return res.json();
}

export async function deleteGame(gameId: string) {
    const res = await fetch(`${BASE_URL}/games/${gameId}`, {
        method: "DELETE",
    });
    return res.json();
}