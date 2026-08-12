import { RegisterForm } from "@/features/auth/components/RegisterForm";

export default function RegisterPage() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-8 px-6">
      <h1 className="text-2xl font-semibold tracking-tight">Create your account</h1>
      <RegisterForm />
    </main>
  );
}
