import { describe, expect, it } from 'vitest';

import { decodeReportId, encodeReportId } from './reportId';

describe('encodeReportId', () => {
  it('menghasilkan string yang stabil untuk id yang sama', () => {
    // Link laporan bisa di-bookmark dan dibagikan, jadi encoding wajib deterministik.
    expect(encodeReportId(291)).toBe(encodeReportId(291));
  });

  it('menghasilkan string berbeda untuk id berurutan', () => {
    // Inti dari obfuskasi: /incident-reports/291 tidak boleh bisa ditebak jadi 292.
    expect(encodeReportId(291)).not.toBe(encodeReportId(292));
  });

  it('menerima id berupa string angka', () => {
    expect(encodeReportId('291')).toBe(encodeReportId(291));
  });

  it('mengembalikan nilai apa adanya untuk id non-positif atau bukan angka', () => {
    expect(encodeReportId(0)).toBe('0');
    expect(encodeReportId(-1)).toBe('-1');
    expect(encodeReportId('abc')).toBe('abc');
  });
});

describe('decodeReportId', () => {
  it('membalik hasil encode kembali ke id semula', () => {
    expect(decodeReportId(encodeReportId(291))).toBe(291);
  });

  it('menerima angka polos apa adanya', () => {
    // Email notifikasi dibuat di server yang tidak tahu encoding ini dan menaut
    // langsung ke id numerik — link lama tersebut harus tetap jalan.
    expect(decodeReportId('291')).toBe(291);
  });

  it('mengembalikan null untuk nilai kosong', () => {
    expect(decodeReportId(undefined)).toBeNull();
    expect(decodeReportId('')).toBeNull();
  });

  it('mengembalikan null untuk string yang tidak bisa didekode', () => {
    // Pemanggil memakai null untuk menampilkan "tidak ditemukan" alih-alih
    // menembak API dengan NaN.
    expect(decodeReportId('!!!')).toBeNull();
  });
});
