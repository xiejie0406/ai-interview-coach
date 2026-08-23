import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: false,
        xfwd: true,
        rewrite: (path) => path.replace(/^\/api(?!\/v1)/, ''),
      },
      '/actuator': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: false,
        xfwd: true,
      },
    },
  },
  build: {
    sourcemap: true,
  },
});
