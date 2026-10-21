import { describe, it, expect } from 'vitest';
import { toLocalDateTimeString } from './date';

/**
 * Berkas terpisah dari date.test.ts karena berkas itu masih WIP sesi lain yang
 * belum di-commit; memisahkan menjaga commit tetap bersih.
 */
describe('toLocalDateTimeString', () => {
  it('memakai komponen waktu lokal, bukan UTC', () => {
    // Inti bug yang diperbaiki: di zona +08:00, toISOString() pada tengah malam
    // tanggal 30 menghasilkan "2026-09-29T16:00" — tenggat mundur sehari dan
    // pengingat H-3/H+3 ikut salah hari.
    const d = new Date(2026, 8, 30, 0, 0, 0); // 30 Sep 2026 00:00 lokal
    expect(toLocalDateTimeString(d)).toBe('2026-09-30 17:00:00');
  });

  it('tengah malam dinaikkan ke 17:00 sebagai akhir hari kerja', () => {
    const d = new Date(2026, 0, 5, 0, 0, 0);
    expect(toLocalDateTimeString(d)).toBe('2026-01-05 17:00:00');
  });

  it('jam yang dipilih pengguna dipertahankan apa adanya', () => {
    const d = new Date(2026, 0, 5, 9, 30, 15);
    expect(toLocalDateTimeString(d)).toBe('2026-01-05 09:30:15');
  });

  it('kenaikan ke akhir hari bisa dimatikan', () => {
    const d = new Date(2026, 0, 5, 0, 0, 0);
    expect(toLocalDateTimeString(d, false)).toBe('2026-01-05 00:00:00');
  });

  it('bulan dan tanggal satu digit tetap dua karakter', () => {
    const d = new Date(2026, 2, 7, 8, 5, 4);
    expect(toLocalDateTimeString(d)).toBe('2026-03-07 08:05:04');
  });
});
