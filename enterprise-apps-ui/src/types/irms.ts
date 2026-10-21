// ============================================================
// IRMS MODULE TYPES — Incident Reports, Safety Observations, Dashboard
// Mapped from kpi_irms Laravel models to modern TypeScript
// ============================================================

// ============================================================
// LOOKUP / REFERENCE TYPES
// ============================================================
export interface CategoryIncident {
  id: number;
  name: string;
  description?: string;
}

export interface ClassificationIncident {
  id: number;
  name: string;
  level: number;
  color?: string;
}

export interface RootCause {
  id: number;
  name: string;
}

export interface AreaIncident {
  id: number;
  name: string;
}

export interface RegionIncident {
  id: number;
  name: string;
}

export interface OriginDepartment {
  id: number;
  name: string;
  code?: string;
}

export interface DesignationDepartment {
  id: number;
  name: string;
  code?: string;
}

export interface JsoLeaderMaster {
  id: number;
  name: string;
  npp?: string;
  department?: string;
  position?: string;
  isActive?: boolean;
}

export interface JsoMemberMaster {
  id: number;
  name: string;
  npp?: string;
  department?: string;
  position?: string;
  isActive?: boolean;
}

export interface ChemicalRelease {
  id: number;
  name: string;
}

export interface ResultType {
  id: number;
  name: string;
}

export interface IrmsLocation {
  id: number;
  name: string;
}

export interface JsoLocation {
  id: number;
  name: string;
}

export interface LocationSection {
  id: number;
  name: string;
  regionNcrId?: number;
}

export interface RegionNcr {
  id: number;
  name: string;
}

export interface IrmsEntity {
  id: number;
  name: string;
}

export interface NonPlantLocation {
  id: number;
  name: string;
}

export interface JobTitle {
  id: number;
  name: string;
}

export interface Division {
  id: number;
  name: string;
  code?: string;
}

export interface ChartOfAccount {
  id: number;
  accountCode: string;
  accountName: string;
  accountType: string;
  parentId?: number;
  description?: string;
  isActive: boolean;
}

// ============================================================
// JSO REPORT / FINDINGS
// ============================================================
// NOTE: keys are camelCase because JsoReportEntity has no @JsonProperty
// snake_case overrides (unlike the SO request DTOs) — Jackson serializes and
// deserializes this entity with plain camelCase field names.
export interface JsoReport {
  id?: number;
  soId?: number;
  findings?: string;
  rootCauseJso?: string;
  potentialHazard?: string;
  recommendation?: string;
  wrNumber?: string;
  /** Nomor WO hasil resolusi dari WR (Avantis) — sumber prefill nomor WO tindak lanjut. */
  wrWoNumber?: string;
  description?: string;
  categoryId?: number;
  divisionId?: number;
  /** PIC per temuan (ditunjuk Superintendent divisi temuan). */
  picId?: number;
  /** Tindak lanjut per temuan oleh PIC. */
  corrective?: string;
  /** JSON array nama file foto milik temuan ini, mis. ["uuid1.jpg"]. */
  photos?: string;
  actionById?: number;
  areaId?: number;
  locationId?: number;
  nonPlantLocationId?: number;
  regionId?: number;
  // Not persisted by the backend (JsoReportEntity has no section column) —
  // kept for the region→section cascading dropdown UI state only.
  sectionId?: number;
}

// ============================================================
// INCIDENT REPORT
// ============================================================
export interface IncidentReport {
  id: number;
  reportNumber: string;
  title: string;
  description?: string;
  incidentDate?: string;
  incidentTime?: string;
  locationDetail?: string;

  // Relationships
  category?: CategoryIncident;
  classification?: ClassificationIncident;
  rootCause?: RootCause;
  area?: AreaIncident;
  region?: RegionIncident;
  originDepartment?: OriginDepartment;
  designationDepartment?: DesignationDepartment;
  result?: ResultType;

  // Reporter info
  reporterName?: string;

  // User IDs
  reporterId?: number;
  reviewerId?: number;
  acknowledgerId?: number;
  actionById?: number;
  designatedManagerId?: number;
  assignedToId?: number;

