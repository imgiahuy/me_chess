import React from "react";

interface SquareProps {
    piece: string;        // "r", "P", etc. or ""
    position: string;     // "e4"
    isDark: boolean;
    isSelected?: boolean;
    onClick?: (pos: string) => void;
}

const PIECE_SYMBOLS: Record<string, string> = {
    'K': '♔', 'Q': '♕', 'R': '♖', 'B': '♗', 'N': '♘', 'P': '♙',
    'k': '♚', 'q': '♛', 'r': '♜', 'b': '♝', 'n': '♞', 'p': '♟',
};

export function Square({
                           piece,
                           position,
                           isDark,
                           isSelected = false,
                           onClick,
                       }: SquareProps) {
    const backgroundColor = isSelected
        ? "#FFD700"
        : isDark
            ? "#B58863"
            : "#F0D9B5";

    const pieceSymbol = piece ? PIECE_SYMBOLS[piece] || piece : '';

    return (
        <div
            onClick={() => onClick?.(position)}
            style={{
                width: 60,
                height: 60,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                backgroundColor,
                cursor: "pointer",
                fontSize: 40,
                userSelect: "none",
                transition: "background-color 0.1s",
            }}
            onMouseEnter={(e) => {
                if (!isSelected) {
                    e.currentTarget.style.backgroundColor = isDark ? "#C9A86C" : "#E8C89A";
                }
            }}
            onMouseLeave={(e) => {
                if (!isSelected) {
                    e.currentTarget.style.backgroundColor = backgroundColor;
                }
            }}
        >
            <span style={{ 
                color: piece === piece.toUpperCase() ? '#fff' : '#000',
                textShadow: piece === piece.toUpperCase() ? '0 0 2px #000' : 'none'
            }}>
                {pieceSymbol}
            </span>
        </div>
    );
}