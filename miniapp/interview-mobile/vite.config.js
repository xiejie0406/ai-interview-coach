import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    host: '127.0.0.1',
    port: 9090,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        // uni-app 将本地 api 模块也编译到 /api/*.js，不能转发给后端。
        bypass: (req) => /^\/api\/.*\.js(?:\?|$)/.test(req.url) ? req.url : undefined,
        rewrite: (path) => path.replace(/^\/api(?!\/v1)/, ''),
      },
      '/ws': {
        target: 'ws://127.0.0.1:8081',
        changeOrigin: true,
        ws: true,
      }
    }
  }
})
