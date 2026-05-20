export type GameResponse = {
    gameId: string;
    fen: string;
    turn: "White" | "Black";
    moveHistory: string[];
    isGameOver: boolean;
    winner: string | null;
};

export type GameSummary = {
    gameId: string;
    turn: "White" | "Black";
    isGameOver: boolean;
    moveCount: number;
};

export type GamesListResponse = {
    games: GameSummary[];
    total: number;
};

export type MoveRequest = {
    from: string;
    to: string;
    promotion?: string | null;
};

export type CreateGameResponse = {
    gameId: string;
    message: string;
};

export type ActionResponse = {
    message: string;
};

export type ErrorResponse = {
    error: string;
};