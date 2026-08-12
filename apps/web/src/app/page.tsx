import { BackendStatus } from "@/components/BackendStatus";

export default function Home() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-6 px-6 text-center">
      <h1 className="text-3xl font-semibold tracking-tight">Meet Platform</h1>
      <p className="text-sm text-black/60 dark:text-white/60 max-w-md">
        Realtime meeting platform foundation. Authentication and meeting
        creation land in upcoming milestones.
      </p>
      <BackendStatus />
    </main>
  );
}
