// ============================================================
// LIMS API RESPONSE (matches Spring Boot Response wrapper)
// ============================================================
export interface LimsResponse<T> {
  status: string;
  httpStatus: string;
  data: T;
  page?: number;
  countData?: number;
  totalPage?: number;
  message: string;
}

// ============================================================
// LABORATORY REPORT
// ============================================================
export type LabReportStatus = "Open" | "Assigned" | "Reviewed" | "Close" | "Rejected";

/** PASS = masuk spesifikasi, FAIL = OOS, NOT_EVALUATED = belum/tak bisa disimpulkan. */
export type SpecStatus = "PASS" | "FAIL" | "NOT_EVALUATED";

/** Satu baris jejak audit LIMS. */
export interface AuditLogDto {
  id: number;
  entityType: string;
  entityId: number;
  action: "CREATE" | "UPDATE" | "DELETE" | "STATUS_CHANGE";
  fieldName: string | null;
  oldValue: string | null;
  newValue: string | null;
  reason: string | null;
  actorId: number | null;
  actorName: string | null;
  createdAt: string;
}

export interface LaboratoryReportDto {
  id: number;
  noReport: string;
  dateSampling: string;
  location: string;
  status: LabReportStatus;
  notes: string | null;
  dateAnalysis: string | null;
  remark: string | null;
  requestedBy: string | null;
  samplerId: number | null;
  analystId: number | null;
  reviewedById: number | null;
  acknowledgeById: number | null;
  departmentId: number | null;
  reporterId: number | null;
  reviewedAt: string | null;
  acknowledgeAt: string | null;
  rejectionReason: string | null;
  rejectedById: number | null;
  rejectedByName: string | null;
  rejectedAt: string | null;
  /** Jumlah hasil di luar spesifikasi pada laporan ini. */
  outOfSpecCount: number | null;
  departmentName: string | null;
  samplerName: string | null;
  analystName: string | null;
  reviewerName: string | null;
  acknowledgerName: string | null;
  reporterName: string | null;
  reporterParentId: number | null;
  reporterParentName: string | null;
  reviewerParentId: number | null;
  reviewerParentName: string | null;
  createdAt: string;
  updatedAt: string;
  analysisDetails: AnalysisDetailDto[];
}

export interface LaboratoryReportPayload {
  /** Dicatat otomatis oleh backend saat create (tanggal laporan dibuat). */
  dateSampling?: string;
  location: string;
  notes?: string;
  dateAnalysis?: string;
  remark?: string;
  requestedBy?: string;
  samplerId?: number;
  analystId?: number;
  departmentId?: number;
  reporterId?: number;
}

// ============================================================
// ANALYSIS DETAIL
// ============================================================
export interface AnalysisDetailDto {
  id: number;
  description: string | null;
  status: string | null;
  /** Hasil gabungan versi lama (data historis, read-only). */
  result: string | null;
  /** Hasil/pengamatan Sampler Lab. */
  resultSampler: string | null;
  /** Hasil pengujian Analis Lab. */
  resultAnalysis: string | null;
  /** Hasil analisis sebagai angka; dihitung backend bila hanya teks yang dikirim. */
  valueNumeric: number | null;
  /** Penanda batas deteksi: null, "<", atau ">". */
  valueOperator: string | null;
  /** Kesimpulan terhadap batas spesifikasi — selalu keluaran backend. */
  specStatus: SpecStatus;
  specEvaluatedAt: string | null;
  /** NCR yang dibuka otomatis karena hasil ini OOS (null bila belum/tidak ada). */
  ncrId: number | null;
  ncrNumber: string | null;
  ncrOpenedAt: string | null;
  laboratoryReportId: number;
  parameterId: number | null;
  parameterName?: string;
  standardId: number | null;
  standardMeasure?: string;
  standardLimitType?: LimitType | null;
  standardLowerLimit?: number | null;
  standardUpperLimit?: number | null;
  standardTargetValue?: number | null;
  standardDecimalPlaces?: number | null;
  testTypeId: number | null;
  testTypeName?: string;
  unitId: number | null;
  unitName?: string;
  methodId: number | null;
  methodName?: string;
}

