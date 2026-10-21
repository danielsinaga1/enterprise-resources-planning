// Harus paling atas: menambal Web Storage sebelum modul lain sempat menyentuhnya.
import './webstorage';

import '@testing-library/jest-dom/vitest';

import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { server } from './msw/server';

// `error` (bukan `warn`) supaya request yang tidak diantisipasi menggagalkan test.
// Request yang lolos diam-diam adalah cara paling umum sebuah test "hijau" padahal
// kodenya menembak endpoint yang salah.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  localStorage.clear();
});

afterAll(() => server.close());
