import { sleep } from 'k6';
import { createGame, getGame, makeMove, listGames, getStatus, openingMoves } from './shared.js';

export const options = {
  vus: 1,
  duration: '1m',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05']
  }
};

export default function () {
  listGames();
  const gameId = createGame();
  sleep(0.5);
  getGame(gameId);
  getStatus(gameId);
  for (let i = 0; i < 5; i++) {
    const move = openingMoves[i % openingMoves.length];
    makeMove(gameId, move.from, move.to);
    sleep(0.2);
  }
  sleep(1);
}
