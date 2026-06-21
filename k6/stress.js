import { sleep } from 'k6';
import { createGame, getGame, makeMove, listGames, getStatus, openingMoves } from './shared.js';

export const options = {
  stages: [
    { duration: '2m', target: 50 },
    { duration: '5m', target: 50 },
    { duration: '2m', target: 0 }
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500'],
    http_req_failed: ['rate<0.1']
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
