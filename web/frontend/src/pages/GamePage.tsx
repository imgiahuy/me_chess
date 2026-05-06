import React from "react";
import { useParams } from "react-router-dom";
import { useGame } from "../hooks/useGame";
import { Board } from "../components/Board.tsx";

export function GamePage() {
    const { gameId } = useParams();
    const { game, loading, move } = useGame(gameId!); // ✅ include move

    if (loading) return <div>Loading...</div>;
    if (!game) return <div>No game found</div>;

    return (
        <div>
            <h2>Turn: {game.currentTurn}</h2>
            <Board
                fen={game.board}
                onMove={(from, to) => move(from, to)}
            />
        </div>
    );
}