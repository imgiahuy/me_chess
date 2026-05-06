import { fenToBoard } from "../utils/fen";
import { Square } from "./Squares.tsx";
import { indexToSquare } from "../utils/coords";
import { useState } from "react";

interface Props {
    fen: string;
    onMove?: (from: string, to: string) => void;
}

export function Board({ fen, onMove }: Props) {
    const board = fenToBoard(fen);
    const [selected, setSelected] = useState<string | null>(null);

    function handleClick(pos: string) {
        if (!selected) {
            setSelected(pos);
        } else {
            if (selected !== pos) {
                onMove?.(selected, pos);
            }
            setSelected(null);
        }
    }

    return (
        <div
            style={{
        display: "grid",
            gridTemplateColumns: "repeat(8, 60px)",
            border: "2px solid black",
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
);
}