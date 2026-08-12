import { AuthLayout } from "@/features/auth/components/AuthLayout";
import { RegisterForm } from "@/features/auth/components/RegisterForm";

export default function RegisterPage() {
  return (
    <AuthLayout title="Tạo tài khoản của bạn" subtitle="Bắt đầu tổ chức cuộc họp chỉ trong vài phút">
      <RegisterForm />
    </AuthLayout>
  );
}
