import { apiFetch } from "@/lib/api/client";

export interface UserSummary {
  id: string;
  email: string;
  displayName: string;
}

export interface UserProfile extends UserSummary {
  avatarUrl: string | null;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: UserSummary;
}

export interface RegisterInput {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

export function register(input: RegisterInput): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function login(input: LoginInput): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function fetchCurrentUser(token: string): Promise<UserProfile> {
  return apiFetch<UserProfile>("/api/v1/users/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
}
