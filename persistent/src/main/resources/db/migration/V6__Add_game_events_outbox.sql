-- Event sourcing outbox table for reliable event publishing to Kafka.
-- Events are written transactionally alongside the main game state,
-- then a background process publishes them to Kafka and marks them as sent.

CREATE TABLE IF NOT EXISTS game_events (
    id BIGSERIAL PRIMARY KEY,
    game_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP,
    CONSTRAINT fk_game_events_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_game_events_unpublished ON game_events(published, created_at) WHERE published = FALSE;
CREATE INDEX IF NOT EXISTS idx_game_events_game_id ON game_events(game_id);
CREATE INDEX IF NOT EXISTS idx_game_events_event_type ON game_events(event_type);
