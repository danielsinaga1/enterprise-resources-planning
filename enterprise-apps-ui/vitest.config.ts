/// <reference types="vitest" />
import { defineConfig, mergeConfig } from 'vitest/config';

import viteConfig from './vite.config.js';

/**
 * Konfigurasi test dipisah dari `vite.config.js` supaya setelan dev-server
 * (proxy ke :8082 / :8000) tidak tercampur dengan setelan test. Plugin React
 * tetap diwarisi lewat `mergeConfig`, jadi JSX di file test ikut ter-transform.
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      // Test unit/komponen hanya di dalam src. Folder `e2e/` milik Playwright —
      // kalau ikut terbaca Vitest, test Playwright akan gagal dengan error
      // "test.describe() is not supported" yang membingungkan.
      include: ['src/**/*.{test,spec}.{ts,tsx}'],
      exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
      css: false,
      restoreMocks: true,
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov'],
        reportsDirectory: './coverage',
        include: ['src/services/**', 'src/utils/**', 'src/components/**'],
        // Scaffolding template bawaan yang tidak dipakai — sudah jadi sumber
        // 24 error tsc baseline, jangan ikut mengotori laporan coverage.
        exclude: ['src/components/admin-panel/**', 'src/**/*.d.ts'],
      },
    },
  }),
);
