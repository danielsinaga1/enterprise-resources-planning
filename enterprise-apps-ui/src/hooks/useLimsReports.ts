import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  analysisDetailService,
  dailyReportService,
  labConfigService,
  laboratoryReportService,
} from "../services/limsService";
import type { AnalysisDetailDto, DailyReportDto, LaboratoryReportDto } from "../types/lims";

// ============================================================
// LIMS — Laporan Laboratorium (alur Open → Assigned → Reviewed → Close)
//
// Catatan: LIMS adalah service terpisah (db_lims, prefix /api/lims/v1) dan
// paginasinya berbasis-0 (`page: 0` = halaman pertama), berbeda dari IRMS yang
// berbasis-1. Key di bawah menyimpan angka apa adanya sesuai yang dikirim ke
// server, jadi jangan campur konvensinya saat memanggil hook ini.
// ============================================================
export interface LabReportListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  status?: string | null;
  search?: string | null;
}

export const limsKeys = {
  all: ["lims"] as const,

  reports: () => [...limsKeys.all, "laboratory-reports"] as const,
  reportList: (params: LabReportListParams) => [...limsKeys.reports(), "list", params] as const,
  reportDetail: (id: number | undefined) => [...limsKeys.reports(), "detail", id] as const,
  reportDashboard: () => [...limsKeys.reports(), "dashboard"] as const,

  // Detail analisis adalah anak dari satu laporan — disimpan di bawah key
  // laporannya supaya invalidasi satu laporan ikut menyegarkan barisnya.
  analysisDetails: (reportId: number | undefined) =>
    [...limsKeys.reports(), "detail", reportId, "analysis"] as const,

  dailyReports: () => [...limsKeys.all, "daily-reports"] as const,
  dailyReportList: (params: { page: number; size: number; sortBy: string; sortDir: string }) =>
    [...limsKeys.dailyReports(), "list", params] as const,
  dailyReportCalendar: (startDate: string, endDate: string) =>
    [...limsKeys.dailyReports(), "calendar", { startDate, endDate }] as const,

  masterData: () => [...limsKeys.all, "config", "master-data"] as const,
};

// ============================================================
// LAPORAN LABORATORIUM
// ============================================================
export function useLabReports(params: LabReportListParams = {}) {
  const { page = 0, size = 10, sortBy = "createdAt", sortDir = "desc", status, search } = params;
  return useQuery({
    queryKey: limsKeys.reportList({ page, size, sortBy, sortDir, status, search }),
    queryFn: () =>
      laboratoryReportService.list(page, size, sortBy, sortDir, { status, search }),
    placeholderData: keepPreviousData,
  });
}

export function useLabReport(id: number | undefined) {
  return useQuery({
    queryKey: limsKeys.reportDetail(id),
    queryFn: () => laboratoryReportService.get(id!),
    enabled: id !== undefined && id !== null,
    // `select` membuka amplop LimsResponse sehingga komponen menerima DTO
    // langsung, bukan `res.data.data`. Transformasi ini terjadi setelah cache —
    // yang tersimpan tetap respons utuh, dan `select` tidak memicu fetch ulang.
    select: (res) => res.data,
  });
}

export function useLabDashboardStats() {
  return useQuery({
    queryKey: limsKeys.reportDashboard(),
    queryFn: () => laboratoryReportService.getDashboardStats(),
    select: (res) => res.data,
  });
}

// ----- Detail analisis (query bertingkat: butuh reportId) -----
export function useAnalysisDetails(reportId: number | undefined) {
  return useQuery({
    queryKey: limsKeys.analysisDetails(reportId),
    queryFn: () => analysisDetailService.listByReport(reportId!),
    enabled: reportId !== undefined && reportId !== null,
    select: (res) => res.data,
  });
}

// ============================================================
// MUTASI LAPORAN LABORATORIUM
// ============================================================
export function useCreateLabReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: Partial<LaboratoryReportDto>) =>
      laboratoryReportService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: limsKeys.reports() });
    },
  });
}

export function useUpdateLabReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Partial<LaboratoryReportDto> }) =>
      laboratoryReportService.update(id, payload),
    onSuccess: (_res, { id }) => {
      // Argumen kedua onSuccess adalah variabel yang dikirim ke mutate() —
      // dipakai untuk tahu id mana yang harus disegarkan.
      queryClient.invalidateQueries({ queryKey: limsKeys.reportDetail(id) });
      queryClient.invalidateQueries({ queryKey: limsKeys.reports() });
    },
  });
}

