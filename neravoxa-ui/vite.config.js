import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Backend dev (bawaan :8090); ganti lewat DEV_BACKEND_URL di environment atau .env.local.
  const backend = loadEnv(mode, process.cwd(), '').DEV_BACKEND_URL || 'http://localhost:8090'

  return {
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
      // Port dev Neravoxa (5173 sering dipakai proyek Vite lain di mesin yang sama).
      port: 5180,
      proxy: {
        // Dev: proxy directly to the Tomcat backend (DEV_BACKEND_URL, default :8090).
        // In production, requests go through the Spring Cloud Gateway (:8080).
        // Vite's http-proxy library is incompatible with Netty's response headers
        // (Parse Error: Data after `Connection: close`) so we bypass the gateway
        // in dev only. JWT validation is still enforced at the backend level.
        '/api': {
          target: backend,
          changeOrigin: true,
        },
        '/uploads': {
          target: backend,
          changeOrigin: true,
        },
      },
    },
  }
})
