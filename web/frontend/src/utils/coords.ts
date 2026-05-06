export function indexToSquare(index: number): string {
    const file = String.fromCharCode(97 + (index % 8)); // a-h
    const rank = 8 - Math.floor(index / 8);
    return `${file}${rank}`;
}