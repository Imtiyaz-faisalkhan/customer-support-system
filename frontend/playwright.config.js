import { defineConfig } from '@playwright/test'

// Assumes the full stack is already running: `docker compose up -d` (Postgres),
// the backend on :8080, and `npm run dev` (frontend) on :5173.
export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL: 'http://localhost:5173',
  },
})
