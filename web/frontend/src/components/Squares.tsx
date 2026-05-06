import React from "react";

interface SquareProps {
    piece: string;        // "r", "P", etc. or ""
    position: string;     // "e4"
    isDark: boolean;
    isSelected?: boolean;
    onClick?: (pos: string) => void;
}

export function Square({
                           piece,
                           position,
                           isDark,
                           isSelected = false,
                           onClick,
                       }: SquareProps) {
    const backgroundColor = isSelected
        ? "yellow"
        : isDark
            ? "#769656"
            : "#eeeed2";

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
                fontSize: 24,
                userSelect: "none",
            }}
        >
            {piece}
        </div>
    );
}