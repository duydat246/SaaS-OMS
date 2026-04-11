export interface UserInfo {
  id: number;
  email: string;
  fullName: string;
  role: "OWNER" | "STAFF";
  tenantId: number;
  shopName: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: UserInfo;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  shopName: string;
  email: string;
  password: string;
  fullName: string;
  platform?: string;
}
