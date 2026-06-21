import React from "react";
import { useNavigate } from "react-router-dom";
import { useTheme } from "../router/App";

interface OpeningInfo {
    name: string;
    ecoCode: string;
    moves: string[];
    description: string;
    category: string;
}

interface OpeningsListResponse {
    openings: OpeningInfo[];
    total: number;
}

export function OpeningCatalogPage() {
    const navigate = useNavigate();
    const { theme, toggleTheme } = useTheme();
    const [openings, setOpenings] = React.useState<OpeningInfo[]>([]);
    const [filteredOpenings, setFilteredOpenings] = React.useState<OpeningInfo[]>([]);
    const [selectedCategory, setSelectedCategory] = React.useState<string>("all");
    const [searchQuery, setSearchQuery] = React.useState<string>("");
    const [loading, setLoading] = React.useState<boolean>(true);
    const [error, setError] = React.useState<string | null>(null);

    const categories = ["all", "open", "semi-open", "closed", "indian", "flank"];

    React.useEffect(() => {
        async function loadOpenings() {
            setLoading(true);
            setError(null);
            try {
                const response = await fetch("/v1/chess/openings");
                if (!response.ok) throw new Error("Failed to load openings");
                const data: OpeningsListResponse = await response.json();
                setOpenings(data.openings);
                setFilteredOpenings(data.openings);
            } catch (e) {
                setError("Failed to load opening catalog");
            } finally {
                setLoading(false);
            }
        }
        loadOpenings();
    }, []);

    React.useEffect(() => {
        let filtered = openings;

        if (selectedCategory !== "all") {
            filtered = filtered.filter(o => o.category === selectedCategory);
        }

        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            filtered = filtered.filter(o =>
                o.name.toLowerCase().includes(query) ||
                o.ecoCode.toLowerCase().includes(query) ||
                o.description.toLowerCase().includes(query)
            );
        }

        setFilteredOpenings(filtered);
    }, [selectedCategory, searchQuery, openings]);

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
                <span style={{ fontWeight: 700, fontSize: "0.95rem" }}>📚 Opening Catalog</span>
                <div style={{ marginLeft: "auto" }}>
                    <button onClick={toggleTheme} className="ghost" style={{ padding: "0.3rem 0.55rem" }}>
                        {theme === "dark" ? "☀️" : "🌙"}
                    </button>
                </div>
            </div>

            <div style={{ padding: "1.25rem 1.5rem" }}>
                {loading && <div style={{ color: "var(--color-text-muted)", textAlign: "center", padding: "2rem" }}>Loading openings…</div>}
                {error && <div style={{ color: "var(--color-danger)", marginBottom: "1rem" }}>⚠ {error}</div>}

                {!loading && (
                    <>
                        {/* Filters */}
                        <div className="card" style={{ marginBottom: "1.25rem", padding: "1rem 1.25rem" }}>
                            <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap", alignItems: "center" }}>
                                <div style={{ flex: 1, minWidth: 200 }}>
                                    <input
                                        type="text"
                                        placeholder="Search openings..."
                                        value={searchQuery}
                                        onChange={(e) => setSearchQuery(e.target.value)}
                                        style={{
                                            width: "100%",
                                            padding: "0.6rem",
                                            background: "var(--color-bg-2)",
                                            border: "1px solid var(--color-border)",
                                            borderRadius: "0.5rem",
                                            color: "var(--color-text)",
                                        }}
                                    />
                                </div>
                                <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                                    {categories.map(cat => (
                                        <button
                                            key={cat}
                                            onClick={() => setSelectedCategory(cat)}
                                            style={{
                                                padding: "0.4rem 0.8rem",
                                                background: selectedCategory === cat ? "var(--color-teal)" : "var(--color-bg-2)",
                                                color: selectedCategory === cat ? "white" : "var(--color-text)",
                                                border: selectedCategory === cat ? "none" : "1px solid var(--color-border)",
                                                borderRadius: "0.4rem",
                                                fontSize: "0.8rem",
                                                fontWeight: 600,
                                                cursor: "pointer",
                                                textTransform: "capitalize",
                                            }}
                                        >
                                            {cat}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </div>

                        {/* Stats */}
                        <div style={{ marginBottom: "1rem", fontSize: "0.85rem", color: "var(--color-text-muted)" }}>
                            Showing {filteredOpenings.length} of {openings.length} openings
                        </div>

                        {/* Openings Grid */}
                        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))", gap: "1rem" }}>
                            {filteredOpenings.map(opening => (
                                <div key={opening.ecoCode} className="card" style={{ padding: "1rem 1.25rem" }}>
                                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "0.5rem" }}>
                                        <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--color-text)", margin: 0 }}>{opening.name}</h3>
                                        <span style={{
                                            padding: "0.2rem 0.5rem",
                                            background: "var(--color-bg-3)",
                                            borderRadius: "0.3rem",
                                            fontSize: "0.7rem",
                                            fontWeight: 600,
                                            color: "var(--color-teal)",
                                            fontFamily: "monospace",
                                        }}>
                                            {opening.ecoCode}
                                        </span>
                                    </div>
                                    <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", marginBottom: "0.5rem", textTransform: "capitalize" }}>
                                        {opening.category}
                                    </div>
                                    <p style={{ fontSize: "0.8rem", color: "var(--color-text-muted)", marginBottom: "0.75rem", lineHeight: 1.4 }}>
                                        {opening.description}
                                    </p>
                                    <div style={{ fontSize: "0.75rem", color: "var(--color-text-dim)", fontFamily: "monospace", wordBreak: "break-all" }}>
                                        {opening.moves.slice(0, 6).join(" ")}{opening.moves.length > 6 ? "..." : ""}
                                    </div>
                                </div>
                            ))}
                        </div>

                        {filteredOpenings.length === 0 && (
                            <div className="card" style={{ padding: "2rem", textAlign: "center", color: "var(--color-text-muted)" }}>
                                <div style={{ fontSize: "2rem", marginBottom: "0.5rem" }}>📚</div>
                                <p>No openings found matching your criteria.</p>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
