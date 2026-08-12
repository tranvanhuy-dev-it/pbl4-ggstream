"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import * as authApi from "@/features/auth/api/authApi";
import type {
  LoginInput,
  RegisterInput,
  UserProfile,
} from "@/features/auth/api/authApi";

const TOKEN_STORAGE_KEY = "meet.auth.token";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  status: AuthStatus;
  user: UserProfile | null;
  token: string | null;
  login: (input: LoginInput) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<UserProfile | null>(null);
  const [token, setToken] = useState<string | null>(null);

  useEffect(() => {
    const storedToken = window.localStorage.getItem(TOKEN_STORAGE_KEY);
    if (!storedToken) {
      setStatus("unauthenticated");
      return;
    }

    authApi
      .fetchCurrentUser(storedToken)
      .then((profile) => {
        setToken(storedToken);
        setUser(profile);
        setStatus("authenticated");
      })
      .catch(() => {
        window.localStorage.removeItem(TOKEN_STORAGE_KEY);
        setStatus("unauthenticated");
      });
  }, []);

  const applySession = useCallback(async (accessToken: string) => {
    const profile = await authApi.fetchCurrentUser(accessToken);
    window.localStorage.setItem(TOKEN_STORAGE_KEY, accessToken);
    setToken(accessToken);
    setUser(profile);
    setStatus("authenticated");
  }, []);

  const login = useCallback(
    async (input: LoginInput) => {
      const response = await authApi.login(input);
      await applySession(response.accessToken);
    },
    [applySession],
  );

  const registerUser = useCallback(
    async (input: RegisterInput) => {
      const response = await authApi.register(input);
      await applySession(response.accessToken);
    },
    [applySession],
  );

  const logout = useCallback(() => {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY);
    setToken(null);
    setUser(null);
    setStatus("unauthenticated");
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, token, login, register: registerUser, logout }),
    [status, user, token, login, registerUser, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
