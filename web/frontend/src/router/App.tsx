import React, { createContext, useContext, useEffect, useState } from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { MenuPage } from "../pages/MenuPage";
import { GamePage } from "../pages/GamePage";
import { GameList } from "../components/GameList";
import { LeaderboardPage } from "../pages/LeaderboardPage";
import { AnalyticsPage } from "../pages/AnalyticsPage";
import { PlayersPage } from "../pages/PlayersPage";
import { TournamentPage } from "../pages/TournamentPage";
import { AnalyticsExportPage } from "../pages/AnalyticsExportPage";
import { OpeningCatalogPage } from "../pages/OpeningCatalogPage";
import { SpectatePage } from "../pages/SpectatePage";
import { EngineManagementPage } from "../pages/EngineManagementPage";

type Theme = "dark" | "light";

interface ThemeContextType {
    theme: Theme;
    toggleTheme: () => void;
}

export const ThemeContext = createContext<ThemeContextType>({
    theme: "dark",
    toggleTheme: () => {},
});

export function useTheme() {
    return useContext(ThemeContext);
}

function ThemeProvider({ children }: { children: React.ReactNode }) {
    const [theme, setTheme] = useState<Theme>(() => {
        return (localStorage.getItem("chess-theme") as Theme) || "dark";
    });

    useEffect(() => {
        document.documentElement.setAttribute("data-theme", theme === "light" ? "light" : "");
        localStorage.setItem("chess-theme", theme);
    }, [theme]);

    const toggleTheme = () => setTheme(t => t === "dark" ? "light" : "dark");

    return (
        <ThemeContext.Provider value={{ theme, toggleTheme }}>
            {children}
        </ThemeContext.Provider>
    );
}

export default function App() {
    return (
        <ThemeProvider>
            <BrowserRouter>
                <Routes>
                    <Route path="/" element={<MenuPage />} />
                    <Route path="/games" element={<GameList />} />
                    <Route path="/game/:gameId" element={<GamePage />} />
                    <Route path="/spectate/:gameId" element={<SpectatePage />} />
                    <Route path="/leaderboard" element={<LeaderboardPage />} />
                    <Route path="/analytics" element={<AnalyticsPage />} />
                    <Route path="/analytics/export" element={<AnalyticsExportPage />} />
                    <Route path="/openings" element={<OpeningCatalogPage />} />
                    <Route path="/engines" element={<EngineManagementPage />} />
                    <Route path="/players" element={<PlayersPage />} />
                    <Route path="/tournaments" element={<TournamentPage />} />
                </Routes>
            </BrowserRouter>
        </ThemeProvider>
    );
}