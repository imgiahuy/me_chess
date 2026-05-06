import React from "react";
import { useNavigate } from "react-router-dom";
import { createGame } from "../utils/apiClient";

export function MenuPage() {
    const navigate = useNavigate();

    async function handleCreate() {
        const res = await createGame();
        navigate(`/game/${res.gameId}`);
    }

    return (
        <div>
            <button onClick={handleCreate}>
                Create Game
            </button>
        </div>
    );
}