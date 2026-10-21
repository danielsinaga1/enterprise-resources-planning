import api, { buildParams, toPaginatedResponse } from "./api";
import type { ApiResponse, PaginatedResponse, PaginationParams } from "../types";
import type {
  SafetyObservation,
  AddSafetyObservationPayload,
  UpdateSafetyObservationPayload,
  SafetyObservationFilters,
  SafetyDashboardStats,
  SafetyChartData,
  FindingsDashboard,
} from "../types/irms";

// ============================================================
// SAFETY OBSERVATION SERVICE (NCR + JSO)
// Maps from kpi_irms SafetyObservation model + controller
// ============================================================
const PREFIX = "/api/v1/safety-observations";

export const safetyObservationService = {
  // ----- LIST (server-side pagination with type filter) -----
  async list(
    params: PaginationParams = {},
    filters: SafetyObservationFilters = {}
  ): Promise<PaginatedResponse<SafetyObservation>> {
    const { data } = await api.get<ApiResponse<SafetyObservation[]>>(PREFIX, {
      params: buildParams({
        page: params.page,
        limit: params.limit,
        "sort-by": params.sortBy,
        "sort-order": params.sortOrder,
        type: filters.type,
        status: filters.status,
        year: filters.year,
        search: filters.search,
      }),
    });
    return toPaginatedResponse<SafetyObservation>(data, params.limit ?? 10);
  },

  // ----- DETAIL -----
  async detail(id: number): Promise<SafetyObservation> {
    const { data } = await api.get<ApiResponse<SafetyObservation>>(`${PREFIX}/${id}`);
    return data.data;
  },

  // ----- CREATE -----
  async create(payload: AddSafetyObservationPayload): Promise<SafetyObservation> {
    const { data } = await api.post<ApiResponse<SafetyObservation>>(PREFIX, payload);
    return data.data;
  },

  // ----- UPDATE -----
  async update(
    id: number,
    payload: UpdateSafetyObservationPayload
  ): Promise<SafetyObservation> {
    const { data } = await api.patch<ApiResponse<SafetyObservation>>(
      `${PREFIX}/${id}`,
      payload
    );
    return data.data;
  },

  // ----- DELETE (soft) -----
  async delete(id: number): Promise<void> {
    await api.delete(`${PREFIX}/${id}`);
  },

  // ----- 5-STAGE WORKFLOW -----

  // Stage 2: Tinjauan QSHE — QSHE reviews and sets designation department
  async review(id: number, reviewedById: number, designationDepartmentId?: number): Promise<SafetyObservation> {
    const { data } = await api.post<ApiResponse<SafetyObservation>>(
      `${PREFIX}/${id}/review`,
      { reviewed_by_id: reviewedById, designation_department_id: designationDepartmentId }
    );
    return data.data;
  },

  // Stage 3: Analisis — Assign PIC (tercatat sebagai Tugas Observasi)
  async assignPic(id: number, assignedToId: number, assignedById?: number): Promise<SafetyObservation> {
    const { data } = await api.post<ApiResponse<SafetyObservation>>(
      `${PREFIX}/${id}/assign-pic`,
      { assigned_to_id: assignedToId, assigned_by_id: assignedById }
    );
    return data.data;
  },

  // Stage 3: Delegasi PIC
  async delegatePic(id: number, assignedToId: number, delegationNote: string): Promise<SafetyObservation> {
    const { data } = await api.post<ApiResponse<SafetyObservation>>(
      `${PREFIX}/${id}/delegate`,
      { assigned_to_id: assignedToId, delegation_note: delegationNote }
    );
    return data.data;
  },

  // Stage 4: Tindakan — Submit corrective action (+ nomor WO dari WR temuan)
  async submitAction(id: number, actionById: number, corrective: string, woNumber?: string): Promise<SafetyObservation> {
    const { data } = await api.post<ApiResponse<SafetyObservation>>(
      `${PREFIX}/${id}/submit-action`,
      { action_by_id: actionById, corrective, wo_number: woNumber || undefined }
    );
    return data.data;
  },

  // Stage 5: Penyelesaian Akhir — Administrator Safety verifies & closes
  async complete(id: number, closedById?: number): Promise<SafetyObservation> {
    const { data } = await api.post<ApiResponse<SafetyObservation>>(
      `${PREFIX}/${id}/complete`,
      closedById ? { reviewed_by_id: closedById } : {}
    );
    return data.data;
  },

  // Reject at any stage
  async reject(id: number, reason: string): Promise<SafetyObservation> {
    const { data } = await api.post<ApiResponse<SafetyObservation>>(
      `${PREFIX}/${id}/reject`,
      { reason }
    );
    return data.data;
  },

  // Step 6b: Return to Creator — QSHE returns incomplete data for revision
  async returnToCreator(id: number, comment: string): Promise<SafetyObservation> {
    const { data } = await api.post<ApiResponse<SafetyObservation>>(
      `${PREFIX}/${id}/return-to-creator`,
      { comment }
    );
    return data.data;
  },

  // Step 6c: Resubmit — Creator resubmits after revision
  async resubmit(id: number): Promise<SafetyObservation> {
    const { data } = await api.post<ApiResponse<SafetyObservation>>(
      `${PREFIX}/${id}/resubmit`
    );
    return data.data;
  },

  // ----- DASHBOARD TEMUAN (Open/Close, kategori, tren, tindak lanjut) -----
  async getFindingsDashboard(year?: number): Promise<FindingsDashboard> {
    const { data } = await api.get<ApiResponse<FindingsDashboard>>(
      `${PREFIX}/dashboard/findings`,
      { params: buildParams({ year }) }
    );
    return data.data;
  },

  // ----- DASHBOARD -----
  async getDashboardStats(): Promise<{
    stats: SafetyDashboardStats;
    chartData: SafetyChartData;
  }> {
    const { data } = await api.get<
      ApiResponse<{ stats: SafetyDashboardStats; chartData: SafetyChartData }>
    >(`${PREFIX}/dashboard/stats`);
    return data.data;
  },
};
