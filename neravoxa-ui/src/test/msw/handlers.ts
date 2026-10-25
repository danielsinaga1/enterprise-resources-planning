import { http, HttpResponse } from 'msw';

/**
 * Handler default sengaja dibiarkan kosong.
 *
 * `setup.ts` menjalankan server dengan `onUnhandledRequest: 'error'`, jadi setiap
 * test wajib mendeklarasikan endpoint yang dipakainya sendiri lewat `server.use()`.
 * Ini disengaja: kalau sebuah komponen diam-diam menembak endpoint yang tidak
 * diharapkan, test gagal keras alih-alih lolos dengan response kosong.
 */
export const handlers = [];

/**
 * Pola URL dipakai dengan awalan `*` supaya cocok di origin manapun.
 *
 * `api.ts` menyusun URL lewat `new URL(path, VITE_API_URL || window.location.origin)`,
 * jadi origin saat test bergantung pada jsdom, bukan pada nilai produksi. Mencocokkan
 * path relatif saja akan rapuh begitu `VITE_API_URL` diisi.
 */
export const anyOrigin = (path: string) => `*${path}`;

/** Response sukses dengan bentuk pembungkus `Response.builder()` milik backend. */
export const okResponse = <T,>(data: T, extra: Record<string, unknown> = {}) =>
  HttpResponse.json({ status: 'SUCCESS', data, ...extra });

/** Response gagal dengan bentuk yang sama seperti yang dikirim backend. */
export const failedResponse = (errorMessage: string, status = 400) =>
  HttpResponse.json({ status: 'FAILED', errorMessage, data: null }, { status });

export { http, HttpResponse };
