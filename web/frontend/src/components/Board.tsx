import React from "react";
import { fenToBoard } from "../utils/fen";
import { Square } from "./Squares.tsx";
import { indexToSquare } from "../utils/coords";
import { useState } from "react";

interface Props {
    fen: string;
    onMove?: (from: string, to: string, promotion?: string | null, castling?: string | null) => void;
    turn: "White" | "Black";
}

export function Board({ fen, onMove, turn }: Props) {
    const board = fenToBoard(fen);
    const [selected, setSelected] = useState<string | null>(null);
    const [showPromotionDialog, setShowPromotionDialog] = useState(false);
    const [pendingMove, setPendingMove] = useState<{ from: string; to: string } | null>(null);

    function handleClick(pos: string) {
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
                const isWhitePiece = piece === piece?.toUpperCase();
                
                // Check if this is a pawn promotion
                const isWhitePawn = isPawn && isWhitePiece && turn === "White";
                const isBlackPawn = isPawn && !isWhitePiece && turn === "Black";
                
                const isPromotion = (isWhitePawn && toRank === 0) || (isBlackPawn && toRank === 7);
                
                if (isPromotion) {
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
            <div
                style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(8, 60px)",
                    border: "2px solid #1a1a1a",
                    borderRadius: "4px",
                    overflow: "hidden",
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

            {/* Promotion Dialog */}
            {showPromotionDialog && (
                <div style={{
                    position: "fixed",
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundColor: "rgba(0, 0, 0, 0.7)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    zIndex: 1000
                }}>
                    <div style={{
                        background: "#1E1E1E",
                        padding: "2rem",
                        borderRadius: "8px",
                        border: "1px solid #444",
                        minWidth: "300px"
                    }}>
                        <h3 style={{ color: "#e0e0e0", marginBottom: "1rem" }}>Choose Promotion Piece</h3>
                        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "center", marginBottom: "1rem" }}>
                            {['q', 'r', 'b', 'n'].map((piece) => (
                                <button
                                    key={piece}
                                    onClick={() => handlePromotion(piece)}
                                    style={{
                                        width: "60px",
                                        height: "60px",
                                        fontSize: "2rem",
                                        background: "#2C2C2C",
                                        border: "1px solid #555",
                                        borderRadius: "4px",
                                        cursor: "pointer",
                                        color: "#e0e0e0"
                                    }}
                                >
                                    {piece.toUpperCase()}
                                </button>
                            ))}
                        </div>
                        <button
                            onClick={handleCancelPromotion}
                            className="secondary"
                            style={{ width: "100%" }}
                        >
                            Cancel
                        </button>
                    </div>
                </div>
            )}
        </>
    );
}