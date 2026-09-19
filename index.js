import { app } from './backend/dist/index.js';

// Vercel serverless entry for node framework
export default async function handler(req, res) {
  await app.ready();
  app.server.emit('request', req, res);
}