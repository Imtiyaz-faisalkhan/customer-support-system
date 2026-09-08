import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Same-origin from the browser's POV -> no CORS, no credentials:'include' needed.
      // Note: `npm run preview` does NOT use this block (see Vite's `preview.proxy` if ever needed).
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
