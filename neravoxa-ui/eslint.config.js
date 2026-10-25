import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  // Artefak hasil build/laporan. Tanpa ini `eslint .` ikut memeriksa berkas yang
  // digenerate (mis. coverage/lcov-report/*.js) dan gagal karena kode yang bukan milik kita —
  // cukup untuk memerahkan gate CI tanpa ada yang salah di kode aplikasi.
  globalIgnores(['dist', 'coverage', 'playwright-report', 'test-results']),
  {
    files: ['**/*.{js,jsx}'],
    extends: [
      js.configs.recommended,
      reactHooks.configs['recommended-latest'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        ecmaVersion: 'latest',
        ecmaFeatures: { jsx: true },
        sourceType: 'module',
      },
    },
    rules: {
      'no-unused-vars': ['error', { varsIgnorePattern: '^[A-Z_]' }],
    },
  },
  {
    // Service worker FCM jalan di scope ServiceWorkerGlobalScope, bukan window: `clients`
    // dan `importScripts` memang tidak ada di globals.browser. `firebase` disuntikkan
    // oleh compat script yang dimuat lewat importScripts, jadi dideklarasikan manual.
    files: ['public/firebase-messaging-sw.js'],
    languageOptions: {
      globals: {
        ...globals.serviceworker,
        firebase: 'readonly',
      },
    },
  },
])
