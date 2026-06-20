-- Initial schema for chess persistence
-- Supports PostgreSQL and H2

CREATE TABLE IF NOT EXISTS players (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS games (
    id VARCHAR(36) PRIMARY KEY,
    white_player_id INTEGER NOT NULL,
    black_player_id INTEGER NOT NULL,
    turn VARCHAR(10) NOT NULL,
    creation_date DATE NOT NULL,
    board_state TEXT NOT NULL,
    result VARCHAR(50),
    time_control VARCHAR(100),
    white_time VARCHAR(100),
    black_time VARCHAR(100),
    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_games_white_player FOREIGN KEY (white_player_id) REFERENCES players(id),
    CONSTRAINT fk_games_black_player FOREIGN KEY (black_player_id) REFERENCES players(id)
);

CREATE TABLE IF NOT EXISTS moves (
    id SERIAL PRIMARY KEY,
    game_id VARCHAR(36) NOT NULL,
    move_index INTEGER NOT NULL,
    from_col INTEGER NOT NULL,
    from_row INTEGER NOT NULL,
    to_col INTEGER NOT NULL,
    to_row INTEGER NOT NULL,
    CONSTRAINT fk_moves_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);
