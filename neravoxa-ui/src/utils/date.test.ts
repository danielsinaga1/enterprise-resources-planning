import { describe, expect, it } from 'vitest';

import { toLocalISODate, toLocalISODateOrUndefined } from './date';

describe('toLocalISODate', () => {
  it('memakai tanggal kalender lokal, bukan hasil konversi UTC', () => {
    // Ini inti dari utilitas ini: pukul 23:30 WIB masih tanggal 1, sedangkan
    // toISOString() akan menggesernya ke tanggal 1 pukul 16:30 UTC — dan untuk
    // offset yang lebih besar bisa lompat ke tanggal berikutnya.
    // `new Date(y, m, d, ...)` dibangun dalam waktu lokal, jadi test ini
    // deterministik di timezone manapun.
    expect(toLocalISODate(new Date(2026, 0, 1, 23, 30))).toBe('2026-01-01');
  });

  it('tetap benar pada pukul 00:00 lokal', () => {
    expect(toLocalISODate(new Date(2026, 0, 1, 0, 0))).toBe('2026-01-01');
  });

  it('memberi padding nol pada bulan dan tanggal satu digit', () => {
    expect(toLocalISODate(new Date(2026, 8, 5))).toBe('2026-09-05');
  });
});

describe('toLocalISODateOrUndefined', () => {
  it('meneruskan tanggal yang terisi', () => {
    expect(toLocalISODateOrUndefined(new Date(2026, 0, 1))).toBe('2026-01-01');
  });

  it('mengembalikan undefined untuk null/undefined agar field tidak ikut terkirim', () => {
    expect(toLocalISODateOrUndefined(null)).toBeUndefined();
    expect(toLocalISODateOrUndefined(undefined)).toBeUndefined();
  });
});
