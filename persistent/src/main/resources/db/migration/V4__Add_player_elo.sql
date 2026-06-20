-- Add ELO rating column to players for tracking competitive performance

ALTER TABLE players ADD COLUMN IF NOT EXISTS elo INTEGER NOT NULL DEFAULT 1200;

CREATE INDEX IF NOT EXISTS idx_players_elo ON players(elo DESC);
