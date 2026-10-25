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
