"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/features/auth/context/AuthContext";
import { ApiClientError } from "@/lib/api/client";
import { TextField } from "./TextField";
import { ErrorAlert } from "./ErrorAlert";
import { SubmitButton } from "./SubmitButton";

export function RegisterForm() {
  const { register } = useAuth();
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register({ email, password, displayName });
      router.push("/");
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Đã xảy ra lỗi, vui lòng thử lại");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <TextField
        id="displayName"
        label="Tên hiển thị"
        type="text"
        autoComplete="name"
        required
        value={displayName}
        onChange={(e) => setDisplayName(e.target.value)}
      />
      <TextField
        id="email"
        label="Email"
        type="email"
        autoComplete="email"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />
      <TextField
        id="password"
        label="Mật khẩu"
        type="password"
        autoComplete="new-password"
        required
        minLength={8}
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <p className="-mt-2 text-xs text-muted">Tối thiểu 8 ký tự.</p>
      {error && <ErrorAlert message={error} />}
      <SubmitButton loading={submitting} loadingLabel="Đang tạo tài khoản…">
        Tạo tài khoản
      </SubmitButton>
      <p className="text-center text-sm text-muted">
        Đã có tài khoản?{" "}
        <Link href="/login" className="font-medium text-accent hover:underline">
          Đăng nhập
        </Link>
      </p>
    </form>
  );
}
