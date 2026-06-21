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

interface MetricsData {
    activeGames: number;
    heapUsedMb: number;
    heapMaxMb: number;
    uptimeSeconds: number;
    timestamp: string;
}

interface HealthData {
    status: string;
    service: string;
    version: string;
    activeGames: number;
    timestamp: string;
}

function StatCard({ label, value, sub, color }: { label: string; value: string | number; sub?: string; color?: string }) {
    return (
        <div className="card" style={{ flex: 1, minWidth: 140, padding: "1rem 1.25rem" }}>
            <div style={{ fontSize: "0.7rem", color: "var(--color-text-dim)", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: "0.4rem" }}>{label}</div>
            <div style={{ fontSize: "1.75rem", fontWeight: 700, color: color || "var(--color-text)" }}>{value}</div>
            {sub && <div style={{ fontSize: "0.75rem", color: "var(--color-text-muted)", marginTop: "0.2rem" }}>{sub}</div>}
        </div>
    );
}

function BarChart({ data, maxVal, label }: { data: { name: string; value: number }[]; maxVal: number; label: string }) {
    return (
        <div>
            <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.5rem", fontWeight: 600 }}>{label}</div>
            {data.map(({ name, value }) => (
                <div key={name} style={{ marginBottom: "0.5rem" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.75rem", marginBottom: "2px" }}>
                        <span style={{ color: "var(--color-text)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: "70%" }}>{name}</span>
                        <span style={{ color: "var(--color-text-muted)", fontFamily: "monospace" }}>{value}</span>
                    </div>
                    <div style={{ height: 6, background: "var(--color-bg-3)", borderRadius: 3, overflow: "hidden" }}>
                        <div style={{
                            height: "100%",
                            width: `${maxVal > 0 ? (value / maxVal) * 100 : 0}%`,
                            background: "var(--color-teal)",
                            borderRadius: 3,
                            transition: "width 0.5s ease"
                        }} />
                    </div>
                </div>
            ))}
        </div>
    );
}

export function AnalyticsPage() {
    const navigate = useNavigate();
    const { theme, toggleTheme } = useTheme();
    const [leaderboard, setLeaderboard] = React.useState<PlayerStats[]>([]);
    const [metrics, setMetrics] = React.useState<MetricsData | null>(null);
    const [health, setHealth] = React.useState<HealthData | null>(null);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState<string | null>(null);

    React.useEffect(() => {
        async function load() {
            setLoading(true);
            setError(null);
            try {
                const [lbRes, metricsRes, healthRes] = await Promise.allSettled([
                    fetch("/v1/players/leaderboard").then(r => r.json()),
                    fetch("/v1/chess/metrics").then(r => r.json()),
                    fetch("/v1/chess/health").then(r => r.json()),
                ]);
                if (lbRes.status === "fulfilled") setLeaderboard(lbRes.value.stats || []);
                if (metricsRes.status === "fulfilled") setMetrics(metricsRes.value);
                if (healthRes.status === "fulfilled") setHealth(healthRes.value);
            } catch (e) {
                setError("Failed to load analytics data");
            } finally {
                setLoading(false);
            }
        }
        load();
        const interval = setInterval(load, 30000);
        return () => clearInterval(interval);
    }, []);

    const topPlayers = leaderboard.slice(0, 8);
    const totalGames = leaderboard.reduce((s, e) => s + e.gamesPlayed, 0);
    const totalWins = leaderboard.reduce((s, e) => s + e.wins, 0);
    const avgWinRate = leaderboard.length > 0
        ? (leaderboard.reduce((s, e) => s + e.winRate, 0) / leaderboard.length).toFixed(1)
        : "0.0";

    const heapPct = metrics ? Math.round((metrics.heapUsedMb / metrics.heapMaxMb) * 100) : 0;
    const uptimeHours = metrics ? Math.floor(metrics.uptimeSeconds / 3600) : 0;
    const uptimeMins = metrics ? Math.floor((metrics.uptimeSeconds % 3600) / 60) : 0;

    return (
        <div style={{ minHeight: "100vh", background: "var(--color-bg)" }}>
            {/* Navbar */}
            <div style={{
                display: "flex", alignItems: "center", gap: "0.75rem",
                padding: "0.75rem 1.5rem",
                background: "var(--color-surface)",
                borderBottom: "1px solid var(--color-border)",
                position: "sticky", top: 0, zIndex: 50,
            }}>
                <button onClick={() => navigate("/")} className="ghost" style={{ padding: "0.3rem 0.6rem" }}>← Menu</button>
                <span style={{ fontWeight: 700, fontSize: "0.95rem" }}>📊 Analytics Dashboard</span>
                <div style={{ marginLeft: "auto", display: "flex", gap: "0.5rem" }}>
                    {health && (
                        <span style={{
                            padding: "0.15rem 0.6rem", borderRadius: "999px", fontSize: "0.7rem", fontWeight: 600,
                            background: health.status === "UP" ? "var(--color-success-bg)" : "var(--color-danger-bg)",
                            color: health.status === "UP" ? "var(--color-success)" : "var(--color-danger)",
                            border: `1px solid ${health.status === "UP" ? "var(--color-success)" : "var(--color-danger)"}`,
                        }}>
                            {health.status === "UP" ? "● Online" : "◼ Offline"}
                        </span>
                    )}
                    <button onClick={toggleTheme} className="ghost" style={{ padding: "0.3rem 0.55rem" }}>
                        {theme === "dark" ? "☀️" : "🌙"}
                    </button>
                </div>
            </div>

            <div style={{ padding: "1.25rem 1.5rem" }}>
                {loading && <div style={{ color: "var(--color-text-muted)", textAlign: "center", padding: "2rem" }}>Loading analytics…</div>}
                {error && <div style={{ color: "var(--color-danger)", marginBottom: "1rem" }}>⚠ {error}</div>}

                {/* Export Button */}
                <div style={{ marginBottom: "1.25rem" }}>
                    <button
                        onClick={() => navigate("/analytics/export")}
                        style={{
                            padding: "0.75rem 1.25rem",
                            background: "var(--color-teal)",
                            color: "white",
                            border: "none",
                            borderRadius: "0.5rem",
                            fontSize: "0.9rem",
                            fontWeight: 600,
                            cursor: "pointer",
                        }}
                    >
                        📦 Export Analytics
                    </button>
                </div>

                {/* Service Health */}
                {health && (
                    <div style={{ marginBottom: "1.25rem" }}>
                        <h3 style={{ fontSize: "0.875rem", marginBottom: "0.75rem", color: "var(--color-text-dim)", textTransform: "uppercase", letterSpacing: "0.06em" }}>Service Health</h3>
                        <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
                            <StatCard label="Status" value={health.status} color={health.status === "UP" ? "var(--color-success)" : "var(--color-danger)"} />
                            <StatCard label="Active Games" value={health.activeGames} color="var(--color-teal)" />
                            {metrics && <StatCard label="Uptime" value={`${uptimeHours}h ${uptimeMins}m`} />}
                            {metrics && <StatCard label="Heap Memory" value={`${metrics.heapUsedMb} MB`} sub={`of ${metrics.heapMaxMb} MB (${heapPct}%)`} color={heapPct > 80 ? "var(--color-danger)" : "var(--color-text)"} />}
                        </div>
                    </div>
                )}

                {/* Memory bar */}
                {metrics && (
                    <div className="card" style={{ marginBottom: "1.25rem", padding: "1rem 1.25rem" }}>
                        <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.5rem", fontWeight: 600 }}>JVM Heap Usage</div>
                        <div style={{ height: 10, background: "var(--color-bg-3)", borderRadius: 5, overflow: "hidden" }}>
                            <div style={{
                                height: "100%",
                                width: `${heapPct}%`,
                                background: heapPct > 80 ? "var(--color-danger)" : heapPct > 60 ? "var(--color-warning)" : "var(--color-teal)",
                                borderRadius: 5,
                                transition: "width 0.5s ease"
                            }} />
                        </div>
                        <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.7rem", color: "var(--color-text-muted)", marginTop: "0.3rem" }}>
                            <span>Used: {metrics.heapUsedMb} MB</span>
                            <span>Max: {metrics.heapMaxMb} MB</span>
                        </div>
                    </div>
                )}

                {/* Game Statistics */}
                {leaderboard.length > 0 && (
                    <div style={{ marginBottom: "1.25rem" }}>
                        <h3 style={{ fontSize: "0.875rem", marginBottom: "0.75rem", color: "var(--color-text-dim)", textTransform: "uppercase", letterSpacing: "0.06em" }}>Player Statistics</h3>
                        <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
                            <StatCard label="Total Games" value={totalGames} />
                            <StatCard label="Total Wins" value={totalWins} color="var(--color-success)" />
                            <StatCard label="Avg Win Rate" value={`${avgWinRate}%`} />
                            <StatCard label="Players Tracked" value={leaderboard.length} color="var(--color-teal)" />
                        </div>
                    </div>
                )}

                {/* Charts */}
                {topPlayers.length > 0 && (
                    <div style={{ display: "flex", gap: "1.25rem", flexWrap: "wrap", marginBottom: "1.25rem" }}>
                        <div className="card" style={{ flex: 1, minWidth: 280, padding: "1rem 1.25rem" }}>
                            <BarChart
                                label="Top Players by Wins"
                                data={topPlayers.map(p => ({ name: p.username, value: p.wins }))}
                                maxVal={Math.max(...topPlayers.map(p => p.wins), 1)}
                            />
                        </div>
                        <div className="card" style={{ flex: 1, minWidth: 280, padding: "1rem 1.25rem" }}>
                            <BarChart
                                label="Top Players by Games Played"
                                data={topPlayers.map(p => ({ name: p.username, value: p.gamesPlayed }))}
                                maxVal={Math.max(...topPlayers.map(p => p.gamesPlayed), 1)}
                            />
                        </div>
                    </div>
                )}

                {/* Leaderboard table */}
                {leaderboard.length > 0 && (
                    <div className="card" style={{ padding: "1rem 1.25rem" }}>
                        <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.75rem", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em" }}>Full Leaderboard</div>
                        <div style={{ overflowX: "auto" }}>
                            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.8rem" }}>
                                <thead>
                                    <tr style={{ borderBottom: "1px solid var(--color-border)" }}>
                                        {["#", "Player", "Rating", "Games", "Wins", "Losses", "Draws", "Win %"].map(h => (
                                            <th key={h} style={{ padding: "0.4rem 0.5rem", textAlign: "left", color: "var(--color-text-dim)", fontWeight: 600 }}>{h}</th>
                                        ))}
                                    </tr>
                                </thead>
                                <tbody>
                                    {leaderboard.map(e => (
                                        <tr key={e.playerId} style={{ borderBottom: "1px solid var(--color-border)" }}>
                                            <td style={{ padding: "0.4rem 0.5rem", color: e.rank <= 3 ? "var(--color-teal)" : "var(--color-text-muted)", fontWeight: e.rank <= 3 ? 700 : 400 }}>{e.rank}</td>
                                            <td style={{ padding: "0.4rem 0.5rem", fontWeight: 600 }}>{e.username}</td>
                                            <td style={{ padding: "0.4rem 0.5rem", color: "var(--color-teal)", fontWeight: 700, fontFamily: "monospace" }}>{e.rating}</td>
                                            <td style={{ padding: "0.4rem 0.5rem", color: "var(--color-text-muted)" }}>{e.gamesPlayed}</td>
                                            <td style={{ padding: "0.4rem 0.5rem", color: "var(--color-success)" }}>{e.wins}</td>
                                            <td style={{ padding: "0.4rem 0.5rem", color: "var(--color-danger)" }}>{e.losses}</td>
                                            <td style={{ padding: "0.4rem 0.5rem", color: "var(--color-text-muted)" }}>{e.draws}</td>
                                            <td style={{ padding: "0.4rem 0.5rem", color: "var(--color-teal)", fontWeight: 600 }}>{e.winRate.toFixed(1)}%</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}

                {!loading && leaderboard.length === 0 && (
                    <div className="card" style={{ padding: "2rem", textAlign: "center", color: "var(--color-text-muted)" }}>
                        <div style={{ fontSize: "2rem", marginBottom: "0.5rem" }}>📊</div>
                        <p>No analytics data yet. Register some players and play games to see stats!</p>
                    </div>
                )}
            </div>
        </div>
    );
}
