import api, { buildParams, toPaginatedResponse } from "./api";
import type { ApiResponse, PaginatedResponse } from "../types";
import type {
  SmartDocsActivity,
  SmartDocsDashboard,
  SmartDocsReports,
  SmartDocumentDetail,
  SmartDocumentFilters,
  SmartDocumentSummary,
  SaveSmartDocumentPayload,
} from "../types/smartdocs";

// ============================================================
// SMARTDOCS SERVICE
// Backend: id.enterpriseapps.smartdocs.controller.* (db_smartdocs)
// ============================================================
// Paginasi modul ini 0-based dengan parameter page/size — mengikuti
// controller SmartDocs/LIMS, BUKAN page/limit 1-based milik API v1 IRMS.
const PREFIX = "/api/smartdocs/v1";

export interface SmartDocsPageParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
}

export const smartDocsService = {
  // ----- DAFTAR DOKUMEN -----
  async list(
    params: SmartDocsPageParams = {},
    filters: SmartDocumentFilters = {}
  ): Promise<PaginatedResponse<SmartDocumentSummary>> {
    const size = params.size ?? 10;
    const { data } = await api.get<ApiResponse<SmartDocumentSummary[]>>(`${PREFIX}/documents`, {
      params: buildParams({
        page: params.page ?? 0,
        size,
        sortBy: params.sortBy,
        sortDir: params.sortDir,
        search: filters.search,
        division: filters.division,
        category: filters.category,
      }),
    });
    return toPaginatedResponse<SmartDocumentSummary>(data, size);
  },

  // ----- DETAIL -----
  async detail(id: number): Promise<SmartDocumentDetail> {
    const { data } = await api.get<ApiResponse<SmartDocumentDetail>>(`${PREFIX}/documents/${id}`);
    return data.data;
  },

  // ----- SIMPAN / UBAH / HAPUS -----
  async create(payload: SaveSmartDocumentPayload): Promise<SmartDocumentDetail> {
    const { data } = await api.post<ApiResponse<SmartDocumentDetail>>(`${PREFIX}/documents`, payload);
    return data.data;
  },

  async update(id: number, payload: SaveSmartDocumentPayload): Promise<SmartDocumentDetail> {
    const { data } = await api.put<ApiResponse<SmartDocumentDetail>>(`${PREFIX}/documents/${id}`, payload);
    return data.data;
  },

  async remove(id: number): Promise<void> {
    await api.delete(`${PREFIX}/documents/${id}`);
  },

  // ----- DASHBOARD & LAPORAN -----
  async dashboard(): Promise<SmartDocsDashboard> {
    const { data } = await api.get<ApiResponse<SmartDocsDashboard>>(`${PREFIX}/dashboard`);
    return data.data;
  },

  async reports(): Promise<SmartDocsReports> {
    const { data } = await api.get<ApiResponse<SmartDocsReports>>(`${PREFIX}/reports`);
    return data.data;
  },

  // ----- JEJAK AKTIVITAS -----
  async activity(params: SmartDocsPageParams = {}): Promise<PaginatedResponse<SmartDocsActivity>> {
    const size = params.size ?? 20;
    const { data } = await api.get<ApiResponse<SmartDocsActivity[]>>(`${PREFIX}/activity`, {
      params: buildParams({ page: params.page ?? 0, size }),
    });
    return toPaginatedResponse<SmartDocsActivity>(data, size);
  },
};

export default smartDocsService;
