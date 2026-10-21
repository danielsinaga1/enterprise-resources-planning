import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Modul ERP (port klien ENTARIS) memakai impor `@/…` relatif terhadap src/erp.
      // Kode platform lain tidak memakai alias ini.
      '@': fileURLToPath(new URL('./src/erp', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    proxy: {
      // Dev: proxy directly to Tomcat backend (:8082).
      // In production, requests go through the Spring Cloud Gateway (:8080).
      // Vite's http-proxy library is incompatible with Netty's response headers
      // (Parse Error: Data after `Connection: close`) so we bypass the gateway
      // in dev only. JWT validation is still enforced at the backend level.
      //
      // RAG chatbot: the FastAPI service is a separate process (:8000). In prod
      // the gateway rewrites /api/rag/** -> /api/v1/** and injects the validated
      // user headers; in dev we bypass the gateway, so we replicate the rewrite
      // here and the FastAPI service validates the shared-secret IRMS JWT itself.
      // This rule is listed before '/api' so it wins for /api/rag paths.
      '/api/rag': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/rag/, '/api/v1'),
      },
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
})
