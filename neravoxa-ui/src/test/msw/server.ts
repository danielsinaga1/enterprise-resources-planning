import { setupServer } from 'msw/node';

import { handlers } from './handlers';

/**
 * Server MSW untuk lingkungan Node (Vitest). Meng-intercept `fetch` di level
 * network, jadi `src/services/*` diuji apa adanya — tanpa perlu mem-mock modul
 * `api.ts` dan tanpa risiko test lolos karena mock-nya yang salah, bukan kodenya.
 */
export const server = setupServer(...handlers);
