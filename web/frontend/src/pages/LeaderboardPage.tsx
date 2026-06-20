import React from "react";
import { useNavigate } from "react-router-dom";
import { useTheme } from "../router/App";

interface PlayerStats {
    playerId: string;
    username: string;
    rating: number;
    gamesPlayed: number;
    wins: number;
    losses: number;
    draws: number;
    winRate: number;
    rank: number;
}

interface LeaderboardResponse {
    stats: PlayerStats[];
    generatedAt: string;
}

async function fetchLeaderboard(): Promise<LeaderboardResponse> {
    const res = await fetch("/v1/players/leaderboard");
    if (!res.ok) throw new Error(`Failed to fetch leaderboard: ${res.status}`);
    return res.json();
}

const medalColor = (rank: number) => {
    if (rank === 1) return "#FFD700";
    if (rank === 2) return "#C0C0C0";
    if (rank === 3) return "#CD7F32";
    return "#666";
};

const medalIcon = (rank: number) => {
    if (rank === 1) return "🥇";
    if (rank === 2) return "🥈";
    if (rank === 3) return "🥉";
    return `#${rank}`;
};

export function LeaderboardPage() {
    const navigate = useNavigate();
    const { theme, toggleTheme } = useTheme();
    const [data, setData] = React.useState<LeaderboardResponse | null>(null);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState<string | null>(null);
    const [countdown, setCountdown] = React.useState(60);

    const fetchData = React.useCallback(() => {
        setLoading(true);
        setError(null);
        fetchLeaderboard()
            .then(setData)
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
        setCountdown(60);
    }, []);

    React.useEffect(() => {
        fetchData();
        const refreshTimer = setInterval(fetchData, 60000);
        return () => clearInterval(refreshTimer);
    }, [fetchData]);

    React.useEffect(() => {
        const tick = setInterval(() => setCountdown(c => c <= 1 ? 60 : c - 1), 1000);
        return () => clearInterval(tick);
    }, []);

    const winRateBadge = (wr: number) => {
        if (wr >= 60) return { bg: "var(--color-success-bg)", color: "var(--color-success)" };
        if (wr >= 40) return { bg: "var(--color-accent-bg)", color: "var(--color-accent)" };
        return { bg: "var(--color-danger-bg)", color: "var(--color-danger)" };
    };

    return (
        <div style={{ minHeight: "100vh" }}>
            {/* Navbar */}
            <div style={{
                display: "flex", alignItems: "center", gap: "0.75rem",
                padding: "0.75rem 1.5rem",
                background: "var(--color-surface)",
                borderBottom: "1px solid var(--color-border)",
                position: "sticky", top: 0, zIndex: 50,
            }}>
                <button onClick={() => navigate("/")} className="ghost" style={{ padding: "0.3rem 0.6rem" }}>← Menu</button>
                <span style={{ fontSize: "1.0625rem", fontWeight: 700 }}>🏆 Leaderboard</span>
                <div style={{ marginLeft: "auto", display: "flex", gap: "0.5rem", alignItems: "center" }}>
                    <span style={{ fontSize: "0.75rem", color: "var(--color-text-dim)" }}>
                        ↻ {countdown}s
                    </span>
                    <button onClick={fetchData} className="ghost" style={{ padding: "0.3rem 0.6rem", fontSize: "0.8rem" }}>Refresh</button>
                    <button onClick={toggleTheme} className="ghost" style={{ padding: "0.3rem 0.55rem", fontSize: "0.95rem" }}>
                        {theme === "dark" ? "☀️" : "🌙"}
                    </button>
                </div>
            </div>

            <div className="container" style={{ maxWidth: 860 }}>
                {loading && <div className="loading">Loading leaderboard…</div>}
                {error && <div className="error">Failed to load leaderboard: {error}</div>}

                {data && data.stats.length === 0 && (
                    <div className="card" style={{ textAlign: "center", padding: "3rem 2rem" }}>
                        <div style={{ fontSize: "3rem", marginBottom: "1rem" }}>📊</div>
                        <h3 style={{ marginBottom: "0.5rem" }}>No players yet</h3>
                        <p style={{ color: "var(--color-text-muted)", marginBottom: "0.75rem" }}>
                            Register some players and play games to see the leaderboard.
                        </p>
                    </div>
                )}

                {data && data.stats.length > 0 && (
                    <>
                        <div style={{ overflowX: "auto" }}>
                            <table style={{ width: "100%", borderCollapse: "collapse" }}>
                                <thead>
                                    <tr style={{ borderBottom: "2px solid var(--color-border)" }}>
                                        {["Rank", "Player", "Rating", "Games", "Wins", "Losses", "Draws", "Win Rate"].map(h => (
                                            <th key={h} style={{
                                                padding: "0.6rem 0.875rem",
                                                textAlign: h === "Rank" || h === "Player" ? "left" : "center",
                                                color: "var(--color-text-dim)",
                                                fontSize: "0.75rem", fontWeight: 600,
                                                letterSpacing: "0.07em", textTransform: "uppercase",
                                            }}>{h}</th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody>
                                    {data.stats.map((entry, i) => (
                                        <tr key={entry.playerId} style={{
                                            borderBottom: "1px solid var(--color-border)",
                                            background: i < 3 ? "var(--color-surface-2)" : "transparent",
                                            transition: "background 0.15s",
                                        }}>
                                            <td style={{ padding: "0.75rem 0.875rem", fontWeight: 700, fontSize: "1.05rem", color: medalColor(entry.rank) }}>
                                                {medalIcon(entry.rank)}
                                            </td>
                                            <td style={{ padding: "0.75rem 0.875rem", fontWeight: entry.rank <= 3 ? 700 : 400, color: "var(--color-text)" }}>
                                                {entry.username}
                                            </td>
                                            <td style={{ padding: "0.75rem 0.875rem", textAlign: "center", color: "var(--color-teal)", fontWeight: 700, fontFamily: "monospace", fontSize: "0.875rem" }}>{entry.rating}</td>
                                            <td style={{ padding: "0.75rem 0.875rem", textAlign: "center", color: "var(--color-text-muted)", fontSize: "0.875rem" }}>{entry.gamesPlayed}</td>
                                            <td style={{ padding: "0.75rem 0.875rem", textAlign: "center", color: "var(--color-success)", fontWeight: 600, fontSize: "0.875rem" }}>{entry.wins}</td>
                                            <td style={{ padding: "0.75rem 0.875rem", textAlign: "center", color: "var(--color-danger)", fontSize: "0.875rem" }}>{entry.losses}</td>
                                            <td style={{ padding: "0.75rem 0.875rem", textAlign: "center", color: "var(--color-text-dim)", fontSize: "0.875rem" }}>{entry.draws}</td>
                                            <td style={{ padding: "0.75rem 0.875rem", textAlign: "center" }}>
                                                <span style={{
                                                    display: "inline-block",
                                                    background: winRateBadge(entry.winRate).bg,
                                                    color: winRateBadge(entry.winRate).color,
                                                    padding: "2px 9px", borderRadius: "999px",
                                                    fontWeight: 700, fontSize: "0.8rem",
                                                }}>
                                                    {entry.winRate.toFixed(1)}%
                                                </span>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        <p style={{ marginTop: "1rem", fontSize: "0.72rem", color: "var(--color-text-dim)", textAlign: "right" }}>
                            Updated {new Date(data.generatedAt).toLocaleString()}
                        </p>
                    </>
                )}
            </div>
        </div>
    );
}
