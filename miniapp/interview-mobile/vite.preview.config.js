import { defineConfig } from 'vite'

const backend = 'http://127.0.0.1:8081'

export default defineConfig({
  preview: {
    host: '127.0.0.1',
    port: 9090,
    proxy: {
      '/api': {
        target: backend,
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/api(?!\/v1)/, ''),
        headers: { Origin: backend }
      },
      '/ws': {
        target: backend,
        changeOrigin: true,
        secure: false,
        ws: true,
        rewriteWsOrigin: true,
        headers: { Origin: backend }
      }
    }
  }
})
