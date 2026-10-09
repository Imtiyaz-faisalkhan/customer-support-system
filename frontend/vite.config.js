import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { configDefaults } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Vite rejects requests whose Host it doesn't know (DNS-rebinding protection). Allow ngrok addresses so the
    // tunnel (scripts/start-tunnel.sh) can serve the app -- and, via the /api proxy below, the Resend webhook.
    allowedHosts: ['.ngrok-free.dev', '.ngrok-free.app'],
    proxy: {
      // Same-origin from the browser's POV -> no CORS, no credentials:'include' needed.
      // Note: `npm run preview` does NOT use this block (see Vite's `preview.proxy` if ever needed).
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    // Playwright's e2e specs use a different test runner/globals -- keep Vitest out of that directory.
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
})
