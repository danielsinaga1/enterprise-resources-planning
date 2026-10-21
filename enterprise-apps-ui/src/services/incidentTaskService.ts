import api, { buildParams, toPaginatedResponse } from "./api";
import type { ApiResponse, PaginatedResponse, PaginationParams } from "../types";
import type {
  IncidentTask,
  IncidentTaskDetail,
  IncidentTaskSummary,
  CreateIncidentTaskPayload,
  SubmitIncidentTaskPayload,
  IncidentTaskFilters,
} from "../types/irms";

// ============================================================
// INCIDENT TASK SERVICE — Tugas Tindak Lanjut Insiden
// ============================================================
const PREFIX = "/api/v1/incident-tasks";

export const incidentTaskService = {
  // ----- LIST (paginated, filtered) -----
  async list(
    params: PaginationParams = {},
    filters: IncidentTaskFilters = {}
  ): Promise<PaginatedResponse<IncidentTask>> {
    const { data } = await api.get<ApiResponse<IncidentTask[]>>(PREFIX, {
      params: buildParams({
        page: params.page,
        limit: params.limit,
        "sort-by": params.sortBy,
        "sort-order": params.sortOrder,
        ...filters,
      }),
    });
    return toPaginatedResponse<IncidentTask>(data, params.limit ?? 10);
  },

  // ----- DETAIL -----
  async detail(id: number): Promise<IncidentTaskDetail> {
    const { data } = await api.get<ApiResponse<IncidentTaskDetail>>(`${PREFIX}/${id}`);
    return data.data;
  },

  // ----- BY INCIDENT -----
  async listByIncident(incidentReportId: number): Promise<IncidentTask[]> {
    const { data } = await api.get<ApiResponse<IncidentTask[]>>(
      `${PREFIX}/incident/${incidentReportId}`
    );
    return data.data;
  },

  // ----- SUMMARY -----
  async summary(params: { assigner_id?: number; assignee_id?: number }): Promise<IncidentTaskSummary> {
    const { data } = await api.get<ApiResponse<IncidentTaskSummary>>(`${PREFIX}/summary`, {
      params: buildParams(params),
    });
    return data.data;
  },

  // ----- CREATE (Manager assigns) -----
  async create(payload: CreateIncidentTaskPayload, managerId: number): Promise<IncidentTask> {
    const { data } = await api.post<ApiResponse<IncidentTask>>(PREFIX, payload, {
      params: { manager_id: managerId },
    });
    return data.data;
  },

  // ----- START (Subordinate starts) -----
  async start(id: number, userId: number): Promise<IncidentTask> {
    const { data } = await api.post<ApiResponse<IncidentTask>>(`${PREFIX}/${id}/start`, null, {
      params: { user_id: userId },
    });
    return data.data;
  },

  // ----- SUBMIT (Subordinate completes) -----
  async submit(id: number, payload: SubmitIncidentTaskPayload, userId: number): Promise<IncidentTask> {
    const { data } = await api.post<ApiResponse<IncidentTask>>(`${PREFIX}/${id}/submit`, payload, {
      params: { user_id: userId },
    });
    return data.data;
  },
};
