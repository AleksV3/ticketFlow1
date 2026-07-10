"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { clearSession, readSession, type StoredSession } from "@/lib/auth";

export default function DashboardPage() {
  const router = useRouter();
  const [session, setSession] = useState<StoredSession | null>(null);

  useEffect(() => {
    const storedSession = readSession();
    if (!storedSession) {
      router.replace("/login");
      return;
    }

    setSession(storedSession);
  }, [router]);

  function handleSignOut() {
    clearSession();
    router.replace("/login");
  }

  if (!session) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-100">
        <p className="text-sm text-slate-600">Loading session…</p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-100 px-6 py-12">
      <section className="mx-auto max-w-4xl rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <div className="flex items-start justify-between gap-6">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500">
              TicketFlow1
            </p>
            <h1 className="mt-3 text-3xl font-bold text-slate-900">Dashboard</h1>
            <p className="mt-2 text-sm text-slate-600">
              Placeholder view after successful login.
            </p>
          </div>

          <button
            className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
            onClick={handleSignOut}
            type="button"
          >
            Sign out
          </button>
        </div>

        <dl className="mt-8 grid gap-4 sm:grid-cols-2">
          <InfoCard label="Name" value={session.user.displayName} />
          <InfoCard label="Email" value={session.user.email} />
          <InfoCard label="Role" value={session.user.roleName} />
          <InfoCard label="Party" value={session.user.party} />
          <InfoCard
            label="Organization"
            value={session.user.organizationName ?? "TicketFlow1 internal"}
          />
          <InfoCard
            label="Permissions"
            value={session.user.permissions.length.toString()}
          />
        </dl>
      </section>
    </main>
  );
}

type InfoCardProps = {
  label: string;
  value: string;
};

function InfoCard({ label, value }: InfoCardProps) {
  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
      <dt className="text-sm font-medium text-slate-500">{label}</dt>
      <dd className="mt-2 text-base font-semibold text-slate-900">{value}</dd>
    </div>
  );
}
