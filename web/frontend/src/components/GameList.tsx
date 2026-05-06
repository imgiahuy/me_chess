import { useEffect, useState } from "react";
import { listGames } from "../utils/apiClient";
// @ts-ignore
import { Link } from "react-router-dom";
import React from "react";

export function GameList() {
    const [games, setGames] = useState<string[]>([]);

    useEffect(() => {
        listGames().then(data => setGames(data.games));
    }, []);

    return (
        <div>
            <h2>Active Games</h2>
            {games.map(id => (
                <div key={id}>
                    <Link to={`/game/${id}`}>{id}</Link>
                </div>
            ))}
        </div>
    );
}