"use client";

import { useEffect, useState } from "react";
import { API_URL } from "@/lib/api/config";

type Status = "checking" | "up" | "down";

export function BackendStatus() {
  const [status, setStatus] = useState<Status>("checking");

  useEffect(() => {
    let cancelled = false;

    fetch(`${API_URL}/actuator/health`)
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((data) => {
        if (!cancelled) setStatus(data.status === "UP" ? "up" : "down");
      })
      .catch(() => {
        if (!cancelled) setStatus("down");
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const label = {
    checking: "Đang kiểm tra máy chủ…",
    up: "Đã kết nối máy chủ",
    down: "Không thể kết nối máy chủ",
  }[status];

  const dotColor = {
    checking: "bg-yellow-400",
    up: "bg-green-500",
    down: "bg-red-500",
  }[status];

  return (
    <div className="inline-flex items-center gap-2 rounded-full border border-black/10 dark:border-white/15 px-3 py-1 text-sm">
      <span className={`h-2 w-2 rounded-full ${dotColor}`} />
      <span>{label}</span>
    </div>
  );
}
