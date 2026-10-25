/// <reference types="vitest" />
import { defineConfig, mergeConfig } from 'vitest/config';

import viteConfig from './vite.config.js';

/**
 * Konfigurasi test dipisah dari `vite.config.js` supaya setelan dev-server
 * (proxy ke backend) tidak tercampur dengan setelan test. Plugin React
 * tetap diwarisi lewat `mergeConfig`, jadi JSX di file test ikut ter-transform.
 * `vite.config.js` berbentuk fungsi (membaca env per mode), jadi dipanggil dulu.
 */
export default defineConfig((configEnv) =>
  mergeConfig(
    viteConfig(configEnv),
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
          exclude: ['src/**/*.d.ts'],
        },
      },
    }),
  ),
);
