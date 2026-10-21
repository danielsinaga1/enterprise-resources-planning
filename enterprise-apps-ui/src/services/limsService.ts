import limsApi from "./limsApi";
import type {
  LimsResponse,
  LaboratoryReportDto,
  AnalysisDetailDto,
  AuditLogDto,
  DailyReportDto,
  InventoryItemDto,
  InventoryItemPayload,
  InventoryDashboardData,
  InventoryFilters,
} from "../types/lims";

// ============================================================
// LABORATORY REPORTS
// ============================================================
export const laboratoryReportService = {
  async list(
    page = 0,
    size = 10,
    sortBy = "createdAt",
    sortDir = "desc",
    filters: { status?: string | null; search?: string | null } = {}
  ): Promise<LimsResponse<LaboratoryReportDto[]>> {
    const params: Record<string, string | number> = { page, size, sortBy, sortDir };
    if (filters.status) params.status = filters.status;
    if (filters.search && filters.search.trim()) params.search = filters.search.trim();
    const { data } = await limsApi.get<LimsResponse<LaboratoryReportDto[]>>("/laboratory-reports", { params });
    return data;
  },

  async get(id: number): Promise<LimsResponse<LaboratoryReportDto>> {
    const { data } = await limsApi.get<LimsResponse<LaboratoryReportDto>>(`/laboratory-reports/${id}`);
    return data;
  },

  async create(
    payload: Partial<LaboratoryReportDto>
  ): Promise<LimsResponse<LaboratoryReportDto>> {
    const { data } = await limsApi.post<LimsResponse<LaboratoryReportDto>>("/laboratory-reports", payload);
    return data;
  },

  async update(
    id: number,
    payload: Partial<LaboratoryReportDto>
  ): Promise<LimsResponse<LaboratoryReportDto>> {
    const { data } = await limsApi.put<LimsResponse<LaboratoryReportDto>>(`/laboratory-reports/${id}`, payload);
    return data;
  },

  async delete(id: number): Promise<LimsResponse<null>> {
    const { data } = await limsApi.delete<LimsResponse<null>>(`/laboratory-reports/${id}`);
    return data;
  },

  /** Tahap 2 — Analisis & Penugasan Tim: Reviewer Lab menunjuk Analis & Sampler. */
  async assignTeam(
    id: number,
    analystId: number,
    samplerId: number
  ): Promise<LimsResponse<LaboratoryReportDto>> {
    const { data } = await limsApi.patch<LimsResponse<LaboratoryReportDto>>(
      `/laboratory-reports/${id}/assign-team?analystId=${analystId}&samplerId=${samplerId}`
    );
    return data;
  },

  async review(
    id: number,
    reviewedById: number
  ): Promise<LimsResponse<LaboratoryReportDto>> {
    const { data } = await limsApi.patch<LimsResponse<LaboratoryReportDto>>(
      `/laboratory-reports/${id}/review?reviewedById=${reviewedById}`
    );
    return data;
  },

  async acknowledge(
    id: number,
    acknowledgeById: number
  ): Promise<LimsResponse<LaboratoryReportDto>> {
    const { data } = await limsApi.patch<LimsResponse<LaboratoryReportDto>>(
      `/laboratory-reports/${id}/acknowledge?acknowledgeById=${acknowledgeById}`
    );
    return data;
  },

  /** Penolakan selalu menghasilkan status "Rejected"; alasannya wajib diisi. */
  async reject(
    id: number,
    reason: string
  ): Promise<LimsResponse<LaboratoryReportDto>> {
    const { data } = await limsApi.patch<LimsResponse<LaboratoryReportDto>>(
      `/laboratory-reports/${id}/reject`,
      { reason }
    );
    return data;
  },

  async auditTrail(id: number): Promise<LimsResponse<AuditLogDto[]>> {
    const { data } = await limsApi.get<LimsResponse<AuditLogDto[]>>(
      `/laboratory-reports/${id}/audit`
    );
    return data;
  },

  async listByStatus(
    status: string
  ): Promise<LimsResponse<LaboratoryReportDto[]>> {
    const { data } = await limsApi.get<LimsResponse<LaboratoryReportDto[]>>(`/laboratory-reports/status/${status}`);
    return data;
  },

  async getDashboardStats(): Promise<LimsResponse<{
    stats: {
      totalLabReports: number;
      open: number;
      reviewed: number;
      closed: number;
      rejected: number;
      totalDailyReports: number;
      /** Jumlah hasil (bukan laporan) yang di luar spesifikasi. */
      outOfSpec: number;
    };
    chartData: {
      byDepartment: { label: string; value: number }[];
      byStatus: { label: string; value: number }[];
      byMonth: { label: string; value: number }[];
    };
  }>> {
    const { data } = await limsApi.get<LimsResponse<{
      stats: {
        totalLabReports: number;
        open: number;
        reviewed: number;
        closed: number;
        rejected: number;
        totalDailyReports: number;
        outOfSpec: number;
      };
      chartData: {
        byDepartment: { label: string; value: number }[];
        byStatus: { label: string; value: number }[];
        byMonth: { label: string; value: number }[];
      };
    }>>("/laboratory-reports/dashboard/stats");
    return data;
  },
};

