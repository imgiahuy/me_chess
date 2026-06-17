import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { MenuPage } from "../pages/MenuPage";
import { GamePage } from "../pages/GamePage";
import { GameList } from "../components/GameList";
import { LeaderboardPage } from "../pages/LeaderboardPage";

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<MenuPage />} />
                <Route path="/games" element={<GameList />} />
                <Route path="/game/:gameId" element={<GamePage />} />
                <Route path="/leaderboard" element={<LeaderboardPage />} />
            </Routes>
        </BrowserRouter>
    );
}