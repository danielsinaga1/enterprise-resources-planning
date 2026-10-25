import { defineConfig, devices } from '@playwright/test';

const PORT = 5173;
const BASE_URL = `http://localhost:${PORT}`;

/**
 * E2E dijalankan terhadap dev server Vite.
 *
 * Perhatikan: dev server mem-proxy /api ke backend :8082 dan /api/rag ke :8000.
 * Skenario yang menyentuh data asli hanya jalan kalau kedua service itu hidup —
 * test di `e2e/` sengaja dibatasi pada hal yang murni frontend, supaya suite ini
 * tidak merah hanya karena backend lokal sedang mati.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? 'github' : 'list',

  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  webServer: {
    command: 'npm run dev',
    url: BASE_URL,
    // Dev server yang sudah jalan dipakai ulang, jadi menjalankan test saat
    // sedang ngoding tidak menyalakan Vite kedua di port lain.
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
