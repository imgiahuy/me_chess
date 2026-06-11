export type GameResponse = {
    gameId: string;
    fen: string;
    turn: "White" | "Black";
    moveHistory: string[];
    gameResult: GameResultInfo;
    whiteTime: TimeControlInfo | null;
    blackTime: TimeControlInfo | null;
    legalMoves: string[] | null;
};

export type GameResultInfo = {
    status: string;  // "ongoing", "checkmate", "draw", "resignation", "timeout"
    reason: string | null;  // e.g., "stalemate", "insufficient material", etc.
    winner: string | null;
};

export type TimeControlInfo = {
    initialTimeMs: number;
    incrementMs: number;
    remainingTimeMs: number | null;
    delayMs: number;
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
    castling?: string | null;
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

export type CreateGameRequest = {
    whitePlayer: string;
    blackPlayer: string;
    timeControl?: string | null;
};

export type ResignRequest = {
    color: string;
};