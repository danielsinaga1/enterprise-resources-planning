import api, { buildParams, toPaginatedResponse } from "./api";
import type { ApiResponse, PaginatedResponse, PaginationParams } from "../types";
import type {
  PurchaseRequisition,
  PurchaseRequisitionFilters,
  PurchaseRequisitionSummary,
  PrStatusUpdate,
  AddPurchaseRequisitionPayload,
  UpdatePurchaseRequisitionPayload,
  AddPrStatusUpdatePayload,
} from "../types/irms";

// ============================================================
// PURCHASE REQUISITION SERVICE — Monitoring Pengadaan
// ============================================================
const PREFIX = "/api/v1/purchase-requisitions";

export const purchaseRequisitionService = {
  // ----- LIST (paginated + filter) -----
  async list(
    params: PaginationParams = {},
    filters: PurchaseRequisitionFilters = {}
  ): Promise<PaginatedResponse<PurchaseRequisition>> {
    const { data } = await api.get<ApiResponse<PurchaseRequisition[]>>(PREFIX, {
      params: buildParams({
        page: params.page,
        limit: params.limit,
        "sort-by": params.sortBy,
        "sort-order": params.sortOrder,
        ...filters,
      }),
    });
    return toPaginatedResponse<PurchaseRequisition>(data, params.limit ?? 10);
  },

  // ----- SUMMARY -----
  async summary(): Promise<PurchaseRequisitionSummary> {
    const { data } = await api.get<ApiResponse<PurchaseRequisitionSummary>>(
      `${PREFIX}/summary`
    );
    return data.data;
  },

  // ----- DETAIL (termasuk riwayat status) -----
  async detail(id: number): Promise<PurchaseRequisition> {
    const { data } = await api.get<ApiResponse<PurchaseRequisition>>(
      `${PREFIX}/${id}`
    );
    return data.data;
  },

  // ----- SEMUA LINE DALAM SATU DOKUMEN PR -----
  async byPrNo(prNo: string): Promise<PurchaseRequisition[]> {
    const { data } = await api.get<ApiResponse<PurchaseRequisition[]>>(
      `${PREFIX}/by-pr-no/${encodeURIComponent(prNo)}`
    );
    return data.data;
  },

  // ----- CREATE -----
  async create(
    payload: AddPurchaseRequisitionPayload
  ): Promise<PurchaseRequisition> {
    const { data } = await api.post<ApiResponse<PurchaseRequisition>>(
      PREFIX,
      payload
    );
    return data.data;
  },

  // ----- UPDATE -----
  async update(
    id: number,
    payload: UpdatePurchaseRequisitionPayload
  ): Promise<PurchaseRequisition> {
    const { data } = await api.patch<ApiResponse<PurchaseRequisition>>(
      `${PREFIX}/${id}`,
      payload
    );
    return data.data;
  },

  // ----- DELETE (soft) -----
  async delete(id: number): Promise<void> {
    await api.delete(`${PREFIX}/${id}`);
  },

  // ----- RIWAYAT STATUS -----
  async statusHistory(id: number): Promise<PrStatusUpdate[]> {
    const { data } = await api.get<ApiResponse<PrStatusUpdate[]>>(
      `${PREFIX}/${id}/status-updates`
    );
    return data.data;
  },

  async addStatusUpdate(
    id: number,
    payload: AddPrStatusUpdatePayload
  ): Promise<PrStatusUpdate> {
    const { data } = await api.post<ApiResponse<PrStatusUpdate>>(
      `${PREFIX}/${id}/status-updates`,
      payload
    );
    return data.data;
  },

  async deleteStatusUpdate(id: number, statusId: number): Promise<void> {
    await api.delete(`${PREFIX}/${id}/status-updates/${statusId}`);
  },
};
