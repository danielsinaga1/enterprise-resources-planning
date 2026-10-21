import api from "./api";
import type {
  LoginPayload,
  LoginResponse,
  ProfileResponse,
  UpdateProfilePayload,
  User,
} from "../types";

const AUTH_PREFIX = "/api/v1/auth";

export const authService = {
  /**
   * Login with email & password.
   * POST /api/v1/auth/login
   * Returns LoginResponse directly (not wrapped in Response.java).
   */
  async login(payload: LoginPayload): Promise<LoginResponse> {
    const { data } = await api.post<LoginResponse>(
      `${AUTH_PREFIX}/login`,
      payload
    );
    return data;
  },

  /**
   * Logout current user (blacklists token in Redis).
   * POST /api/v1/auth/logout
   */
  async logout(): Promise<void> {
    await api.post(`${AUTH_PREFIX}/logout`);
    localStorage.removeItem("access_token");
    localStorage.removeItem("refresh_token");
    localStorage.removeItem("user");
  },

  /**
   * Refresh access token.
   * POST /api/v1/auth/refresh-token
   */
  async refresh(): Promise<LoginResponse> {
    const refreshToken = localStorage.getItem("refresh_token");
    const { data } = await api.post<LoginResponse>(
      `${AUTH_PREFIX}/refresh-token`,
      { refreshToken }
    );
    return data;
  },

  /**
   * Get user profile by userId.
   * GET /api/v1/profile/{userId}
   * Returns ProfileResponse directly.
   */
  async getProfile(userId: number): Promise<ProfileResponse> {
    const { data } = await api.get<ProfileResponse>(
      `/api/v1/profile/${userId}`
    );
    return data;
  },

  /**
   * Update user profile.
   * PUT /api/v1/profile/{userId}
   */
  async updateProfile(
    userId: number,
    payload: UpdateProfilePayload
  ): Promise<ProfileResponse> {
    const { data } = await api.put<ProfileResponse>(
      `/api/v1/profile/${userId}`,
      payload
    );
    return data;
  },

  // ---- Token helpers ----

  saveLoginData(loginResponse: LoginResponse): void {
    localStorage.setItem("access_token", loginResponse.token);
    if (loginResponse.refreshToken) {
      localStorage.setItem("refresh_token", loginResponse.refreshToken);
    }
    const user: User = {
      id: loginResponse.id ?? loginResponse.userId,
      npp: loginResponse.npp,
      npk: loginResponse.npk,
      name: loginResponse.name,
      email: loginResponse.email,
      mobilePhone: loginResponse.mobilePhone,
      departmentId: loginResponse.departmentId ?? undefined,
      status: 1,
      lastLogin: loginResponse.lastLogin,
      roles: loginResponse.roles,
      permissions: loginResponse.permissions,
    };
    localStorage.setItem("user", JSON.stringify(user));
  },

  getToken(): string | null {
    return localStorage.getItem("access_token");
  },

  getRefreshToken(): string | null {
    return localStorage.getItem("refresh_token");
  },

  getUser(): User | null {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  },

  isAuthenticated(): boolean {
    return !!localStorage.getItem("access_token");
  },

  hasRole(role: string): boolean {
    const user = this.getUser();
    return user?.roles?.includes(role) ?? false;
  },

  hasPermission(module: string, action: string): boolean {
    const user = this.getUser();
    const modulePerms = user?.permissions?.[module];
    return modulePerms?.includes(action) ?? false;
  },
};
