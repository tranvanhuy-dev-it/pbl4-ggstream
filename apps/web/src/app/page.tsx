"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Navbar } from "@/components/Navbar";
import { ThemeToggle } from "@/components/ThemeToggle";
import { BackendStatus } from "@/components/BackendStatus";
import { useAuth } from "@/features/auth/context/AuthContext";
import { CreateJoinMeeting } from "@/features/meeting/components/CreateJoinMeeting";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import type { Meeting } from "@/features/meeting/api/meetingApi";
import { LivestreamIcon } from "@/components/icons";

function Brand() {
  return (
    <Link href="/" className="flex items-center gap-2.5">
      <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-accent text-accent-foreground">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M15 10l4.5-2.3A1 1 0 0121 8.6v6.8a1 1 0 01-1.5.9L15 14" /><rect x="3" y="6" width="12" height="12" rx="2" />
        </svg>
      </span>
      <span className="hidden text-lg font-semibold tracking-tight md:inline">GGStream</span>
    </Link>
  );
}

function EmptyMeetings({ date }: { date: Date }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 pb-10 text-center">
      <svg className="mb-7 w-64 max-w-full text-foreground" viewBox="0 0 360 210" fill="none" aria-hidden="true">
        <path d="M67 169h229M104 160c-24-12-29-43-9-59 24-20 55-3 57 27 2 25-23 43-48 32z" stroke="currentColor" strokeWidth="2" />
        <path d="M209 67l23 93h-19l-24-89 20-4z" fill="#d4d4d4" stroke="currentColor" strokeWidth="2" />
        <path d="M155 109h57v51h-57zM212 119l35-16v54l-35-16z" fill="var(--card)" stroke="currentColor" strokeWidth="2" />
        <circle cx="280" cy="53" r="15" fill="#a3a3a3" />
        <path d="M46 85c33-28 69-14 58-51M232 58c14-29 45-31 48-12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      </svg>
      <h1 className="text-2xl font-medium tracking-tight md:text-4xl">Không có cuộc họp vào ngày này</h1>
      <p className="mt-2 text-sm font-medium capitalize">{date.toLocaleDateString("vi-VN", { weekday: "long", day: "numeric", month: "long" })}</p>
      <p className="mt-3 text-sm text-muted md:text-base">Tạo một cuộc họp mới hoặc nhập mã để tham gia ngay.</p>
    </div>
  );
}

