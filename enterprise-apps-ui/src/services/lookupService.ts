import api from "./api";
import type {
  ApiResponse,
  LookupItem,
} from "../types";

// ============================================================
// LOOKUP
// ============================================================
export const lookupService = {
  /**
   * Get lookup values by type.
   * GET /api/v1/lookup?type=gender|blood_type|religion|...
   */
  async byType(type: string): Promise<LookupItem[]> {
    const { data } = await api.get<ApiResponse<LookupItem[]>>("/api/v1/lookup", {
      params: { type },
    });
    return data.data;
  },
};
