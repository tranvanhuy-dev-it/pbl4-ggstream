import Link from "next/link";

function LogoMark() {
  return (
    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent text-accent-foreground shadow-sm">
      <svg
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M15 10l4.55-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.45.894L15 14" />
        <rect x="3" y="6" width="12" height="12" rx="2" />
      </svg>
    </div>
  );
}

export function AuthLayout({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <main className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 py-12">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 -z-10"
        style={{
          background:
            "radial-gradient(60% 50% at 50% 0%, color-mix(in srgb, var(--accent) 12%, transparent), transparent)",
        }}
      />

      <Link href="/" className="mb-8 flex items-center gap-2.5">
        <LogoMark />
        <span className="text-lg font-semibold tracking-tight">GGStream</span>
      </Link>

      <div className="w-full max-w-sm rounded-2xl border border-card-border bg-card p-8 shadow-xl shadow-black/5">
        <div className="mb-6 flex flex-col gap-1 text-center">
          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          <p className="text-sm text-muted">{subtitle}</p>
        </div>
        {children}
      </div>
    </main>
  );
}
