// ============================================================
// SERVICES BARREL EXPORT
// ============================================================

// --- Core ---
export { default as api } from "./api";
export { toPaginatedResponse, unwrap, buildParams, toFormData } from "./api";
export { authService } from "./authService";
export { workRequestService } from "./workRequestService";

// --- Inventory ---


// --- RBAC ---
export {
  roleService,
  permissionService,
  userRoleService,
  rolePermissionService,
  userPermissionService,
  rbacAdminService,
} from "./rbacService";



// --- Lookup / Reference ---
export {
  lookupService,
} from "./lookupService";

// --- IRMS (Incident Reporting & Management System) ---
export {
  incidentReportService,
  investigationDetailService,
  incidentLookupService,
  locationSectionService,
} from "./incidentService";
export { safetyObservationService } from "./safetyObservationService";
export { fileUploadService } from "./fileUploadService";
export { observationTaskService } from "./observationTaskService";
export { taskAssignmentService } from "./taskAssignmentService";
export { incidentTaskService } from "./incidentTaskService";

// --- Notifications ---
export { notificationService } from "./notificationService";
export type { Notification } from "./notificationService";

// --- FCM Push Notifications ---
export { fcmTokenService } from "./fcmTokenService";
export { requestFcmToken, onForegroundMessage } from "./firebase";

// --- Master Data ---
export {
  masterAreaService,
  masterCategoryService,
  masterClassificationService,
  masterRootCauseService,
  masterRegionService,
  masterOriginDeptService,
  masterDesignationDeptService,
  masterJsoLeaderService,
  masterJsoMemberService,
  masterJobTitleService,
  masterJsoLocationService,
  masterDivisionService,
  masterLimsParameterService,
  masterLimsTestTypeService,
  masterLimsMethodService,
  masterLimsStandardService,
  masterLimsUnitService,
  masterLimsDepartmentService,
  masterLimsResultService,
  masterLimsCompoundService,
  masterCoaService,
} from "./masterDataService";

// --- JSO Reports ---
export { jsoReportService } from "./jsoReportService";

// --- LIMS (Laboratory Information Management System) ---
export {
  laboratoryReportService,
  analysisDetailService,
  dailyReportService,
  labConfigService,
  inventoryService,
} from "./limsService";
export { default as limsApi } from "./limsApi";

// --- RAG Chatbot (document-grounded AI, via gateway /api/rag/**) ---
export { ragService } from "./ragService";

// --- Purchase Requisition (monitoring pengadaan) ---
export { purchaseRequisitionService } from "./purchaseRequisitionService";
