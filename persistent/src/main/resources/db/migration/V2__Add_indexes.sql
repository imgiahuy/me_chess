-- Performance indexes for chess persistence

CREATE INDEX IF NOT EXISTS idx_players_name ON players(name);
CREATE INDEX IF NOT EXISTS idx_games_creation_date ON games(creation_date);
CREATE INDEX IF NOT EXISTS idx_games_last_modified ON games(last_modified);
CREATE INDEX IF NOT EXISTS idx_moves_game_id ON moves(game_id);
CREATE INDEX IF NOT EXISTS idx_moves_game_id_move_index ON moves(game_id, move_index);
