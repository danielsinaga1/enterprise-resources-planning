import api from "./api";
import type {
  ApiResponse,
  Role,
  Permission,
  UserRoleAssignment,
  RolePermission,
  ModuleAccess,
  User,
  MenuSection,
} from "../types";

const PREFIX = "/api/rbac";

// ============================================================
// ROLES
// ============================================================
export const roleService = {
  async list(): Promise<Role[]> {
    const { data } = await api.get<ApiResponse<Role[]>>(`${PREFIX}/roles`);
    return data.data;
  },

  async detail(id: number): Promise<Role> {
    const { data } = await api.get<ApiResponse<Role>>(`${PREFIX}/roles/${id}`);
    return data.data;
  },

  async byDepartment(departmentId: number): Promise<Role[]> {
    const { data } = await api.get<ApiResponse<Role[]>>(
      `${PREFIX}/roles/department/${departmentId}`
    );
    return data.data;
  },

  async create(payload: {
    code: string;
    name: string;
    description?: string;
    departmentId?: number;
  }): Promise<Role> {
    const { data } = await api.post<ApiResponse<Role>>(`${PREFIX}/roles`, payload);
    return data.data;
  },

  async update(
    id: number,
    payload: { code?: string; name?: string; description?: string; departmentId?: number }
  ): Promise<Role> {
    const { data } = await api.put<ApiResponse<Role>>(`${PREFIX}/roles/${id}`, payload);
    return data.data;
  },

  async delete(id: number): Promise<void> {
    await api.delete(`${PREFIX}/roles/${id}`);
  },
};

// ============================================================
// PERMISSIONS
// ============================================================
export const permissionService = {
  async list(): Promise<Permission[]> {
    const { data } = await api.get<ApiResponse<Permission[]>>(`${PREFIX}/permissions`);
    return data.data;
  },

  async byModule(module: string): Promise<Permission[]> {
    const { data } = await api.get<ApiResponse<Permission[]>>(
      `${PREFIX}/permissions/module/${module}`
    );
    return data.data;
  },

  async modules(): Promise<string[]> {
    const { data } = await api.get<ApiResponse<string[]>>(`${PREFIX}/permissions/modules`);
    return data.data;
  },

  async create(payload: {
    module: string;
    action: string;
    description?: string;
  }): Promise<Permission> {
    const { data } = await api.post<ApiResponse<Permission>>(`${PREFIX}/permissions`, payload);
    return data.data;
  },
};

// ============================================================
// USER ROLE ASSIGNMENT
// ============================================================
export const userRoleService = {
  async listUsers(params?: {
    page?: number;
    limit?: number;
    sortBy?: string;
    sortOrder?: string;
    search?: string;
    deptId?: number | null;
    status?: number | null;
  }): Promise<ApiResponse<User[]>> {
    const { data } = await api.get<ApiResponse<User[]>>(`${PREFIX}/users`, { params });
    return data;
  },

  /**
   * Look up the name of a single user by job title (case-insensitive).
   * Used by COA/PDF exports — avoids loading the full user list every time.
   */
  async findUserNameByJobTitle(title: string): Promise<string | null> {
    try {
      const { data } = await api.get<ApiResponse<{ id: number; name: string } | null>>(
        `${PREFIX}/users/by-job-title`,
        { params: { title } },
      );
      return data.data?.name ?? null;
    } catch {
      return null;
    }
  },

  async createUser(payload: {
    name: string;
    email: string;
    npk?: string;
    npp?: string;
    mobilePhone?: string;
    password?: string;
    roleId?: number;
    parentId?: number | null;
    jobId?: number | null;
    deptId?: number | null;
    divisionId?: number | null;
  }): Promise<Record<string, unknown>> {
    const { data } = await api.post<ApiResponse<Record<string, unknown>>>(`${PREFIX}/users`, payload);
    return data.data;
  },

  async updateUser(
    userId: number,
    payload: {
      name?: string;
      email?: string;
      npk?: string | null;
      npp?: string;
      mobilePhone?: string;
      password?: string;
      parentId?: number | null;
      jobId?: number | null;
      deptId?: number | null;
      divisionId?: number | null;
    }
  ): Promise<Record<string, unknown>> {
    const { data } = await api.put<ApiResponse<Record<string, unknown>>>(`${PREFIX}/users/${userId}`, payload);
    return data.data;
  },

  async deleteUser(userId: number): Promise<void> {
    await api.delete(`${PREFIX}/users/${userId}`);
  },

  async toggleStatus(userId: number): Promise<Record<string, unknown>> {
    const { data } = await api.patch<ApiResponse<Record<string, unknown>>>(`${PREFIX}/users/${userId}/status`);
    return data.data;
  },

  async resetPassword(userId: number): Promise<Record<string, unknown>> {
    const { data } = await api.post<ApiResponse<Record<string, unknown>>>(`${PREFIX}/users/${userId}/reset-password`);
    return data.data;
  },

  async userRoles(userId: number): Promise<UserRoleAssignment[]> {
    const { data } = await api.get<ApiResponse<UserRoleAssignment[]>>(
      `${PREFIX}/users/${userId}/roles`
    );
    return data.data;
  },

  async assign(
    userId: number,
    roleId: number,
    assignedBy: string = "SYSTEM"
  ): Promise<UserRoleAssignment> {
    const { data } = await api.post<ApiResponse<UserRoleAssignment>>(
      `${PREFIX}/users/${userId}/roles/${roleId}`,
      null,
      { params: { assignedBy } }
    );
    return data.data;
  },

  async remove(userId: number, roleId: number): Promise<void> {
    await api.delete(`${PREFIX}/users/${userId}/roles/${roleId}`);
  },

  async setPrimary(userId: number, roleId: number): Promise<void> {
    await api.put(`${PREFIX}/users/${userId}/roles/${roleId}/primary`);
  },

  async getSubordinates(userId: number): Promise<{ id: number; name: string; npk?: string; email?: string; jobTitle?: string | null; status?: number }[]> {
    const { data } = await api.get<ApiResponse<{ id: number; name: string; npk?: string; email?: string; jobTitle?: string | null; status?: number }[]>>(
      `${PREFIX}/users/${userId}/subordinates`
    );
    return data.data;
  },

  async getUsersDropdown(): Promise<{ id: number; name: string; npk?: string }[]> {
    const { data } = await api.get<ApiResponse<{ id: number; name: string; npk?: string }[]>>(
      `${PREFIX}/users/dropdown`
    );
    return data.data;
  },

  /**
   * Ambil daftar user berdasarkan role title untuk dropdown.
   * Contoh roleTitle: "Leader JSO", "Member JSO"
   * Jika user punya >1 role, ia akan muncul di setiap list yang sesuai.
   */
  async getUsersDropdownByRole(
    roleTitle: string
  ): Promise<{ id: number; name: string }[]> {
    const { data } = await api.get<ApiResponse<{ id: number; name: string }[]>>(
      `${PREFIX}/users/dropdown/by-role`,
      { params: { role: roleTitle } }
    );
    return data.data;
  },

  /**
   * Ambil daftar user berdasarkan kode role (AuthRole.code) untuk dropdown.
   * Contoh roleCode: "ANALIS_LAB", "SAMPLER_LAB"
   */
  async getUsersDropdownByRoleCode(
    roleCode: string
  ): Promise<{ id: number; name: string }[]> {
    const { data } = await api.get<ApiResponse<{ id: number; name: string }[]>>(
      `${PREFIX}/users/dropdown/by-role-code`,
      { params: { role: roleCode } }
    );
    return data.data;
  },
};

