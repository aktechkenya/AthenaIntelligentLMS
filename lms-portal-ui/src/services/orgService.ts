import { apiGet, apiPut, apiPost, apiDelete } from "@/lib/api";

export interface OrgSettings {
  tenantId: string;
  currency: string;
  orgName: string | null;
  countryCode: string | null;
  timezone: string;
  twoFactorEnabled: boolean;
  sessionTimeoutMinutes: number;
  auditTrailEnabled: boolean;
  ipWhitelistEnabled: boolean;
}

export interface UpdateOrgSettings {
  currency?: string;
  orgName?: string;
  countryCode?: string;
  timezone?: string;
  twoFactorEnabled?: boolean;
  sessionTimeoutMinutes?: number;
  auditTrailEnabled?: boolean;
  ipWhitelistEnabled?: boolean;
}

export interface Branch {
  id: string;
  tenantId: string;
  name: string;
  code: string;
  type: string;
  address: string;
  city: string;
  county: string;
  country: string;
  phone: string;
  email: string;
  managerId: string;
  status: string;
  parentId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBranchRequest {
  name: string;
  code: string;
  type: string;
  address?: string;
  city?: string;
  county?: string;
  country?: string;
  phone?: string;
  email?: string;
  managerId?: string;
  status?: string;
  parentId?: string | null;
}

export interface User {
  id: string;
  tenantId: string;
  username: string;
  name: string;
  email: string;
  role: string;
  status: string;
  branchId: string | null;
  lastLogin: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UsersResponse {
  content: User[];
  totalElements: number;
  page: number;
  size: number;
}

export interface CreateUserRequest {
  username: string;
  name: string;
  email: string;
  role: string;
}

export interface UpdateUserRequest {
  name: string;
  email: string;
  role: string;
  branchId?: string | null;
}

// Org settings live in account-service, proxied via /proxy/auth/
const BASE = "/proxy/auth/api/v1/organization";

export const orgService = {
  async getSettings(): Promise<OrgSettings> {
    const result = await apiGet<OrgSettings>(`${BASE}/settings`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to load org settings");
    }
    return result.data;
  },

  async updateSettings(req: UpdateOrgSettings): Promise<OrgSettings> {
    const result = await apiPut<OrgSettings>(`${BASE}/settings`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to save org settings");
    }
    return result.data;
  },

  async listBranches(): Promise<Branch[]> {
    const result = await apiGet<Branch[]>(`${BASE}/branches`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to load branches");
    }
    return result.data;
  },

  async createBranch(req: CreateBranchRequest): Promise<Branch> {
    const result = await apiPost<Branch>(`${BASE}/branches`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to create branch");
    }
    return result.data;
  },

  async updateBranch(id: string, req: CreateBranchRequest): Promise<Branch> {
    const result = await apiPut<Branch>(`${BASE}/branches/${id}`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to update branch");
    }
    return result.data;
  },

  async deleteBranch(id: string): Promise<void> {
    const result = await apiDelete(`${BASE}/branches/${id}`);
    if (result.error && result.status !== 204) {
      throw new Error(result.error ?? "Failed to delete branch");
    }
  },

  async getUsers(page = 0, size = 100): Promise<UsersResponse> {
    const result = await apiGet<UsersResponse>(`${BASE}/users?page=${page}&size=${size}`);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to load users");
    }
    return result.data;
  },

  async createUser(req: CreateUserRequest): Promise<User> {
    const result = await apiPost<User>(`${BASE}/users`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to create user");
    }
    return result.data;
  },

  async updateUser(id: string, req: UpdateUserRequest): Promise<User> {
    const result = await apiPut<User>(`${BASE}/users/${id}`, req);
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to update user");
    }
    return result.data;
  },

  async toggleUserStatus(id: string, status: string): Promise<User> {
    const result = await apiPut<User>(`${BASE}/users/${id}/status`, { status });
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to update user status");
    }
    return result.data;
  },
};
