import api, { buildParams, toPaginatedResponse } from "./api";
import type { ApiResponse, PaginatedResponse, PaginationParams } from "../types";
import type {
  TaskAssignment,
  AddTaskAssignmentPayload,
  UpdateTaskAssignmentPayload,
  TaskAssignmentFilters,
} from "../types/irms";

// ============================================================
// TASK ASSIGNMENT SERVICE — Calendar Penugasan
// ============================================================
const PREFIX = "/api/v1/task-assignments";

export const taskAssignmentService = {
  // ----- LIST (paginated) -----
  async list(
    params: PaginationParams = {},
    filters: TaskAssignmentFilters = {}
  ): Promise<PaginatedResponse<TaskAssignment>> {
    const { data } = await api.get<ApiResponse<TaskAssignment[]>>(PREFIX, {
      params: buildParams({
        page: params.page,
        limit: params.limit,
        "sort-by": params.sortBy,
        "sort-order": params.sortOrder,
        ...filters,
      }),
    });
    return toPaginatedResponse<TaskAssignment>(data, params.limit ?? 10);
  },

  // ----- CALENDAR (date range) -----
  async calendar(
    startDate: string,
    endDate: string,
    assigneeId?: number
  ): Promise<TaskAssignment[]> {
    const { data } = await api.get<ApiResponse<TaskAssignment[]>>(
      `${PREFIX}/calendar`,
      {
        params: { startDate, endDate, assigneeId },
      }
    );
    return data.data;
  },

  // ----- DETAIL -----
  async detail(id: number): Promise<TaskAssignment> {
    const { data } = await api.get<ApiResponse<TaskAssignment>>(
      `${PREFIX}/${id}`
    );
    return data.data;
  },

  // ----- CREATE -----
  async create(payload: AddTaskAssignmentPayload): Promise<TaskAssignment> {
    const { data } = await api.post<ApiResponse<TaskAssignment>>(
      PREFIX,
      payload
    );
    return data.data;
  },

  // ----- UPDATE -----
  async update(
    id: number,
    payload: UpdateTaskAssignmentPayload
  ): Promise<TaskAssignment> {
    const { data } = await api.patch<ApiResponse<TaskAssignment>>(
      `${PREFIX}/${id}`,
      payload
    );
    return data.data;
  },

  // ----- DELETE (soft) -----
  async delete(id: number): Promise<void> {
    await api.delete(`${PREFIX}/${id}`);
  },
};
