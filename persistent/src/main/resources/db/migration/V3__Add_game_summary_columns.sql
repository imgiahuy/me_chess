-- Add denormalized summary columns to games for efficient listSummaries queries

ALTER TABLE games ADD COLUMN IF NOT EXISTS move_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE games ADD COLUMN IF NOT EXISTS is_game_over BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_games_is_game_over ON games(is_game_over);
