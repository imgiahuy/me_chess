import React from "react";
import { useNavigate } from "react-router-dom";
import { useTheme } from "../router/App";

interface Player {
    id: string;
    username: string;
    email?: string;
    rating: number;
    gamesPlayed: number;
    wins: number;
    losses: number;
    draws: number;
    createdAt: string;
    lastSeenAt: string;
    winRate: number;
}

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

const PLAYER_API = "/v1/players";

async function fetchPlayers(): Promise<Player[]> {
    const res = await fetch(`${PLAYER_API}`);
    if (!res.ok) throw new Error(`Failed to fetch players: ${res.status}`);
    const data = await res.json();
    return data.players || [];
}

async function fetchLeaderboard(): Promise<PlayerStats[]> {
    const res = await fetch(`${PLAYER_API}/leaderboard`);
    if (!res.ok) throw new Error(`Failed to fetch leaderboard: ${res.status}`);
    const data = await res.json();
    return data.stats || [];
}

async function createPlayer(username: string, email?: string): Promise<Player> {
    const res = await fetch(PLAYER_API, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, email: email || undefined, initialRating: 1200 }),
    });
    if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || "Failed to create player");
    }
    return res.json();
}

async function deletePlayer(id: string): Promise<void> {
    const res = await fetch(`${PLAYER_API}/${id}`, { method: "DELETE" });
    if (!res.ok) throw new Error("Failed to delete player");
}

function RatingBadge({ rating }: { rating: number }) {
    const color = rating >= 1800 ? "#f59e0b" : rating >= 1400 ? "var(--color-teal)" : "var(--color-text-muted)";
    const label = rating >= 1800 ? "Expert" : rating >= 1400 ? "Advanced" : "Beginner";
    return (
        <span style={{
            display: "inline-block", padding: "0.1rem 0.45rem", borderRadius: "999px",
            fontSize: "0.65rem", fontWeight: 700, color,
            background: "var(--color-bg-3)", border: `1px solid ${color}`,
        }}>{label}</span>
    );
}