export interface AnalysisDetailPayload {
  description?: string;
  status?: string;
  result?: string;
  resultSampler?: string;
  resultAnalysis?: string;
  valueNumeric?: number | null;
  valueOperator?: string | null;
  parameterId?: number;
  testTypeId?: number;
  methodId?: number;
  standardId?: number;
  unitId?: number;
  /** Wajib saat menimpa hasil yang sudah terisi; tersimpan di jejak audit. */
  changeReason?: string;
}

// ============================================================
// DAILY REPORT
// ============================================================
export interface DailyReportDto {
  id: number;
  name: string;
  description: string | null;
  dueDate: string;
  location: string | null;
  status: string;
  reviewedById: number | null;
  reviewedByName?: string;
  reviewedAt: string | null;
  acknowledgedById: number | null;
  acknowledgedByName?: string;
  acknowledgedAt: string | null;
  assignedToId: number | null;
  unitId: number | null;
  unitName?: string;
  parameterId: number | null;
  parameterName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DailyReportPayload {
  name: string;
  description?: string;
  dueDate: string;
  location?: string;
  assignedToId?: number;
  unitId?: number;
  parameterId?: number;
}

// ============================================================
// MASTER DATA (Lab Config)
// ============================================================
export interface LabParameter {
  id: number;
  name: string;
}

export interface LabTestType {
  id: number;
  name: string;
  parameterId: number;
  parameterName?: string;
}

export interface LabMethod {
  id: number;
  name: string;
}

/** Cara standar dibandingkan dengan hasil uji. */
export type LimitType = "QUALITATIVE" | "RANGE" | "MAX" | "MIN";

export interface LabStandard {
  id: number;
  measure: string;
  methodId: number;
  methodName?: string;
  limitType?: LimitType;
  lowerLimit?: number | null;
  upperLimit?: number | null;
  targetValue?: number | null;
  decimalPlaces?: number | null;
}

export interface LabUnit {
  id: number;
  name: string;
}

export interface LabDepartment {
  id: number;
  name: string;
}

export interface LabResult {
  id: number;
  name: string;
}

export interface LabCompound {
  id: number;
  name: string;
  formula: string;
  casNumber?: string;
  category?: string;
  description?: string;
  isActive?: boolean;
}

// ============================================================
// INVENTORY (lab equipment)
// ============================================================
export type InventoryStatus = "Available" | "Borrowed" | "Service";
export type InventoryCondition = "Baik" | "Rusak Ringan" | "Rusak Berat";

export interface InventoryItemDto {
  id: number;
  itemCode: string;
  name: string;
  category: string | null;
  specification: string | null;
  location: string | null;
  condition: string | null;
  quantity: number;
  minStock: number;
  unitPrice: number;
  status: string;
  lastCalibrationDate: string | null;
  nextCalibrationDate: string | null;
  lastServiceDate: string | null;
  nextServiceDate: string | null;
  borrowedBy: string | null;
  borrowedAt: string | null;
  notes: string | null;
  isActive: boolean | null;
  totalValue: number;
  lowStock: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export type InventoryItemPayload = Partial<Omit<InventoryItemDto, "id" | "totalValue" | "lowStock" | "createdAt" | "updatedAt">> & {
  name: string;
};

export interface InventoryStats {
  totalItems: number;
  totalUnits: number;
  totalValue: number;
  lowStockCount: number;
  calibrationCount: number;
  borrowedCount: number;
  serviceCount: number;
}

export interface InventoryDashboardData {
  stats: InventoryStats;
  chartData: {
    byCategory: { label: string; value: number }[];
    byStatus: { label: string; value: number }[];
  };
}

export interface InventoryFilters {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  status?: string | null;
  category?: string | null;
  search?: string | null;
}

// ============================================================
// FILTERS
// ============================================================
export interface LabReportFilters {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  status?: LabReportStatus;
}

export interface DailyReportFilters {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
}