  // Status
  status: string;
  revisionNotes?: string;
  managerNotes?: string;
  followUpNotes?: string;
  investigationRequired?: boolean;
  investigationDone?: boolean;

  // Follow-up content (filled by pelaksana)
  perbaikanAwal?: string;
  corrective?: string;
  preventive?: string;
  reason?: string;
  directCost?: number;
  copq?: number;
  doneAt?: string;
  followUpAttachments?: string;

  // Workflow timestamps
  dateAction?: string;
  assignedAt?: string;
  reviewedAt?: string;
  acknowledgedAt?: string;
  fullyApprovedAt?: string;
  inProgressAt?: string;

  // Risk
  severity?: number;
  probability?: number;
  riskLevel?: string;

  // Media
  photoPath?: string;
  documentPath?: string;
  photos?: string;

  // Timestamps
  createdAt?: string;
  updatedAt?: string;
}

export interface InvestigationDetail {
  id: number;
  incidentReportId: number;
  description?: string;
  finding?: string;
  recommendation?: string;
  correctiveAction?: string;
  preventiveAction?: string;
  picId?: number;
  actionById?: number;
  dueDate?: string;
  status: 'open' | 'in_progress' | 'closed';
  createdAt?: string;
  updatedAt?: string;
}

export interface AddIncidentReportPayload {
  title: string;
  description?: string;
  incident_date?: string;
  incident_time?: string;
  location_detail?: string;
  category_id?: number;
  classification_id?: number;
  root_cause_id?: number;
  area_id?: number;
  region_id?: number;
  origin_department_id?: number;
  designation_department_id?: number;
  reporter_id?: number;
  investigation_required?: boolean;
  severity?: number;
  probability?: number;
  risk_level?: string;
  photos?: string;
}

export interface UpdateIncidentReportPayload {
  title?: string;
  description?: string;
  incident_date?: string;
  incident_time?: string;
  location_detail?: string;
  category_id?: number;
  classification_id?: number;
  root_cause_id?: number;
  area_id?: number;
  region_id?: number;
  origin_department_id?: number;
  designation_department_id?: number;
  designated_manager_id?: number;
  action_by_id?: number;
  assigned_to_id?: number;
  reviewer_id?: number;
  acknowledger_id?: number;
  status?: string;
  superintendent_approval?: string;
  manager_approval?: string;
  investigation_required?: boolean;
  investigation_done?: boolean;
  severity?: number;
  probability?: number;
  risk_level?: string;
  photos?: string;
}

export interface IncidentReportFilters {
  status?: string;
  category_id?: number;
  classification_id?: number;
  origin_department_id?: number;
  designated_manager_id?: number;
  dept_designated_id?: number;
  year?: number;
  search?: string;
}

// ============================================================
// SAFETY OBSERVATION (NCR + JSO)
// ============================================================
export interface SafetyObservation {
  id: number;
  reportNumber: string;
  title: string;
  description?: string;
  observationDate?: string;
  isJso: boolean;

  // Location
  locationId?: number;
  jsoLocationId?: number;
  jsoLocation?: JsoLocation;
  locationDetail?: string;
  area?: AreaIncident;
  regionNcrId?: number;
  entityId?: number;
  nonplantLocationId?: number;
  // Departments
  originDepartment?: OriginDepartment;
  designationDepartment?: DesignationDepartment;

  // Root cause & result
  rootCauseId?: number;
  resultId?: number;

  // Users
  reporterId?: number;
  actionById?: number;
  assignedToId?: number;
  acknowledgedById?: number;
  reviewedById?: number;
  leaderId?: number;
  member1Id?: number;
  member2Id?: number;

  // Status
  status: 'open' | 'approved_partially' | 'reviewed' | 'in_progress' | 'action_taken' | 'closed' | 'overdue' | 'rejected' | 'returned';

  // Additional fields
  descriptionJso?: string;
  observationDateJso?: string;
  observationDateDisplay?: string;
  corrective?: string;
  entityName?: string;
  woNumber?: string;
  reason?: string;
  reason2?: string;
  delegationNote?: string;
  dateAction?: string;
  assignedAt?: string;
  acknowledgedAt?: string;
  doneAt?: string;
  actionTakenAt?: string;

