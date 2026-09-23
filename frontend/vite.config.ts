import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Only /api and /images are proxied to the backend - the management port (8081, metrics)
// is deliberately unreachable from the SPA (research R-26).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/images': 'http://localhost:8080',
    },
  },
});
