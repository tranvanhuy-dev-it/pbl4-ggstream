import { LoginForm } from "@/features/auth/components/LoginForm";

export default function LoginPage() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-8 px-6">
      <h1 className="text-2xl font-semibold tracking-tight">Sign in</h1>
      <LoginForm />
    </main>
  );
}