  // Timestamps
  createdAt?: string;
  updatedAt?: string;

  // Photos
  photos?: string;
}

export interface AddSafetyObservationPayload {
  title: string;
  description?: string;
  observation_date?: string;
  is_jso?: boolean;
  location_id?: number;
  jso_location_id?: number;
  location_detail?: string;
  area_id?: number;
  region_ncr_id?: number;
  entity_id?: number;
  nonplant_location_id?: number;
  origin_department_id?: number;
  designation_department_id?: number;
  reporter_id?: number;
  leader_id?: number;
  member_1_id?: number;
  member_2_id?: number;
  wo_number?: string;
  photos?: string;
}

export interface UpdateSafetyObservationPayload {
  title?: string;
  description?: string;
  observation_date?: string;
  is_jso?: boolean;
  location_id?: number;
  jso_location_id?: number;
  location_detail?: string;
  area_id?: number;
  region_ncr_id?: number;
  entity_id?: number;
  nonplant_location_id?: number;
  origin_department_id?: number;
  designation_department_id?: number;
  root_cause_id?: number;
  result_id?: number;
  action_by_id?: number;
  assigned_to_id?: number;
  acknowledged_by_id?: number;
  reviewed_by_id?: number;
  leader_id?: number;
  member_1_id?: number;
  member_2_id?: number;
  status?: string;
  corrective?: string;
  wo_number?: string;
  reason?: string;
  delegation_note?: string;
  photos?: string;
  report_number?: string;
}

export interface SafetyObservationFilters {
  type?: 'jso' | 'ncr' | 'all';
  status?: string;
  year?: number;
  /** Dicocokkan di server ke nomor laporan & judul — bukan filter layar. */
  search?: string;
}

// ============================================================
// DASHBOARD / KPI STATS
// ============================================================
export interface IncidentDashboardStats {
  total: number;
  new: number;
  partiallyApproved: number;
  pendingManager: number;
  fullyApproved: number;
  inProgress: number;
  done: number;
  open: number;
  closed: number;
}

export interface SafetyDashboardStats {
  total: number;
  open: number;
  reviewed: number;
  inProgress: number;
  actionTaken: number;
  closed: number;
  rejected: number;
  returned: number;
  overdue: number;
  jsoCount: number;
  ncrCount: number;
}

export interface ChartDataPoint {
  label: string;
  value: number;
}

export interface IncidentChartData {
  byCategory: ChartDataPoint[];
  byOriginDepartment: ChartDataPoint[];
  byDesignationDepartment: ChartDataPoint[];
  byMonth: ChartDataPoint[];
  byStatus: ChartDataPoint[];
  riskMatrix: Array<{ classification: string; category: string; count: number }>;
}

/* ------------------------------------------------------------
   DASHBOARD TEMUAN JSO
   Basisnya temuan (irms_jso_reports), bukan laporan observasi:
   kategori K3/PS/LH melekat di temuan, dan satu observasi bisa
   berisi belasan temuan.
   ------------------------------------------------------------ */
export interface FindingsDashboardStats {
  total: number;
  open: number;
  closed: number;
  /** Belum ada rumus baku dari QSHE — null berarti UI menampilkan "—". */
  indexV: number | null;
}

export interface FindingsMonthlySeries {
  year: number;
  /** 12 nilai, Januari–Desember. */
  points: number[];
}

export interface FindingsFollowUpPoint {
  month: number;
  total: number;
  closed: number;
  open: number;
  /** 0–100, satu desimal. */
  percentage: number;
}

export interface FindingsDashboard {
  year: number;
  availableYears: number[];
  stats: FindingsDashboardStats;
  byCategory: ChartDataPoint[];
  byMonthPerYear: FindingsMonthlySeries[];
  followUpByMonth: FindingsFollowUpPoint[];
}

export interface SafetyChartData {
  byDepartmentAndStatus: Array<{ department: string; status: string; count: number }>;
  byMonth: ChartDataPoint[];
}

