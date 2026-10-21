import api, { buildParams, toPaginatedResponse } from "./api";
import type { ApiResponse, PaginatedResponse, PaginationParams } from "../types";
import type {
  IncidentReport,
  InvestigationDetail,
  AddIncidentReportPayload,
  UpdateIncidentReportPayload,
  IncidentReportFilters,
  IncidentDashboardStats,
  IncidentChartData,
  IncidentLookups,
  LocationSection,
  NonPlantLocation,
} from "../types/irms";

// ============================================================
// INCIDENT REPORT SERVICE
// Maps from kpi_irms IncidentReport model + controller
// ============================================================
const PREFIX = "/api/v1/incident-reports";

export const incidentReportService = {
  // ----- LIST (server-side pagination) -----
  async list(
    params: PaginationParams = {},
    filters: IncidentReportFilters = {}
  ): Promise<PaginatedResponse<IncidentReport>> {
    const { data } = await api.get<ApiResponse<IncidentReport[]>>(PREFIX, {
      params: buildParams({
        page: params.page,
        limit: params.limit,
        "sort-by": params.sortBy,
        "sort-order": params.sortOrder,
        ...filters,
      }),
    });
    return toPaginatedResponse<IncidentReport>(data, params.limit ?? 10);
  },

  // ----- DETAIL -----
  async detail(id: number): Promise<{
    report: IncidentReport;
    investigations: InvestigationDetail[];
    reviewerName?: string;
    acknowledgerName?: string;
    managerName?: string;
    reporterName?: string;
    reporterParentId?: number;
    reporterParentName?: string;
    reviewerParentId?: number;
    reviewerParentName?: string;
    assignedToName?: string;
    actionByName?: string;
  }> {
    const { data } = await api.get<ApiResponse<{
      report: IncidentReport;
      investigations: InvestigationDetail[];
      reviewerName?: string;
      acknowledgerName?: string;
      managerName?: string;
      reporterName?: string;
      reporterParentId?: number;
      reporterParentName?: string;
      reviewerParentId?: number;
      reviewerParentName?: string;
      assignedToName?: string;
      actionByName?: string;
    }>>(`${PREFIX}/${id}`);
    return data.data;
  },

  // ----- CREATE -----
  async create(payload: AddIncidentReportPayload): Promise<IncidentReport> {
    const { data } = await api.post<ApiResponse<IncidentReport>>(PREFIX, payload);
    return data.data;
  },

  // ----- UPDATE -----
  async update(id: number, payload: UpdateIncidentReportPayload): Promise<IncidentReport> {
    const { data } = await api.patch<ApiResponse<IncidentReport>>(`${PREFIX}/${id}`, payload);
    return data.data;
  },

  // ----- DELETE (soft) -----
  async delete(id: number): Promise<void> {
    await api.delete(`${PREFIX}/${id}`);
  },

  // ----- APPROVAL WORKFLOW -----
  async approveBySuperintendent(id: number, userId: number, notes?: string): Promise<IncidentReport> {
    const { data } = await api.post<ApiResponse<IncidentReport>>(
      `${PREFIX}/${id}/approve/superintendent`,
      { user_id: userId, notes }
    );
    return data.data;
  },

  async rejectBySuperintendent(id: number, userId: number, notes?: string): Promise<IncidentReport> {
    const { data } = await api.post<ApiResponse<IncidentReport>>(
      `${PREFIX}/${id}/reject/superintendent`,
      { user_id: userId, notes }
    );
    return data.data;
  },

  async approveByManager(id: number, userId: number, notes?: string): Promise<IncidentReport> {
    const { data } = await api.post<ApiResponse<IncidentReport>>(
      `${PREFIX}/${id}/approve/manager`,
      { user_id: userId, notes }
    );
    return data.data;
  },

  async rejectByManager(id: number, userId: number, notes?: string): Promise<IncidentReport> {
    const { data } = await api.post<ApiResponse<IncidentReport>>(
      `${PREFIX}/${id}/reject/manager`,
      { user_id: userId, notes }
    );
    return data.data;
  },

  async assignSubordinate(id: number, managerId: number, subordinateId: number, notes?: string): Promise<IncidentReport> {
    const { data } = await api.post<ApiResponse<IncidentReport>>(
      `${PREFIX}/${id}/assign-subordinate`,
      { manager_id: managerId, subordinate_id: subordinateId, notes }
    );
    return data.data;
  },

  async submitFollowUp(id: number, userId: number, payload: {
    perbaikanAwal?: string;
    corrective?: string;
    preventive?: string;
    followUpNotes?: string;
    dateAction?: string;
    directCost?: number;
    copq?: number;
    followUpAttachments?: string;
  }): Promise<IncidentReport> {
    const { data } = await api.post<ApiResponse<IncidentReport>>(
      `${PREFIX}/${id}/follow-up`,
      {
        user_id: userId,
        perbaikan_awal: payload.perbaikanAwal,
        corrective: payload.corrective,
        preventive: payload.preventive,
        follow_up_notes: payload.followUpNotes,
        date_action: payload.dateAction,
        direct_cost: payload.directCost,
        copq: payload.copq,
        follow_up_attachments: payload.followUpAttachments,
      }
    );
    return data.data;
  },

  // ----- DASHBOARD -----
  async getDashboardStats(): Promise<{
    stats: IncidentDashboardStats;
    chartData: IncidentChartData;
  }> {
    const { data } = await api.get<
      ApiResponse<{ stats: IncidentDashboardStats; chartData: IncidentChartData }>
    >(`${PREFIX}/dashboard/stats`);
    return data.data;
  },
};

