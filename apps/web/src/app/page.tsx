"use client";

import Link from "next/link";
import { BackendStatus } from "@/components/BackendStatus";
import { useAuth } from "@/features/auth/context/AuthContext";
import { CreateJoinMeeting } from "@/features/meeting/components/CreateJoinMeeting";

export default function Home() {
  const { status, user, logout } = useAuth();

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 px-6 py-12 text-center">
      <h1 className="text-3xl font-semibold tracking-tight">Meet Platform</h1>
      <p className="max-w-md text-sm text-muted">
        Realtime meeting platform. WebRTC calling lands in the next milestone
        — for now, meetings can be created and joined.
      </p>
      <BackendStatus />

      {status === "loading" && <p className="text-sm text-muted">Loading…</p>}

      {status === "unauthenticated" && (
        <div className="flex gap-3">
          <Link
            href="/login"
            className="rounded-lg border border-input-border px-4 py-2 text-sm font-medium transition-colors hover:bg-black/5 dark:hover:bg-white/10"
          >
            Sign in
          </Link>
          <Link
            href="/register"
            className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-accent-foreground transition-colors hover:bg-accent-hover"
          >
            Register
          </Link>
        </div>
      )}

      {status === "authenticated" && user && (
        <div className="flex flex-col items-center gap-6">
          <div className="flex items-center gap-3 text-sm">
            <span>
              Signed in as <span className="font-medium">{user.displayName}</span>
            </span>
            <button onClick={logout} className="text-muted hover:underline">
              Sign out
            </button>
          </div>
          <CreateJoinMeeting />
        </div>
      )}
    </main>
  );
}