export interface IncidentDashboard {
  stats: IncidentDashboardStats;
  chartData: IncidentChartData;
}

export interface SafetyDashboard {
  stats: SafetyDashboardStats;
  chartData: SafetyChartData;
}

// ============================================================
// LOOKUPS (dropdown data)
// ============================================================
export interface IncidentLookups {
  categories: CategoryIncident[];
  classifications: ClassificationIncident[];
  rootCauses: RootCause[];
  areas: AreaIncident[];
  regions: RegionIncident[];
  originDepartments: OriginDepartment[];
  designationDepartments: DesignationDepartment[];
}

// ============================================================
// PAGINATION RESPONSE (for server-side pagination)
// ============================================================
export interface IrmsPagination {
  current_page: number;
  per_page: number;
  total: number;
  total_pages: number;
  has_previous: boolean;
  has_next: boolean;
}

export interface IncidentReportListResponse {
  reports: IncidentReport[];
  pagination: IrmsPagination;
  lookups: IncidentLookups;
  stats: IncidentDashboardStats;
  chartData: IncidentChartData;
}

export interface SafetyObservationListResponse {
  observations: SafetyObservation[];
  pagination: IrmsPagination;
  stats: SafetyDashboardStats;
  chartData: SafetyChartData;
}

// ============================================================
// OBSERVATION TASK TYPES — Tugas Observasi for JSO/NCR
// ============================================================

export interface ObservationTask {
  id: number;
  taskNumber: string;
  safetyObservation?: {
    id: number;
    reportNumber: string;
    title: string;
    isJso: boolean;
    status: string;
  };
  title: string;
  description?: string;
  findings?: string;
  correctiveAction?: string;
  preventiveAction?: string;
  assignedToId?: number;
  assignedById?: number;
  priority: 'low' | 'medium' | 'high' | 'critical';
  dueDate?: string;
  status: 'open' | 'in_progress' | 'completed' | 'overdue' | 'cancelled';
  completionNotes?: string;
  completedAt?: string;
  completedById?: number;
  verifiedById?: number;
  verifiedAt?: string;
  verificationNotes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AddObservationTaskPayload {
  safetyObservationId: number;
  title: string;
  description?: string;
  findings?: string;
  correctiveAction?: string;
  preventiveAction?: string;
  assignedToId?: number;
  assignedById?: number;
  priority?: string;
  dueDate?: string;
}

export interface UpdateObservationTaskPayload {
  title?: string;
  description?: string;
  findings?: string;
  correctiveAction?: string;
  preventiveAction?: string;
  assignedToId?: number;
  priority?: string;
  dueDate?: string;
  completionNotes?: string;
}

export interface ObservationTaskFilters {
  status?: string;
}

export interface ObservationTaskStats {
  total: number;
  open: number;
  inProgress: number;
  completed: number;
  overdue: number;
}

// ============================================================
// TASK ASSIGNMENT — Calendar Penugasan
// ============================================================
export interface TaskAssignment {
  id: number;
  title: string;
  description?: string;
  assigneeId?: number;
  assignee?: { id: number; name: string; npp?: string };
  assignerId?: number;
  startDate: string;
  endDate: string;
  status: string;
  priority?: string;
  dailyReportId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AddTaskAssignmentPayload {
  title: string;
  description?: string;
  assignee_id?: number;
  assigner_id?: number;
  start_date: string;
  end_date: string;
  status?: string;
  priority?: string;
  daily_report_id?: number;
}

export interface UpdateTaskAssignmentPayload {
  title?: string;
  description?: string;
  assignee_id?: number;
  start_date?: string;
  end_date?: string;
  status?: string;
  priority?: string;
  daily_report_id?: number;
}

export interface TaskAssignmentFilters {
  status?: string;
  assigneeId?: number;
}

// ============================================================
// INCIDENT TASK — Tugas Tindak Lanjut Insiden
// ============================================================
export interface IncidentTask {
  id: number;
  taskNumber: string;
  incidentReportId: number;
  assignerId: number;
  assigneeId: number;
  instruction?: string;
  deadline?: string;
  priority: string;
  status: 'Pending' | 'In Progress' | 'Completed';

