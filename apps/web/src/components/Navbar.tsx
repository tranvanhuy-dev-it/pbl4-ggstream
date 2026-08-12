"use client";

import Link from "next/link";
import { useAuth } from "@/features/auth/context/AuthContext";

function LogoMark() {
  return (
    <div
      className="flex h-8 w-8 items-center justify-center rounded-lg text-white shadow-sm"
      style={{ background: "var(--gradient)" }}
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M15 10l4.55-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.45.894L15 14" />
        <rect x="3" y="6" width="12" height="12" rx="2" />
      </svg>
    </div>
  );
}

export function Navbar() {
  const { status, user, logout } = useAuth();

  return (
    <header className="sticky top-0 z-10 border-b border-card-border bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
        <Link href="/" className="flex items-center gap-2.5">
          <LogoMark />
          <span className="text-base font-semibold tracking-tight">Meet Platform</span>
        </Link>

        {status === "authenticated" && user && (
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-accent/15 text-sm font-medium text-accent">
                {user.displayName.trim().charAt(0).toUpperCase() || "?"}
              </div>
              <span className="hidden text-sm sm:inline">{user.displayName}</span>
            </div>
            <button
              onClick={logout}
              className="rounded-lg border border-input-border px-3 py-1.5 text-sm font-medium transition-colors hover:bg-black/5 dark:hover:bg-white/10"
            >
              Đăng xuất
            </button>
          </div>
        )}

        {status === "unauthenticated" && (
          <div className="flex items-center gap-3">
            <Link href="/login" className="text-sm font-medium text-muted hover:text-foreground">
              Đăng nhập
            </Link>
            <Link
              href="/register"
              className="rounded-lg px-3.5 py-1.5 text-sm font-medium text-white shadow-sm transition-opacity hover:opacity-90"
              style={{ background: "var(--gradient)" }}
            >
              Đăng ký
            </Link>
          </div>
        )}
      </div>
    </header>
  );
}
