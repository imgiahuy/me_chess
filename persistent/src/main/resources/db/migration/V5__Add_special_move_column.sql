-- Add special_move column to moves table for storing castling, en passant, promotion info

ALTER TABLE moves ADD COLUMN IF NOT EXISTS special_move VARCHAR(30);

CREATE INDEX IF NOT EXISTS idx_moves_special_move ON moves(special_move) WHERE special_move IS NOT NULL;
