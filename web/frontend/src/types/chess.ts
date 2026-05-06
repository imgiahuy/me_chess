export type GameResponse = {
    board: string; // FEN
    currentTurn: "white" | "black";
    moveHistory: string[];
};