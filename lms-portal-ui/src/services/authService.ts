import { apiGet, apiPost } from "@/lib/api";

export interface AuthResponse {
  token: string;
  username: string;
  name: string;
  email: string;
  role: string;
  roles: string[];
  tenantId: string;
  expiresIn: number;
}

export interface MeResponse {
  id: string;
  username: string;
  name: string;
  email: string;
  role: string;
  roles: string[];
  tenantId: string;
}

export const authService = {
  async login(username: string, password: string): Promise<AuthResponse> {
    const result = await apiPost<AuthResponse>("/proxy/auth/api/auth/login", {
      username,
      password,
    });
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Login failed");
    }
    return result.data;
  },

  async me(): Promise<MeResponse> {
    const result = await apiGet<MeResponse>("/proxy/auth/api/auth/me");
    if (result.error || !result.data) {
      throw new Error(result.error ?? "Failed to fetch user");
    }
    return result.data;
  },
};
