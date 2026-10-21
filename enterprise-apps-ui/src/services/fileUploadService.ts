import api from "./api";
import type { ApiResponse } from "../types";

const PREFIX = "/api/v1/uploads";

export const fileUploadService = {
  /**
   * Upload multiple photo files. Returns array of stored filenames.
   */
  async uploadPhotos(files: File[]): Promise<string[]> {
    const formData = new FormData();
    files.forEach((file) => formData.append("files", file));

    const { data } = await api.post<ApiResponse<string[]>>(PREFIX, formData, {
      headers: { "Content-Type": undefined },
    });
    return data.data;
  },

  /**
   * Delete a previously uploaded photo by filename.
   */
  async deletePhoto(filename: string): Promise<void> {
    await api.delete(`${PREFIX}/${filename}`);
  },

  /**
   * Get the full URL for a photo filename.
   * Uses the API endpoint which is more reliable than static resource serving.
   */
  getPhotoUrl(filename: string): string {
    const baseUrl = import.meta.env.VITE_API_URL || "";
    return `${baseUrl}/api/v1/uploads/${encodeURIComponent(filename)}`;
  },
};