// ============================================================
// ANALYSIS DETAILS
// ============================================================
export const analysisDetailService = {
  async listByReport(
    reportId: number
  ): Promise<LimsResponse<AnalysisDetailDto[]>> {
    const { data } = await limsApi.get<LimsResponse<AnalysisDetailDto[]>>(
      `/laboratory-reports/${reportId}/details`
    );
    return data;
  },

  async create(
    reportId: number,
    payload: Partial<AnalysisDetailDto>
  ): Promise<LimsResponse<AnalysisDetailDto>> {
    const { data } = await limsApi.post<LimsResponse<AnalysisDetailDto>>(
      `/laboratory-reports/${reportId}/details`,
      payload
    );
    return data;
  },

  async update(
    reportId: number,
    id: number,
    payload: Partial<AnalysisDetailDto>
  ): Promise<LimsResponse<AnalysisDetailDto>> {
    const { data } = await limsApi.put<LimsResponse<AnalysisDetailDto>>(
      `/laboratory-reports/${reportId}/details/${id}`,
      payload
    );
    return data;
  },

  async delete(reportId: number, id: number): Promise<LimsResponse<null>> {
    const { data } = await limsApi.delete<LimsResponse<null>>(
      `/laboratory-reports/${reportId}/details/${id}`
    );
    return data;
  },
};

// ============================================================
// DAILY REPORTS
// ============================================================
export const dailyReportService = {
  async list(
    page = 0,
    size = 10,
    sortBy = "dueDate",
    sortDir = "desc"
  ): Promise<LimsResponse<DailyReportDto[]>> {
    const { data } = await limsApi.get<LimsResponse<DailyReportDto[]>>("/daily-reports", {
      params: { page, size, sortBy, sortDir },
    });
    return data;
  },

  async get(id: number): Promise<LimsResponse<DailyReportDto>> {
    const { data } = await limsApi.get<LimsResponse<DailyReportDto>>(`/daily-reports/${id}`);
    return data;
  },

  async create(
    payload: Partial<DailyReportDto>
  ): Promise<LimsResponse<DailyReportDto>> {
    const { data } = await limsApi.post<LimsResponse<DailyReportDto>>("/daily-reports", payload);
    return data;
  },

  async update(
    id: number,
    payload: Partial<DailyReportDto>
  ): Promise<LimsResponse<DailyReportDto>> {
    const { data } = await limsApi.put<LimsResponse<DailyReportDto>>(`/daily-reports/${id}`, payload);
    return data;
  },

  async delete(id: number): Promise<LimsResponse<null>> {
    const { data } = await limsApi.delete<LimsResponse<null>>(`/daily-reports/${id}`);
    return data;
  },

  async calendar(
    startDate: string,
    endDate: string
  ): Promise<LimsResponse<DailyReportDto[]>> {
    const { data } = await limsApi.get<LimsResponse<DailyReportDto[]>>("/daily-reports/calendar", {
      params: { startDate, endDate },
    });
    return data;
  },

  async review(
    id: number,
    reviewedById: number
  ): Promise<LimsResponse<DailyReportDto>> {
    const { data } = await limsApi.put<LimsResponse<DailyReportDto>>(`/daily-reports/${id}/review`, null, {
      params: { reviewedById },
    });
    return data;
  },

  async acknowledge(
    id: number,
    acknowledgedById: number
  ): Promise<LimsResponse<DailyReportDto>> {
    const { data } = await limsApi.put<LimsResponse<DailyReportDto>>(
      `/daily-reports/${id}/acknowledge`,
      null,
      { params: { acknowledgedById } }
    );
    return data;
  },

  async reject(id: number): Promise<LimsResponse<DailyReportDto>> {
    const { data } = await limsApi.put<LimsResponse<DailyReportDto>>(`/daily-reports/${id}/reject`);
    return data;
  },
};