export function PlayersPage() {
    const navigate = useNavigate();
    const { theme, toggleTheme } = useTheme();
    const [players, setPlayers] = React.useState<Player[]>([]);
    const [leaderboard, setLeaderboard] = React.useState<PlayerStats[]>([]);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState<string | null>(null);
    const [view, setView] = React.useState<"list" | "leaderboard">("list");
    const [showCreate, setShowCreate] = React.useState(false);
    const [createForm, setCreateForm] = React.useState({ username: "", email: "" });
    const [createError, setCreateError] = React.useState<string | null>(null);
    const [creating, setCreating] = React.useState(false);
    const [search, setSearch] = React.useState("");

    async function load() {
        setLoading(true);
        setError(null);
        try {
            const [p, lb] = await Promise.all([fetchPlayers(), fetchLeaderboard()]);
            setPlayers(p);
            setLeaderboard(lb);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to load players");
        } finally {
            setLoading(false);
        }
    }

    React.useEffect(() => { load(); }, []);

    async function handleCreate(e: React.FormEvent) {
        e.preventDefault();
        if (!createForm.username.trim()) {
            setCreateError("Username is required");
            return;
        }
        setCreating(true);
        setCreateError(null);
        try {
            await createPlayer(createForm.username.trim(), createForm.email.trim() || undefined);
            setShowCreate(false);
            setCreateForm({ username: "", email: "" });
            await load();
        } catch (err) {
            setCreateError(err instanceof Error ? err.message : "Failed to create player");
        } finally {
            setCreating(false);
        }
    }

    async function handleDelete(id: string, username: string) {
        if (!window.confirm(`Delete player "${username}"?`)) return;
        try {
            await deletePlayer(id);
            await load();
        } catch {
            setError("Failed to delete player");
        }
    }

    const filteredPlayers = players.filter(p =>
        p.username.toLowerCase().includes(search.toLowerCase())
    );

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
                <span style={{ fontWeight: 700, fontSize: "0.95rem" }}>👤 Players</span>
                <div style={{ marginLeft: "auto", display: "flex", gap: "0.5rem", alignItems: "center" }}>
                    <button onClick={toggleTheme} className="ghost" style={{ padding: "0.3rem 0.55rem" }}>
                        {theme === "dark" ? "☀️" : "🌙"}
                    </button>
                </div>
            </div>

            <div style={{ padding: "1.25rem 1.5rem" }}>
                {/* Toolbar */}
                <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", marginBottom: "1.25rem", alignItems: "center" }}>
                    <div style={{ display: "flex", gap: "0.5rem" }}>
                        {(["list", "leaderboard"] as const).map(v => (
                            <button
                                key={v}
                                onClick={() => setView(v)}
                                className={view === v ? "" : "secondary"}
                                style={{ fontSize: "0.8rem", padding: "0.35rem 0.75rem" }}
                            >
                                {v === "list" ? "👥 All Players" : "📊 Player Stats"}
                            </button>
                        ))}
                    </div>
                    {view === "list" && (
                        <input
                            type="text"
                            placeholder="Search players…"
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                            style={{ flex: 1, minWidth: 160, maxWidth: 300, fontSize: "0.8rem" }}
                        />
                    )}
                    <div style={{ marginLeft: "auto" }}>
                        <button onClick={() => setShowCreate(true)} style={{ fontSize: "0.8rem", padding: "0.35rem 0.75rem" }}>
                            + New Player
                        </button>
                    </div>
                </div>

                {error && <div style={{ color: "var(--color-danger)", marginBottom: "1rem", fontSize: "0.85rem" }}>⚠ {error}</div>}
                {loading && <div style={{ color: "var(--color-text-muted)", textAlign: "center", padding: "2rem" }}>Loading…</div>}

                {/* List view */}
                {!loading && view === "list" && (
                    <>
                        {filteredPlayers.length === 0 ? (
                            <div className="card" style={{ padding: "2rem", textAlign: "center", color: "var(--color-text-muted)" }}>
                                <div style={{ fontSize: "2rem", marginBottom: "0.5rem" }}>👤</div>
                                <p>{search ? "No players match your search." : "No players registered yet. Create the first one!"}</p>
                            </div>
                        ) : (
                            <div style={{ display: "grid", gap: "0.75rem", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))" }}>
                                {filteredPlayers.map(p => (
                                    <div key={p.id} className="card" style={{ padding: "1rem 1.25rem" }}>
                                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "0.5rem" }}>
                                            <div>
                                                <div style={{ fontWeight: 700, fontSize: "0.9375rem", marginBottom: "0.2rem" }}>{p.username}</div>
                                                {p.email && <div style={{ fontSize: "0.75rem", color: "var(--color-text-muted)" }}>{p.email}</div>}
                                            </div>
                                            <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                                                <RatingBadge rating={p.rating} />
                                                <button
                                                    onClick={() => handleDelete(p.id, p.username)}
                                                    className="ghost"
                                                    style={{ fontSize: "0.75rem", padding: "0.2rem 0.4rem", color: "var(--color-danger)" }}
                                                >
                                                    ✕
                                                </button>
                                            </div>
                                        </div>
                                        <div style={{ display: "flex", gap: "0.5rem", marginBottom: "0.5rem" }}>
                                            <span style={{ fontSize: "1.1rem", fontWeight: 700, color: "var(--color-teal)", fontFamily: "monospace" }}>
                                                {p.rating}
                                            </span>
                                            <span style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", alignSelf: "flex-end" }}>rating</span>
                                        </div>
                                        <div style={{ display: "flex", gap: "1rem", fontSize: "0.75rem" }}>
                                            <span><span style={{ color: "var(--color-success)", fontWeight: 600 }}>{p.wins}W</span></span>
                                            <span><span style={{ color: "var(--color-danger)", fontWeight: 600 }}>{p.losses}L</span></span>
                                            <span><span style={{ color: "var(--color-text-muted)", fontWeight: 600 }}>{p.draws}D</span></span>
                                            <span style={{ color: "var(--color-text-dim)" }}>{p.gamesPlayed} games</span>
                                        </div>
                                        {p.gamesPlayed > 0 && (
                                            <div style={{ marginTop: "0.5rem" }}>
                                                <div style={{ height: 4, background: "var(--color-bg-3)", borderRadius: 2, overflow: "hidden" }}>
                                                    <div style={{
                                                        height: "100%",
                                                        width: `${p.winRate.toFixed(0)}%`,
                                                        background: "var(--color-success)",
                                                        borderRadius: 2,
                                                    }} />
                                                </div>
                                                <div style={{ fontSize: "0.65rem", color: "var(--color-text-dim)", marginTop: "1px" }}>
                                                    {p.winRate.toFixed(1)}% win rate
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}
                    </>
                )}

                {/* Player Stats view */}
                {!loading && view === "leaderboard" && (
                    <div className="card" style={{ padding: "1rem 1.25rem" }}>
                        {leaderboard.length === 0 ? (
                            <div style={{ padding: "2rem", textAlign: "center", color: "var(--color-text-muted)" }}>
                                No players yet. Register some players to see their stats!
                            </div>
                        ) : (
                            <div style={{ overflowX: "auto" }}>
                                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.82rem" }}>
                                    <thead>
                                        <tr style={{ borderBottom: "1px solid var(--color-border)" }}>
                                            {["Rank", "Player", "Rating", "Games", "W", "L", "D", "Win %"].map(h => (
                                                <th key={h} style={{ padding: "0.4rem 0.75rem", textAlign: "left", color: "var(--color-text-dim)", fontWeight: 600, whiteSpace: "nowrap" }}>{h}</th>
                                            ))}
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {leaderboard.map(p => (
                                            <tr key={p.playerId} style={{ borderBottom: "1px solid var(--color-border)" }}>
                                                <td style={{ padding: "0.5rem 0.75rem", fontWeight: 700, color: p.rank <= 3 ? "#f59e0b" : "var(--color-text-muted)" }}>
                                                    {p.rank <= 3 ? ["🥇", "🥈", "🥉"][p.rank - 1] : p.rank}
                                                </td>
                                                <td style={{ padding: "0.5rem 0.75rem", fontWeight: 600 }}>{p.username}</td>
                                                <td style={{ padding: "0.5rem 0.75rem", color: "var(--color-teal)", fontWeight: 700, fontFamily: "monospace" }}>{p.rating}</td>
                                                <td style={{ padding: "0.5rem 0.75rem", color: "var(--color-text-muted)" }}>{p.gamesPlayed}</td>
                                                <td style={{ padding: "0.5rem 0.75rem", color: "var(--color-success)", fontWeight: 600 }}>{p.wins}</td>
                                                <td style={{ padding: "0.5rem 0.75rem", color: "var(--color-danger)", fontWeight: 600 }}>{p.losses}</td>
                                                <td style={{ padding: "0.5rem 0.75rem", color: "var(--color-text-muted)" }}>{p.draws}</td>
                                                <td style={{ padding: "0.5rem 0.75rem", color: "var(--color-teal)" }}>
                                                    {p.winRate.toFixed(1)}%
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Create Player Modal */}
            {showCreate && (
                <div
                    onClick={e => { if (e.target === e.currentTarget) setShowCreate(false); }}
                    style={{ position: "fixed", inset: 0, backgroundColor: "rgba(0,0,0,0.6)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: "1rem" }}
                >
                    <div style={{ background: "var(--color-surface)", border: "1px solid var(--color-border)", borderRadius: "var(--radius-lg)", boxShadow: "var(--shadow-lg)", width: "100%", maxWidth: 380 }}>
                        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "1rem 1.25rem", borderBottom: "1px solid var(--color-border)" }}>
                            <h3 style={{ margin: 0, fontSize: "1rem" }}>Register New Player</h3>
                            <button onClick={() => setShowCreate(false)} className="ghost" style={{ padding: "0.2rem 0.45rem" }}>✕</button>
                        </div>
                        <form onSubmit={handleCreate} style={{ padding: "1.25rem" }}>
                            <label style={{ display: "block", marginBottom: "0.35rem", fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                                Username *
                            </label>
                            <input
                                type="text"
                                value={createForm.username}
                                onChange={e => setCreateForm(f => ({ ...f, username: e.target.value }))}
                                placeholder="e.g. Magnus"
                                style={{ marginBottom: "0.875rem" }}
                                autoFocus
                            />
                            <label style={{ display: "block", marginBottom: "0.35rem", fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                                Email (optional)
                            </label>
                            <input
                                type="email"
                                value={createForm.email}
                                onChange={e => setCreateForm(f => ({ ...f, email: e.target.value }))}
                                placeholder="player@example.com"
                                style={{ marginBottom: createError ? "0.5rem" : "0" }}
                            />
                            {createError && <div style={{ color: "var(--color-danger)", fontSize: "0.8rem", marginBottom: "0.5rem" }}>⚠ {createError}</div>}
                        </form>
                        <div style={{ display: "flex", gap: "0.75rem", justifyContent: "flex-end", padding: "0.875rem 1.25rem", borderTop: "1px solid var(--color-border)" }}>
                            <button onClick={() => { setShowCreate(false); setCreateError(null); }} className="secondary" disabled={creating}>
                                Cancel
                            </button>
                            <button onClick={handleCreate as any} disabled={creating}>
                                {creating ? "Creating…" : "Register"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
