import React from "react";
import { useNavigate } from "react-router-dom";
import { getLeaderboard } from "../utils/apiClient";

interface LeaderboardEntry {
    rank: number;
    player: string;
    totalGames: number;
    victories: number;
    defeats: number;
    draws: number;
    winRate: number;
}

interface LeaderboardResponse {
    entries: LeaderboardEntry[];
    generatedAt: string;
    source: string;
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
    const [data, setData] = React.useState<LeaderboardResponse | null>(null);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState<string | null>(null);

    const [countdown, setCountdown] = React.useState(60);

    const fetchData = React.useCallback(() => {
        setLoading(true);
        setError(null);
        getLeaderboard()
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

    return (
        <div className="container" style={{ maxWidth: "800px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "1rem", marginBottom: "0.5rem" }}>
                <button onClick={() => navigate("/")} className="secondary" style={{ padding: "0.4rem 0.8rem" }}>
                    ← Back
                </button>
                <h1 style={{ color: "#e0e0e0", margin: 0 }}>🏆 Leaderboard</h1>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: "1rem", marginBottom: "1.5rem" }}>
                <p style={{ color: "#888", fontSize: "0.9rem", margin: 0 }}>
                    Powered by Apache Spark analytics
                </p>
                <span style={{ color: "#555", fontSize: "0.8rem", marginLeft: "auto" }}>
                    Auto-refresh in {countdown}s
                </span>
                <button onClick={fetchData} className="secondary" style={{ padding: "0.3rem 0.7rem", fontSize: "0.8rem" }}>
                    ↻ Refresh now
                </button>
            </div>

            {loading && <p style={{ color: "#b0b0b0" }}>Loading leaderboard...</p>}

            {error && (
                <div className="error">
                    Failed to load leaderboard: {error}
                </div>
            )}

            {data && data.source === "no-spark-data" && (
                <div style={{
                    background: "#2a2a2a",
                    border: "1px solid #555",
                    borderRadius: "8px",
                    padding: "2rem",
                    textAlign: "center",
                    color: "#b0b0b0"
                }}>
                    <div style={{ fontSize: "3rem", marginBottom: "1rem" }}>📊</div>
                    <h3 style={{ color: "#e0e0e0" }}>No analytics data yet</h3>
                    <p>Spark analytics haven't run yet. Play some games and the leaderboard will appear here.</p>
                    <p style={{ fontSize: "0.8rem", color: "#666" }}>
                        Run: <code style={{ background: "#333", padding: "2px 6px", borderRadius: "3px" }}>
                            sbt "spark/run batch"
                        </code> to generate initial data.
                    </p>
                </div>
            )}

            {data && data.entries.length > 0 && (
                <>
                    <div style={{ overflowX: "auto" }}>
                        <table style={{
                            width: "100%",
                            borderCollapse: "collapse",
                            background: "#1e1e1e",
                            borderRadius: "8px",
                            overflow: "hidden"
                        }}>
                            <thead>
                                <tr style={{ background: "#2a2a2a", borderBottom: "2px solid #444" }}>
                                    {["Rank", "Player", "Games", "Wins", "Losses", "Draws", "Win Rate"].map(h => (
                                        <th key={h} style={{
                                            padding: "0.75rem 1rem",
                                            textAlign: h === "Rank" || h === "Player" ? "left" : "center",
                                            color: "#b0b0b0",
                                            fontSize: "0.85rem",
                                            fontWeight: "600",
                                            letterSpacing: "0.05em"
                                        }}>{h}</th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {data.entries.map((entry, i) => (
                                    <tr key={entry.player} style={{
                                        borderBottom: "1px solid #333",
                                        background: i % 2 === 0 ? "#1e1e1e" : "#222",
                                        transition: "background 0.15s"
                                    }}>
                                        <td style={{ padding: "0.75rem 1rem", color: medalColor(entry.rank), fontWeight: "bold", fontSize: "1.1rem" }}>
                                            {medalIcon(entry.rank)}
                                        </td>
                                        <td style={{ padding: "0.75rem 1rem", color: "#e0e0e0", fontWeight: entry.rank <= 3 ? "bold" : "normal" }}>
                                            {entry.player}
                                        </td>
                                        <td style={{ padding: "0.75rem 1rem", color: "#b0b0b0", textAlign: "center" }}>{entry.totalGames}</td>
                                        <td style={{ padding: "0.75rem 1rem", color: "#4caf50", textAlign: "center", fontWeight: "bold" }}>{entry.victories}</td>
                                        <td style={{ padding: "0.75rem 1rem", color: "#f44336", textAlign: "center" }}>{entry.defeats}</td>
                                        <td style={{ padding: "0.75rem 1rem", color: "#9e9e9e", textAlign: "center" }}>{entry.draws}</td>
                                        <td style={{ padding: "0.75rem 1rem", textAlign: "center" }}>
                                            <span style={{
                                                display: "inline-block",
                                                background: entry.winRate >= 0.6 ? "#1b5e20" : entry.winRate >= 0.4 ? "#1a237e" : "#3e2723",
                                                color: entry.winRate >= 0.6 ? "#81c784" : entry.winRate >= 0.4 ? "#90caf9" : "#ffab91",
                                                padding: "2px 8px",
                                                borderRadius: "12px",
                                                fontWeight: "bold",
                                                fontSize: "0.9rem"
                                            }}>
                                                {(entry.winRate * 100).toFixed(1)}%
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    <div style={{ marginTop: "1rem", fontSize: "0.75rem", color: "#555", textAlign: "right" }}>
                        Last updated: {new Date(data.generatedAt).toLocaleString()} · Source: {data.source}
                    </div>
                </>
            )}
        </div>
    );
}