export function useDeleteLabReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => laboratoryReportService.delete(id),
    onSuccess: (_res, id) => {
      // Entri detail dibuang, bukan di-invalidate: barangnya sudah tidak ada,
      // refetch hanya akan menghasilkan 404.
      queryClient.removeQueries({ queryKey: limsKeys.reportDetail(id) });
      queryClient.invalidateQueries({ queryKey: limsKeys.reports() });
    },
  });
}

// ----- Transisi alur kerja 4 tahap -----
function useLabWorkflowMutation<TVars extends { id: number }>(
  mutationFn: (vars: TVars) => Promise<unknown>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (_res, { id }) => {
      queryClient.invalidateQueries({ queryKey: limsKeys.reportDetail(id) });
      queryClient.invalidateQueries({ queryKey: limsKeys.reports() });
    },
  });
}

// Tahap 2 — Reviewer Lab menunjuk Analis & Sampler
export function useAssignLabTeam() {
  return useLabWorkflowMutation(
    ({ id, analystId, samplerId }: { id: number; analystId: number; samplerId: number }) =>
      laboratoryReportService.assignTeam(id, analystId, samplerId)
  );
}

// Tahap 3 — Review
export function useReviewLabReport() {
  return useLabWorkflowMutation(
    ({ id, reviewedById }: { id: number; reviewedById: number }) =>
      laboratoryReportService.review(id, reviewedById)
  );
}

// Tahap 4 — Pengesahan (Close)
export function useAcknowledgeLabReport() {
  return useLabWorkflowMutation(
    ({ id, acknowledgeById }: { id: number; acknowledgeById: number }) =>
      laboratoryReportService.acknowledge(id, acknowledgeById)
  );
}

// ----- Detail analisis: mutasi selalu menyegarkan laporan induknya -----
export function useCreateAnalysisDetail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reportId, payload }: { reportId: number; payload: Partial<AnalysisDetailDto> }) =>
      analysisDetailService.create(reportId, payload),
    onSuccess: (_res, { reportId }) => {
      queryClient.invalidateQueries({ queryKey: limsKeys.analysisDetails(reportId) });
    },
  });
}

export function useDeleteAnalysisDetail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reportId, id }: { reportId: number; id: number }) =>
      analysisDetailService.delete(reportId, id),
    onSuccess: (_res, { reportId }) => {
      queryClient.invalidateQueries({ queryKey: limsKeys.analysisDetails(reportId) });
    },
  });
}

// ============================================================
// LAPORAN HARIAN
// ============================================================
export function useDailyReports(
  params: { page?: number; size?: number; sortBy?: string; sortDir?: "asc" | "desc" } = {}
) {
  const { page = 0, size = 10, sortBy = "dueDate", sortDir = "desc" } = params;
  return useQuery({
    queryKey: limsKeys.dailyReportList({ page, size, sortBy, sortDir }),
    queryFn: () => dailyReportService.list(page, size, sortBy, sortDir),
    placeholderData: keepPreviousData,
  });
}

// Kalender memakai rentang tanggal sebagai bagian key: pindah bulan =
// entri cache baru, dan bulan yang sudah dibuka tampil instan saat dibuka lagi.
export function useDailyReportCalendar(startDate: string, endDate: string) {
  return useQuery({
    queryKey: limsKeys.dailyReportCalendar(startDate, endDate),
    queryFn: () => dailyReportService.calendar(startDate, endDate),
    enabled: Boolean(startDate && endDate),
    select: (res) => res.data,
  });
}

export function useCreateDailyReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: Partial<DailyReportDto>) => dailyReportService.create(payload),
    onSuccess: () => {
      // Sengaja luas: list dan kalender sama-sama harus ikut berubah.
      queryClient.invalidateQueries({ queryKey: limsKeys.dailyReports() });
    },
  });
}

// ============================================================
// MASTER DATA (dropdown konfigurasi lab)
// ============================================================
export function useLabMasterData() {
  return useQuery({
    queryKey: limsKeys.masterData(),
    queryFn: () => labConfigService.getMasterData(),
    // Master data lab praktis statis — 30 menit segar, dan jangan refetch
    // hanya karena user kembali ke tab browser.
    staleTime: 30 * 60_000,
    refetchOnWindowFocus: false,
    select: (res) => res.data,
  });
}
