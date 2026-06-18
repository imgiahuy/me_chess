import React from "react";
import { fenToBoard } from "../utils/fen";
import { Square } from "./Squares.tsx";
import { indexToSquare } from "../utils/coords";
import { useState } from "react";

interface Props {
    fen: string;
    onMove?: (from: string, to: string, promotion?: string | null, castling?: string | null) => void;
    turn: "White" | "Black";
    gameOver?: boolean;
}

export function Board({ fen, onMove, turn, gameOver = false }: Props) {
    const board = fenToBoard(fen);
    const [selected, setSelected] = useState<string | null>(null);
    const [showPromotionDialog, setShowPromotionDialog] = useState(false);
    const [pendingMove, setPendingMove] = useState<{ from: string; to: string } | null>(null);

    function handleClick(pos: string) {
        if (gameOver) return;
        if (!selected) {
            setSelected(pos);
        } else {
            if (selected !== pos) {
                // Convert algebraic notation to board indices
                const fromFile = selected.charCodeAt(0) - 97; // a=0, b=1, etc.
                const fromRank = 8 - parseInt(selected[1]); // 8=0, 7=1, etc.
                const toRank = 8 - parseInt(pos[1]);
                
                // Get the piece at the from position
                const piece = board[fromRank][fromFile];
                const isPawn = piece?.toLowerCase() === 'p';
                const isWhitePiece = piece && piece === piece.toUpperCase();
                
                // Check if this is a castling move (king moves 2 squares horizontally)
                const toFile = pos.charCodeAt(0) - 97;
                const isKing = piece?.toLowerCase() === 'k';
                const isCastling = isKing && Math.abs(toFile - fromFile) === 2 && parseInt(pos[1]) === parseInt(selected[1]);

                // Check if this is a pawn promotion
                const isWhitePawn = isPawn && isWhitePiece && turn === "White";
                const isBlackPawn = isPawn && !isWhitePiece && turn === "Black";
                
                const isPromotion = (isWhitePawn && toRank === 0) || (isBlackPawn && toRank === 7);
                
                if (isCastling) {
                    const castlingType = toFile > fromFile ? "kingside" : "queenside";
                    onMove?.(selected, pos, null, castlingType);
                    setSelected(null);
                } else if (isPromotion) {
                    setPendingMove({ from: selected, to: pos });
                    setShowPromotionDialog(true);
                    setSelected(null);
                } else {
                    onMove?.(selected, pos);
                    setSelected(null);
                }
            } else {
                setSelected(null);
            }
        }
    }

    function handlePromotion(piece: string) {
        if (pendingMove) {
            onMove?.(pendingMove.from, pendingMove.to, piece);
            setPendingMove(null);
            setShowPromotionDialog(false);
        }
    }

    function handleCancelPromotion() {
        setPendingMove(null);
        setShowPromotionDialog(false);
    }

    return (
        <>
            <div style={{ position: "relative", display: "inline-block" }}>
            {gameOver && (
                <div style={{
                    position: "absolute",
                    inset: 0,
                    backgroundColor: "rgba(0, 0, 0, 0.45)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    zIndex: 10,
                    borderRadius: "4px",
                    pointerEvents: "none",
                }}>
                    <span style={{ color: "#FFD700", fontSize: "2rem", fontWeight: "bold", textShadow: "0 2px 8px #000" }}>Game Over</span>
                </div>
            )}
            <div
                style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(8, 60px)",
                    border: "2px solid #1a1a1a",
                    borderRadius: "4px",
                    overflow: "hidden",
                    cursor: gameOver ? "not-allowed" : "pointer",
                }}
            >
                {board.flat().map((piece, i) => {
                    const pos = indexToSquare(i);
                    const isDark = (Math.floor(i / 8) + i) % 2 === 1;

                    return (
                        <Square
                            key={i}
                            piece={piece}
                            position={pos}
                            isDark={isDark}
                            isSelected={selected === pos}
                            onClick={handleClick}
                        />
                    );
                })}
            </div>
            </div>

            {/* Promotion Dialog */}
            {showPromotionDialog && (
                <div style={{
                    position: "fixed", inset: 0,
                    backgroundColor: "rgba(0,0,0,0.7)",
                    display: "flex", alignItems: "center", justifyContent: "center",
                    zIndex: 1000,
                }}>
                    <div style={{
                        background: "var(--color-surface)",
                        border: "1px solid var(--color-border)",
                        borderRadius: "var(--radius-lg)",
                        boxShadow: "var(--shadow-lg)",
                        padding: "1.5rem",
                        minWidth: 280,
                    }}>
                        <h3 style={{ marginBottom: "1rem", textAlign: "center" }}>Choose Promotion</h3>
                        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "center", marginBottom: "1rem" }}>
                            {[
                                { key: 'q', symbol: '♕' },
                                { key: 'r', symbol: '♖' },
                                { key: 'b', symbol: '♗' },
                                { key: 'n', symbol: '♘' },
                            ].map(({ key, symbol }) => (
                                <button
                                    key={key}
                                    onClick={() => handlePromotion(key)}
                                    className="secondary"
                                    style={{
                                        width: 62, height: 62,
                                        fontSize: "2.25rem",
                                        padding: 0,
                                        display: "flex", alignItems: "center", justifyContent: "center",
                                        borderRadius: "var(--radius-sm)",
                                    }}
                                >
                                    {symbol}
                                </button>
                            ))}
                        </div>
                        <button onClick={handleCancelPromotion} className="ghost" style={{ width: "100%" }}>
                            Cancel
                        </button>
                    </div>
                </div>
            )}
        </>
    );
}