export default function Home() {
  const { status, user, token, logout } = useAuth();
  const router = useRouter();
  const [meetings, setMeetings] = useState<Meeting[]>([]);
  const [scheduleRevision, setScheduleRevision] = useState(0);
  const [weekOffset, setWeekOffset] = useState(0);
  const [selectedDate, setSelectedDate] = useState(() => {
    const date = new Date(); date.setHours(0, 0, 0, 0); return date;
  });
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  useEffect(() => {
    if (!token) return;
    const from = new Date();
    from.setHours(0, 0, 0, 0);
    from.setDate(from.getDate() + weekOffset * 7);
    const to = new Date(from);
    to.setDate(to.getDate() + 7);
    meetingApi.listMyMeetings(token, from.toISOString(), to.toISOString())
      .then(setMeetings)
      .catch(() => setMeetings([]));
  }, [token, scheduleRevision, weekOffset]);

  const weekDays = Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() + weekOffset * 7 + index);
    return date;
  });
  const sameDay = (left: Date, right: Date) =>
    left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth() && left.getDate() === right.getDate();
  const selectedMeetings = meetings.filter((meeting) =>
    meeting.status !== "CANCELLED" && meeting.scheduledStartAt && sameDay(new Date(meeting.scheduledStartAt), selectedDate));

  async function copyCode(code: string) {
    await navigator.clipboard.writeText(code);
    setCopiedCode(code);
    window.setTimeout(() => setCopiedCode((current) => current === code ? null : current), 1800);
  }

  function changeWeek(delta: number) {
    const offset = weekOffset + delta;
    setWeekOffset(offset);
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() + offset * 7);
    setSelectedDate(date);
  }

  function goToday() {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    setWeekOffset(0);
    setSelectedDate(date);
  }

  async function cancelScheduled(meeting: Meeting) {
    if (!token) return;
    await meetingApi.cancelMeeting(token, meeting.id);
    setScheduleRevision((value) => value + 1);
  }

  if (status === "authenticated" && user) {
    return (
      <div className="flex h-dvh flex-col overflow-hidden bg-card">
        <header className="z-30 flex h-16 flex-shrink-0 items-center justify-between gap-4 border-b border-card-border bg-card px-4 lg:px-7">
          <Brand />
          <div className="absolute left-1/2 hidden -translate-x-1/2 lg:block">
            <CreateJoinMeeting onScheduled={() => setScheduleRevision((value) => value + 1)} />
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <button onClick={logout} title="Đăng xuất" className="flex h-9 w-9 items-center justify-center rounded-full bg-accent text-sm font-semibold text-accent-foreground">
              {user.displayName.trim().charAt(0).toUpperCase() || "?"}
            </button>
          </div>
        </header>

        <div className="flex min-h-0 flex-1">
          <aside className="hidden w-24 flex-shrink-0 flex-col items-center gap-5 border-r border-card-border py-6 md:flex">
            <button className="flex w-20 flex-col items-center gap-2 text-xs font-medium">
              <span className="flex h-11 w-14 items-center justify-center rounded-full bg-neutral-200 dark:bg-neutral-700">
                <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4M17 3v4M3 10h18" /></svg>
              </span>
              Cuộc họp
            </button>
            <button disabled className="flex w-20 flex-col items-center gap-2 text-xs text-muted">
              <span className="flex h-11 w-14 items-center justify-center rounded-full">
                <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M22 16.9v3a2 2 0 01-2.2 2 19.8 19.8 0 01-8.6-3.1 19.5 19.5 0 01-6-6A19.8 19.8 0 012.1 4.2 2 2 0 014.1 2h3a2 2 0 012 1.7c.1.9.3 1.8.7 2.6a2 2 0 01-.5 2.1L8.1 9.6a16 16 0 006 6l1.2-1.2a2 2 0 012.1-.5c.8.3 1.7.6 2.6.7a2 2 0 012 2.3z" /></svg>
              </span>
              Cuộc gọi
            </button>
            <Link href="/live" className="flex w-20 flex-col items-center gap-2 text-xs font-medium">
              <span className="flex h-11 w-14 items-center justify-center rounded-full hover:bg-neutral-200 dark:hover:bg-neutral-700">
                {LivestreamIcon}
              </span>
              Trực tiếp
            </Link>
          </aside>

          <main className="flex min-w-0 flex-1 flex-col overflow-y-auto px-4 py-5 md:px-8 lg:px-12">
            <div className="mx-auto flex w-full max-w-7xl flex-col">
              <div className="mb-5 lg:hidden">
                <CreateJoinMeeting onScheduled={() => setScheduleRevision((value) => value + 1)} />
              </div>

              <section className="mb-5 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                <div className="flex flex-shrink-0 items-center gap-3">
                  <h1 className="capitalize text-xl font-medium md:text-2xl">{selectedDate.toLocaleDateString("vi-VN", { weekday: "long", day: "numeric", month: "long" })}</h1>
                  <svg className="hidden text-muted sm:block" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4M17 3v4M3 10h18" /></svg>
                  {sameDay(selectedDate, new Date()) && (
                    <button onClick={goToday} className="rounded-full border border-input-border px-4 py-2 text-sm font-medium hover:bg-black/5 dark:hover:bg-white/10">Hôm nay</button>
                  )}
                </div>
                <div className="flex min-w-0 items-center justify-center gap-1">
                  <button onClick={() => changeWeek(-1)} aria-label="Tuần trước" className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full hover:bg-black/5 dark:hover:bg-white/10">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 18l-6-6 6-6" /></svg>
                  </button>
                  <div className="grid min-w-0 flex-1 grid-cols-7 gap-1 lg:w-[560px] lg:flex-none lg:gap-2">
                    {weekDays.map((date) => {
                      const selected = sameDay(date, selectedDate);
                      const hasMeeting = meetings.some((meeting) => meeting.status !== "CANCELLED" && meeting.scheduledStartAt && sameDay(new Date(meeting.scheduledStartAt), date));
                      return (
                        <button key={date.toISOString()} onClick={() => setSelectedDate(date)}
                          className={`relative flex min-w-0 flex-col items-center rounded-2xl px-1 py-2 text-xs transition-colors lg:px-3 ${selected ? "bg-accent text-accent-foreground" : "hover:bg-black/5 dark:hover:bg-white/10"}`}>
                          <span className={`uppercase ${selected ? "opacity-80" : "text-muted"}`}>{date.toLocaleDateString("vi-VN", { weekday: "short" })}</span>
                          <span className="mt-1 text-lg font-semibold">{date.getDate()}</span>
                          {hasMeeting && <span className={`absolute bottom-1 h-1 w-1 rounded-full ${selected ? "bg-white" : "bg-accent"}`} />}
                        </button>
                      );
                    })}
                  </div>
                  <button onClick={() => changeWeek(1)} aria-label="Tuần sau" className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full hover:bg-black/5 dark:hover:bg-white/10">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 18l6-6-6-6" /></svg>
                  </button>
                </div>
              </section>

              <section className="flex items-center gap-4 rounded-3xl bg-neutral-100 px-5 py-4 dark:bg-neutral-800 md:px-7">
                <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-accent text-accent-foreground">
                  <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /><rect x="9" y="10" width="6" height="5" rx="1" /><path d="M10 10V8a2 2 0 014 0v2" /></svg>
                </span>
                <div className="min-w-0">
                  <h2 className="text-sm font-semibold md:text-base">Cuộc họp của bạn được bảo vệ an toàn</h2>
                  <p className="mt-0.5 text-xs text-muted md:text-sm">Chỉ người có mã phòng hoặc được chủ phòng cho phép mới có thể tham gia.</p>
                </div>
              </section>
            </div>
            {selectedMeetings.length === 0 ? (
              <EmptyMeetings date={selectedDate} />
            ) : (
              <section className="mx-auto mt-6 flex w-full max-w-5xl flex-col gap-3 pb-8">
                <h2 className="mb-1 text-sm font-semibold text-muted">Cuộc họp đã lên lịch</h2>
                {selectedMeetings.map((meeting) => {
                  const start = new Date(meeting.scheduledStartAt as string);
                  const end = new Date(meeting.scheduledEndAt as string);
                  return (
                    <article key={meeting.id} className="group flex flex-col gap-4 rounded-2xl border border-card-border bg-card p-4 transition-shadow hover:shadow-md sm:flex-row sm:items-center md:p-5">
                      <div className="flex flex-shrink-0 items-center sm:w-40 sm:border-r sm:border-card-border sm:pr-5">
                        <p className="whitespace-nowrap text-base font-semibold tabular-nums md:text-lg">
                          {start.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}
                          <span className="mx-2 font-normal text-muted">–</span>
                          {end.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}
                        </p>
                      </div>
                      <div className="min-w-0 flex-1">
                        <h3 className="truncate text-base font-semibold">{meeting.title}</h3>
                        <div className="mt-1 flex items-center gap-2 text-xs text-muted">
                          <span>Mã phòng: <span className="font-mono text-foreground">{meeting.code}</span></span>
                          <button onClick={() => copyCode(meeting.code)} title="Sao chép mã phòng"
                            className="inline-flex items-center gap-1 rounded-md px-2 py-1 font-medium text-accent hover:bg-accent/10">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="9" y="9" width="13" height="13" rx="2" /><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" /></svg>
                            {copiedCode === meeting.code ? "Đã sao chép" : "Sao chép"}
                          </button>
                        </div>
                      </div>
                      <div className="flex flex-shrink-0 gap-2">
                        {meeting.status === "ENDED" ? (
                          <span className="inline-flex items-center rounded-lg bg-neutral-100 px-3 py-2 text-sm font-medium text-muted dark:bg-neutral-800">
                            Đã kết thúc
                          </span>
                        ) : (
                          <button onClick={() => router.push(`/meet/${meeting.code}`)}
                            className="rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover">
                            {meeting.status === "ACTIVE" ? "Vào phòng" : "Mở"}
                          </button>
                        )}
                        {meeting.status === "SCHEDULED" && (
                          <button onClick={() => cancelScheduled(meeting)} className="rounded-lg border border-danger-border px-3 py-2 text-sm text-danger">Hủy</button>
                        )}
                      </div>
                    </article>
                  );
                })}
              </section>
            )}
            <div className="mt-auto flex justify-center pb-2 pt-6"><BackendStatus /></div>
          </main>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col">
      <Navbar />
      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col items-center justify-center gap-6 px-6 py-16 text-center">
        <h1 className="max-w-xl text-4xl font-semibold tracking-tight">Họp trực tuyến thời gian thực</h1>
        <p className="max-w-md text-sm text-muted">Tạo phòng, chia sẻ đường dẫn và trò chuyện trực tiếp với kết nối WebRTC ngang hàng.</p>
        <div className="flex gap-3">
          <Link href="/register" className="rounded-lg bg-accent px-5 py-2.5 text-sm font-medium text-accent-foreground hover:bg-accent-hover">Bắt đầu ngay</Link>
          <Link href="/login" className="rounded-lg border border-input-border px-5 py-2.5 text-sm font-medium">Đăng nhập</Link>
        </div>
      </main>
    </div>
  );
}
