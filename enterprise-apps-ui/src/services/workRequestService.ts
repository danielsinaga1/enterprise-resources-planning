import api from "./api";
import type { ApiResponse } from "../types";

export interface WorkRequest {
  number: string;
  title: string;
  entityNumber?: string;
  entityName?: string;
  workOrder?: string;
  workOrderTitle?: string;
  closed: boolean;
}

// ============================================================
// WORK REQUEST SERVICE
// Read-only search against Avantis (kpi_wr_irms) for the WR
// autocomplete on incident/JSO forms. See WorkRequestController.
// ============================================================
export const workRequestService = {
  async search(query: string): Promise<WorkRequest[]> {
    if (!query || query.trim().length < 2) return [];
    const { data } = await api.get<ApiResponse<WorkRequest[]>>(
      "/api/v1/work-requests/search",
      { params: { q: query.trim() } }
    );
    return data.data ?? [];
  },

  /**
   * Ambil satu WR berdasarkan nomor persis. Dipakai untuk mengisi kartu info WR
   * pada nomor yang diketik manual atau yang sudah tersimpan saat form edit dibuka.
   *
   * Bukan search() lalu disaring di klien: search dibatasi 50 baris teratas urut
   * tanggal, jadi WR lama yang nomornya kebetulan juga muncul di 50 judul yang
   * lebih baru tidak akan pernah ketemu. Nomor tak dikenal / Avantis mati -> null.
   */
  async getByNumber(number: string): Promise<WorkRequest | null> {
    const trimmed = (number ?? "").trim();
    if (trimmed.length < 2) return null;
    const { data } = await api.get<ApiResponse<WorkRequest | null>>(
      `/api/v1/work-requests/${encodeURIComponent(trimmed)}`
    );
    return data.data ?? null;
  },
};