// ============================================================
// LAB CONFIG (Master Data & Dependent Dropdowns)
// ============================================================
export const labConfigService = {
  async getMasterData(): Promise<LimsResponse<Record<string, unknown[]>>> {
    const { data } = await limsApi.get<LimsResponse<Record<string, unknown[]>>>("/config/master-data");
    return data;
  },

  async getDependencies(params: {
    parameterId?: number;
    testTypeId?: number;
    methodId?: number;
    standardId?: number;
  }): Promise<LimsResponse<Record<string, unknown[]>>> {
    const { data } = await limsApi.get<LimsResponse<Record<string, unknown[]>>>("/config/dependencies", { params });
    return data;
  },
};

// ============================================================
// INVENTORY (lab equipment)
// ============================================================
export const inventoryService = {
  async list(
    filters: InventoryFilters = {}
  ): Promise<LimsResponse<InventoryItemDto[]>> {
    const {
      page = 0,
      size = 10,
      sortBy = "name",
      sortDir = "asc",
      status,
      category,
      search,
    } = filters;
    const params: Record<string, string | number> = { page, size, sortBy, sortDir };
    if (status) params.status = status;
    if (category) params.category = category;
    if (search && search.trim()) params.search = search.trim();
    const { data } = await limsApi.get<LimsResponse<InventoryItemDto[]>>("/inventory", { params });
    return data;
  },

  async get(id: number): Promise<LimsResponse<InventoryItemDto>> {
    const { data } = await limsApi.get<LimsResponse<InventoryItemDto>>(`/inventory/${id}`);
    return data;
  },

  async create(payload: InventoryItemPayload): Promise<LimsResponse<InventoryItemDto>> {
    const { data } = await limsApi.post<LimsResponse<InventoryItemDto>>("/inventory", payload);
    return data;
  },

  async update(
    id: number,
    payload: InventoryItemPayload
  ): Promise<LimsResponse<InventoryItemDto>> {
    const { data } = await limsApi.put<LimsResponse<InventoryItemDto>>(`/inventory/${id}`, payload);
    return data;
  },

  async delete(id: number): Promise<LimsResponse<null>> {
    const { data } = await limsApi.delete<LimsResponse<null>>(`/inventory/${id}`);
    return data;
  },

  async categories(): Promise<LimsResponse<string[]>> {
    const { data } = await limsApi.get<LimsResponse<string[]>>("/inventory/categories");
    return data;
  },

  async getDashboardStats(): Promise<LimsResponse<InventoryDashboardData>> {
    const { data } = await limsApi.get<LimsResponse<InventoryDashboardData>>("/inventory/dashboard/stats");
    return data;
  },
};
