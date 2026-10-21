import api from "./api";
import type { ApiResponse } from "../types";
import type { JsoReport } from "../types/irms";

const PREFIX = "/api/v1/safety-observations";

export const jsoReportService = {
  async list(soId: number): Promise<JsoReport[]> {
    const { data } = await api.get<ApiResponse<JsoReport[]>>(`${PREFIX}/${soId}/jso-reports`);
    return data.data;
  },

  async create(soId: number, payload: JsoReport): Promise<JsoReport> {
    const { data } = await api.post<ApiResponse<JsoReport>>(`${PREFIX}/${soId}/jso-reports`, payload);
    return data.data;
  },

  async update(soId: number, id: number, payload: JsoReport): Promise<JsoReport> {
    const { data } = await api.put<ApiResponse<JsoReport>>(`${PREFIX}/${soId}/jso-reports/${id}`, payload);
    return data.data;
  },

  async remove(soId: number, id: number): Promise<void> {
    await api.delete(`${PREFIX}/${soId}/jso-reports/${id}`);
  },

  /** Tugaskan PIC untuk satu temuan; bila semua temuan tertugaskan, observasi naik ke in_progress. */
  async assignPic(soId: number, id: number, assignedToId: number, assignedById?: number): Promise<void> {
    await api.post(`${PREFIX}/${soId}/jso-reports/${id}/assign`,
      { assigned_to_id: assignedToId, assigned_by_id: assignedById });
  },

  /** Submit tindak lanjut satu temuan; bila semua selesai, observasi naik ke action_taken. */
  async submitAction(soId: number, id: number, actionById: number, corrective: string, woNumber?: string): Promise<void> {
    await api.post(`${PREFIX}/${soId}/jso-reports/${id}/submit-action`,
      { action_by_id: actionById, corrective, wo_number: woNumber || undefined });
  },
};
