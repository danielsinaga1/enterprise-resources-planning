import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { safetyObservationService } from "../services/safetyObservationService";
import type { PaginationParams } from "../types";
import type {
  AddSafetyObservationPayload,
  SafetyObservation,
  SafetyObservationFilters,
  UpdateSafetyObservationPayload,
} from "../types/irms";

// ============================================================
// SAFETY OBSERVATION (NCR + JSO) — alur 5 tahap
// Beda dengan modul insiden: di sini ada query detail dan banyak mutasi
// workflow, jadi key-nya punya cabang "detail" agar satu laporan bisa
// di-invalidate tanpa menyeret seluruh list.
// ============================================================
export const safetyObservationKeys = {
  all: ["safety-observations"] as const,
  lists: () => [...safetyObservationKeys.all, "list"] as const,
  list: (params: PaginationParams, filters: SafetyObservationFilters) =>
    [...safetyObservationKeys.lists(), { params, filters }] as const,
  details: () => [...safetyObservationKeys.all, "detail"] as const,
  detail: (id: number | undefined) => [...safetyObservationKeys.details(), id] as const,
  dashboard: () => [...safetyObservationKeys.all, "dashboard"] as const,
};

// ----- LIST -----
export function useSafetyObservations(
  params: PaginationParams = {},
  filters: SafetyObservationFilters = {}
) {
  return useQuery({
    queryKey: safetyObservationKeys.list(params, filters),
    queryFn: () => safetyObservationService.list(params, filters),
    placeholderData: keepPreviousData,
  });
}

// ----- DETAIL -----
export function useSafetyObservation(id: number | undefined) {
  return useQuery({
    queryKey: safetyObservationKeys.detail(id),
    // queryFn hanya jalan saat `enabled` true, jadi `id!` di sini aman.
    queryFn: () => safetyObservationService.detail(id!),
    // Dependent query: jangan menembak API saat id belum ada (mis. route param
    // belum ter-parse, atau dialog detail belum dibuka). Tanpa ini React Query
    // akan memanggil /safety-observations/undefined.
    enabled: id !== undefined && id !== null,
  });
}

// ----- DASHBOARD -----
export function useSafetyDashboardStats() {
  return useQuery({
    queryKey: safetyObservationKeys.dashboard(),
    queryFn: () => safetyObservationService.getDashboardStats(),
  });
}

// ============================================================
// MUTASI
// ============================================================

export function useCreateSafetyObservation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: AddSafetyObservationPayload) =>
      safetyObservationService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: safetyObservationKeys.all });
    },
  });
}

export function useUpdateSafetyObservation() {
  const queryClient = useQueryClient();
  return useMutation({
    // Mutasi hanya menerima SATU argumen — parameter ganda dibungkus objek.
    mutationFn: ({ id, payload }: { id: number; payload: UpdateSafetyObservationPayload }) =>
      safetyObservationService.update(id, payload),
    onSuccess: (updated) => {
      // Server sudah mengembalikan versi terbaru: tulis langsung ke cache detail
      // supaya halaman detail update seketika tanpa GET ulang.
      queryClient.setQueryData(safetyObservationKeys.detail(updated.id), updated);
      // List tetap di-invalidate: urutan/filter/kolom turunan dihitung server.
      queryClient.invalidateQueries({ queryKey: safetyObservationKeys.lists() });
    },
  });
}

// ----- MUTASI WORKFLOW (tahap 2–5) -----
// Semua transisi status berbagi efek cache yang sama, jadi dibuat satu pabrik
// kecil daripada menyalin blok onSuccess yang identik lima kali.
function useWorkflowMutation<TVars>(
  mutationFn: (vars: TVars) => Promise<SafetyObservation>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (updated) => {
      queryClient.setQueryData(safetyObservationKeys.detail(updated.id), updated);
      // Status berubah → kartu KPI dashboard dan semua varian list ikut berubah.
      queryClient.invalidateQueries({ queryKey: safetyObservationKeys.lists() });
      queryClient.invalidateQueries({ queryKey: safetyObservationKeys.dashboard() });
    },
  });
}

// Tahap 2 — Tinjauan QSHE
export function useReviewSafetyObservation() {
  return useWorkflowMutation(
    ({ id, reviewedById, designationDepartmentId }: {
      id: number;
      reviewedById: number;
      designationDepartmentId?: number;
    }) => safetyObservationService.review(id, reviewedById, designationDepartmentId)
  );
}

// Tahap 3 — Penunjukan PIC
export function useAssignPic() {
  return useWorkflowMutation(
    ({ id, assignedToId, assignedById }: {
      id: number;
      assignedToId: number;
      assignedById?: number;
    }) => safetyObservationService.assignPic(id, assignedToId, assignedById)
  );
}

// Tahap 4 — Tindakan perbaikan (+ nomor WO)
export function useSubmitAction() {
  return useWorkflowMutation(
    ({ id, actionById, corrective, woNumber }: {
      id: number;
      actionById: number;
      corrective: string;
      woNumber?: string;
    }) => safetyObservationService.submitAction(id, actionById, corrective, woNumber)
  );
}

// Tahap 5 — Penyelesaian akhir
export function useCompleteSafetyObservation() {
  return useWorkflowMutation(
    ({ id, closedById }: { id: number; closedById?: number }) =>
      safetyObservationService.complete(id, closedById)
  );
}

// Kembalikan ke pembuat untuk revisi
export function useReturnToCreator() {
  return useWorkflowMutation(
    ({ id, comment }: { id: number; comment: string }) =>
      safetyObservationService.returnToCreator(id, comment)
  );
}
