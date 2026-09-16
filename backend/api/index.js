import { app } from '../dist/index.js';

// Vercel serverless entry: hand the raw request to Fastify's http server.
export default async function handler(req, res) {
  await app.ready();
  app.server.emit('request', req, res);
}
