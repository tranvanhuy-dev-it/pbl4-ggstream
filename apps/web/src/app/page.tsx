"use client";

import Link from "next/link";
import { BackendStatus } from "@/components/BackendStatus";
import { useAuth } from "@/features/auth/context/AuthContext";

export default function Home() {
  const { status, user, logout } = useAuth();

  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-6 px-6 text-center">
      <h1 className="text-3xl font-semibold tracking-tight">Meet Platform</h1>
      <p className="text-sm text-black/60 dark:text-white/60 max-w-md">
        Realtime meeting platform foundation. Meeting creation lands in the
        next milestone.
      </p>
      <BackendStatus />

      {status === "loading" && <p className="text-sm text-black/50 dark:text-white/50">Loading…</p>}

      {status === "unauthenticated" && (
        <div className="flex gap-3">
          <Link
            href="/login"
            className="rounded-md border border-black/15 dark:border-white/20 px-4 py-2 text-sm font-medium"
          >
            Sign in
          </Link>
          <Link
            href="/register"
            className="rounded-md bg-foreground text-background px-4 py-2 text-sm font-medium"
          >
            Register
          </Link>
        </div>
      )}

      {status === "authenticated" && user && (
        <div className="flex flex-col items-center gap-3">
          <p className="text-sm">
            Signed in as <span className="font-medium">{user.displayName}</span> ({user.email})
          </p>
          <button
            onClick={logout}
            className="rounded-md border border-black/15 dark:border-white/20 px-4 py-2 text-sm font-medium"
          >
            Sign out
          </button>
        </div>
      )}
    </main>
  );
}
