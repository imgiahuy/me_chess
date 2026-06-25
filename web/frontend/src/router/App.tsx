import React, { createContext, useContext, useEffect, useState } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
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
import { AuthPage } from "../pages/AuthPage";
import { getAccessToken } from "../utils/apiClient";

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

// Protected route wrapper
function ProtectedRoute({ children }: { children: React.ReactNode }) {
    const token = getAccessToken();
    if (!token) {
        return <Navigate to="/auth" replace />;
    }
    return <>{children}</>;
}

export default function App() {
    return (
        <ThemeProvider>
            <BrowserRouter>
                <Routes>
                    <Route path="/auth" element={<AuthPage />} />
                    <Route path="/" element={
                        <ProtectedRoute>
                            <MenuPage />
                        </ProtectedRoute>
                    } />
                    <Route path="/games" element={
                        <ProtectedRoute>
                            <GameList />
                        </ProtectedRoute>
                    } />
                    <Route path="/game/:gameId" element={
                        <ProtectedRoute>
                            <GamePage />
                        </ProtectedRoute>
                    } />
                    <Route path="/spectate/:gameId" element={
                        <ProtectedRoute>
                            <SpectatePage />
                        </ProtectedRoute>
                    } />
                    <Route path="/leaderboard" element={
                        <ProtectedRoute>
                            <LeaderboardPage />
                        </ProtectedRoute>
                    } />
                    <Route path="/analytics" element={
                        <ProtectedRoute>
                            <AnalyticsPage />
                        </ProtectedRoute>
                    } />
                    <Route path="/analytics/export" element={
                        <ProtectedRoute>
                            <AnalyticsExportPage />
                        </ProtectedRoute>
                    } />
                    <Route path="/openings" element={
                        <ProtectedRoute>
                            <OpeningCatalogPage />
                        </ProtectedRoute>
                    } />
                    <Route path="/engines" element={
                        <ProtectedRoute>
                            <EngineManagementPage />
                        </ProtectedRoute>
                    } />
                    <Route path="/players" element={
                        <ProtectedRoute>
                            <PlayersPage />
                        </ProtectedRoute>
                    } />
                    <Route path="/tournaments" element={
                        <ProtectedRoute>
                            <TournamentPage />
                        </ProtectedRoute>
                    } />
                </Routes>
            </BrowserRouter>
        </ThemeProvider>
    );
}