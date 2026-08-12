import { AuthLayout } from "@/features/auth/components/AuthLayout";
import { LoginForm } from "@/features/auth/components/LoginForm";

export default function LoginPage() {
  return (
    <AuthLayout title="Chào mừng trở lại" subtitle="Đăng nhập để tiếp tục vào các cuộc họp của bạn">
      <LoginForm />
    </AuthLayout>
  );
}