// ============================================================
// ROLE PERMISSION ASSIGNMENT
// ============================================================
export const rolePermissionService = {
  async list(roleId: number): Promise<RolePermission[]> {
    const { data } = await api.get<ApiResponse<RolePermission[]>>(
      `${PREFIX}/roles/${roleId}/permissions`
    );
    return data.data;
  },

  async assign(payload: {
    roleId: number;
    permissionId: number;
    grantedBy?: string;
  }): Promise<RolePermission> {
    const { data } = await api.post<ApiResponse<RolePermission>>(
      `${PREFIX}/roles/permissions`,
      payload
    );
    return data.data;
  },

  async remove(roleId: number, permissionId: number): Promise<void> {
    await api.delete(`${PREFIX}/roles/${roleId}/permissions/${permissionId}`);
  },

  async bulkSave(
    roleId: number,
    permissions: Array<{
      permissionId: number;
      canCreate: boolean;
      canRead: boolean;
      canUpdate: boolean;
      canDelete: boolean;
      canApprove: boolean;
      canExport: boolean;
    }>
  ): Promise<RolePermission[]> {
    const { data } = await api.put<ApiResponse<RolePermission[]>>(
      `${PREFIX}/roles/${roleId}/permissions/bulk`,
      permissions
    );
    return data.data;
  },
};

// ============================================================
// USER PERMISSION CHECKS
// ============================================================
export const userPermissionService = {
  async all(userId: number): Promise<Permission[]> {
    const { data } = await api.get<ApiResponse<Permission[]>>(
      `${PREFIX}/users/${userId}/permissions`
    );
    return data.data;
  },

  async check(
    userId: number,
    permissionCode: string,
    action: string
  ): Promise<boolean> {
    const { data } = await api.get<ApiResponse<boolean>>(
      `${PREFIX}/users/${userId}/check`,
      { params: { permissionCode, action } }
    );
    return data.data;
  },

  async moduleAccess(userId: number, module: string): Promise<ModuleAccess> {
    const { data } = await api.get<ApiResponse<ModuleAccess>>(
      `${PREFIX}/users/${userId}/module-access`,
      { params: { module } }
    );
    return data.data;
  },

  async menu(userId: number): Promise<MenuSection[]> {
    const { data } = await api.get<ApiResponse<MenuSection[]>>(
      `${PREFIX}/users/${userId}/menu`
    );
    return data.data;
  },
};

// ============================================================
// RBAC INITIALIZATION
// ============================================================
export const rbacAdminService = {
  async init(): Promise<void> {
    await api.post(`${PREFIX}/init`);
  },

  async sync(): Promise<void> {
    await api.post(`${PREFIX}/sync`);
  },
};

// ============================================================
// STRUKTUR ORGANISASI (db_auth) — opsi dropdown form pengguna
// ============================================================
export interface OrgOption {
  id: number;
  name: string;
}

export const orgService = {
  async departments(): Promise<OrgOption[]> {
    const { data } = await api.get<ApiResponse<OrgOption[]>>(`${PREFIX}/org/departments`);
    return data.data;
  },
  async divisions(): Promise<OrgOption[]> {
    const { data } = await api.get<ApiResponse<OrgOption[]>>(`${PREFIX}/org/divisions`);
    return data.data;
  },
  async jobTitles(): Promise<OrgOption[]> {
    const { data } = await api.get<ApiResponse<OrgOption[]>>(`${PREFIX}/org/job-titles`);
    return data.data;
  },
};
