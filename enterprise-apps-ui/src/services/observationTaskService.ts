import api, { buildParams, toPaginatedResponse } from "./api";
import type { ApiResponse, PaginatedResponse, PaginationParams } from "../types";
import type {
  ObservationTask,
  AddObservationTaskPayload,
  UpdateObservationTaskPayload,
  ObservationTaskFilters,
  ObservationTaskStats,
} from "../types/irms";

// ============================================================
// OBSERVATION TASK SERVICE — Tugas Observasi (JSO/NCR Tasks)
// ============================================================
const PREFIX = "/api/v1/observation-tasks";

export const observationTaskService = {
  // ----- LIST (paginated + status filter) -----
  async list(
    params: PaginationParams = {},
    filters: ObservationTaskFilters = {}
  ): Promise<PaginatedResponse<ObservationTask>> {
    const { data } = await api.get<ApiResponse<ObservationTask[]>>(PREFIX, {
      params: buildParams({
        page: params.page,
        limit: params.limit,
        "sort-by": params.sortBy,
        "sort-order": params.sortOrder,
        status: filters.status,
      }),
    });
    return toPaginatedResponse<ObservationTask>(data, params.limit ?? 10);
  },

  // ----- DETAIL -----
  async detail(id: number): Promise<ObservationTask> {
    const { data } = await api.get<ApiResponse<ObservationTask>>(`${PREFIX}/${id}`);
    return data.data;
  },

  // ----- BY OBSERVATION -----
  async listByObservation(soId: number): Promise<ObservationTask[]> {
    const { data } = await api.get<ApiResponse<ObservationTask[]>>(
      `${PREFIX}/by-observation/${soId}`
    );
    return data.data;
  },

  // ----- MY TASKS -----
  async myTasks(userId: number): Promise<ObservationTask[]> {
    const { data } = await api.get<ApiResponse<ObservationTask[]>>(
      `${PREFIX}/my-tasks/${userId}`
    );
    return data.data;
  },

  // ----- CREATE -----
  async create(payload: AddObservationTaskPayload): Promise<ObservationTask> {
    const { data } = await api.post<ApiResponse<ObservationTask>>(PREFIX, payload);
    return data.data;
  },

  // ----- UPDATE -----
  async update(
    id: number,
    payload: UpdateObservationTaskPayload
  ): Promise<ObservationTask> {
    const { data } = await api.patch<ApiResponse<ObservationTask>>(
      `${PREFIX}/${id}`,
      payload
    );
    return data.data;
  },

  // ----- DELETE (soft) -----
  async delete(id: number): Promise<void> {
    await api.delete(`${PREFIX}/${id}`);
  },

  // ----- COMPLETE -----
  async complete(
    id: number,
    payload: { completionNotes?: string; completedById?: number }
  ): Promise<ObservationTask> {
    const { data } = await api.post<ApiResponse<ObservationTask>>(
      `${PREFIX}/${id}/complete`,
      payload
    );
    return data.data;
  },

  // ----- VERIFY -----
  async verify(
    id: number,
    payload: { verifiedById?: number; verificationNotes?: string }
  ): Promise<ObservationTask> {
    const { data } = await api.post<ApiResponse<ObservationTask>>(
      `${PREFIX}/${id}/verify`,
      payload
    );
    return data.data;
  },

  // ----- STATS -----
  async getStats(): Promise<ObservationTaskStats> {
    const { data } = await api.get<ApiResponse<ObservationTaskStats>>(
      `${PREFIX}/stats`
    );
    return data.data;
  },
};
