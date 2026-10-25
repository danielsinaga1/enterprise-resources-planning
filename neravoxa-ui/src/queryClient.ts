import { QueryClient } from "@tanstack/react-query";
import { HttpError } from "./services/api";

// ============================================================
// Satu QueryClient untuk seluruh app — cache React Query hidup di objek ini.
// Dipisah dari main.tsx supaya bisa diimpor di test dan dipakai untuk
// invalidasi cache dari luar komponen bila perlu.
// ============================================================
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Data dianggap segar 30 detik: navigasi bolak-balik antar halaman dalam
      // rentang itu dilayani dari cache tanpa memanggil API lagi. Cukup pendek
      // untuk data insiden yang diubah banyak user, cukup panjang untuk
      // menghilangkan fetch ganda saat mount berulang.
      staleTime: 30_000,
      // Error 4xx tidak di-retry: 401 sudah ditangani refresh-token di api.ts
      // (yang sampai sini berarti sesi habis), dan 400/403/404 tidak akan
      // sembuh dengan diulang. Error jaringan/5xx dicoba ulang maksimal 2x.
      retry: (failureCount, error) => {
        if (error instanceof HttpError && error.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount < 2;
      },
    },
    mutations: {
      // Mutasi tidak di-retry otomatis — POST yang diulang buta berisiko
      // membuat laporan ganda (create tidak idempotent).
      retry: false,
    },
  },
});
