import React from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { MenuPage } from "../pages/MenuPage";
import { GamePage } from "../pages/GamePage";
import { GameList } from "../components/GameList";

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<MenuPage />} />
                <Route path="/games" element={<GameList />} />
                <Route path="/game/:gameId" element={<GamePage />} />
            </Routes>
        </BrowserRouter>
    );
}