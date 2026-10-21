/**
 * Mengambil pesan galat yang dikirim backend.
 *
 * Backend membungkus respons dalam `Response` (status/message/errorMessage).
 * Tanpa helper ini, aturan bisnis yang ditolak server — "Alasan penolakan wajib
 * diisi", "Standar bertipe MAX wajib punya batas atas" — hilang di balik toast
 * generik, dan pengguna tidak tahu apa yang harus diperbaiki.
 *
 * Mengembalikan null bila respons tidak memuat pesan yang bisa dibaca, supaya
 * pemanggil memakai teks cadangannya sendiri.
 */
export function serverMessage(error: unknown): string | null {
  if (!error || typeof error !== "object") return null;

  const data = (error as { response?: { data?: unknown } }).response?.data;
  if (data && typeof data === "object") {
    const body = data as Record<string, unknown>;
    for (const key of ["message", "errorMessage", "error"] as const) {
      const value = body[key];
      if (typeof value === "string" && value.trim()) return value.trim();
    }
    // Galat validasi Bean Validation: { fieldErrors: { reason: "..." } }
    const fieldErrors = body.fieldErrors;
    if (fieldErrors && typeof fieldErrors === "object") {
      const first = Object.values(fieldErrors as Record<string, unknown>)
        .find((v) => typeof v === "string" && v.trim());
      if (typeof first === "string") return first.trim();
    }
  }

  const message = (error as { message?: unknown }).message;
  return typeof message === "string" && message.trim() ? message.trim() : null;
}
