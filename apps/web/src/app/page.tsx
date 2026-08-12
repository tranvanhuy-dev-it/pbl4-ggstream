"use client";

import Link from "next/link";
import { Navbar } from "@/components/Navbar";
import { BackendStatus } from "@/components/BackendStatus";
import { useAuth } from "@/features/auth/context/AuthContext";
import { CreateJoinMeeting } from "@/features/meeting/components/CreateJoinMeeting";

export default function Home() {
  const { status, user } = useAuth();

  return (
    <div className="flex min-h-screen flex-col">
      <Navbar />

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col items-center justify-center gap-10 px-6 py-16">
        {status === "authenticated" && user ? (
          <div className="flex w-full max-w-md flex-col items-center gap-6">
            <div className="flex flex-col items-center gap-2 text-center">
              <h1 className="text-2xl font-semibold tracking-tight">Bắt đầu hoặc tham gia cuộc họp</h1>
              <p className="text-sm text-muted">Chào mừng trở lại, {user.displayName}.</p>
            </div>
            <div className="w-full rounded-2xl border border-card-border bg-card p-6 shadow-xl shadow-black/5">
              <CreateJoinMeeting />
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-6 text-center">
            <h1 className="max-w-xl text-4xl font-semibold tracking-tight">
              <span className="gradient-text">Họp trực tuyến</span> thời gian thực
            </h1>
            <p className="max-w-md text-sm text-muted">
              Tạo phòng, chia sẻ đường dẫn và trò chuyện trực tiếp — sử dụng
              WebRTC kết nối ngang hàng, dữ liệu video không đi qua máy chủ.
            </p>
            <div className="flex gap-3">
              <Link
                href="/register"
                className="rounded-lg px-5 py-2.5 text-sm font-medium text-white shadow-sm transition-opacity hover:opacity-90"
                style={{ background: "var(--gradient)" }}
              >
                Bắt đầu ngay
              </Link>
              <Link
                href="/login"
                className="rounded-lg border border-input-border px-5 py-2.5 text-sm font-medium transition-colors hover:bg-black/5 dark:hover:bg-white/10"
              >
                Đăng nhập
              </Link>
            </div>
          </div>
        )}

        <BackendStatus />
      </main>

      <footer className="border-t border-card-border py-6 text-center text-xs text-muted">
        Meet Platform — ứng dụng họp trực tuyến tập trung vào mạng máy tính, không dùng Docker.
      </footer>
    </div>
  );
}
