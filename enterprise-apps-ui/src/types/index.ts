// ============================================================
// COMMON TYPES — Adapted for the platform backend
// ============================================================

/**
 * Matches backend Response.java wrapper:
 * { status, httpStatus, errorCode, errorMessage, page, countData, totalPage, data, message }
 */
export interface ApiResponse<T> {
  status: "SUCCESS" | "FAILED";
  httpStatus: string;
  errorCode?: string | null;
  errorMessage?: string | null;
  data: T;
  dataProtected?: string | null;
  message?: string | null;
  page?: number | null;
  countData?: number | null;
  totalPage?: number | null;
}

export interface PaginatedResponse<T> {
  data: T[];
  current_page: number;
  last_page: number;
  per_page: number;
  total: number;
  from: number | null;
  to: number | null;
}

export interface ApiError {
  status: "FAILED";
  httpStatus: string;
  errorCode?: string;
  errorMessage?: string;
  message?: string;
}

// ============================================================
// PAGINATION & FILTERS
// ============================================================
export interface PaginationParams {
  page?: number;
  limit?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export interface PaginatedListRequest<F = Record<string, unknown>> {
  username?: string;
  page?: number;
  sortBy?: string;
  sortOrder?: string;
  limit?: number;
  filters?: F;
}

// ============================================================
// AUTH & USER
// ============================================================
export interface LoginPayload {
  npk: string;
  password: string;
}

export interface LoginResponse {
  success: boolean;
  message: string;
  token: string;
  refreshToken: string;
  id: number;
  userId: number;
  npp: string;
  npk?: string | null;
  name: string;
  email: string;
  mobilePhone?: string | null;
  lastLogin?: string | null;
  departmentId?: number | null;
  roles: string[];
  permissions: Record<string, string[]>;
}

export interface User {
  id: number;
  npp?: string | null;
  npk?: string | null;
  name: string;
  email: string;
  mobilePhone?: string | null;
  rfId?: string | null;
  departmentId?: number | null;
  departmentName?: string | null;
  divisionId?: number | null;
  divisionName?: string | null;
  jobTitle?: string | null;
  parentId?: number | null;
  parentName?: string | null;
  subordinates?: { id: number; name: string; npk?: string | null }[];
  status: number;
  lastLogin?: string | null;
  createdAt?: string;
  updatedAt?: string;
  roles?: string[];
  permissions?: Record<string, string[]>;
}

export interface ProfileResponse {
  success: boolean;
  message: string;
  userId: number;
  npp?: string | null;
  npk?: string | null;
  name: string;
  email: string;
  mobilePhone?: string | null;
  lastLogin?: string | null;
}

export interface UpdateProfilePayload {
  name?: string;
  mobilePhone?: string;
  currentPassword?: string;
  newPassword?: string;
}

// ============================================================
// ASSET
// ============================================================
export interface Asset {
  id: number;
  name: string;
  specificName?: string | null;
  assetNumber?: string | null;
  location?: string | null;
  specificLocation?: string | null;
  buyValue?: number | null;
  currentValue?: number | null;
  currencyCode?: string | null;
  category?: string | null;
  status?: string | null;
  condition?: string | null;
  lastMaintenanceDate?: string | null;
  nextMaintenanceDate?: string | null;
}

export interface AddAssetPayload {
  name: string;
  specificName?: string;
  assetNumber?: string;
  location?: string;
  specificLocation?: string;
  buyValue?: number;
  currencyCode?: string;
  categoryId: number;
  statusId: number;
  conditionId: number;
}

export interface UpdateAssetPayload extends Partial<AddAssetPayload> {
  currentValue?: number;
  lastMaintenanceDate?: string;
  nextMaintenanceDate?: string;
}

// ============================================================
// RBAC
// ============================================================
export interface Role {
  id: number;
  code: string;
  name: string;
  description?: string;
  departmentId?: number;
  isSystemRole?: boolean;
  isActive?: boolean;
  priority?: number;
  createdAt?: string;
}

export interface Permission {
  id: number;
  code: string;
  name: string;
  module: string;
  subModule?: string;
  action: string;
  description?: string;
  isActive?: boolean;
}

export interface UserRoleAssignment {
  userId: number;
  roleId: number;
  roleName?: string;
  isPrimary?: boolean;
}

export interface RolePermission {
  id?: number;
  roleId: number;
  permissionId: number;
  canCreate?: boolean;
  canRead?: boolean;
  canUpdate?: boolean;
  canDelete?: boolean;
  canApprove?: boolean;
  canExport?: boolean;
  permission?: Permission;
}

export interface ModuleAccess {
  module: string;
  actions: string[];
}

export interface SubMenuItem {
  label: string;
  to: string;
}

export interface MenuItem {
  label: string;
  icon?: string;
  to?: string;
  items?: SubMenuItem[];
}

export interface MenuSection {
  label: string;
  items: MenuItem[];
}

// ============================================================
// LOOKUP
// ============================================================
export interface LookupItem {
  id: number;
  type: string;
  code?: string;
  name: string;
  description?: string;
}