  // Data yang diisi subordinate
  kronologi?: string;
  dampak?: string;
  solusiSementara?: string;
  correctiveAction?: string;
  preventiveAction?: string;
  evidence?: string;
  catatan?: string;

  // Timestamps
  startedAt?: string;
  completedAt?: string;
  createdAt?: string;
  updatedAt?: string;

  // Joined
  assigner?: { id: number; name: string };
  assignee?: { id: number; name: string };
  incidentReport?: {
    id: number;
    reportNumber: string;
    title: string;
    status: string;
  };
}

export interface IncidentTaskDetail {
  task: IncidentTask;
  assignerName?: string;
  assigneeName?: string;
  incidentReport?: {
    id: number;
    reportNumber: string;
    title: string;
    status: string;
    description?: string;
    category?: CategoryIncident;
    classification?: ClassificationIncident;
    originDepartment?: OriginDepartment;
    designationDepartment?: DesignationDepartment;
    incidentDate?: string;
    locationDetail?: string;
  };
}

export interface CreateIncidentTaskPayload {
  incident_report_id: number;
  assignee_id: number;
  instruction?: string;
  deadline?: string;
  priority?: string;
}

export interface SubmitIncidentTaskPayload {
  kronologi: string;
  dampak: string;
  solusi_sementara?: string;
  corrective_action?: string;
  preventive_action?: string;
  evidence?: string;
  catatan?: string;
}

export interface IncidentTaskSummary {
  total: number;
  pending: number;
  inProgress: number;
  completed: number;
  overdue?: number;
}

export interface IncidentTaskFilters {
  status?: string;
  assigner_id?: number;
  assignee_id?: number;
}

// ============================================================
// PURCHASE REQUISITION — Monitoring Pengadaan
// ============================================================
// Satu record = satu BARIS PR (prNo + prLine), bukan satu dokumen PR.
// Satu PR bisa punya belasan line yang jalan di status berbeda-beda.

export interface PrStatusUpdate {
  id: number;
  purchaseRequisitionId: number;
  updateDate: string;
  status: string;
  notes?: string;
  createdBy?: string;
  createdAt?: string;
}

export interface PurchaseRequisition {
  id: number;
  prNo: string;
  prLine: number;
  prLineDescription?: string;
  prCreatedDate?: string;
  prApprovalDate?: string;
  prToLogisticDate?: string;
  buyerPr?: string;
  currentStatus?: string;
  currentStatusDate?: string;
  isRenewal?: boolean;
  source?: string;

  /** Umur PR sejak diserahkan ke Logistic. Dihitung backend, bukan kolom tabel. */
  days?: number | null;

  statusHistory?: PrStatusUpdate[] | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface AddPurchaseRequisitionPayload {
  pr_no: string;
  pr_line: number;
  pr_line_description?: string;
  pr_created_date?: string;
  pr_approval_date?: string;
  pr_to_logistic_date?: string;
  buyer_pr?: string;
  is_renewal?: boolean;
  status?: string;
  status_date?: string;
}

export interface UpdatePurchaseRequisitionPayload {
  pr_no?: string;
  pr_line?: number;
  pr_line_description?: string;
  pr_created_date?: string;
  pr_approval_date?: string;
  pr_to_logistic_date?: string;
  buyer_pr?: string;
  is_renewal?: boolean;
}

export interface AddPrStatusUpdatePayload {
  status: string;
  update_date?: string;
  notes?: string;
}

export interface PurchaseRequisitionFilters {
  "pr-no"?: string;
  buyer?: string;
  status?: string;
  "is-renewal"?: boolean;
  keyword?: string;
}

export interface PurchaseRequisitionSummary {
  totalPr: number;
  totalLine: number;
  totalPrRenewal: number;
  totalPrNonRenewal: number;
  totalLineRenewal: number;
  totalLineNonRenewal: number;
  perBuyer: Array<{ buyerPr: string; totalLine: number }>;
}
