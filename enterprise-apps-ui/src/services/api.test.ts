import { describe, expect, it } from 'vitest';

import { server } from '../test/msw/server';
import { failedResponse, http, HttpResponse, okResponse } from '../test/msw/handlers';
import api, { HttpError, buildParams, toFormData, toPaginatedResponse, unwrap } from './api';

describe('api — autentikasi', () => {
  it('melampirkan Bearer token dari localStorage', async () => {
    let seen: string | null = null;
    server.use(
      http.get('*/api/v1/ping', ({ request }) => {
        seen = request.headers.get('Authorization');
        return okResponse({ ok: true });
      }),
    );

    localStorage.setItem('access_token', 'token-abc');
    await api.get('/api/v1/ping');

    expect(seen).toBe('Bearer token-abc');
  });

  it('me-refresh token lalu mengulang request saat kena 401', async () => {
    server.use(
      http.get('*/api/v1/ping', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer token-baru'
          ? okResponse({ ok: true })
          : new HttpResponse(null, { status: 401 }),
      ),
      http.post('*/api/v1/auth/refresh-token', () =>
        HttpResponse.json({ token: 'token-baru', refreshToken: 'refresh-baru' }),
      ),
    );

    localStorage.setItem('access_token', 'token-lama');
    localStorage.setItem('refresh_token', 'refresh-lama');

    const res = await api.get<{ data: { ok: boolean } }>('/api/v1/ping');

    expect(res.status).toBe(200);
    // Token baru harus tersimpan, kalau tidak request berikutnya kena 401 lagi.
    expect(localStorage.getItem('access_token')).toBe('token-baru');
    expect(localStorage.getItem('refresh_token')).toBe('refresh-baru');
  });

  it('hanya sekali me-refresh walau beberapa request kena 401 bersamaan', async () => {
    // Ini alasan `isRefreshing` + `failedQueue` ada. Tanpa antrean, tiga request
    // paralel akan memicu tiga refresh dan dua di antaranya memakai refresh token
    // yang sudah dirotasi — yang di server berarti logout paksa.
    let refreshCount = 0;
    server.use(
      http.get('*/api/v1/ping', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer token-baru'
          ? okResponse({ ok: true })
          : new HttpResponse(null, { status: 401 }),
      ),
      http.post('*/api/v1/auth/refresh-token', () => {
        refreshCount += 1;
        return HttpResponse.json({ token: 'token-baru', refreshToken: 'refresh-baru' });
      }),
    );

    localStorage.setItem('access_token', 'token-lama');
    localStorage.setItem('refresh_token', 'refresh-lama');

    const hasil = await Promise.all([
      api.get('/api/v1/ping'),
      api.get('/api/v1/ping'),
      api.get('/api/v1/ping'),
    ]);

    expect(hasil.map((r) => r.status)).toEqual([200, 200, 200]);
    expect(refreshCount).toBe(1);
  });

  it('tidak mencoba refresh saat login sendiri yang gagal', async () => {
    // Kalau login 401 ikut memicu refresh, user salah password akan melihat error
    // "sesi berakhir" alih-alih "kredensial salah".
    let refreshCalled = false;
    server.use(
      http.post('*/api/v1/auth/login', () => failedResponse('Kredensial salah', 401)),
      http.post('*/api/v1/auth/refresh-token', () => {
        refreshCalled = true;
        return HttpResponse.json({ token: 'x' });
      }),
    );

    await expect(api.post('/api/v1/auth/login', { npk: '123' })).rejects.toBeInstanceOf(HttpError);
    expect(refreshCalled).toBe(false);
  });
});

describe('api — penanganan error', () => {
  it('membungkus response non-2xx sebagai HttpError beserta status dan body', async () => {
    server.use(http.get('*/api/v1/ping', () => failedResponse('Data tidak ditemukan', 404)));

    await expect(api.get('/api/v1/ping')).rejects.toMatchObject({
      name: 'HttpError',
      status: 404,
    });
  });
});

describe('buildParams', () => {
  it('membuang nilai undefined, null, dan string kosong', () => {
    // Tanpa ini, query string berisi `status=undefined` dan backend memfilter
    // dengan nilai literal "undefined".
    expect(buildParams({ status: 'OPEN', divisi: undefined, unit: null, q: '' })).toEqual({
      status: 'OPEN',
    });
  });

  it('mengubah angka dan boolean menjadi string', () => {
    expect(buildParams({ page: 2, aktif: false })).toEqual({ page: '2', aktif: 'false' });
  });
});

describe('unwrap', () => {
  it('mengembalikan payload saat status SUCCESS', () => {
    expect(unwrap({ status: 'SUCCESS', data: { id: 1 } } as never)).toEqual({ id: 1 });
  });

  it('melempar error dengan pesan dari backend saat status FAILED', () => {
    expect(() => unwrap({ status: 'FAILED', errorMessage: 'Akses ditolak' } as never)).toThrow(
      'Akses ditolak',
    );
  });
});

describe('toPaginatedResponse', () => {
  it('memetakan bentuk response backend ke bentuk paginasi frontend', () => {
    const hasil = toPaginatedResponse(
      { status: 'SUCCESS', data: [{ id: 1 }, { id: 2 }], page: 2, countData: 12, totalPage: 3 } as never,
      10,
    );

    expect(hasil).toMatchObject({ current_page: 2, last_page: 3, per_page: 10, total: 12 });
    // Halaman 2 berisi item ke-11 dan ke-12.
    expect(hasil.from).toBe(11);
    expect(hasil.to).toBe(12);
  });

  it('memberi from/to null saat data kosong', () => {
    // Supaya UI tidak menampilkan "menampilkan 1–0 dari 0".
    const hasil = toPaginatedResponse({ status: 'SUCCESS', data: [], page: 1, countData: 0 } as never);

    expect(hasil.from).toBeNull();
    expect(hasil.to).toBeNull();
  });
});

describe('toFormData', () => {
  it('meratakan objek bersarang memakai notasi kurung siku', () => {
    const fd = toFormData({ pelapor: { nama: 'Budi', npk: '123' } });

    expect(fd.get('pelapor[nama]')).toBe('Budi');
    expect(fd.get('pelapor[npk]')).toBe('123');
  });

  it('melewatkan File apa adanya, tidak menjadikannya string', () => {
    const file = new File(['isi'], 'bukti.png', { type: 'image/png' });
    const fd = toFormData({ lampiran: [file] });

    expect(fd.get('lampiran[0]')).toBeInstanceOf(File);
  });

  it('membuang field undefined dan null', () => {
    const fd = toFormData({ judul: 'Insiden', catatan: undefined, penutup: null });

    expect(fd.has('catatan')).toBe(false);
    expect(fd.has('penutup')).toBe(false);
    expect(fd.get('judul')).toBe('Insiden');
  });
});
