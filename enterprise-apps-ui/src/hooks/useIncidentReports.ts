import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import {
  incidentLookupService,
  incidentReportService,
} from "../services/incidentService";
import type { PaginationParams } from "../types";
import type { AddIncidentReportPayload, IncidentReportFilters } from "../types/irms";

// ============================================================
// Query keys — hierarkis supaya invalidasi bisa setepat yang dibutuhkan:
//   invalidate(incidentKeys.all)     → semua data turunan insiden (list + dashboard)
//   invalidate(incidentKeys.lists()) → hanya varian list (semua kombinasi page/filter)
// Lookups sengaja di luar hierarki "incident-reports": isinya master data
// dropdown yang tidak berubah saat laporan dibuat, jadi tidak ikut basi.
// ============================================================
export const incidentKeys = {
  all: ["incident-reports"] as const,
  lists: () => [...incidentKeys.all, "list"] as const,
  list: (params: PaginationParams, filters: IncidentReportFilters) =>
    [...incidentKeys.lists(), { params, filters }] as const,
  dashboard: () => [...incidentKeys.all, "dashboard"] as const,
  lookups: ["incident-lookups"] as const,
};

// ----- LIST (server-side pagination) -----
export function useIncidentReports(
  params: PaginationParams = {},
  filters: IncidentReportFilters = {}
) {
  return useQuery({
    queryKey: incidentKeys.list(params, filters),
    queryFn: () => incidentReportService.list(params, filters),
    // Saat ganti halaman/filter, data lama tetap tampil sambil halaman baru
    // di-fetch — tabel tidak berkedip kosong tiap pindah page.
    placeholderData: keepPreviousData,
  });
}

// ----- DASHBOARD STATS -----
export function useIncidentDashboardStats() {
  return useQuery({
    queryKey: incidentKeys.dashboard(),
    queryFn: () => incidentReportService.getDashboardStats(),
  });
}

// ----- LOOKUPS (master data untuk dropdown form) -----
export function useIncidentLookups() {
  return useQuery({
    queryKey: incidentKeys.lookups,
    queryFn: () => incidentLookupService.getAll(),
    // Master data hampir tidak pernah berubah — override staleTime global
    // (30 detik) jadi 5 menit supaya buka-tutup form tidak memukul 7 endpoint.
    staleTime: 5 * 60_000,
  });
}

// ----- CREATE -----
export function useCreateIncidentReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: AddIncidentReportPayload) =>
      incidentReportService.create(payload),
    onSuccess: () => {
      // Tandai semua data turunan insiden basi: list (semua halaman/filter)
      // dan dashboard. Query yang sedang tampil di layar langsung refetch;
      // sisanya refetch saat komponennya dipakai lagi.
      queryClient.invalidateQueries({ queryKey: incidentKeys.all });
    },
  });
}