// ============================================================
// INVESTIGATION DETAIL SERVICE
// ============================================================
const INV_PREFIX = "/api/v1/investigation-details";

export const investigationDetailService = {
  async listByIncident(incidentId: number): Promise<InvestigationDetail[]> {
    const { data } = await api.get<ApiResponse<InvestigationDetail[]>>(INV_PREFIX, {
      params: { incident_report_id: incidentId },
    });
    return data.data;
  },

  async create(
    payload: Partial<InvestigationDetail> & { incidentReportId: number }
  ): Promise<InvestigationDetail> {
    const { data } = await api.post<ApiResponse<InvestigationDetail>>(INV_PREFIX, payload);
    return data.data;
  },

  async update(
    id: number,
    payload: Partial<InvestigationDetail>
  ): Promise<InvestigationDetail> {
    const { data } = await api.patch<ApiResponse<InvestigationDetail>>(
      `${INV_PREFIX}/${id}`,
      payload
    );
    return data.data;
  },

  async delete(id: number): Promise<void> {
    await api.delete(`${INV_PREFIX}/${id}`);
  },
};

// ============================================================
// INCIDENT LOOKUPS SERVICE
// Provides dropdown data for incident report forms
// ============================================================
export const incidentLookupService = {
  async getAll(): Promise<IncidentLookups> {
    const [categories, classifications, rootCauses, areas, regions, origins, designations] =
      await Promise.all([
        api.get<ApiResponse<IncidentLookups["categories"]>>("/api/v1/category-incidents"),
        api.get<ApiResponse<IncidentLookups["classifications"]>>(
          "/api/v1/classification-incidents"
        ),
        api.get<ApiResponse<IncidentLookups["rootCauses"]>>("/api/v1/root-causes"),
        api.get<ApiResponse<IncidentLookups["areas"]>>("/api/v1/area-incidents"),
        api.get<ApiResponse<IncidentLookups["regions"]>>("/api/v1/region-incidents"),
        api.get<ApiResponse<IncidentLookups["originDepartments"]>>(
          "/api/v1/origin-departments"
        ),
        api.get<ApiResponse<IncidentLookups["designationDepartments"]>>(
          "/api/v1/designation-departments"
        ),
      ]);

    return {
      categories: categories.data.data,
      classifications: classifications.data.data,
      rootCauses: rootCauses.data.data,
      areas: areas.data.data,
      regions: regions.data.data,
      originDepartments: origins.data.data,
      designationDepartments: designations.data.data,
    };
  },

  async getYears(): Promise<number[]> {
    const { data } = await api.get<ApiResponse<number[]>>("/api/v1/incident-years");
    return data.data;
  },
};

// ============================================================
// LOCATION SECTION SERVICE
// Provides section data filtered by region
// ============================================================
export const locationSectionService = {
  async getByRegion(regionId: number): Promise<LocationSection[]> {
    const { data } = await api.get<ApiResponse<LocationSection[]>>(
      `/api/v1/locations`, { params: { regionId } }
    );
    return data.data;
  },

  // Non-Plant "sections" — buildings/facilities outside the plant area
  // (irms_nonplant_locations), used when a JSO finding's Area is Non-Plant.
  async getNonPlant(): Promise<NonPlantLocation[]> {
    const { data } = await api.get<ApiResponse<NonPlantLocation[]>>(
      `/api/v1/nonplant-locations`
    );
    return data.data;
  },
};
