import api from "./api";
import type { ApiResponse } from "../types";

export interface AuditLogEntry {
  timestamp: string;
  user: string;
  module: string;
  action: string;
  refNo: string;
  details: string;
  ipAddress: string;
}

const PREFIX = "/api/v1/audit-logs";

export const auditLogService = {
  async getAll(params: {
    module?: string;
    user?: string;
    startDate?: string;
    endDate?: string;
  } = {}): Promise<AuditLogEntry[]> {
    const { data } = await api.get<ApiResponse<AuditLogEntry[]>>(PREFIX, { params });
    return data.data;
  },
};
