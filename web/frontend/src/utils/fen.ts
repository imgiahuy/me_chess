export function fenToBoard(fen: string): string[][] {
    const rows = fen.split(" ")[0].split("/");

    return rows.map(row => {
        const result: string[] = [];

        for (const char of row) {
            if (isNaN(Number(char))) {
                result.push(char);
            } else {
                for (let i = 0; i < Number(char); i++) {
                    result.push("");
                }
            }
        }

        return result;
    });
}