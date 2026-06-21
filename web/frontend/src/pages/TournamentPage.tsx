import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTheme } from "../router/App";

const API_BASE = import.meta.env.VITE_TOURNAMENT_API_URL || "http://localhost:8070/v1";

interface Tournament {
  id: string;
  name: string;
  format: string;
  status: string;
  rounds: number;
  gamesPerPairing: number;
  participants: Participant[];
  roundsData: Round[];
  standings: Standing[];
}

interface Participant {
  id: string;
  name: string;
  botType?: string;
}

interface Round {
  number: number;
  pairings: Pairing[];
}

interface Pairing {
  whiteId: string;
  blackId: string;
  round: number;
  gameId?: string;
  result?: string;
}

interface Standing {
  participantId: string;
  name: string;
  played: number;
  wins: number;
  draws: number;
  losses: number;
  score: number;
}

interface TournamentSummary {
  id: string;
  name: string;
  format: string;
  status: string;
  participantCount: number;
  rounds: number;
  currentRound: number;
  createdAt: string;
}

export function TournamentPage() {
  const navigate = useNavigate();
  const { theme } = useTheme();
  const [tournaments, setTournaments] = useState<TournamentSummary[]>([]);
  const [selected, setSelected] = useState<Tournament | null>(null);
  const [name, setName] = useState("");
  const [format, setFormat] = useState("roundrobin");
  const [rounds, setRounds] = useState(3);
  const [participantName, setParticipantName] = useState("");
  const [botType, setBotType] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const loadTournaments = async () => {
    try {
      const res = await fetch(`${API_BASE}/tournaments`);
      if (!res.ok) throw new Error("Failed to load tournaments");
      const data = await res.json();
      setTournaments(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error");
    }
  };

  useEffect(() => {
    loadTournaments();
  }, []);

  const createTournament = async () => {
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/tournaments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, format, rounds }),
      });
      if (!res.ok) throw new Error("Failed to create tournament");
      await loadTournaments();
      setName("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error");
    } finally {
      setLoading(false);
    }
  };

  const registerParticipant = async () => {
    if (!selected) return;
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/tournaments/${selected.id}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: participantName, botType: botType || undefined }),
      });
      if (!res.ok) throw new Error("Failed to register participant");
      const updated = await res.json();
      setSelected(updated);
      await loadTournaments();
      setParticipantName("");
      setBotType("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error");
    } finally {
      setLoading(false);
    }
  };

  const startTournament = async () => {
    if (!selected) return;
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/tournaments/${selected.id}/start`, { method: "POST" });
      if (!res.ok) throw new Error("Failed to start tournament");
      const updated = await res.json();
      setSelected(updated);
      await loadTournaments();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error");
    } finally {
      setLoading(false);
    }
  };

  const fetchDetails = async (id: string) => {
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/tournaments/${id}`);
      if (!res.ok) throw new Error("Failed to load tournament details");
      const data = await res.json();
      setSelected(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error");
    } finally {
      setLoading(false);
    }
  };

  const reportResult = async (gameId: string, result: string) => {
    if (!selected) return;
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/tournaments/${selected.id}/result`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ gameId, result }),
      });
      if (!res.ok) throw new Error("Failed to report result");
      const updated = await res.json();
      setSelected(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page tournament-page" data-theme={theme === "light" ? "light" : undefined}>
      <header className="page-header">
        <h1>Tournaments</h1>
        <button className="btn secondary" onClick={() => navigate("/")}>Back to Menu</button>
      </header>

      {error && <div className="error-banner">{error}</div>}

      <section className="card">
        <h2>Create Tournament</h2>
        <div className="form-row">
          <input
            type="text"
            placeholder="Tournament name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <select value={format} onChange={(e) => setFormat(e.target.value)}>
            <option value="roundrobin">Round Robin</option>
            <option value="swiss">Swiss</option>
            <option value="arena">Arena</option>
          </select>
          <input
            type="number"
            min={1}
            max={20}
            value={rounds}
            onChange={(e) => setRounds(parseInt(e.target.value) || 1)}
          />
          <button className="btn primary" onClick={createTournament} disabled={loading || !name}>
            Create
          </button>
        </div>
      </section>

      <section className="card">
        <h2>Tournaments</h2>
        {tournaments.length === 0 ? (
          <p>No tournaments yet.</p>
        ) : (
          <ul className="tournament-list">
            {tournaments.map((t) => (
              <li key={t.id} className="tournament-item">
                <div>
                  <strong>{t.name}</strong>
                  <span className="badge">{t.format}</span>
                  <span className="badge">{t.status}</span>
                  <span>{t.participantCount} participants</span>
                </div>
                <button className="btn" onClick={() => fetchDetails(t.id)}>Manage</button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {selected && (
        <section className="card">
          <h2>{selected.name}</h2>
          <p>
            Format: {selected.format} | Status: {selected.status} | Rounds: {selected.rounds}
          </p>

          {selected.status === "Created" || selected.status === "Open" ? (
            <div className="form-row">
              <input
                type="text"
                placeholder="Participant name"
                value={participantName}
                onChange={(e) => setParticipantName(e.target.value)}
              />
              <input
                type="text"
                placeholder="Bot type (optional)"
                value={botType}
                onChange={(e) => setBotType(e.target.value)}
              />
              <button className="btn" onClick={registerParticipant} disabled={loading || !participantName}>
                Register
              </button>
              <button className="btn primary" onClick={startTournament} disabled={loading || selected.participants.length < 2}>
                Start Tournament
              </button>
            </div>
          ) : null}

          <h3>Participants</h3>
          <ul>
            {selected.participants.map((p) => (
              <li key={p.id}>{p.name} {p.botType ? `(${p.botType})` : ""}</li>
            ))}
          </ul>

          <h3>Pairings</h3>
          {selected.roundsData.map((round) => (
            <div key={round.number} className="round">
              <h4>Round {round.number}</h4>
              <ul>
                {round.pairings.map((p, idx) => {
                  const white = selected.participants.find((x) => x.id === p.whiteId);
                  const black = selected.participants.find((x) => x.id === p.blackId);
                  return (
                    <li key={idx} className="pairing">
                      {white?.name ?? p.whiteId} vs {black?.name ?? p.blackId}
                      {p.result ? (
                        <span className="result">{p.result}</span>
                      ) : (
                        <span className="actions">
                          <button className="btn small" onClick={() => reportResult(p.gameId ?? `${p.whiteId}-${p.blackId}-${round.number}`, "WhiteWin")}>1-0</button>
                          <button className="btn small" onClick={() => reportResult(p.gameId ?? `${p.whiteId}-${p.blackId}-${round.number}`, "Draw")}>1/2</button>
                          <button className="btn small" onClick={() => reportResult(p.gameId ?? `${p.whiteId}-${p.blackId}-${round.number}`, "BlackWin")}>0-1</button>
                        </span>
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}

          <h3>Standings</h3>
          <table className="standings">
            <thead>
              <tr>
                <th>#</th>
                <th>Participant</th>
                <th>Played</th>
                <th>Wins</th>
                <th>Draws</th>
                <th>Losses</th>
                <th>Score</th>
              </tr>
            </thead>
            <tbody>
              {selected.standings.map((s, idx) => (
                <tr key={s.participantId}>
                  <td>{idx + 1}</td>
                  <td>{s.name}</td>
                  <td>{s.played}</td>
                  <td>{s.wins}</td>
                  <td>{s.draws}</td>
                  <td>{s.losses}</td>
                  <td>{s.score}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
