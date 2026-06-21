import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/v1/chess';

export function createGame() {
  const payload = JSON.stringify({
    whitePlayer: `player_${Math.random().toString(36).substring(7)}`,
    blackPlayer: `player_${Math.random().toString(36).substring(7)}`,
    timeControl: 'unlimited'
  });
  const res = http.post(`${BASE_URL}/games`, payload, {
    headers: { 'Content-Type': 'application/json' }
  });
  check(res, {
    'create game status is 201': (r) => r.status === 201,
    'create game returns gameId': (r) => r.json('gameId') !== undefined
  });
  return res.json('gameId');
}

export function getGame(gameId) {
  const res = http.get(`${BASE_URL}/games/${gameId}`);
  check(res, {
    'get game status is 200': (r) => r.status === 200
  });
  return res;
}

export function makeMove(gameId, from, to) {
  const payload = JSON.stringify({ from, to });
  const res = http.post(`${BASE_URL}/games/${gameId}/moves`, payload, {
    headers: { 'Content-Type': 'application/json' }
  });
  check(res, {
    'make move status is 200 or 400': (r) => r.status === 200 || r.status === 400
  });
  return res;
}

export function listGames() {
  const res = http.get(`${BASE_URL}/games`);
  check(res, {
    'list games status is 200': (r) => r.status === 200
  });
  return res;
}

export function getStatus(gameId) {
  const res = http.get(`${BASE_URL}/games/${gameId}/status`);
  check(res, {
    'get status status is 200': (r) => r.status === 200
  });
  return res;
}

export const openingMoves = [
  { from: 'e2', to: 'e4' },
  { from: 'd2', to: 'd4' },
  { from: 'c2', to: 'c4' },
  { from: 'g1', to: 'f3' },
  { from: 'b1', to: 'c3' }
];
